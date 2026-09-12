package com.gurbakir.account.session

import com.gurbakir.account.oauth.CustomerAccountAuthorizationGrant
import com.gurbakir.account.oauth.CustomerAccountLogoutClient
import com.gurbakir.account.oauth.CustomerAccountTokenClient
import com.gurbakir.account.oauth.CustomerLogoutResult
import com.gurbakir.account.oauth.CustomerTokenFailure
import com.gurbakir.account.oauth.CustomerTokenPayload
import com.gurbakir.account.oauth.CustomerTokenResult
import com.gurbakir.account.oauth.SensitiveNonce
import com.gurbakir.account.oauth.UnconfiguredCustomerAccountLogoutClient
import com.gurbakir.foundation.config.CustomerAccountCapability
import com.gurbakir.foundation.config.CustomerAccountConfiguration
import java.security.MessageDigest
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.Base64
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

private val DEFAULT_REFRESH_LEAD_TIME: Duration = Duration.ofMinutes(1)
private val MAXIMUM_CLOCK_SKEW: Duration = Duration.ofMinutes(1)
private const val SHOPIFY_ID_TOKEN_ALGORITHM = "RS256"
private const val JWT_SECTION_COUNT = 3

sealed interface CustomerSessionResolution {
    data class Authenticated(val session: CustomerSession) : CustomerSessionResolution {
        override fun toString(): String = "Authenticated(<redacted>)"
    }

    data object SignedOut : CustomerSessionResolution

    data class Failed(val reason: CustomerTokenFailure, val encryptedSessionRetained: Boolean) :
        CustomerSessionResolution
}

sealed interface CustomerLogoutResolution {
    data object SignedOut : CustomerLogoutResolution

    data object Completed : CustomerLogoutResolution

    data class RemoteFailed(val reason: CustomerTokenFailure) : CustomerLogoutResolution
}

