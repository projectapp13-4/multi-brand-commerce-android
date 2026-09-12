package com.gurbakir.storefront

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.gurbakir.foundation.config.ProtectedStoreIdentity
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val ANDROID_KEYSTORE = "AndroidKeyStore"
private const val CIPHER_TRANSFORMATION = "AES/GCM/NoPadding"
private const val GCM_TAG_LENGTH_BITS = 128
private const val LEGACY_CART_PAYLOAD_VERSION = 1
private const val CART_PAYLOAD_VERSION = 2
private const val MAXIMUM_CART_ID_BYTE_COUNT = 64 * 1024
private const val ANONYMOUS_OWNERSHIP_CODE = 1
private const val CUSTOMER_ASSOCIATED_OWNERSHIP_CODE = 2
private const val DETACH_PENDING_OWNERSHIP_CODE = 3
private const val QUARANTINED_OWNERSHIP_CODE = 4

class AndroidKeystoreCartSessionStore(context: Context, identity: ProtectedStoreIdentity) : CartSessionStore {
    private val applicationContext = context.applicationContext
    private val preferences = applicationContext.getSharedPreferences(
        identity.preferencesName,
        Context.MODE_PRIVATE
    )
    private val keyAlias = identity.keyAlias
    private val lock = Any()

    override suspend fun read(): PersistedCart? = withContext(Dispatchers.IO) {
        synchronized(lock) {
            val storedValues =
                runCatching {
                    preferences.getString(PREFERENCE_IV, null) to
                        preferences.getString(PREFERENCE_CIPHERTEXT, null)
                }.getOrElse {
                    clearStoredCiphertext()
                    return@synchronized null
                }
            val (encodedIv, encodedCiphertext) = storedValues
            if (encodedIv == null && encodedCiphertext == null) return@synchronized null
            if (encodedIv == null || encodedCiphertext == null) {
                clearStoredCiphertext()
                return@synchronized null
            }
            runCatching {
                val iv = Base64.getDecoder().decode(encodedIv)
                val ciphertext = Base64.getDecoder().decode(encodedCiphertext)
                val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
                cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
                val plaintext = cipher.doFinal(ciphertext)
                try {
                    CartSessionPayloadCodec.decode(plaintext)
                } finally {
                    plaintext.fill(0)
                }
            }.getOrElse {
                clearStoredCiphertext()
                null
            }
        }
    }

    override suspend fun write(cart: PersistedCart): Unit = withContext(Dispatchers.IO) {
        synchronized(lock) {
            val plaintext = CartSessionPayloadCodec.encode(cart)
            try {
                val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
                cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
                val ciphertext = cipher.doFinal(plaintext)
                check(
                    preferences.edit()
                        .putString(PREFERENCE_IV, Base64.getEncoder().encodeToString(cipher.iv))
                        .putString(PREFERENCE_CIPHERTEXT, Base64.getEncoder().encodeToString(ciphertext))
                        .commit()
                ) { "Unable to persist encrypted cart." }
            } finally {
                plaintext.fill(0)
            }
        }
    }

    override suspend fun clear(): Unit = withContext(Dispatchers.IO) {
        synchronized(lock) { clearStoredCiphertext() }
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(keyAlias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val parameters =
            KeyGenParameterSpec.Builder(
                keyAlias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .setUserAuthenticationRequired(false)
                .build()
        generator.init(parameters)
        return generator.generateKey()
    }

    private fun clearStoredCiphertext() {
        check(
            preferences.edit()
                .remove(PREFERENCE_IV)
                .remove(PREFERENCE_CIPHERTEXT)
                .commit()
        ) { "Unable to clear encrypted cart." }
    }

    private companion object {
        const val PREFERENCE_IV = "iv"
        const val PREFERENCE_CIPHERTEXT = "ciphertext"
    }
}

internal object CartSessionPayloadCodec {
    fun encode(cart: PersistedCart): ByteArray {
        val output = ByteArrayOutputStream()
        DataOutputStream(output).use { stream ->
            stream.writeInt(CART_PAYLOAD_VERSION)
            cart.id.use { rawCartId ->
                val bytes = rawCartId.toByteArray(Charsets.UTF_8)
                try {
                    require(bytes.size <= MAXIMUM_CART_ID_BYTE_COUNT)
                    stream.writeInt(bytes.size)
                    stream.write(bytes)
                } finally {
                    bytes.fill(0)
                }
            }
            stream.writeLong(cart.expiresAt.epochSecond)
            stream.writeInt(cart.expiresAt.nano)
            stream.writeInt(cart.ownership.persistedCode)
        }
        return output.toByteArray()
    }

    fun decode(payload: ByteArray): PersistedCart = DataInputStream(ByteArrayInputStream(payload)).use { stream ->
        val version = stream.readInt()
        require(version == LEGACY_CART_PAYLOAD_VERSION || version == CART_PAYLOAD_VERSION)
        val length = stream.readInt()
        require(length in 1..MAXIMUM_CART_ID_BYTE_COUNT)
        val bytes = ByteArray(length)
        stream.readFully(bytes)
        val cartId =
            try {
                SensitiveCartId.from(bytes.toString(Charsets.UTF_8))
            } finally {
                bytes.fill(0)
            }
        val expiresAt = java.time.Instant.ofEpochSecond(stream.readLong(), stream.readInt().toLong())
        val ownership =
            if (version == LEGACY_CART_PAYLOAD_VERSION) {
                CartOwnership.QUARANTINED
            } else {
                stream.readInt().toCartOwnership()
            }
        require(stream.available() == 0)
        PersistedCart(cartId, expiresAt, ownership)
    }
}

private val CartOwnership.persistedCode: Int
    get() = when (this) {
        CartOwnership.ANONYMOUS -> ANONYMOUS_OWNERSHIP_CODE
        CartOwnership.CUSTOMER_ASSOCIATED -> CUSTOMER_ASSOCIATED_OWNERSHIP_CODE
        CartOwnership.DETACH_PENDING -> DETACH_PENDING_OWNERSHIP_CODE
        CartOwnership.QUARANTINED -> QUARANTINED_OWNERSHIP_CODE
    }

private fun Int.toCartOwnership(): CartOwnership = when (this) {
    ANONYMOUS_OWNERSHIP_CODE -> CartOwnership.ANONYMOUS
    CUSTOMER_ASSOCIATED_OWNERSHIP_CODE -> CartOwnership.CUSTOMER_ASSOCIATED
    DETACH_PENDING_OWNERSHIP_CODE -> CartOwnership.DETACH_PENDING
    QUARANTINED_OWNERSHIP_CODE -> CartOwnership.QUARANTINED
    else -> throw IllegalArgumentException("Unsupported cart ownership state.")
}
