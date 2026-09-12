package com.gurbakir.account.oauth

import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.Base64

private const val OAUTH_STATE_BYTE_COUNT = 32
private const val OAUTH_NONCE_BYTE_COUNT = 32
private const val DEFAULT_TRANSACTION_LIFETIME_MINUTES = 10L

data class OAuthTransaction(
    val state: String,
    val nonce: String,
    val pkce: PkcePair,
    val discovery: CustomerAccountDiscoveredConfiguration,
    val createdAt: Instant
) {
    override fun toString(): String = "OAuthTransaction(<redacted>)"
}

sealed interface OAuthCallbackValidation {
    data class Accepted(val transaction: OAuthTransaction) : OAuthCallbackValidation {
        override fun toString(): String = "Accepted(<redacted>)"
    }

    data object MissingTransaction : OAuthCallbackValidation

    data object StateMismatch : OAuthCallbackValidation

    data object Expired : OAuthCallbackValidation
}

class OAuthTransactionCoordinator(
    private val pkceGenerator: PkceGenerator = PkceGenerator(),
    private val secureRandom: SecureRandom = SecureRandom(),
    private val clock: Clock = Clock.systemUTC(),
    private val lifetime: Duration = Duration.ofMinutes(DEFAULT_TRANSACTION_LIFETIME_MINUTES)
) {
    private var activeTransaction: OAuthTransaction? = null

    fun begin(discovery: CustomerAccountDiscoveredConfiguration): OAuthTransaction {
        val stateBytes = ByteArray(OAUTH_STATE_BYTE_COUNT).also(secureRandom::nextBytes)
        val nonceBytes = ByteArray(OAUTH_NONCE_BYTE_COUNT).also(secureRandom::nextBytes)
        return OAuthTransaction(
            state = Base64.getUrlEncoder().withoutPadding().encodeToString(stateBytes),
            nonce = Base64.getUrlEncoder().withoutPadding().encodeToString(nonceBytes),
            pkce = pkceGenerator.generate(),
            discovery = discovery,
            createdAt = clock.instant()
        ).also { activeTransaction = it }
    }

    fun validateAndConsume(returnedState: String?): OAuthCallbackValidation {
        val transaction = activeTransaction
        activeTransaction = null
        return when {
            transaction == null -> OAuthCallbackValidation.MissingTransaction

            returnedState == null || !constantTimeEquals(transaction.state, returnedState) ->
                OAuthCallbackValidation.StateMismatch

            clock.instant().isAfter(transaction.createdAt.plus(lifetime)) -> OAuthCallbackValidation.Expired

            else -> OAuthCallbackValidation.Accepted(transaction)
        }
    }

    fun cancel() {
        activeTransaction = null
    }
}

private fun constantTimeEquals(expected: String, actual: String): Boolean = MessageDigest.isEqual(
    expected.toByteArray(Charsets.UTF_8),
    actual.toByteArray(Charsets.UTF_8)
)
