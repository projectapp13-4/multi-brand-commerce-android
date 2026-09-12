package com.gurbakir.account.session

import java.time.Instant

data class CustomerSession(
    val accessToken: SensitiveToken,
    val refreshToken: SensitiveToken?,
    val idToken: SensitiveToken?,
    val expiresAt: Instant
) {
    override fun toString(): String = "CustomerSession(<redacted>, expiresAt=$expiresAt)"
}

class SensitiveToken private constructor(private val rawValue: String) {
    fun <T> use(block: (String) -> T): T = block(rawValue)

    suspend fun <T> useSuspending(block: suspend (String) -> T): T = block(rawValue)

    override fun toString(): String = "<redacted>"

    companion object {
        fun from(rawValue: String): SensitiveToken {
            require(rawValue.isNotBlank())
            return SensitiveToken(rawValue)
        }
    }
}

interface CustomerSessionStore {
    suspend fun read(): CustomerSession?

    suspend fun write(session: CustomerSession)

    suspend fun clear()
}
