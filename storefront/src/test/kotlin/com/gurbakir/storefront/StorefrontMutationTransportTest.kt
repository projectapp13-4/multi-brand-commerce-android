package com.gurbakir.storefront

import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.api.Mutation
import com.apollographql.apollo.api.Optional
import com.gurbakir.storefront.graphql.CartBuyerIdentityUpdateMutation
import com.gurbakir.storefront.graphql.CartCreateMutation
import com.gurbakir.storefront.graphql.CartLinesAddMutation
import com.gurbakir.storefront.graphql.CartLinesRemoveMutation
import com.gurbakir.storefront.graphql.CartLinesUpdateMutation
import com.gurbakir.storefront.graphql.ShopSummaryQuery
import com.gurbakir.storefront.graphql.type.CartBuyerIdentityInput
import com.gurbakir.storefront.graphql.type.CartInput
import com.gurbakir.storefront.graphql.type.CartLineInput
import com.gurbakir.storefront.graphql.type.CartLineUpdateInput
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.SocketPolicy
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.MethodSource

/** Real production factory and pinned HTTP engine; all endpoints and values are synthetic. */
class StorefrontMutationTransportTest {
    @ParameterizedTest(name = "{0}: {1} must not retransmit a sent mutation")
    @MethodSource("uncertainMutationCases")
    fun `sent mutation is not transparently retried or redirected`(
        mutation: CartTransportMutation,
        failure: CartTransportFailure
    ) = runBlocking {
        withFixture { server, client ->
            warmConnection(server, client)
            server.enqueue(failure.response(server))
            // A repeat receives valid data so replay is observable rather than a test timeout.
            server.enqueue(mutation.successResponse())
            val operation = mutation.operation()
            val response = withTimeout(TEST_DEADLINE_MILLIS) { client.mutation(operation).execute() }

            assertCompleteRequest(nextRequest(server), operation.name())
            assertNull(
                server.takeRequest(EXTRA_REQUEST_WAIT_MILLIS, TimeUnit.MILLISECONDS),
                "The production factory retransmitted a completely received mutation body"
            )
            assertEquals(2, server.requestCount, "Expected warm query and exactly one mutation submission")
            assertNotNull(response.exception, "Uncertain mutation must remain available to application reconciliation")
        }
    }

    @ParameterizedTest
    @EnumSource(CartTransportMutation::class)
    fun `ordinary mutation succeeds with one complete submission`(mutation: CartTransportMutation) = runBlocking {
        withFixture { server, client ->
            server.enqueue(mutation.successResponse())
            val operation = mutation.operation()
            val response = withTimeout(TEST_DEADLINE_MILLIS) { client.mutation(operation).execute() }

            assertNull(response.exception)
            assertNotNull(response.data, "Generated response parsing must still work")
            assertCompleteRequest(nextRequest(server), operation.name())
            assertEquals(1, server.requestCount)
        }
    }

    @ParameterizedTest
    @EnumSource(CartTransportFailure::class)
    fun `query retains safe connection and HTTP followup recovery`(failure: CartTransportFailure) = runBlocking {
        withFixture { server, client ->
            warmConnection(server, client)
            server.enqueue(failure.response(server))
            server.enqueue(shopResponse())

            val response = withTimeout(TEST_DEADLINE_MILLIS) { client.query(ShopSummaryQuery()).execute() }

            assertNull(response.exception)
            assertEquals("Synthetic shop", response.data?.shop?.name)
            assertCompleteRequest(nextRequest(server), "ShopSummary", hasVariables = false)
            val recovered = nextRequest(server)
            assertCompleteRequest(
                recovered,
                "ShopSummary",
                if (failure.isRedirect) "/redirect" else "/graphql",
                hasVariables = false
            )
            assertEquals(3, server.requestCount, "Expected warm query and two safe read attempts")
        }
    }

    private suspend fun warmConnection(server: MockWebServer, client: ApolloClient) {
        server.enqueue(shopResponse())
        val response = withTimeout(TEST_DEADLINE_MILLIS) { client.query(ShopSummaryQuery()).execute() }
        assertNull(response.exception)
        assertEquals("Synthetic shop", response.data?.shop?.name)
        assertCompleteRequest(nextRequest(server), "ShopSummary", hasVariables = false)
    }

