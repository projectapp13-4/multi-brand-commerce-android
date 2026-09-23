package com.gurbakir.account.oauth

import com.gurbakir.account.session.SensitiveToken
import com.gurbakir.foundation.config.CustomerAccountConfiguration
import com.gurbakir.foundation.config.REQUIRED_CUSTOMER_ACCOUNT_SCOPES
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import okio.BufferedSource
import okio.ForwardingSource
import okio.buffer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** Exercises the production HTTP callback bridge; a watchdog expiry is a test failure. */
class CustomerAccountHttpBodyFailureTest {
    private lateinit var server: MockWebServer
    private val responseCloses = AtomicInteger()

    @BeforeEach
    fun start() {
        server = MockWebServer()
        server.start()
    }

    @AfterEach
    fun stop() {
        server.shutdown()
    }

    @Test
    fun `declared token body truncation completes as a typed failure`() = runBlocking {
        server.enqueue(
            jsonResponse("""{"access_token":"partial"""")
                .setHeader("Content-Length", "512")
                .setSocketPolicy(SocketPolicy.DISCONNECT_AT_END)
        )

        val result = withTimeoutOrNull(2_000) { tokenClient().refresh(SensitiveToken.from("synthetic-refresh")) }
        assertNotNull(result, "Body-read exception left the production token await pending")
        assertTrue(result is CustomerTokenResult.Failure)
        assertEquals(CustomerTokenFailure.Transient, (result as CustomerTokenResult.Failure).reason)
        assertEquals(1, responseCloses.get())
    }

    @Test
    fun `mid body disconnect completes token await as a typed failure`() = runBlocking {
        server.enqueue(
            jsonResponse("""{"access_token":"${"x".repeat(4_096)}","expires_in":3600}""")
                .setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY)
        )

        val result = withTimeoutOrNull(2_000) { tokenClient().refresh(SensitiveToken.from("synthetic-refresh")) }
        assertNotNull(result, "Mid-body disconnect left the production token await pending")
        assertTrue(result is CustomerTokenResult.Failure)
        assertEquals(CustomerTokenFailure.Transient, (result as CustomerTokenResult.Failure).reason)
        assertEquals(1, responseCloses.get())
    }

    @Test
    fun `body read timeout completes token await as a typed failure`() = runBlocking {
        server.enqueue(
            jsonResponse("""{"access_token":"synthetic-access","expires_in":3600}""").setBodyDelay(2, TimeUnit.SECONDS)
        )

        val result = withTimeoutOrNull(2_000) { tokenClient().refresh(SensitiveToken.from("synthetic-refresh")) }
        assertNotNull(result, "Body-read timeout left the production token await pending")
        assertTrue(result is CustomerTokenResult.Failure)
        assertEquals(CustomerTokenFailure.Transient, (result as CustomerTokenResult.Failure).reason)
        assertEquals(1, responseCloses.get())
    }

    @Test
    fun `discovery fault releases mutex for a subsequent operation`() = runBlocking {
        server.enqueue(jsonResponse(OPEN_ID_DOCUMENT).setBodyDelay(2, TimeUnit.SECONDS))
        server.enqueue(jsonResponse(OPEN_ID_DOCUMENT))
        server.enqueue(jsonResponse(API_DOCUMENT))
        val discovery = ShopifyCustomerAccountDiscoveryClient(
            shopDomain = "shop.example",
            expectedIssuer = ISSUER,
            httpClient = httpClient()
        )

        val first = async(Dispatchers.IO) { discovery.discover() }
        assertNotNull(server.takeRequest(2, TimeUnit.SECONDS), "First discovery request did not reach the socket")
        delay(350)
        val second = async(Dispatchers.IO) { discovery.discover() }
        val firstResult = withTimeoutOrNull(1_000) { first.await() }
        first.cancelAndJoin()
        val secondResult = withTimeoutOrNull(2_000) { second.await() }
        assertNotNull(secondResult, "Recovery request failed to finish after cancellation")
        assertTrue(secondResult is CustomerAccountDiscoveryResult.Success)
        assertNotNull(firstResult, "The first discovery await remained pending after its body-read exception")
        assertTrue(firstResult is CustomerAccountDiscoveryResult.Failure)
        assertEquals(
            CustomerAccountDiscoveryFailure.NETWORK,
            (firstResult as CustomerAccountDiscoveryResult.Failure).reason
        )
        assertEquals(3, responseCloses.get())
    }

