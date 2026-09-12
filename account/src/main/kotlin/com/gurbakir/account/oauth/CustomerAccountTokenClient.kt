package com.gurbakir.account.oauth

import com.gurbakir.account.session.SensitiveToken
import com.gurbakir.foundation.config.CustomerAccountConfiguration
import java.io.IOException
import java.time.Clock
import java.time.Instant
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import net.openid.appauth.AuthorizationServiceConfiguration
import net.openid.appauth.AuthorizationServiceDiscovery
import net.openid.appauth.EndSessionRequest
import okhttp3.Call
import okhttp3.Callback
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject

private const val AUTHORIZATION_CODE_GRANT = "authorization_code"
private const val REFRESH_TOKEN_GRANT = "refresh_token"
private const val BEARER_TOKEN_TYPE = "Bearer"
private const val HTTP_OK = 200
private const val HTTP_REQUEST_TIMEOUT = 408
private const val HTTP_TOO_MANY_REQUESTS = 429
private const val HTTP_SERVER_ERROR_START = 500
private const val MAXIMUM_TOKEN_RESPONSE_BYTES = 128L * 1024L
private const val MAXIMUM_TOKEN_VALUE_LENGTH = 64 * 1024
private const val MAXIMUM_ACCESS_TOKEN_LIFETIME_SECONDS = 31L * 24L * 60L * 60L
private const val CLIENT_USER_AGENT = "Gurbakir-Android"

data class CustomerTokenPayload(
    val accessToken: SensitiveToken,
    val refreshToken: SensitiveToken?,
    val idToken: SensitiveToken?,
    val expiresAt: Instant
) {
    override fun toString(): String = "CustomerTokenPayload(<redacted>, expiresAt=$expiresAt)"
}

sealed interface CustomerTokenFailure {
    data object Rejected : CustomerTokenFailure

    data object Transient : CustomerTokenFailure

    data object InvalidResponse : CustomerTokenFailure

    data object MissingRequiredFields : CustomerTokenFailure

    data object UnsupportedTokenType : CustomerTokenFailure

    data object ScopeMismatch : CustomerTokenFailure

    data object ExpiredAccessToken : CustomerTokenFailure

    data object InvalidIdToken : CustomerTokenFailure
}

sealed interface CustomerTokenResult {
    data class Success(val payload: CustomerTokenPayload) : CustomerTokenResult

    data class Failure(val reason: CustomerTokenFailure) : CustomerTokenResult
}

interface CustomerAccountTokenClient {
    suspend fun exchange(grant: CustomerAccountAuthorizationGrant): CustomerTokenResult

    suspend fun refresh(refreshToken: SensitiveToken): CustomerTokenResult
}

class UnconfiguredCustomerAccountTokenClient : CustomerAccountTokenClient {
    override suspend fun exchange(grant: CustomerAccountAuthorizationGrant): CustomerTokenResult =
        CustomerTokenResult.Failure(CustomerTokenFailure.InvalidResponse)

    override suspend fun refresh(refreshToken: SensitiveToken): CustomerTokenResult =
        CustomerTokenResult.Failure(CustomerTokenFailure.InvalidResponse)
}

data class CustomerTokenExchangePlan(
    val clientId: String,
    val discovery: CustomerAccountDiscoveredConfiguration,
    val redirectUri: String,
    val authorizationCode: SensitiveAuthorizationCode,
    val codeVerifier: SensitiveCodeVerifier,
    val expectedNonce: SensitiveNonce
) {
    override fun toString(): String = "CustomerTokenExchangePlan(<redacted>)"
}

data class CustomerTokenRefreshPlan(
    val clientId: String,
    val discovery: CustomerAccountDiscoveredConfiguration,
    val refreshToken: SensitiveToken
) {
    override fun toString(): String = "CustomerTokenRefreshPlan(<redacted>)"
}

data class CustomerLogoutPlan(val discovery: CustomerAccountDiscoveredConfiguration, val idToken: SensitiveToken) {
    override fun toString(): String = "CustomerLogoutPlan(<redacted>)"
}

class CustomerAccountTokenRequestPlanner(private val configuration: CustomerAccountConfiguration) {
    init {
        require(configuration.validationIssues().isEmpty()) {
            "Customer Account configuration must be valid before token requests."
        }
    }

    fun exchange(grant: CustomerAccountAuthorizationGrant): CustomerTokenExchangePlan {
        require(grant.redirectUri == configuration.redirectUri) {
            "Authorization redirect does not match configuration."
        }
        return CustomerTokenExchangePlan(
            clientId = configuration.clientId,
            discovery = grant.discovery,
            redirectUri = grant.redirectUri,
            authorizationCode = grant.code,
            codeVerifier = grant.codeVerifier,
            expectedNonce = grant.expectedNonce
        )
    }

    fun refresh(
        refreshToken: SensitiveToken,
        discovery: CustomerAccountDiscoveredConfiguration
    ): CustomerTokenRefreshPlan = CustomerTokenRefreshPlan(
        clientId = configuration.clientId,
        discovery = discovery,
        refreshToken = refreshToken
    )

