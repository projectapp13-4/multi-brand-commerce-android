package com.gurbakir.account.oauth

import java.io.IOException
import java.net.URI
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

private const val OPENID_CONFIGURATION_PATH = "/.well-known/openid-configuration"
private const val CUSTOMER_ACCOUNT_API_CONFIGURATION_PATH = "/.well-known/customer-account-api"
private const val MAXIMUM_DISCOVERY_DOCUMENT_BYTES = 128L * 1024L
private const val PKCE_S256 = "S256"
private const val AUTHORIZATION_CODE_GRANT = "authorization_code"
private const val SHOPIFY_ID_TOKEN_ALGORITHM = "RS256"

class CustomerAccountDiscoveredConfiguration internal constructor(
    val issuer: String,
    val authorizationEndpoint: String,
    val tokenEndpoint: String,
    val logoutEndpoint: String,
    val graphqlEndpoint: String,
    internal val openIdConfigurationJson: String
) {
    override fun toString(): String = "CustomerAccountDiscoveredConfiguration(issuer=$issuer, endpoints=<validated>)"
}

enum class CustomerAccountDiscoveryFailure {
    UNCONFIGURED,
    NETWORK,
    HTTP_REJECTED,
    DOCUMENT_TOO_LARGE,
    INVALID_DOCUMENT,
    ISSUER_MISMATCH,
    UNSUPPORTED_SECURITY_CAPABILITIES
}

sealed interface CustomerAccountDiscoveryResult {
    data class Success(val configuration: CustomerAccountDiscoveredConfiguration) :
        CustomerAccountDiscoveryResult

    data class Failure(val reason: CustomerAccountDiscoveryFailure) : CustomerAccountDiscoveryResult
}

fun interface CustomerAccountDiscoveryClient {
    suspend fun discover(): CustomerAccountDiscoveryResult
}

class UnconfiguredCustomerAccountDiscoveryClient : CustomerAccountDiscoveryClient {
    override suspend fun discover(): CustomerAccountDiscoveryResult =
        CustomerAccountDiscoveryResult.Failure(CustomerAccountDiscoveryFailure.UNCONFIGURED)
}

class ShopifyCustomerAccountDiscoveryClient(
    shopDomain: String,
    private val expectedIssuer: String,
    private val httpClient: OkHttpClient = OkHttpClient.Builder().followRedirects(false).build(),
    private val parser: CustomerAccountDiscoveryParser = CustomerAccountDiscoveryParser()
) : CustomerAccountDiscoveryClient {
    private val openIdConfigurationUrl = trustedDiscoveryUrl(shopDomain, OPENID_CONFIGURATION_PATH)
    private val customerApiConfigurationUrl =
        trustedDiscoveryUrl(shopDomain, CUSTOMER_ACCOUNT_API_CONFIGURATION_PATH)
    private val lock = Mutex()
    private var cached: CustomerAccountDiscoveredConfiguration? = null

    init {
        require(expectedIssuer.isSecureEndpoint()) { "The expected Customer Account issuer must be HTTPS." }
    }

    override suspend fun discover(): CustomerAccountDiscoveryResult = lock.withLock {
        cached?.let { return@withLock CustomerAccountDiscoveryResult.Success(it) }
        val openIdDocument = fetch(openIdConfigurationUrl)
        if (openIdDocument is DiscoveryDocumentFetch.Failure) {
            return@withLock CustomerAccountDiscoveryResult.Failure(openIdDocument.reason)
        }
        val apiDocument = fetch(customerApiConfigurationUrl)
        if (apiDocument is DiscoveryDocumentFetch.Failure) {
            return@withLock CustomerAccountDiscoveryResult.Failure(apiDocument.reason)
        }
        val parsed =
            parser.parse(
                expectedIssuer = expectedIssuer,
                openIdDocument = (openIdDocument as DiscoveryDocumentFetch.Success).body,
                customerApiDocument = (apiDocument as DiscoveryDocumentFetch.Success).body
            )
        if (parsed is CustomerAccountDiscoveryResult.Success) cached = parsed.configuration
        parsed
    }

    private suspend fun fetch(url: String): DiscoveryDocumentFetch {
        val request = Request.Builder().url(url).header("Accept", "application/json").get().build()
        return httpClient.newCall(request).awaitDiscoveryDocument()
    }
}

class CustomerAccountDiscoveryParser(private val json: Json = Json { ignoreUnknownKeys = true }) {
    fun parse(
        expectedIssuer: String,
        openIdDocument: String,
        customerApiDocument: String
    ): CustomerAccountDiscoveryResult = try {
        CustomerAccountDiscoveryResult.Success(
            parseConfiguration(expectedIssuer, openIdDocument, customerApiDocument)
        )
    } catch (failure: DiscoveryDocumentException) {
        CustomerAccountDiscoveryResult.Failure(failure.reason)
    }

