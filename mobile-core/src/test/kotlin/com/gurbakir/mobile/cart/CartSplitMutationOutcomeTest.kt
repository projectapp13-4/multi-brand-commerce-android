package com.gurbakir.mobile.cart

import com.gurbakir.account.CustomerAccountGateway
import com.gurbakir.account.CustomerAccountResult
import com.gurbakir.account.CustomerIdentity
import com.gurbakir.account.session.CustomerAccountSessionCoordinator
import com.gurbakir.account.session.CustomerSession
import com.gurbakir.foundation.config.CustomerAccountCapability
import com.gurbakir.storefront.ApolloStorefrontGateway
import com.gurbakir.storefront.CartCoordinator
import com.gurbakir.storefront.CartOwnership
import com.gurbakir.storefront.CartSessionStore
import com.gurbakir.storefront.PersistedCart
import com.gurbakir.storefront.SensitiveCartId
import com.gurbakir.storefront.SensitiveCartLineId
import com.gurbakir.storefront.SensitiveCustomerId
import com.gurbakir.storefront.StorefrontMediaPolicy
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/** Provider line redistribution regressions using synthetic loopback responses only. */
class CartSplitMutationOutcomeTest {
    @Test
    fun `single merchandise update succeeds when provider splits or rekeys its lines`() = runBlocking {
        for (rekeyed in listOf(false, true)) {
            withFixture { f ->
                f.reply(read(snapshot(1)))
                val split = duplicateSnapshot(1, 1).let {
                    if (rekeyed) it.replace(LINE, "gid://shopify/CartLine/rekeyed-synthetic") else it
                }
                f.reply(mutation("cartLinesUpdate", split))
                assertEquals(CartActionResult.Completed, f.repository.update(lineId(), 2))
                assertEquals(2, f.repository.state.value.badgeQuantity)
                assertEquals(2, f.repository.state.value.cart?.lines?.size)
                assertNull(f.repository.state.value.failure)
                assertEquals(2, f.server.requestCount)
            }
        }
    }

    @Test
    fun `split update reports actual aggregate correction without replay`() = runBlocking {
        withFixture { f ->
            f.reply(read(snapshot(1)))
            f.reply(mutation("cartLinesUpdate", duplicateSnapshot(1, 1)))
            assertAdjustment(f, f.repository.update(lineId(), 3), CartActionAdjustment(CartActionKind.UPDATE, 1, 3, 2))
            assertEquals(2, f.repository.state.value.badgeQuantity)
            assertEquals(2, f.server.requestCount)
        }
    }

    @Test
    fun `preexisting duplicate merchandise cannot hide unchanged target line`() = runBlocking {
        withFixture { f ->
            f.reply(read(duplicateSnapshot(2, 3)))
            f.reply(mutation("cartLinesUpdate", duplicateSnapshot(2, 5)))
            assertInstanceOf(CartActionResult.Failed::class.java, f.repository.update(lineId(), 4))
            assertEquals(7, f.repository.state.value.badgeQuantity)
            assertEquals(CartFailureCategory.QUANTITY_OR_AVAILABILITY, f.repository.state.value.failure?.category)
        }
    }

    @Test
    fun `ambiguous single merchandise update reconciles split lines without replay`() = runBlocking {
        withFixture { f ->
            f.reply(read(snapshot(1)))
            f.server.enqueue(MockResponse().setResponseCode(503))
            f.reply(read(duplicateSnapshot(1, 1)))
            assertEquals(CartActionResult.Completed, f.repository.update(lineId(), 2))
            assertEquals(listOf("CartById", "CartLinesUpdate", "CartById"), f.requests().map(::operationName))
            assertNull(f.repository.state.value.failure)
        }
    }

    @Test
    fun `split update never hides unchanged excess or unrelated merchandise quantities`() = runBlocking {
        for (after in listOf(duplicateSnapshot(1, 1), duplicateSnapshot(1, 4), snapshot(2, otherQuantity = 2))) {
            withFixture { f ->
                f.reply(read(snapshot(2)))
                f.reply(mutation("cartLinesUpdate", after))
                val result = f.repository.update(lineId(), 4)
                if (after == duplicateSnapshot(1, 4)) {
                    assertAdjustment(f, result, CartActionAdjustment(CartActionKind.UPDATE, 2, 4, 5))
                } else {
                    assertInstanceOf(CartActionResult.Failed::class.java, result)
                    assertEquals(
                        CartFailureCategory.QUANTITY_OR_AVAILABILITY,
                        f.repository.state.value.failure?.category
                    )
                }
                assertEquals(2, f.server.requestCount)
            }
        }
    }