    fun logout(idToken: SensitiveToken, discovery: CustomerAccountDiscoveredConfiguration): CustomerLogoutPlan =
        CustomerLogoutPlan(discovery, idToken)
}

object AppAuthCustomerAccountEndSessionRequestFactory {
    fun logout(configuration: CustomerAccountConfiguration, plan: CustomerLogoutPlan): EndSessionRequest {
        require(plan.discovery.issuer == configuration.issuer)
        return plan.idToken.use { rawIdToken ->
            EndSessionRequest.Builder(plan.discovery.toAppAuthServiceConfiguration())
                .setIdTokenHint(rawIdToken)
                .build()
        }
    }
}

internal object ShopifyCustomerAccountTokenRequestFactory {
    fun exchange(configuration: CustomerAccountConfiguration, plan: CustomerTokenExchangePlan): Request {
        require(plan.clientId == configuration.clientId && plan.discovery.issuer == configuration.issuer)
        val body = plan.authorizationCode.use { code ->
            plan.codeVerifier.use { verifier ->
                FormBody.Builder()
                    .add("grant_type", AUTHORIZATION_CODE_GRANT)
                    .add("client_id", plan.clientId)
                    .add("redirect_uri", plan.redirectUri)
                    .add("code", code)
                    .add("code_verifier", verifier)
                    .build()
            }
        }
        return tokenRequest(plan.discovery.tokenEndpoint, body)
    }

    fun refresh(configuration: CustomerAccountConfiguration, plan: CustomerTokenRefreshPlan): Request {
        require(plan.clientId == configuration.clientId && plan.discovery.issuer == configuration.issuer)
        val body = plan.refreshToken.use { refreshToken ->
            FormBody.Builder()
                .add("grant_type", REFRESH_TOKEN_GRANT)
                .add("client_id", plan.clientId)
                .add("refresh_token", refreshToken)
                .build()
        }
        return tokenRequest(plan.discovery.tokenEndpoint, body)
    }

    private fun tokenRequest(endpoint: String, body: FormBody): Request = Request.Builder()
        .url(endpoint)
        .header("Accept", "application/json")
        .header("User-Agent", CLIENT_USER_AGENT)
        .post(body)
        .build()
}

class ShopifyCustomerAccountTokenClient(
    configuration: CustomerAccountConfiguration,
    private val discoveryClient: CustomerAccountDiscoveryClient,
    httpClient: OkHttpClient = OkHttpClient(),
    clock: Clock = Clock.systemUTC()
) : CustomerAccountTokenClient {
    private val configuration = configuration
    private val requestPlanner = CustomerAccountTokenRequestPlanner(configuration)
    private val parser = ShopifyCustomerTokenResponseParser(configuration.scopes, clock)
    private val noRedirectHttpClient =
        httpClient.newBuilder().followRedirects(false).followSslRedirects(false).build()

    override suspend fun exchange(grant: CustomerAccountAuthorizationGrant): CustomerTokenResult =
        noRedirectHttpClient.newCall(
            ShopifyCustomerAccountTokenRequestFactory.exchange(
                configuration,
                requestPlanner.exchange(grant)
            )
        ).awaitTokenResult(parser)

    override suspend fun refresh(refreshToken: SensitiveToken): CustomerTokenResult =
        when (val discovery = discoveryClient.discover()) {
            is CustomerAccountDiscoveryResult.Failure ->
                CustomerTokenResult.Failure(discovery.reason.toTokenFailure())

            is CustomerAccountDiscoveryResult.Success ->
                noRedirectHttpClient.newCall(
                    ShopifyCustomerAccountTokenRequestFactory.refresh(
                        configuration,
                        requestPlanner.refresh(refreshToken, discovery.configuration)
                    )
                ).awaitTokenResult(parser)
        }
}