    private fun parseConfiguration(
        expectedIssuer: String,
        openIdDocument: String,
        customerApiDocument: String
    ): CustomerAccountDiscoveredConfiguration {
        val openId = openIdDocument.requireObject()
        val customerApi = customerApiDocument.requireObject()
        val issuer = openId.requireString("issuer")
        if (issuer != expectedIssuer) fail(CustomerAccountDiscoveryFailure.ISSUER_MISMATCH)
        val authorizationEndpoint = openId.requireSecureEndpoint("authorization_endpoint")
        val tokenEndpoint = openId.requireSecureEndpoint("token_endpoint")
        val logoutEndpoint = openId.requireSecureEndpoint("end_session_endpoint")
        openId.requireSecureEndpoint("jwks_uri")
        val graphqlEndpoint = customerApi.requireSecureEndpoint("graphql_api")
        val capabilitiesAreSupported =
            PKCE_S256 in openId.strings("code_challenge_methods_supported") &&
                AUTHORIZATION_CODE_GRANT in openId.strings("grant_types_supported") &&
                SHOPIFY_ID_TOKEN_ALGORITHM in openId.strings("id_token_signing_alg_values_supported")
        if (!capabilitiesAreSupported) {
            fail(CustomerAccountDiscoveryFailure.UNSUPPORTED_SECURITY_CAPABILITIES)
        }
        return CustomerAccountDiscoveredConfiguration(
            issuer = issuer,
            authorizationEndpoint = authorizationEndpoint,
            tokenEndpoint = tokenEndpoint,
            logoutEndpoint = logoutEndpoint,
            graphqlEndpoint = graphqlEndpoint,
            openIdConfigurationJson = openIdDocument
        )
    }

    private fun String.requireObject(): JsonObject = runCatching { json.parseToJsonElement(this) as? JsonObject }
        .getOrNull()
        ?: fail(CustomerAccountDiscoveryFailure.INVALID_DOCUMENT)

    private fun JsonObject.requireString(name: String): String =
        string(name) ?: fail(CustomerAccountDiscoveryFailure.INVALID_DOCUMENT)

    private fun JsonObject.requireSecureEndpoint(name: String): String =
        secureEndpoint(name) ?: fail(CustomerAccountDiscoveryFailure.INVALID_DOCUMENT)
}

private class DiscoveryDocumentException(val reason: CustomerAccountDiscoveryFailure) : Exception()

private fun fail(reason: CustomerAccountDiscoveryFailure): Nothing = throw DiscoveryDocumentException(reason)

private sealed interface DiscoveryDocumentFetch {
    data class Success(val body: String) : DiscoveryDocumentFetch

    data class Failure(val reason: CustomerAccountDiscoveryFailure) : DiscoveryDocumentFetch
}

private suspend fun Call.awaitDiscoveryDocument(): DiscoveryDocumentFetch =
    suspendCancellableCoroutine { continuation ->
        val completed = AtomicBoolean(false)
        fun complete(result: DiscoveryDocumentFetch) {
            if (completed.compareAndSet(false, true)) {
                continuation.resumeWith(Result.success(result))
            }
        }
        continuation.invokeOnCancellation {
            completed.set(true)
            cancel()
        }
        enqueue(
            object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    complete(DiscoveryDocumentFetch.Failure(CustomerAccountDiscoveryFailure.NETWORK))
                }

                override fun onResponse(call: Call, response: Response) {
                    val result = try {
                        response.use {
                            val body = response.body
                            when {
                                !response.isSuccessful ->
                                    DiscoveryDocumentFetch.Failure(CustomerAccountDiscoveryFailure.HTTP_REJECTED)

                                body.contentLength() > MAXIMUM_DISCOVERY_DOCUMENT_BYTES ->
                                    DiscoveryDocumentFetch.Failure(CustomerAccountDiscoveryFailure.DOCUMENT_TOO_LARGE)

                                else -> {
                                    val source = body.source()
                                    source.request(MAXIMUM_DISCOVERY_DOCUMENT_BYTES + 1)
                                    if (source.buffer.size > MAXIMUM_DISCOVERY_DOCUMENT_BYTES) {
                                        DiscoveryDocumentFetch.Failure(
                                            CustomerAccountDiscoveryFailure.DOCUMENT_TOO_LARGE
                                        )
                                    } else {
                                        DiscoveryDocumentFetch.Success(source.readUtf8())
                                    }
                                }
                            }
                        }
                    } catch (_: IOException) {
                        DiscoveryDocumentFetch.Failure(CustomerAccountDiscoveryFailure.NETWORK)
                    }
                    complete(result)
                }
            }
        )
    }

private fun trustedDiscoveryUrl(shopDomain: String, path: String): String {
    require(shopDomain.isNotBlank()) { "Shop domain must be configured before discovery." }
    val uri = URI("https://$shopDomain$path")
    require(
        uri.scheme == "https" &&
            uri.host == shopDomain &&
            uri.userInfo == null &&
            uri.port == -1 &&
            uri.query == null &&
            uri.fragment == null
    ) { "Shop domain must be a plain HTTPS host." }
    return uri.toString()
}

private fun JsonObject.string(name: String): String? =
    (get(name) as? JsonPrimitive)?.contentOrNull?.takeIf(String::isNotBlank)

private fun JsonObject.secureEndpoint(name: String): String? = string(name)?.takeIf(String::isSecureEndpoint)

private fun JsonObject.strings(name: String): Set<String> = (get(name) as? JsonArray)
    ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
    ?.toSet()
    .orEmpty()

private fun String.isSecureEndpoint(): Boolean = runCatching {
    val uri = URI(this)
    uri.scheme == "https" &&
        !uri.host.isNullOrBlank() &&
        uri.userInfo == null &&
        uri.fragment == null
}.getOrDefault(false)
