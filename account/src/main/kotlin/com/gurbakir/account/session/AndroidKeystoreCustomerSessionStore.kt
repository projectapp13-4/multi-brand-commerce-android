package com.gurbakir.account.session

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.gurbakir.foundation.config.ProtectedStoreIdentity
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.security.KeyStore
import java.time.Instant
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
private const val PAYLOAD_VERSION = 2
private const val MAXIMUM_TOKEN_BYTE_COUNT = 64 * 1024

class AndroidKeystoreCustomerSessionStore(context: Context, identity: ProtectedStoreIdentity) : CustomerSessionStore {
    private val applicationContext = context.applicationContext
    private val preferences = applicationContext.getSharedPreferences(
        identity.preferencesName,
        Context.MODE_PRIVATE
    )
    private val keyAlias = identity.keyAlias
    private val lock = Any()

    override suspend fun read(): CustomerSession? = withContext(Dispatchers.IO) {
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
                    SessionPayloadCodec.decode(plaintext)
                } finally {
                    plaintext.fill(0)
                }
            }.getOrElse {
                clearStoredCiphertext()
                null
            }
        }
    }

    override suspend fun write(session: CustomerSession): Unit = withContext(Dispatchers.IO) {
        synchronized(lock) {
            val plaintext = SessionPayloadCodec.encode(session)
            try {
                val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
                cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
                val ciphertext = cipher.doFinal(plaintext)
                check(
                    preferences.edit()
                        .putString(PREFERENCE_IV, Base64.getEncoder().encodeToString(cipher.iv))
                        .putString(PREFERENCE_CIPHERTEXT, Base64.getEncoder().encodeToString(ciphertext))
                        .commit()
                ) { "Unable to persist encrypted customer session." }
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

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val parameters = KeyGenParameterSpec.Builder(
            keyAlias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
            .setUserAuthenticationRequired(false)
            .build()
        keyGenerator.init(parameters)
        return keyGenerator.generateKey()
    }

    private fun clearStoredCiphertext() {
        check(
            preferences.edit()
                .remove(PREFERENCE_IV)
                .remove(PREFERENCE_CIPHERTEXT)
                .commit()
        ) { "Unable to clear encrypted customer session." }
    }

    private companion object {
        private const val PREFERENCE_IV = "iv"
        private const val PREFERENCE_CIPHERTEXT = "ciphertext"
    }
}

internal object SessionPayloadCodec {
    fun encode(session: CustomerSession): ByteArray {
        val output = ByteArrayOutputStream()
        DataOutputStream(output).use { stream ->
            stream.writeInt(PAYLOAD_VERSION)
            writeToken(stream, session.accessToken)
            stream.writeBoolean(session.refreshToken != null)
            session.refreshToken?.let { writeToken(stream, it) }
            stream.writeBoolean(session.idToken != null)
            session.idToken?.let { writeToken(stream, it) }
            stream.writeLong(session.expiresAt.epochSecond)
            stream.writeInt(session.expiresAt.nano)
        }
        return output.toByteArray()
    }

    fun decode(payload: ByteArray): CustomerSession = DataInputStream(ByteArrayInputStream(payload)).use { stream ->
        require(stream.readInt() == PAYLOAD_VERSION) { "Unsupported encrypted session payload." }
        val accessToken = readToken(stream)
        val refreshToken = if (stream.readBoolean()) readToken(stream) else null
        val idToken = if (stream.readBoolean()) readToken(stream) else null
        val expiresAt = Instant.ofEpochSecond(stream.readLong(), stream.readInt().toLong())
        require(stream.available() == 0) { "Unexpected encrypted session payload content." }
        CustomerSession(accessToken, refreshToken, idToken, expiresAt)
    }

    private fun writeToken(stream: DataOutputStream, token: SensitiveToken) {
        token.use { rawValue ->
            val bytes = rawValue.toByteArray(Charsets.UTF_8)
            try {
                require(bytes.size <= MAXIMUM_TOKEN_BYTE_COUNT) { "Customer token exceeds the storage limit." }
                stream.writeInt(bytes.size)
                stream.write(bytes)
            } finally {
                bytes.fill(0)
            }
        }
    }

    private fun readToken(stream: DataInputStream): SensitiveToken {
        val length = stream.readInt()
        require(length in 1..MAXIMUM_TOKEN_BYTE_COUNT) { "Invalid encrypted customer token length." }
        val bytes = ByteArray(length)
        stream.readFully(bytes)
        return try {
            SensitiveToken.from(bytes.toString(Charsets.UTF_8))
        } finally {
            bytes.fill(0)
        }
    }
}