internal class ShopifyCustomerTokenResponseParser(
    private val expectedScopes: Set<String>,
    private val clock: Clock = Clock.systemUTC(),
    private val json: Json = Json { ignoreUnknownKeys = true }
) {
    @Suppress("ReturnCount")
    fun parse(rawBody: String): CustomerTokenResult {
        val body = runCatching { json.parseToJsonElement(rawBody) as? JsonObject }.getOrNull()
            ?: return failure(CustomerTokenFailure.InvalidResponse)
        val accessToken = body.boundedString("access_token")
            ?: return failure(CustomerTokenFailure.MissingRequiredFields)
        val expiresIn = body.long("expires_in")
            ?.takeIf { it in 1..MAXIMUM_ACCESS_TOKEN_LIFETIME_SECONDS }
            ?: return failure(CustomerTokenFailure.MissingRequiredFields)
        val tokenType = body.optionalString("token_type")
        if (tokenType is OptionalString.Invalid) return failure(CustomerTokenFailure.InvalidResponse)
        if (tokenType is OptionalString.Value && !tokenType.value.equals(BEARER_TOKEN_TYPE, ignoreCase = true)) {
            return failure(CustomerTokenFailure.UnsupportedTokenType)
        }
        val returnedScope = body.optionalString("scope")
        if (returnedScope is OptionalString.Invalid) return failure(CustomerTokenFailure.InvalidResponse)
        if (returnedScope is OptionalString.Value) {
            val returnedScopes = returnedScope.value.split(' ').filter(String::isNotBlank).toSet()
            if (!returnedScopes.containsAll(expectedScopes)) {
                return failure(CustomerTokenFailure.ScopeMismatch)
            }
        }
        val refreshToken = body.optionalBoundedToken("refresh_token")
        val idToken = body.optionalBoundedToken("id_token")
        if (refreshToken is OptionalToken.Invalid || idToken is OptionalToken.Invalid) {
            return failure(CustomerTokenFailure.InvalidResponse)
        }
        return CustomerTokenResult.Success(
            CustomerTokenPayload(
                accessToken = SensitiveToken.from(accessToken),
                refreshToken = (refreshToken as? OptionalToken.Value)?.value?.let(SensitiveToken::from),
                idToken = (idToken as? OptionalToken.Value)?.value?.let(SensitiveToken::from),
                expiresAt = clock.instant().plusSeconds(expiresIn)
            )
        )
    }

    private fun JsonObject.boundedString(name: String): String? = (get(name) as? JsonPrimitive)?.contentOrNull
        ?.takeIf { it.isNotBlank() && it.length <= MAXIMUM_TOKEN_VALUE_LENGTH }

    private fun JsonObject.long(name: String): Long? = (get(name) as? JsonPrimitive)?.longOrNull

    @Suppress("ReturnCount")
    private fun JsonObject.optionalString(name: String): OptionalString {
        val element = get(name) ?: return OptionalString.Absent
        val value = (element as? JsonPrimitive)?.contentOrNull
            ?.takeIf { it.isNotBlank() && it.length <= MAXIMUM_TOKEN_VALUE_LENGTH }
            ?: return OptionalString.Invalid
        return OptionalString.Value(value)
    }

    private fun JsonObject.optionalBoundedToken(name: String): OptionalToken = when (
        val value = optionalString(name)
    ) {
        OptionalString.Absent -> OptionalToken.Absent
        OptionalString.Invalid -> OptionalToken.Invalid
        is OptionalString.Value -> OptionalToken.Value(value.value)
    }
}

private sealed interface OptionalString {
    data object Absent : OptionalString

    data object Invalid : OptionalString

    data class Value(val value: String) : OptionalString
}

private sealed interface OptionalToken {
    data object Absent : OptionalToken

    data object Invalid : OptionalToken

    data class Value(val value: String) : OptionalToken
}

private fun failure(reason: CustomerTokenFailure): CustomerTokenResult = CustomerTokenResult.Failure(reason)

private suspend fun Call.awaitTokenResult(parser: ShopifyCustomerTokenResponseParser): CustomerTokenResult =
    suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { cancel() }
        enqueue(
            object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) {
                        continuation.resumeWith(Result.success(failure(CustomerTokenFailure.Transient)))
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use {
                        val result = response.toCustomerTokenResult(parser)
                        if (continuation.isActive) continuation.resumeWith(Result.success(result))
                    }
                }
            }
        )
    }

@Suppress("ReturnCount")
private fun Response.toCustomerTokenResult(parser: ShopifyCustomerTokenResponseParser): CustomerTokenResult {
    if (code == HTTP_REQUEST_TIMEOUT || code == HTTP_TOO_MANY_REQUESTS || code >= HTTP_SERVER_ERROR_START) {
        return failure(CustomerTokenFailure.Transient)
    }
    if (code != HTTP_OK) return failure(CustomerTokenFailure.Rejected)
    val responseType = body.contentType()
    if (responseType?.type != "application" || !responseType.subtype.endsWith("json")) {
        return failure(CustomerTokenFailure.InvalidResponse)
    }
    if (body.contentLength() > MAXIMUM_TOKEN_RESPONSE_BYTES) {
        return failure(CustomerTokenFailure.InvalidResponse)
    }
    val source = body.source()
    source.request(MAXIMUM_TOKEN_RESPONSE_BYTES + 1)
    if (source.buffer.size > MAXIMUM_TOKEN_RESPONSE_BYTES) {
        return failure(CustomerTokenFailure.InvalidResponse)
    }
    return parser.parse(source.readUtf8())
}

internal fun CustomerAccountDiscoveredConfiguration.toAppAuthServiceConfiguration(): AuthorizationServiceConfiguration =
    AuthorizationServiceConfiguration(
        AuthorizationServiceDiscovery(JSONObject(openIdConfigurationJson))
    )

internal fun CustomerAccountDiscoveryFailure.toTokenFailure(): CustomerTokenFailure = when (this) {
    CustomerAccountDiscoveryFailure.NETWORK -> CustomerTokenFailure.Transient
    else -> CustomerTokenFailure.InvalidResponse
}
