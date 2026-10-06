package com.gurbakir.storefront

import com.apollographql.apollo.ApolloClient
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class CartMutationPayloadTest {
    @Test
    fun `merchandise input errors never establish whole cart expiry`() = runBlocking {
        for (operation in listOf("cartCreate", "cartLinesAdd", "cartLinesUpdate", "cartLinesRemove")) {
            for (code in listOf("INVALID_MERCHANDISE_LINE", "MERCHANDISE_NOT_APPLICABLE")) {
                withGateway { gateway, server ->
                    server.enqueue(MockResponse().setBody(envelope(operation, "null", code)))
                    val result = mutate(gateway, operation)
                    val failure = assertInstanceOf(StorefrontResult.Failure::class.java, result)
                    val errors = assertInstanceOf(StorefrontFailure.UserErrors::class.java, failure.error)
                    assertEquals(code, errors.errors.single().code)
                    assertEquals(listOf("lines", "0", "id"), errors.errors.single().fieldPath)
                    assertEquals(1, server.requestCount)
                }
            }
        }
    }

    @Test
    fun `nonpaged direct mutation cannot certify inconsistent line totals`() = runBlocking {
        withGateway { gateway, server ->
            server.enqueue(MockResponse().setBody(envelope("cartCreate", snapshot(totalQuantity = 2))))
            val result = gateway.createCart(listOf(CartLineInput(VARIANT, 1)))
            val failure = assertInstanceOf(StorefrontResult.Failure::class.java, result)
            assertInstanceOf(StorefrontFailure.GraphQl::class.java, failure.error)
        }
    }

    @Test
    fun `input rejection carries complete data for each mutation without leaking provider messages`() = runBlocking {
        for (operation in listOf("cartCreate", "cartLinesAdd", "cartLinesUpdate", "cartLinesRemove")) {
            for (code in listOf(
                "INVALID_MERCHANDISE_LINE",
                "MERCHANDISE_NOT_APPLICABLE",
                "MAXIMUM_EXCEEDED",
                "FUTURE_INPUT_ERROR"
            )) {
                withGateway { gateway, server ->
                    server.enqueue(MockResponse().setBody(envelope(operation, snapshot(), code)))
                    val failure = assertInstanceOf(StorefrontResult.Failure::class.java, mutate(gateway, operation))
                    val error = assertInstanceOf(StorefrontFailure.UserErrors::class.java, failure.error)
                    assertNotNull(error.cart)
                    assertEquals(1, error.cart?.totalQuantity)
                    assertEquals(VARIANT, error.cart?.lines?.single()?.merchandiseId)
                    assertFalse(error.toString().contains(CART))
                    assertEquals(1, server.requestCount)
                }
            }
        }
    }

    @Test
    fun `rejected malformed cart data cannot become authoritative`() = runBlocking {
        for (cart in listOf(
            snapshot(totalQuantity = 2),
            snapshot().replace("https://gurbakir.com", "http://untrusted.example")
        )) {
            withGateway { gateway, server ->
                server.enqueue(MockResponse().setBody(envelope("cartCreate", cart, "MAXIMUM_EXCEEDED")))
                val failure = assertInstanceOf(
                    StorefrontResult.Failure::class.java,
                    gateway.createCart(listOf(CartLineInput(VARIANT, 1)))
                )
                val error = assertInstanceOf(StorefrontFailure.UserErrors::class.java, failure.error)
                assertNull(error.cart)
                assertEquals("MAXIMUM_EXCEEDED", error.errors.single().code)
            }
        }
    }

    @Test
    fun `top level GraphQL errors keep returned cart outside authoritative data`() = runBlocking {
        withGateway { gateway, server ->
            val body = envelope("cartCreate", snapshot()).dropLast(1) +
                """, "errors":[{"message":"synthetic private message","extensions":{"code":"SYNTHETIC_PARTIAL"}}]}"""
            server.enqueue(MockResponse().setBody(body))
            val failure = assertInstanceOf(
                StorefrontResult.Failure::class.java,
                gateway.createCart(listOf(CartLineInput(VARIANT, 1)))
            )
            assertEquals(StorefrontFailure.GraphQl(setOf("SYNTHETIC_PARTIAL")), failure.error)
        }
    }

    private suspend fun mutate(gateway: StorefrontGateway, operation: String): StorefrontResult<CartReference> =
        when (operation) {
            "cartCreate" -> gateway.createCart(listOf(CartLineInput(VARIANT, 1)))

            "cartLinesAdd" -> gateway.addCartLines(SensitiveCartId.from(CART), listOf(CartLineInput(VARIANT, 1)))

            "cartLinesUpdate" -> gateway.updateCartLines(
                SensitiveCartId.from(CART),
                listOf(CartLineUpdate(SensitiveCartLineId.from(LINE), 2))
            )

            else -> gateway.removeCartLines(SensitiveCartId.from(CART), listOf(SensitiveCartLineId.from(LINE)))
        }

    private suspend fun withGateway(block: suspend (StorefrontGateway, MockWebServer) -> Unit) {
        val server = MockWebServer()
        server.start()
        val client = ApolloClient.Builder().serverUrl(server.url("graphql").toString()).build()
        try {
            block(ApolloStorefrontGateway(client, StorefrontMediaPolicy("gurbakir.com")), server)
        } finally {
            client.close()
            server.shutdown()
        }
    }

    private fun envelope(operation: String, cart: String, error: String? = null): String {
        val errors = error?.let { """[{"code":"$it","field":["lines","0","id"]}]""" } ?: "[]"
        return """{"data":{"$operation":{"cart":$cart,"userErrors":$errors,"warnings":[]}}}"""
    }

    private fun snapshot(totalQuantity: Int = 1): String = """{"__typename":"Cart","id":"$CART",
        "checkoutUrl":"https://gurbakir.com/cart/c/synthetic-payload", "totalQuantity":$totalQuantity,
        "buyerIdentity":{"customer":null},"cost":{"subtotalAmount":{"amount":"10.00","currencyCode":"TRY"},
        "totalAmount":{"amount":"10.00","currencyCode":"TRY"}},
        "lines":{"nodes":[{"__typename":"CartLine","id":"$LINE","quantity":1,
        "instructions":{"canRemove":true,"canUpdateQuantity":true},
        "cost":{"amountPerQuantity":{"amount":"10.00","currencyCode":"TRY"},
        "totalAmount":{"amount":"10.00","currencyCode":"TRY"}},
        "merchandise":{"__typename":"ProductVariant","id":"$VARIANT","title":"Synthetic variant",
        "availableForSale":true,"currentlyNotInStock":false,"quantityRule":{"minimum":1,"maximum":null,"increment":1},
        "image":null,"product":{"id":"gid://shopify/Product/payload","title":"Synthetic product"}}}],
        "pageInfo":{"endCursor":null,"hasNextPage":false}}}"""

    private companion object {
        const val CART = "gid://shopify/Cart/payload?key=synthetic-only"
        const val LINE = "gid://shopify/CartLine/payload"
        const val VARIANT = "gid://shopify/ProductVariant/payload"
    }
}
