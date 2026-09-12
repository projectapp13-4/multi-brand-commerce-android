package com.gurbakir.storefront

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.gurbakir.foundation.config.ProtectedStoreIdentity
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.security.KeyStore
import java.time.Instant
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Gate2CartStoreCompatibilityTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        gate2Identities.forEach { identity -> FrozenGate2CartStore.clear(context, identity) }
    }

    @After
    fun tearDown() {
        gate2Identities.forEach { identity -> FrozenGate2CartStore.clear(context, identity) }
    }

    @Test
    fun frozenGate2WriterAndGate3ReaderAreCompatibleForExactDevelopmentAndStagingIdentities() = runBlocking {
        gate2Identities.forEach { identity ->
            FrozenGate2CartStore.write(
                context = context,
                identity = identity,
                cartId = CART_ID,
                expiresAt = EXPIRES_AT,
                ownershipCode = 2
            )

            val restored = AndroidKeystoreCartSessionStore(context, identity).read()

            assertTrue(restored?.id?.use { it == CART_ID } == true)
            assertEquals(EXPIRES_AT, restored?.expiresAt)
            assertEquals(CartOwnership.CUSTOMER_ASSOCIATED, restored?.ownership)
        }
    }

    @Test
    fun gate3WriterAndFrozenGate2ReaderAreCompatibleForExactDevelopmentAndStagingIdentities() = runBlocking {
        gate2Identities.forEach { identity ->
            AndroidKeystoreCartSessionStore(context, identity).write(
                PersistedCart(
                    id = SensitiveCartId.from(CART_ID),
                    expiresAt = EXPIRES_AT,
                    ownership = CartOwnership.DETACH_PENDING
                )
            )

            val restored = FrozenGate2CartStore.read(context, identity)

            assertEquals(CART_ID, restored.cartId)
            assertEquals(EXPIRES_AT, restored.expiresAt)
            assertEquals(3, restored.ownershipCode)
        }
    }

    @Test
    fun frozenGate2OwnershipCodesAndLegacyV1QuarantineRemainExact() = runBlocking {
        val identity = gate2Identities.first()
        listOf(
            1 to CartOwnership.ANONYMOUS,
            2 to CartOwnership.CUSTOMER_ASSOCIATED,
            3 to CartOwnership.DETACH_PENDING,
            4 to CartOwnership.QUARANTINED
        ).forEach { (code, expected) ->
            FrozenGate2CartStore.write(context, identity, CART_ID, EXPIRES_AT, code)
            assertEquals(expected, AndroidKeystoreCartSessionStore(context, identity).read()?.ownership)
        }

        FrozenGate2CartStore.writeLegacyV1(context, identity, CART_ID, EXPIRES_AT)

        assertEquals(
            CartOwnership.QUARANTINED,
            AndroidKeystoreCartSessionStore(context, identity).read()?.ownership
        )
    }

    @Test
    fun everyCorruptionClassFailsClosedAndClearsBothStoredFields() = runBlocking {
        val identity = gate2Identities.first()
        val preferences = context.getSharedPreferences(identity.preferencesName, Context.MODE_PRIVATE)

        suspend fun assertRejected(seed: suspend () -> Unit) {
            FrozenGate2CartStore.clear(context, identity)
            seed()
            assertNull(AndroidKeystoreCartSessionStore(context, identity).read())
            assertFalse(preferences.contains("iv"))
            assertFalse(preferences.contains("ciphertext"))
        }

        assertRejected { preferences.edit().putString("iv", VALID_BASE64).commit() }
        assertRejected { preferences.edit().putString("ciphertext", VALID_BASE64).commit() }
        assertRejected {
            preferences.edit().putInt("iv", 7).putString("ciphertext", VALID_BASE64).commit()
        }
        assertRejected { preferences.edit().putString("iv", "!").putString("ciphertext", "!").commit() }
        assertRejected {
            preferences.edit()
                .putString("iv", Base64.encodeToString(ByteArray(12), Base64.NO_WRAP))
                .putString("ciphertext", Base64.encodeToString(byteArrayOf(1, 2, 3), Base64.NO_WRAP))
                .commit()
        }
        assertRejected {
            FrozenGate2CartStore.writeRaw(context, identity, byteArrayOf(0, 0, 0, 99))
        }
        assertRejected {
            FrozenGate2CartStore.writeRaw(
                context,
                identity,
                FrozenGate2CartStore.v2Payload(CART_ID, EXPIRES_AT, 1) + byteArrayOf(1)
            )
        }
        assertRejected {
            AndroidKeystoreCartSessionStore(context, identity).write(
                PersistedCart(SensitiveCartId.from(CART_ID), EXPIRES_AT, CartOwnership.ANONYMOUS)
            )
            FrozenGate2CartStore.deleteKey(identity)
        }
    }

    @Test
    fun bothAbsentIsEmptyAndClearDoesNotDeleteTheKeystoreKey() = runBlocking {
        val identity = gate2Identities.first()
        val store = AndroidKeystoreCartSessionStore(context, identity)

        assertNull(store.read())
        store.write(PersistedCart(SensitiveCartId.from(CART_ID), EXPIRES_AT, CartOwnership.ANONYMOUS))
        assertTrue(FrozenGate2CartStore.containsKey(identity))

        store.clear()

        assertNull(store.read())
        assertTrue(FrozenGate2CartStore.containsKey(identity))
    }

    private companion object {
        const val CART_ID = "gid://shopify/Cart/gate2-fixture?key=fake"
        const val VALID_BASE64 = "AA=="
        val EXPIRES_AT: Instant = Instant.ofEpochSecond(2_000_000_000L, 123_000_000L)
        val gate2Identities =
            listOf(
                ProtectedStoreIdentity(
                    preferencesName = "gurbakir_secure_cart_development",
                    keyAlias = "gurbakir.cart.development.v1"
                ),
                ProtectedStoreIdentity(
                    preferencesName = "gurbakir_secure_cart_staging",
                    keyAlias = "gurbakir.cart.staging.v1"
                )
            )
    }
}