    @Test
    fun `authenticated split update retains associated customer ownership`() = runBlocking {
        withFixture(authenticated = true) { f ->
            f.reply(read(owned(snapshot(1))))
            f.reply(mutation("cartBuyerIdentityUpdate", owned(snapshot(1))))
            f.reply(mutation("cartLinesUpdate", owned(duplicateSnapshot(1, 1))))
            assertEquals(CartActionResult.Completed, f.repository.update(lineId(), 2))
            assertEquals(2, f.repository.state.value.badgeQuantity)
            assertEquals(CartOwnership.CUSTOMER_ASSOCIATED, f.repository.state.value.ownership)
            assertEquals(CartOwnership.CUSTOMER_ASSOCIATED, f.store.cart?.ownership)
            assertEquals(CUSTOMER, customerValue(f.store.cart?.customerId))
            assertEquals(0, f.store.clearCount)
            assertEquals(3, f.server.requestCount)
        }
    }

    private suspend fun withFixture(authenticated: Boolean = false, block: suspend (Fixture) -> Unit) {
        val server = MockWebServer()
        server.start()
        val client = outcomeApolloClient(server.url("graphql").toString())
        try {
            val store = OutcomeChallengeStore(
                PersistedCart(
                    cartId(),
                    NOW.plusSeconds(3600),
                    if (authenticated) CartOwnership.CUSTOMER_ASSOCIATED else CartOwnership.ANONYMOUS,
                    if (authenticated) customerId() else null
                )
            )
            val clock = Clock.fixed(NOW, ZoneOffset.UTC)
            val coordinator =
                CartCoordinator(
                    ApolloStorefrontGateway(client, StorefrontMediaPolicy("gurbakir.com"), requestTimeoutMillis = 3000),
                    store,
                    clock
                )
            val sessions = if (authenticated) {
                authenticatedOutcomeSessions(clock)
            } else {
                CustomerAccountSessionCoordinator(
                    capability = CustomerAccountCapability.Disabled,
                    tokenClient = { error("disabled customer client requested") },
                    sessionStore = { error("disabled protected customer store requested") }
                )
            }
            block(
                Fixture(
                    server,
                    store,
                    DefaultCartRepository(
                        CoordinatedCartOperations(
                            coordinator,
                            sessions,
                            javax.inject.Provider {
                                check(authenticated) { "disabled identity gateway requested" }
                                object : CustomerAccountGateway {
                                    override suspend fun loadIdentity(): CustomerAccountResult<CustomerIdentity> =
                                        error("explicit leased session required")
                                    override suspend fun loadIdentity(
                                        session: CustomerSession
                                    ): CustomerAccountResult<CustomerIdentity> {
                                        assertEquals("synthetic-current-access", session.accessToken.use { it })
                                        return CustomerAccountResult.Success(CustomerIdentity(CUSTOMER, "Synthetic"))
                                    }
                                }
                            }
                        )
                    )
                )
            )
        } finally {
            client.close()
            server.shutdown()
        }
    }

    private fun owned(cart: String, customer: String = CUSTOMER): String =
        cart.replace("\"customer\":null", "\"customer\":{\"id\":\"$customer\"}")

    private fun customerId(): SensitiveCustomerId = SensitiveCustomerId.from(CUSTOMER)

    private fun customerValue(value: SensitiveCustomerId?): String? = value?.use { it }

    private fun operationName(body: String): String =
        kotlinx.serialization.json.Json.parseToJsonElement(body).let { parsed ->
            (parsed as kotlinx.serialization.json.JsonObject).getValue("operationName").toString().trim('"')
        }

