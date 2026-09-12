package com.gurbakir.account.session

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
class Gate2CustomerStoreCompatibilityTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        gate2Identities.forEach { identity -> FrozenGate2CustomerStore.clear(context, identity) }
    }

    @After
    fun tearDown() {
        gate2Identities.forEach { identity -> FrozenGate2CustomerStore.clear(context, identity) }
    }

    @Test
    fun frozenGate2WriterAndGate3ReaderAreCompatibleForExactDevelopmentAndStagingIdentities() = runBlocking {
        gate2Identities.forEach { identity ->
            FrozenGate2CustomerStore.write(
                context = context,
                identity = identity,
                accessToken = ACCESS_TOKEN,
                refreshToken = REFRESH_TOKEN,
                idToken = ID_TOKEN,
                expiresAt = EXPIRES_AT
            )

            val restored = AndroidKeystoreCustomerSessionStore(context, identity).read()

            assertTrue(restored?.accessToken?.use { it == ACCESS_TOKEN } == true)
            assertTrue(restored?.refreshToken?.use { it == REFRESH_TOKEN } == true)
            assertTrue(restored?.idToken?.use { it == ID_TOKEN } == true)
            assertEquals(EXPIRES_AT, restored?.expiresAt)
        }
    }

    @Test
    fun gate3WriterAndFrozenGate2ReaderAreCompatibleForExactDevelopmentAndStagingIdentities() = runBlocking {
        gate2Identities.forEach { identity ->
            AndroidKeystoreCustomerSessionStore(context, identity).write(
                CustomerSession(
                    accessToken = SensitiveToken.from(ACCESS_TOKEN),
                    refreshToken = SensitiveToken.from(REFRESH_TOKEN),
                    idToken = SensitiveToken.from(ID_TOKEN),
                    expiresAt = EXPIRES_AT
                )
            )

            val restored = FrozenGate2CustomerStore.read(context, identity)

            assertEquals(ACCESS_TOKEN, restored.accessToken)
            assertEquals(REFRESH_TOKEN, restored.refreshToken)
            assertEquals(ID_TOKEN, restored.idToken)
            assertEquals(EXPIRES_AT, restored.expiresAt)
        }
    }

    @Test
    fun everyCorruptionClassFailsClosedAndClearsBothStoredFields() = runBlocking {
        val identity = gate2Identities.first()
        val preferences = context.getSharedPreferences(identity.preferencesName, Context.MODE_PRIVATE)

        suspend fun assertRejected(seed: suspend () -> Unit) {
            FrozenGate2CustomerStore.clear(context, identity)
            seed()
            assertNull(AndroidKeystoreCustomerSessionStore(context, identity).read())
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
            FrozenGate2CustomerStore.writeRaw(context, identity, byteArrayOf(0, 0, 0, 99))
        }
        assertRejected {
            FrozenGate2CustomerStore.writeRaw(
                context,
                identity,
                FrozenGate2CustomerStore.v2Payload(
                    ACCESS_TOKEN,
                    REFRESH_TOKEN,
                    ID_TOKEN,
                    EXPIRES_AT
                ) + byteArrayOf(1)
            )
        }
        assertRejected {
            AndroidKeystoreCustomerSessionStore(context, identity).write(
                CustomerSession(
                    SensitiveToken.from(ACCESS_TOKEN),
                    refreshToken = null,
                    idToken = null,
                    expiresAt = EXPIRES_AT
                )
            )
            FrozenGate2CustomerStore.deleteKey(identity)
        }
    }

    @Test
    fun bothAbsentIsEmptyAndClearDoesNotDeleteTheKeystoreKey() = runBlocking {
        val identity = gate2Identities.first()
        val store = AndroidKeystoreCustomerSessionStore(context, identity)

        assertNull(store.read())
        store.write(
            CustomerSession(
                SensitiveToken.from(ACCESS_TOKEN),
                refreshToken = null,
                idToken = null,
                expiresAt = EXPIRES_AT
            )
        )
        assertTrue(FrozenGate2CustomerStore.containsKey(identity))

        store.clear()

        assertNull(store.read())
        assertTrue(FrozenGate2CustomerStore.containsKey(identity))
    }

    private companion object {
        const val ACCESS_TOKEN = "fake-access-token"
        const val REFRESH_TOKEN = "fake-refresh-token"
        const val ID_TOKEN = "fake-id-token"
        const val VALID_BASE64 = "AA=="
        val EXPIRES_AT: Instant = Instant.ofEpochSecond(2_000_000_000L, 123_000_000L)
        val gate2Identities =
            listOf(
                ProtectedStoreIdentity(
                    preferencesName = "gurbakir_secure_customer_session_development",
                    keyAlias = "gurbakir.customer.session.development.v1"
                ),
                ProtectedStoreIdentity(
                    preferencesName = "gurbakir_secure_customer_session_staging",
                    keyAlias = "gurbakir.customer.session.staging.v1"
                )
            )
    }
}

private object FrozenGate2CustomerStore {
    private const val ANDROID_KEY_STORE = "AndroidKeyStore"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"

    data class DecodedSession(
        val accessToken: String,
        val refreshToken: String?,
        val idToken: String?,
        val expiresAt: Instant
    )

    fun write(
        context: Context,
        identity: ProtectedStoreIdentity,
        accessToken: String,
        refreshToken: String?,
        idToken: String?,
        expiresAt: Instant
    ) = writeRaw(context, identity, v2Payload(accessToken, refreshToken, idToken, expiresAt))

    fun v2Payload(accessToken: String, refreshToken: String?, idToken: String?, expiresAt: Instant): ByteArray {
        val output = ByteArrayOutputStream()
        DataOutputStream(output).use { stream ->
            stream.writeInt(2)
            stream.writeToken(accessToken)
            stream.writeBoolean(refreshToken != null)
            if (refreshToken != null) stream.writeToken(refreshToken)
            stream.writeBoolean(idToken != null)
            if (idToken != null) stream.writeToken(idToken)
            stream.writeLong(expiresAt.epochSecond)
            stream.writeInt(expiresAt.nano)
        }
        return output.toByteArray()
    }

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

    fun read(context: Context, identity: ProtectedStoreIdentity): DecodedSession {
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

    private fun decodeV2(payload: ByteArray): DecodedSession =
        DataInputStream(ByteArrayInputStream(payload)).use { stream ->
            check(stream.readInt() == 2)
            val accessToken = stream.readToken()
            val refreshToken = if (stream.readBoolean()) stream.readToken() else null
            val idToken = if (stream.readBoolean()) stream.readToken() else null
            val expiresAt = Instant.ofEpochSecond(stream.readLong(), stream.readInt().toLong())
            check(stream.available() == 0)
            DecodedSession(accessToken, refreshToken, idToken, expiresAt)
        }

    private fun DataOutputStream.writeToken(value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        writeInt(bytes.size)
        write(bytes)
    }

    private fun DataInputStream.readToken(): String {
        val bytes = ByteArray(readInt())
        readFully(bytes)
        return bytes.toString(Charsets.UTF_8)
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