private object FrozenGate2CartStore {
    private const val ANDROID_KEY_STORE = "AndroidKeyStore"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"

    data class DecodedCart(val cartId: String, val expiresAt: Instant, val ownershipCode: Int)

    fun write(
        context: Context,
        identity: ProtectedStoreIdentity,
        cartId: String,
        expiresAt: Instant,
        ownershipCode: Int
    ) = writeRaw(context, identity, v2Payload(cartId, expiresAt, ownershipCode))

    fun writeLegacyV1(context: Context, identity: ProtectedStoreIdentity, cartId: String, expiresAt: Instant) =
        writeRaw(
            context,
            identity,
            payload(version = 1, cartId = cartId, expiresAt = expiresAt, ownershipCode = null)
        )

    fun v2Payload(cartId: String, expiresAt: Instant, ownershipCode: Int): ByteArray = payload(
        version = 2,
        cartId = cartId,
        expiresAt = expiresAt,
        ownershipCode = ownershipCode
    )

    fun writeRaw(context: Context, identity: ProtectedStoreIdentity, plaintext: ByteArray) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey(identity))
        val ciphertext = cipher.doFinal(plaintext)
        check(
            context.getSharedPreferences(identity.preferencesName, Context.MODE_PRIVATE)
                .edit()
                .putString("iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
                .putString("ciphertext", Base64.encodeToString(ciphertext, Base64.NO_WRAP))
                .commit()
        )
    }

    fun read(context: Context, identity: ProtectedStoreIdentity): DecodedCart {
        val preferences = context.getSharedPreferences(identity.preferencesName, Context.MODE_PRIVATE)
        val iv = Base64.decode(requireNotNull(preferences.getString("iv", null)), Base64.NO_WRAP)
        val ciphertext =
            Base64.decode(requireNotNull(preferences.getString("ciphertext", null)), Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(identity), GCMParameterSpec(128, iv))
        return decodeV2(cipher.doFinal(ciphertext))
    }

    fun clear(context: Context, identity: ProtectedStoreIdentity) {
        check(context.getSharedPreferences(identity.preferencesName, Context.MODE_PRIVATE).edit().clear().commit())
        deleteKey(identity)
    }

    fun deleteKey(identity: ProtectedStoreIdentity) {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        if (keyStore.containsAlias(identity.keyAlias)) keyStore.deleteEntry(identity.keyAlias)
    }

    fun containsKey(identity: ProtectedStoreIdentity): Boolean =
        KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }.containsAlias(identity.keyAlias)

    private fun payload(version: Int, cartId: String, expiresAt: Instant, ownershipCode: Int?): ByteArray {
        val output = ByteArrayOutputStream()
        DataOutputStream(output).use { stream ->
            val cartBytes = cartId.toByteArray(Charsets.UTF_8)
            stream.writeInt(version)
            stream.writeInt(cartBytes.size)
            stream.write(cartBytes)
            stream.writeLong(expiresAt.epochSecond)
            stream.writeInt(expiresAt.nano)
            ownershipCode?.let(stream::writeInt)
        }
        return output.toByteArray()
    }

    private fun decodeV2(payload: ByteArray): DecodedCart =
        DataInputStream(ByteArrayInputStream(payload)).use { stream ->
            check(stream.readInt() == 2)
            val bytes = ByteArray(stream.readInt())
            stream.readFully(bytes)
            val cartId = bytes.toString(Charsets.UTF_8)
            val expiresAt = Instant.ofEpochSecond(stream.readLong(), stream.readInt().toLong())
            val ownershipCode = stream.readInt()
            check(stream.available() == 0)
            DecodedCart(cartId, expiresAt, ownershipCode)
        }

    private fun getOrCreateKey(identity: ProtectedStoreIdentity): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        (keyStore.getKey(identity.keyAlias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                identity.keyAlias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .setUserAuthenticationRequired(false)
                .build()
        )
        return generator.generateKey()
    }
}