    private fun assertAdjustment(fixture: Fixture, result: CartActionResult, expected: CartActionAdjustment) {
        val adjusted = assertInstanceOf(CartActionResult.Adjusted::class.java, result)
        assertEquals(expected, adjusted.adjustment)
        assertEquals(expected, fixture.repository.state.value.adjustment)
        assertNull(fixture.repository.state.value.failure)
    }

    private data class Fixture(
        val server: MockWebServer,
        val store: OutcomeChallengeStore,
        val repository: DefaultCartRepository
    ) {
        fun reply(body: String) {
            server.enqueue(MockResponse().setBody(body))
        }
        fun requests(): List<String> = List(server.requestCount) {
            requireNotNull(server.takeRequest(1, TimeUnit.SECONDS)).body.readUtf8()
        }
    }

    private class OutcomeChallengeStore(var cart: PersistedCart?) : CartSessionStore {
        var clearCount = 0
        override suspend fun read(): PersistedCart? = cart
        override suspend fun write(cart: PersistedCart) {
            this.cart = cart
        }
        override suspend fun clear() {
            clearCount++
            cart = null
        }
    }

    private fun duplicateSnapshot(first: Int, second: Int): String =
        snapshot(first, otherQuantity = second).replace("gid://shopify/ProductVariant/other-outcome-challenge", VARIANT)

    private fun read(cart: String) = """{"data":{"cart":$cart}}"""
    private fun mutation(operation: String, cart: String): String =
        """{"data":{"$operation":{"cart":$cart,"userErrors":[],"warnings":[]}}}"""

    private fun snapshot(quantity: Int, otherQuantity: Int? = null): String {
        fun line(lineQuantity: Int, id: String, variant: String, available: Boolean = true): String =
            """{"__typename":"CartLine","id":"$id","quantity":$lineQuantity,
            "instructions":{"canRemove":true,"canUpdateQuantity":true},
            "cost":{"amountPerQuantity":{"amount":"10.00","currencyCode":"TRY"},
                "totalAmount":{"amount":"${lineQuantity.toLong() * 10}.00","currencyCode":"TRY"}},
            "merchandise":{"__typename":"ProductVariant","id":"$variant","title":"Synthetic variant",
                "availableForSale":$available,"currentlyNotInStock":false,
                "quantityRule":{"minimum":1,"maximum":null,"increment":1},"image":null,
                "product":{"id":"gid://shopify/Product/outcome-challenge","title":"Synthetic product"}}}"""
        val nodes = buildList {
            if (quantity > 0) add(line(quantity, LINE, VARIANT))
            otherQuantity?.let {
                add(
                    line(
                        it,
                        "gid://shopify/CartLine/other-outcome-challenge",
                        "gid://shopify/ProductVariant/other-outcome-challenge",
                        available =
                            it > 0
                    )
                )
            }
        }.joinToString(",")
        val totalQuantity = quantity + (otherQuantity ?: 0)
        return """{"__typename":"Cart","id":"$CART",
            "checkoutUrl":"https://gurbakir.com/cart/c/synthetic-outcome-challenge",
            "totalQuantity":$totalQuantity,"buyerIdentity":{"customer":null},
            "cost":{"subtotalAmount":{"amount":"${totalQuantity.toLong() * 10}.00","currencyCode":"TRY"},
                "totalAmount":{"amount":"${totalQuantity.toLong() * 10}.00","currencyCode":"TRY"}},
            "lines":{"nodes":[$nodes],"pageInfo":{"endCursor":null,"hasNextPage":false}}}"""
    }
    private fun cartId() = SensitiveCartId::class.java.getDeclaredConstructor(String::class.java).run {
        isAccessible =
            true
        newInstance(CART)
    }
    private fun lineId() = SensitiveCartLineId::class.java.getDeclaredConstructor(String::class.java).run {
        isAccessible =
            true
        newInstance(LINE)
    }
    private companion object {
        val NOW: Instant = Instant.parse("2026-10-05T12:00:00Z")
        const val CART = "gid://shopify/Cart/outcome-challenge?key=synthetic-only"
        const val LINE = "gid://shopify/CartLine/outcome-challenge"
        const val VARIANT = "gid://shopify/ProductVariant/outcome-challenge"
        const val CUSTOMER = "gid://shopify/Customer/current"
    }
}