class CustomerAccountSessionCoordinator(
    private val capability: CustomerAccountCapability,
    tokenClient: () -> CustomerAccountTokenClient,
    sessionStore: () -> CustomerSessionStore,
    logoutClient: () -> CustomerAccountLogoutClient = { UnconfiguredCustomerAccountLogoutClient() },
    private val clock: Clock = Clock.systemUTC(),
    private val refreshLeadTime: Duration = DEFAULT_REFRESH_LEAD_TIME
) {
    constructor(
        configuration: CustomerAccountConfiguration,
        tokenClient: CustomerAccountTokenClient,
        sessionStore: CustomerSessionStore,
        logoutClient: CustomerAccountLogoutClient = UnconfiguredCustomerAccountLogoutClient(),
        clock: Clock = Clock.systemUTC(),
        refreshLeadTime: Duration = DEFAULT_REFRESH_LEAD_TIME
    ) : this(CustomerAccountCapability.Enabled(configuration), {
        tokenClient
    }, { sessionStore }, { logoutClient }, clock, refreshLeadTime)

    private val tokenClient by lazy(tokenClient)
    private val sessionStore by lazy(sessionStore)
    private val logoutClient by lazy(logoutClient)
    private val lock = Mutex()
    private val idTokenValidator = (capability as? CustomerAccountCapability.Enabled)
        ?.let { CustomerIdTokenValidator(it.configuration, clock) }

    init {
        require(refreshLeadTime >= Duration.ZERO) { "Refresh lead time must not be negative." }
    }

    suspend fun exchange(grant: CustomerAccountAuthorizationGrant): CustomerSessionResolution = lock.withLock {
        if (capability == CustomerAccountCapability.Disabled) return@withLock CustomerSessionResolution.SignedOut
        when (val result = tokenClient.exchange(grant)) {
            is CustomerTokenResult.Failure -> {
                sessionStore.clear()
                CustomerSessionResolution.Failed(result.reason, false)
            }

            is CustomerTokenResult.Success -> {
                when (val validation = result.payload.toInitialSession(grant.expectedNonce)) {
                    is InitialSessionValidation.Failure -> {
                        sessionStore.clear()
                        CustomerSessionResolution.Failed(validation.reason, false)
                    }

                    is InitialSessionValidation.Success -> {
                        sessionStore.write(validation.session)
                        CustomerSessionResolution.Authenticated(validation.session)
                    }
                }
            }
        }
    }

    suspend fun restore(): CustomerSessionResolution = lock.withLock {
        if (capability == CustomerAccountCapability.Disabled) return@withLock CustomerSessionResolution.SignedOut
        val stored = sessionStore.read() ?: return@withLock CustomerSessionResolution.SignedOut
        if (stored.expiresAt.isAfter(clock.instant().plus(refreshLeadTime))) {
            return@withLock CustomerSessionResolution.Authenticated(stored)
        }
        refreshLocked(stored)
    }

    suspend fun refresh(): CustomerSessionResolution = lock.withLock {
        if (capability == CustomerAccountCapability.Disabled) return@withLock CustomerSessionResolution.SignedOut
        val stored = sessionStore.read() ?: return@withLock CustomerSessionResolution.SignedOut
        refreshLocked(stored)
    }

    suspend fun clearForLogout() = lock.withLock {
        if (capability != CustomerAccountCapability.Disabled) sessionStore.clear()
    }

    suspend fun logout(): CustomerLogoutResolution = lock.withLock {
        if (capability == CustomerAccountCapability.Disabled) return@withLock CustomerLogoutResolution.SignedOut
        val stored = sessionStore.read() ?: return@withLock CustomerLogoutResolution.SignedOut
        val result = stored.idToken?.let { logoutClient.logout(it) }
            ?: CustomerLogoutResult.Failure(CustomerTokenFailure.InvalidResponse)
        sessionStore.clear()
        when (result) {
            CustomerLogoutResult.Success -> CustomerLogoutResolution.Completed
            is CustomerLogoutResult.Failure -> CustomerLogoutResolution.RemoteFailed(result.reason)
        }
    }

    private suspend fun refreshLocked(stored: CustomerSession): CustomerSessionResolution {
        val refreshToken = stored.refreshToken
        return if (refreshToken == null) {
            sessionStore.clear()
            CustomerSessionResolution.Failed(CustomerTokenFailure.Rejected, false)
        } else {
            when (val result = tokenClient.refresh(refreshToken)) {
                is CustomerTokenResult.Success -> {
                    val refreshed = result.payload.toRefreshedSession(stored)
                    if (refreshed == null) {
                        sessionStore.clear()
                        CustomerSessionResolution.Failed(CustomerTokenFailure.InvalidResponse, false)
                    } else {
                        sessionStore.write(refreshed)
                        CustomerSessionResolution.Authenticated(refreshed)
                    }
                }

                is CustomerTokenResult.Failure -> {
                    val retain = result.reason == CustomerTokenFailure.Transient
                    if (!retain) sessionStore.clear()
                    CustomerSessionResolution.Failed(result.reason, retain)
                }
            }
        }
    }

    @Suppress("ReturnCount")
    private fun CustomerTokenPayload.toInitialSession(expectedNonce: SensitiveNonce): InitialSessionValidation {
        val refresh = refreshToken
        val id = idToken
        if (refresh == null || id == null) {
            return InitialSessionValidation.Failure(CustomerTokenFailure.MissingRequiredFields)
        }
        if (!expiresAt.isAfter(clock.instant())) {
            return InitialSessionValidation.Failure(CustomerTokenFailure.ExpiredAccessToken)
        }
        if (idTokenValidator?.validate(id, expectedNonce) != true) {
            return InitialSessionValidation.Failure(CustomerTokenFailure.InvalidIdToken)
        }
        return InitialSessionValidation.Success(CustomerSession(accessToken, refresh, id, expiresAt))
    }

    private fun CustomerTokenPayload.toRefreshedSession(previous: CustomerSession): CustomerSession? {
        val nextIdToken = idToken ?: previous.idToken
        val returnedIdTokenIsValid =
            idToken == null || idTokenValidator?.validate(idToken, expectedNonce = null) == true
        return if (expiresAt.isAfter(clock.instant()) && returnedIdTokenIsValid) {
            CustomerSession(
                accessToken = accessToken,
                refreshToken = refreshToken ?: previous.refreshToken,
                idToken = nextIdToken,
                expiresAt = expiresAt
            )
        } else {
            null
        }
    }
}