    private fun assertCompleteRequest(
        request: RecordedRequest,
        operationName: String,
        path: String = "/graphql",
        hasVariables: Boolean = true
    ) {
        assertEquals("POST", request.method)
        assertEquals(path, request.path)
        assertEquals(SYNTHETIC_CLIENT_TOKEN, request.getHeader("X-Shopify-Storefront-Access-Token"))
        val bodySize = request.body.size
        assertTrue(bodySize > 0, "Server must receive a complete nonempty GraphQL body")
        assertEquals(bodySize, request.getHeader("Content-Length")?.toLong())
        val document = Json.parseToJsonElement(request.body.readUtf8()).jsonObject
        assertEquals(operationName, document.getValue("operationName").jsonPrimitive.content)
        assertTrue(document.getValue("query").jsonPrimitive.content.isNotBlank())
        if (hasVariables) assertNotNull(document["variables"])
    }

    private fun nextRequest(server: MockWebServer): RecordedRequest =
        requireNotNull(server.takeRequest(1, TimeUnit.SECONDS)) { "Expected complete synthetic HTTP request" }

    private suspend fun withFixture(block: suspend (MockWebServer, ApolloClient) -> Unit) {
        val server = MockWebServer()
        server.start()
        val client = StorefrontApolloClientFactory.createClientForEndpoint(
            server.url("graphql").toString(),
            SYNTHETIC_CLIENT_TOKEN
        )
        try {
            block(server, client)
        } finally {
            client.close()
            server.shutdown()
        }
    }

    private companion object {
        const val SYNTHETIC_CLIENT_TOKEN = "synthetic-public-client-token"
        const val TEST_DEADLINE_MILLIS = 5_000L
        const val EXTRA_REQUEST_WAIT_MILLIS = 150L

        @JvmStatic
        fun uncertainMutationCases(): List<Arguments> = CartTransportMutation.entries.flatMap { mutation ->
            CartTransportFailure.entries.map { failure -> Arguments.of(mutation, failure) }
        }

        fun shopResponse(): MockResponse = MockResponse().setBody(
            """{"data":{"shop":{"name":"Synthetic shop","primaryDomain":{"host":"synthetic.invalid"}}}}"""
        )
    }
}

enum class CartTransportFailure(val isRedirect: Boolean = false) {
    LOST_RESPONSE,
    HTTP_408,
    HTTP_408_RETRY_AFTER_ZERO,
    HTTP_503_RETRY_AFTER_ZERO,
    HTTP_307(true),
    HTTP_308(true);

    fun response(server: MockWebServer): MockResponse = when (this) {
        LOST_RESPONSE -> MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST)

        HTTP_408 -> MockResponse().setResponseCode(408).setBody("Synthetic request timeout")

        HTTP_408_RETRY_AFTER_ZERO ->
            MockResponse().setResponseCode(408).setHeader("Retry-After", "0").setBody("Synthetic request timeout")

        HTTP_503_RETRY_AFTER_ZERO ->
            MockResponse().setResponseCode(503).setHeader("Retry-After", "0").setBody("Synthetic unavailable")

        HTTP_307 -> MockResponse().setResponseCode(307).setHeader("Location", server.url("redirect"))

        HTTP_308 -> MockResponse().setResponseCode(308).setHeader("Location", server.url("redirect"))
    }
}

enum class CartTransportMutation(private val responseField: String) {
    CREATE("cartCreate"),
    ADD("cartLinesAdd"),
    UPDATE("cartLinesUpdate"),
    REMOVE("cartLinesRemove"),
    BUYER_IDENTITY("cartBuyerIdentityUpdate");

    fun operation(): Mutation<*> = when (this) {
        CREATE -> CartCreateMutation(CartInput(lines = Optional.present(listOf(lineInput()))))

        ADD -> CartLinesAddMutation(CART_ID, listOf(lineInput()))

        UPDATE -> CartLinesUpdateMutation(
            CART_ID,
            listOf(CartLineUpdateInput(id = Optional.present(LINE_ID), quantity = Optional.present(2)))
        )

        REMOVE -> CartLinesRemoveMutation(CART_ID, Optional.present(listOf(LINE_ID)))

        BUYER_IDENTITY ->
            CartBuyerIdentityUpdateMutation(
                CART_ID,
                CartBuyerIdentityInput(customerAccessToken = Optional.present("synthetic-buyer-token"))
            )
    }

    fun successResponse(): MockResponse = MockResponse().setBody(
        """{"data":{"$responseField":{"cart":null,"userErrors":[],"warnings":[]}}}"""
    )

    private fun lineInput(): CartLineInput = CartLineInput(merchandiseId = VARIANT_ID, quantity = Optional.present(1))

    private companion object {
        const val CART_ID = "gid://shopify/Cart/synthetic-transport?key=synthetic-only"
        const val LINE_ID = "gid://shopify/CartLine/synthetic-transport"
        const val VARIANT_ID = "gid://shopify/ProductVariant/synthetic-transport"
    }
}
