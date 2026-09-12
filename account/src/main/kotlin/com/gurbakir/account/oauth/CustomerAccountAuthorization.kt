package com.gurbakir.account.oauth

import android.net.Uri
import com.gurbakir.foundation.config.CustomerAccountCapability
import com.gurbakir.foundation.config.CustomerAccountConfiguration
import java.net.URI
import java.net.URLDecoder
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationServiceConfiguration
import net.openid.appauth.ResponseTypeValues

private const val PKCE_S256 = "S256"

data class CustomerAccountAuthorizationPlan(
    val configuration: CustomerAccountConfiguration,
    val transaction: OAuthTransaction
) {
    override fun toString(): String = "CustomerAccountAuthorizationPlan(<redacted>)"
}

class CustomerAccountAuthorizationPlanner(
    private val configuration: CustomerAccountConfiguration,
    private val transactionCoordinator: OAuthTransactionCoordinator = OAuthTransactionCoordinator()
) {
    init {
        require(configuration.validationIssues().isEmpty()) {
            "Customer Account configuration must be valid before authorization."
        }
    }

    fun prepare(discovery: CustomerAccountDiscoveredConfiguration): CustomerAccountAuthorizationPlan {
        require(discovery.issuer == configuration.issuer) { "Discovered issuer does not match configuration." }
        return CustomerAccountAuthorizationPlan(configuration, transactionCoordinator.begin(discovery))
    }

    fun validateAndConsumeCallback(rawUri: String): CustomerAccountCallbackResult {
        val callback = CustomerAccountCallbackParser.parse(configuration.redirectUri, rawUri)
        if (callback !is ParsedCustomerAccountCallback) return callback

        return when (val validation = transactionCoordinator.validateAndConsume(callback.state)) {
            is OAuthCallbackValidation.Accepted ->
                when {
                    callback.errorCode != null -> CustomerAccountCallbackResult.Cancelled

                    callback.authorizationCode != null ->
                        CustomerAccountCallbackResult.Authorized(
                            CustomerAccountAuthorizationGrant(
                                code = SensitiveAuthorizationCode.from(callback.authorizationCode),
                                codeVerifier = SensitiveCodeVerifier.from(validation.transaction.pkce.verifier),
                                expectedNonce = SensitiveNonce.from(validation.transaction.nonce),
                                redirectUri = configuration.redirectUri,
                                discovery = validation.transaction.discovery
                            )
                        )

                    else -> CustomerAccountCallbackResult.RejectedMissingResponse
                }

            OAuthCallbackValidation.MissingTransaction -> CustomerAccountCallbackResult.RejectedMissingTransaction

            OAuthCallbackValidation.StateMismatch -> CustomerAccountCallbackResult.RejectedState

            OAuthCallbackValidation.Expired -> CustomerAccountCallbackResult.RejectedExpired
        }
    }

    fun cancel() {
        transactionCoordinator.cancel()
    }
}

data class CustomerAccountAuthorizationGrant(
    val code: SensitiveAuthorizationCode,
    val codeVerifier: SensitiveCodeVerifier,
    val expectedNonce: SensitiveNonce,
    val redirectUri: String,
    val discovery: CustomerAccountDiscoveredConfiguration
) {
    override fun toString(): String = "CustomerAccountAuthorizationGrant(<redacted>)"
}

object AppAuthCustomerAccountRequestFactory {
    fun create(plan: CustomerAccountAuthorizationPlan): AuthorizationRequest {
        val configuration = plan.configuration
        val serviceConfiguration = plan.transaction.discovery.toAppAuthServiceConfiguration()

        return AuthorizationRequest.Builder(
            serviceConfiguration,
            configuration.clientId,
            ResponseTypeValues.CODE,
            Uri.parse(configuration.redirectUri)
        ).setScope(configuration.scopes.sorted().joinToString(" "))
            .setState(plan.transaction.state)
            .setNonce(plan.transaction.nonce)
            .setCodeVerifier(
                plan.transaction.pkce.verifier,
                plan.transaction.pkce.challenge,
                PKCE_S256
            )
            .build()
    }
}

sealed interface CustomerAccountAuthorizationPreparation {
    data class Prepared(val plan: CustomerAccountAuthorizationPlan) : CustomerAccountAuthorizationPreparation

    data class Failed(val reason: CustomerAccountDiscoveryFailure) : CustomerAccountAuthorizationPreparation
}