private sealed interface InitialSessionValidation {
    data class Success(val session: CustomerSession) : InitialSessionValidation

    data class Failure(val reason: CustomerTokenFailure) : InitialSessionValidation
}

class CustomerIdTokenValidator(
    private val configuration: CustomerAccountConfiguration,
    private val clock: Clock = Clock.systemUTC(),
    private val json: Json = Json { ignoreUnknownKeys = true }
) {
    fun validate(idToken: SensitiveToken, expectedNonce: SensitiveNonce?): Boolean = idToken.use { rawToken ->
        val parts = rawToken.split('.')
        val header = parts.getOrNull(0)?.decodeJsonObject()
        val payload = parts.getOrNull(1)?.decodeJsonObject()
        parts.size == JWT_SECTION_COUNT &&
            parts.none(String::isBlank) &&
            header.hasSupportedAlgorithm() &&
            payload.hasValidIdentityClaims() &&
            payload.hasValidTimeClaims() &&
            payload.hasExpectedNonce(expectedNonce)
    }

    private fun JsonObject?.hasSupportedAlgorithm(): Boolean = this?.stringClaim("alg") == SHOPIFY_ID_TOKEN_ALGORITHM

    private fun JsonObject?.hasValidIdentityClaims(): Boolean {
        if (this == null) return false
        val audiences = audienceClaim()
        val authorizedPartyIsValid =
            audiences.size <= 1 || stringClaim("azp") == configuration.clientId
        return stringClaim("iss") == configuration.issuer &&
            !stringClaim("sub").isNullOrBlank() &&
            configuration.clientId in audiences &&
            authorizedPartyIsValid
    }

    private fun JsonObject?.hasValidTimeClaims(): Boolean {
        val now = clock.instant()
        val expiration = this?.longClaim("exp")
        val issuedAt = this?.longClaim("iat")
        return expiration != null &&
            expiration > now.epochSecond &&
            issuedAt != null &&
            expiration > issuedAt &&
            issuedAt <= now.plus(MAXIMUM_CLOCK_SKEW).epochSecond
    }

    private fun JsonObject?.hasExpectedNonce(expectedNonce: SensitiveNonce?): Boolean = expectedNonce == null ||
        expectedNonce.use { nonce ->
            val returnedNonce = this?.stringClaim("nonce")
            returnedNonce != null && constantTimeEquals(nonce, returnedNonce)
        }

    private fun String.decodeJsonObject(): JsonObject? = runCatching {
        val decoded = Base64.getUrlDecoder().decode(this).toString(Charsets.UTF_8)
        json.parseToJsonElement(decoded) as? JsonObject
    }.getOrNull()
}

private fun JsonObject.stringClaim(name: String): String? = (get(name) as? JsonPrimitive)?.contentOrNull

private fun JsonObject.longClaim(name: String): Long? = get(name)?.jsonPrimitive?.longOrNull

private fun JsonObject.audienceClaim(): Set<String> = when (val value = get("aud")) {
    is JsonPrimitive -> setOfNotNull(value.contentOrNull)
    is JsonArray -> value.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.toSet()
    else -> emptySet()
}

private fun constantTimeEquals(expected: String, actual: String): Boolean = MessageDigest.isEqual(
    expected.toByteArray(Charsets.UTF_8),
    actual.toByteArray(Charsets.UTF_8)
)