    @Test
    fun `cancellation during discovery body read remains cancellation`() = runBlocking {
        server.enqueue(jsonResponse(OPEN_ID_DOCUMENT).setBodyDelay(2, TimeUnit.SECONDS))
        server.enqueue(jsonResponse(OPEN_ID_DOCUMENT))
        server.enqueue(jsonResponse(API_DOCUMENT))
        val discovery = ShopifyCustomerAccountDiscoveryClient(
            shopDomain = "shop.example",
            expectedIssuer = ISSUER,
            httpClient = httpClient()
        )

        val cancelled = async(Dispatchers.IO) { discovery.discover() }
        assertNotNull(server.takeRequest(2, TimeUnit.SECONDS))
        cancelled.cancelAndJoin()
        val next = withTimeoutOrNull(2_000) { discovery.discover() }
        assertTrue(cancelled.isCancelled)
        assertTrue(next is CustomerAccountDiscoveryResult.Success)
        assertEquals(3, server.requestCount)
    }

    @Test
    fun `healthy token body completes through the same production callback`() = runBlocking {
        server.enqueue(jsonResponse("""{"access_token":"synthetic-access","expires_in":3600}"""))

        val result = withTimeoutOrNull(2_000) { tokenClient().refresh(SensitiveToken.from("synthetic-refresh")) }
        assertTrue(result is CustomerTokenResult.Success)
        assertEquals(1, responseCloses.get())
    }

    private fun tokenClient() = ShopifyCustomerAccountTokenClient(
        configuration = configuration(),
        discoveryClient = CustomerAccountDiscoveryClient { CustomerAccountDiscoveryResult.Success(testDiscovery()) },
        httpClient = httpClient()
    )

    private fun httpClient() = OkHttpClient.Builder()
        .readTimeout(200, TimeUnit.MILLISECONDS)
        .addInterceptor { chain ->
            val rewritten = chain.request().newBuilder().url(server.url(chain.request().url.encodedPath)).build()
            chain.proceed(rewritten)
        }
        .addNetworkInterceptor { chain ->
            val response = chain.proceed(chain.request())
            val original = response.body
            val tracked: BufferedSource = object : ForwardingSource(original.source()) {
                override fun close() {
                    responseCloses.incrementAndGet()
                    super.close()
                }
            }.buffer()
            response.newBuilder().body(object : ResponseBody() {
                override fun contentType() = original.contentType()
                override fun contentLength() = original.contentLength()
                override fun source() = tracked
            }).build()
        }
        .build()

    private fun jsonResponse(body: String) = MockResponse()
        .setResponseCode(200)
        .setHeader("Content-Type", "application/json")
        .setBody(body)

    private fun configuration() = CustomerAccountConfiguration(
        clientId = "fixture-public-client",
        issuer = ISSUER,
        authorizationEndpoint = "https://shop.example/authentication/oauth/authorize",
        tokenEndpoint = "https://shop.example/authentication/oauth/token",
        logoutEndpoint = "https://shop.example/authentication/logout",
        graphqlEndpoint = "https://shop.example/customer/api/2026-07/graphql",
        redirectUri = "shop.123456.fixture://oauth/callback",
        userAgent = "Fixture-Android",
        scopes = REQUIRED_CUSTOMER_ACCOUNT_SCOPES
    )

    private companion object {
        const val ISSUER = "https://shopify.com/authentication/123456"
        val OPEN_ID_DOCUMENT = """{
          "issuer":"$ISSUER",
          "authorization_endpoint":"https://shop.example/authentication/oauth/authorize",
          "token_endpoint":"https://shop.example/authentication/oauth/token",
          "end_session_endpoint":"https://shop.example/authentication/logout",
          "jwks_uri":"https://shop.example/authentication/.well-known/jwks.json",
          "code_challenge_methods_supported":["S256"],
          "grant_types_supported":["authorization_code"],
          "id_token_signing_alg_values_supported":["RS256"]
        }"""
        const val API_DOCUMENT = """{"graphql_api":"https://shop.example/customer/api/2026-07/graphql"}"""
    }
}