class CustomerAccountAuthorizationCoordinator(
    capability: CustomerAccountCapability,
    discoveryClient: () -> CustomerAccountDiscoveryClient
) {
    private val planner: CustomerAccountAuthorizationPlanner? =
        (capability as? CustomerAccountCapability.Enabled)?.configuration
            ?.takeIf { it.validationIssues().isEmpty() }
            ?.let(::CustomerAccountAuthorizationPlanner)
    constructor(
        configuration: CustomerAccountConfiguration,
        discoveryClient: CustomerAccountDiscoveryClient
    ) : this(CustomerAccountCapability.Enabled(configuration), { discoveryClient })

    private val discoveryClient by lazy(discoveryClient)
    suspend fun prepare(): CustomerAccountAuthorizationPreparation {
        val activePlanner = planner
            ?: return CustomerAccountAuthorizationPreparation.Failed(CustomerAccountDiscoveryFailure.UNCONFIGURED)
        return when (val discovery = discoveryClient.discover()) {
            is CustomerAccountDiscoveryResult.Failure ->
                CustomerAccountAuthorizationPreparation.Failed(discovery.reason)

            is CustomerAccountDiscoveryResult.Success ->
                CustomerAccountAuthorizationPreparation.Prepared(activePlanner.prepare(discovery.configuration))
        }
    }

    fun validateAndConsumeCallback(rawUri: String): CustomerAccountCallbackResult =
        planner?.validateAndConsumeCallback(rawUri)
            ?: CustomerAccountCallbackResult.RejectedMissingTransaction

    fun cancel() {
        planner?.cancel()
    }
}

class SensitiveAuthorizationCode private constructor(private val rawValue: String) {
    fun <T> use(block: (String) -> T): T = block(rawValue)

    override fun toString(): String = "<redacted>"

    companion object {
        fun from(rawValue: String): SensitiveAuthorizationCode {
            require(rawValue.isNotBlank())
            return SensitiveAuthorizationCode(rawValue)
        }
    }
}

class SensitiveCodeVerifier private constructor(private val rawValue: String) {
    fun <T> use(block: (String) -> T): T = block(rawValue)

    override fun toString(): String = "<redacted>"

    companion object {
        fun from(rawValue: String): SensitiveCodeVerifier {
            require(rawValue.isNotBlank())
            return SensitiveCodeVerifier(rawValue)
        }
    }
}

class SensitiveNonce private constructor(private val rawValue: String) {
    fun <T> use(block: (String) -> T): T = block(rawValue)

    override fun toString(): String = "<redacted>"

    companion object {
        fun from(rawValue: String): SensitiveNonce {
            require(rawValue.isNotBlank())
            return SensitiveNonce(rawValue)
        }
    }
}

sealed interface CustomerAccountCallbackResult {
    data class Authorized(val grant: CustomerAccountAuthorizationGrant) : CustomerAccountCallbackResult {
        override fun toString(): String = "Authorized(<redacted>)"
    }

    data object Cancelled : CustomerAccountCallbackResult

    data object RejectedRoute : CustomerAccountCallbackResult

    data object RejectedMalformed : CustomerAccountCallbackResult

    data object RejectedMissingResponse : CustomerAccountCallbackResult

    data object RejectedMissingTransaction : CustomerAccountCallbackResult

    data object RejectedState : CustomerAccountCallbackResult

    data object RejectedExpired : CustomerAccountCallbackResult
}

private data class ParsedCustomerAccountCallback(
    val authorizationCode: String?,
    val errorCode: String?,
    val state: String?
) : CustomerAccountCallbackResult

private object CustomerAccountCallbackParser {
    fun parse(expectedRedirectUri: String, rawUri: String): CustomerAccountCallbackResult {
        val uris = runCatching { URI(expectedRedirectUri) to URI(rawUri) }.getOrNull()
        val parameters = uris?.let { parseUniqueQueryParameters(it.second.rawQuery) }
        return when {
            uris == null -> CustomerAccountCallbackResult.RejectedMalformed

            !routesMatch(uris.first, uris.second) -> CustomerAccountCallbackResult.RejectedRoute

            parameters == null -> CustomerAccountCallbackResult.RejectedMalformed

            else ->
                ParsedCustomerAccountCallback(
                    authorizationCode = parameters["code"]?.takeIf(String::isNotBlank),
                    errorCode = parameters["error"]?.takeIf(String::isNotBlank),
                    state = parameters["state"]?.takeIf(String::isNotBlank)
                )
        }
    }

    private fun parseUniqueQueryParameters(rawQuery: String?): Map<String, String>? {
        if (rawQuery.isNullOrBlank()) return emptyMap()
        val result = linkedMapOf<String, String>()
        var valid = true
        for (pair in rawQuery.split('&')) {
            val parsed = parseQueryPair(pair)
            if (parsed == null || result.containsKey(parsed.first)) {
                valid = false
                break
            }
            result[parsed.first] = parsed.second
        }
        return result.takeIf { valid }
    }

    private fun parseQueryPair(rawPair: String): Pair<String, String>? {
        val parts = rawPair.split('=', limit = 2)
        val key = decode(parts[0])
        val value = decode(parts.getOrElse(1) { "" })
        return if (key.isNullOrBlank() || value == null) null else key to value
    }

    private fun routesMatch(expected: URI, actual: URI): Boolean = listOf(
        expected.scheme.equals(actual.scheme, ignoreCase = true),
        expected.host.equals(actual.host, ignoreCase = true),
        expected.port == actual.port,
        expected.path == actual.path,
        actual.userInfo == null,
        actual.fragment == null
    ).all { it }

    private fun decode(value: String): String? = runCatching {
        URLDecoder.decode(value, Charsets.UTF_8.name())
    }.getOrNull()
}
