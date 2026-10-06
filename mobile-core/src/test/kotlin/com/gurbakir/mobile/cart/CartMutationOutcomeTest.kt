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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Real generated parser to coordinator to repository regressions. Synthetic loopback data only. */
class CartMutationOutcomeTest {
    @Test
    fun `large add target remains a Long and cannot wrap into false completion`() = runBlocking {
        withFixture { f ->
            f.reply(read(snapshot(Int.MAX_VALUE)))
            f.reply(mutation("cartLinesAdd", snapshot(Int.MAX_VALUE - 1)))
            assertAdjustment(
                f,
                f.repository.add(VARIANT, 1),
                CartActionAdjustment(CartActionKind.ADD, 2147483647L, 2147483648L, 2147483646L)
            )
        }
    }

    @Test
    fun `rejected new merchandise preserves unrelated existing lines without a reread`() = runBlocking {
        withFixture { f ->
            f.reply(read(snapshot(2)))
            f.reply(mutation("cartLinesAdd", snapshot(2), error = "MERCHANDISE_NOT_APPLICABLE"))
            assertInstanceOf(
                CartActionResult.Failed::class.java,
                f.repository.add("gid://shopify/ProductVariant/rejected-new", 1)
            )
            assertEquals(VARIANT, f.repository.state.value.cart?.lines?.single()?.merchandiseId)
            assertEquals(2, f.repository.state.value.badgeQuantity)
            assertNotNull(f.store.cart)
            assertEquals(0, f.store.clearCount)
            assertEquals(2, f.server.requestCount)
        }
    }

    @Test
    fun `stale update rejection publishes remaining unrelated provider lines`() = runBlocking {
        withFixture { f ->
            f.reply(read(snapshot(2, otherQuantity = 1)))
            f.reply(mutation("cartLinesUpdate", snapshot(0, otherQuantity = 1), error = "INVALID_MERCHANDISE_LINE"))
            assertInstanceOf(CartActionResult.Failed::class.java, f.repository.update(lineId(), 3))
            assertEquals(1, f.repository.state.value.badgeQuantity)
            assertEquals(
                "gid://shopify/ProductVariant/other-outcome-challenge",
                f.repository.state.value.cart?.lines?.single()?.merchandiseId
            )
            assertEquals(0, f.store.clearCount)
            assertEquals(2, f.server.requestCount)
        }
    }

    @Test
    fun `achieved create update and remove stay completed despite warnings`() = runBlocking {
        withFixture(persisted = false) { f ->
            f.reply(mutation("cartCreate", snapshot(1), warning = "MERCHANDISE_NOT_ENOUGH_STOCK"))
            assertEquals(CartActionResult.Completed, f.repository.add(VARIANT, 1))
            assertNull(f.repository.state.value.adjustment)
        }
        withFixture { f ->
            f.reply(read(snapshot(2)))
            f.reply(mutation("cartLinesUpdate", snapshot(4), warning = "MERCHANDISE_NOT_ENOUGH_STOCK"))
            assertEquals(CartActionResult.Completed, f.repository.update(lineId(), 4))
            assertNull(f.repository.state.value.adjustment)
        }
        withFixture { f ->
            f.reply(read(snapshot(2)))
            f.reply(mutation("cartLinesRemove", snapshot(0), warning = "MERCHANDISE_OUT_OF_STOCK"))
            assertEquals(CartActionResult.Completed, f.repository.remove(lineId()))
            assertNull(f.repository.state.value.adjustment)
            assertNotNull(f.store.cart)
        }
    }

    @Test
    fun `lost response and retryable HTTP errors submit one add then reconcile current truth`() = runBlocking {
        for (failure in listOf("lost", "408", "503")) {
            for (quantityAfter in listOf(1, 2)) {
                withFixture { f ->
                    f.reply(read(snapshot(1)))
                    f.server.enqueue(
                        when (failure) {
                            "lost" -> MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST)
                            "408" -> MockResponse().setResponseCode(408)
                            else -> MockResponse().setResponseCode(503).setHeader("Retry-After", "0")
                        }
                    )
                    f.reply(read(snapshot(quantityAfter)))
                    val result = f.repository.add(VARIANT, 1)
                    if (quantityAfter == 2) {
                        assertEquals(CartActionResult.Completed, result)
                    } else {
                        val failed = assertInstanceOf(CartActionResult.Failed::class.java, result)
                        assertEquals(CartFailureCategory.AMBIGUOUS_MUTATION, failed.failure.category)
                    }
                    assertEquals(listOf("CartById", "CartLinesAdd", "CartById"), f.requests().map(::operationName))
                    assertEquals(quantityAfter, f.repository.state.value.badgeQuantity)
                    assertNotNull(f.store.cart)
                    assertEquals(0, f.store.clearCount)
                }
            }
        }
    }

    @Test
    fun `authenticated correction and rejection preserve exact associated ownership`() = runBlocking {
        for (rejected in listOf(false, true)) {
            withFixture(authenticated = true) { f ->
                f.reply(read(owned(snapshot(2, otherQuantity = 1))))
                f.reply(mutation("cartBuyerIdentityUpdate", owned(snapshot(2, otherQuantity = 1))))
                f.reply(
                    mutation(
                        "cartLinesAdd",
                        owned(snapshot(3, otherQuantity = 1)),
                        error = if (rejected) "MAXIMUM_EXCEEDED" else null
                    )
                )
                val result = f.repository.add(VARIANT, 2)
                if (rejected) {
                    val failed = assertInstanceOf(CartActionResult.Failed::class.java, result)
                    assertEquals(CartFailureCategory.QUANTITY_OR_AVAILABILITY, failed.failure.category)
                } else {
                    assertAdjustment(f, result, CartActionAdjustment(CartActionKind.ADD, 2, 4, 3))
                }
                assertEquals(CartOwnership.CUSTOMER_ASSOCIATED, f.repository.state.value.ownership)
                assertEquals(CartOwnership.CUSTOMER_ASSOCIATED, f.store.cart?.ownership)
                assertEquals(CUSTOMER, customerValue(f.store.cart?.customerId))
                assertEquals(4, f.repository.state.value.badgeQuantity)
                assertEquals(3, f.server.requestCount)
                assertEquals(0, f.store.clearCount)
            }
        }
    }

    @Test
    fun `authenticated ambiguous reread never downgrades current cart ownership to anonymous`() = runBlocking {
        withFixture(authenticated = true) { f ->
            f.reply(read(owned(snapshot(2))))
            f.reply(mutation("cartBuyerIdentityUpdate", owned(snapshot(2))))
            f.reply(partial(mutation("cartLinesAdd", owned(snapshot(3)))))
            f.reply(read(owned(snapshot(2))))
            f.reply(mutation("cartBuyerIdentityUpdate", owned(snapshot(2))))
            val result = assertInstanceOf(CartActionResult.Failed::class.java, f.repository.add(VARIANT, 1))
            assertEquals(CartFailureCategory.AMBIGUOUS_MUTATION, result.failure.category)
            assertEquals(CartOwnership.CUSTOMER_ASSOCIATED, f.repository.state.value.ownership)
            assertEquals(CUSTOMER, customerValue(f.store.cart?.customerId))
            assertEquals(2, f.repository.state.value.badgeQuantity)
            assertEquals(5, f.server.requestCount)
            assertEquals(1, f.requests().count { it.contains("CartLinesAdd") })
        }
    }

    @Test
    fun `rejected cart data for another customer stays quarantined with original handle`() = runBlocking {
        withFixture(authenticated = true) { f ->
            f.reply(read(owned(snapshot(2))))
            f.reply(mutation("cartBuyerIdentityUpdate", owned(snapshot(2))))
            f.reply(
                mutation(
                    "cartLinesAdd",
                    owned(snapshot(3), "gid://shopify/Customer/other"),
                    error = "INVALID_MERCHANDISE_LINE"
                )
            )
            assertEquals(CartActionResult.Restricted, f.repository.add(VARIANT, 1))
            assertEquals(CartStatus.RESTRICTED, f.repository.state.value.status)
            assertNull(f.repository.state.value.cart)
            assertEquals(CartOwnership.QUARANTINED, f.store.cart?.ownership)
            assertEquals(CUSTOMER, customerValue(f.store.cart?.customerId))
            assertEquals(cartId(), f.store.cart?.id)
            assertEquals(0, f.store.clearCount)
            assertEquals(3, f.server.requestCount)
        }
    }

    @Test
    fun `positive create correction is distinct from exact add completion`() = runBlocking {
        withFixture(persisted = false) { f ->
            f.reply(mutation("cartCreate", snapshot(2)))
            assertAdjustment(f, f.repository.add(VARIANT, 3), CartActionAdjustment(CartActionKind.ADD, 0, 3, 2))
            assertEquals(2, f.repository.state.value.badgeQuantity)
            assertNotNull(f.store.cart)
            assertEquals(1, f.server.requestCount)
        }
    }

    @Test
    fun `unchanged add is rejected even with a direct successful response`() = runBlocking {
        withFixture { f ->
            f.reply(read(snapshot(2)))
            f.reply(mutation("cartLinesAdd", snapshot(2)))
            assertInstanceOf(CartActionResult.Failed::class.java, f.repository.add(VARIANT, 1))
            assertEquals(CartFailureCategory.QUANTITY_OR_AVAILABILITY, f.repository.state.value.failure?.category)
            assertEquals(2, f.repository.state.value.badgeQuantity)
            assertEquals(0, f.store.clearCount)
        }
    }

    @Test
    fun `partial and excessive direct adds report adjustment without replay`() = runBlocking {
        for (observed in listOf(3, 5)) {
            withFixture { f ->
                f.reply(read(snapshot(2)))
                f.reply(mutation("cartLinesAdd", snapshot(observed)))
                assertAdjustment(
                    f,
                    f.repository.add(VARIANT, 2),
                    CartActionAdjustment(CartActionKind.ADD, 2, 4, observed.toLong())
                )
                assertEquals(observed, f.repository.state.value.badgeQuantity)
                assertEquals(2, f.server.requestCount)
                assertEquals(1, f.requests().count { it.contains("CartLinesAdd") })
            }
        }
    }

    @Test
    fun `add compares total quantity across duplicate merchandise lines`() = runBlocking {
        for (afterSecond in listOf(4, 5)) {
            withFixture { f ->
                f.reply(read(duplicateSnapshot(2, 3)))
                f.reply(mutation("cartLinesAdd", duplicateSnapshot(2, afterSecond)))
                val result = f.repository.add(VARIANT, 2)
                if (afterSecond == 5) {
                    assertEquals(CartActionResult.Completed, result)
                } else {
                    assertAdjustment(f, result, CartActionAdjustment(CartActionKind.ADD, 5, 7, 6))
                }
                assertEquals(2, f.repository.state.value.cart?.lines?.size)
                assertEquals(2 + afterSecond, f.repository.state.value.badgeQuantity)
            }
        }
    }

    @Test
    fun `unchanged update and remove are rejected while direct partial removal is adjusted`() = runBlocking {
        withFixture { f ->
            f.reply(read(snapshot(2)))
            f.reply(mutation("cartLinesUpdate", snapshot(2)))
            assertInstanceOf(CartActionResult.Failed::class.java, f.repository.update(lineId(), 4))
            assertEquals(2, f.repository.state.value.badgeQuantity)
        }
        for (after in listOf(2, 1)) {
            withFixture { f ->
                f.reply(read(snapshot(2)))
                f.reply(mutation("cartLinesRemove", snapshot(after)))
                val result = f.repository.remove(lineId())
                if (after == 2) {
                    assertInstanceOf(CartActionResult.Failed::class.java, result)
                } else {
                    assertAdjustment(f, result, CartActionAdjustment(CartActionKind.REMOVE, 2, 0, 1))
                }
                assertEquals(after, f.repository.state.value.badgeQuantity)
            }
        }
    }

    @Test
    fun `missing cart and target cannot complete an unattempted remove`() = runBlocking {
        withFixture(persisted = false) { f ->
            assertInstanceOf(CartActionResult.Failed::class.java, f.repository.remove(lineId()))
            assertEquals(0, f.server.requestCount)
        }
        withFixture { f ->
            f.reply(read(snapshot(0, otherQuantity = 1)))
            assertInstanceOf(CartActionResult.Failed::class.java, f.repository.remove(lineId()))
            assertEquals(1, f.server.requestCount)
            assertNotNull(f.store.cart)
            assertEquals(1, f.repository.state.value.badgeQuantity)
        }
    }

    @Test
    fun `user error without data reads current cart once and preserves unrelated work`() = runBlocking {
        withFixture { f ->
            f.reply(read(snapshot(1, otherQuantity = 2)))
            f.reply(mutation("cartLinesAdd", "null", error = "MERCHANDISE_NOT_APPLICABLE"))
            f.reply(read(snapshot(1, otherQuantity = 2)))
            assertInstanceOf(CartActionResult.Failed::class.java, f.repository.add(VARIANT, 1))
            assertEquals(3, f.repository.state.value.badgeQuantity)
            assertEquals(3, f.server.requestCount)
            assertEquals(1, f.requests().count { it.contains("CartLinesAdd") })
            assertEquals(0, f.store.clearCount)
        }
    }

    @Test
    fun `rejected create retains its verified returned empty cart without claiming success`() = runBlocking {
        withFixture(persisted = false) { f ->
            f.reply(mutation("cartCreate", snapshot(0), error = "INVALID_MERCHANDISE_LINE"))
            assertInstanceOf(CartActionResult.Failed::class.java, f.repository.add(VARIANT, 1))
            assertEquals(CartStatus.ACTIVE, f.repository.state.value.status)
            assertNotNull(f.store.cart)
            assertEquals(0, f.store.clearCount)
            assertEquals(1, f.server.requestCount)
        }
    }

    @Test
    fun `create with no inserted lines rejects the add while retaining the valid empty cart`() = runBlocking {
        withFixture(persisted = false) { f ->
            f.reply(mutation("cartCreate", snapshot(0), warning = "MERCHANDISE_OUT_OF_STOCK"))
            assertInstanceOf(CartActionResult.Failed::class.java, f.repository.add(VARIANT, 1))
            assertEquals(0, f.repository.state.value.badgeQuantity)
            assertEquals(true, f.repository.state.value.cart?.hasWarnings)
            assertNotNull(f.store.cart)
            assertEquals(1, f.server.requestCount)
        }
    }

    @Test
    fun `update correction publishes provider quantity and reports adjustment`() = runBlocking {
        withFixture { f ->
            f.reply(read(snapshot(2)))
            f.reply(mutation("cartLinesUpdate", snapshot(3), warning = "MERCHANDISE_NOT_ENOUGH_STOCK"))
            assertAdjustment(f, f.repository.update(lineId(), 4), CartActionAdjustment(CartActionKind.UPDATE, 2, 4, 3))
            assertEquals(3, f.repository.state.value.cart?.lines?.single()?.quantity)
            assertEquals(true, f.repository.state.value.cart?.hasWarnings)
            assertNotNull(f.store.cart)
            assertEquals(2, f.server.requestCount)
        }
    }

    @Test
    fun `warning presence does not invalidate an actually achieved add`() = runBlocking {
        withFixture { f ->
            f.reply(read(snapshot(1, otherQuantity = 1)))
            f.reply(mutation("cartLinesAdd", snapshot(2, otherQuantity = 0), warning = "MERCHANDISE_OUT_OF_STOCK"))
            assertEquals(CartActionResult.Completed, f.repository.add(VARIANT, 1))
            assertEquals(2, f.repository.state.value.badgeQuantity)
            assertEquals(2, f.repository.state.value.cart?.lines?.first { it.merchandiseId == VARIANT }?.quantity)
            assertEquals(0, f.repository.state.value.cart?.lines?.first { it.merchandiseId != VARIANT }?.quantity)
            assertEquals(true, f.repository.state.value.cart?.hasWarnings)
            assertNull(f.repository.state.value.failure)
            assertEquals(2, f.server.requestCount)
        }
    }

    @Test
    fun `remove rejection preserves returned valid cart and never completes the requested removal`() = runBlocking {
        withFixture { f ->
            f.reply(read(snapshot(1)))
            val remainingOtherLine = snapshot(
                1
            ).replace(
                LINE,
                "gid://shopify/CartLine/unrelated-valid"
            ).replace(VARIANT, "gid://shopify/ProductVariant/unrelated-valid")
            f.reply(mutation("cartLinesRemove", remainingOtherLine, error = "INVALID_MERCHANDISE_LINE"))
            assertInstanceOf(CartActionResult.Failed::class.java, f.repository.remove(lineId()))
            assertEquals(CartStatus.ACTIVE, f.repository.state.value.status)
            assertNotNull(f.store.cart)
            assertEquals(0, f.store.clearCount)
            assertEquals(
                "gid://shopify/ProductVariant/unrelated-valid",
                f.repository.state.value.cart?.lines?.single()?.merchandiseId
            )
            assertEquals(2, f.server.requestCount)
        }
    }

    @Test
    fun `ordinary rejection publishes updated provider rule and checkout independently rereads`() = runBlocking {
        withFixture { f ->
            f.reply(read(snapshot(1)))
            f.repository.refresh()
            f.reply(read(snapshot(1)))
            val revisedMaximum = snapshot(1).replace("\"maximum\":null", "\"maximum\":1")
            f.reply(mutation("cartLinesUpdate", revisedMaximum, error = "MAXIMUM_EXCEEDED"))
            assertInstanceOf(CartActionResult.Failed::class.java, f.repository.update(lineId(), 2))
            assertEquals(1, f.repository.state.value.badgeQuantity)
            assertEquals(CartFailureCategory.QUANTITY_OR_AVAILABILITY, f.repository.state.value.failure?.category)
            assertNotNull(f.store.cart)
            assertEquals(0, f.store.clearCount)
            assertEquals(3, f.server.requestCount)
            assertEquals(1, f.repository.state.value.cart?.lines?.single()?.quantityRule?.maximum)
            f.reply(read(revisedMaximum))
            assertInstanceOf(CartCheckoutResolution.Eligible::class.java, f.repository.prepareCheckout())
            assertEquals(4, f.server.requestCount)
        }
    }

    @Test
    fun `GraphQL partial data is not trusted and confirmed add reread completes without replay`() = runBlocking {
        withFixture { f ->
            f.reply(read(snapshot(1)))
            f.reply(partial(mutation("cartLinesAdd", snapshot(2))))
            f.reply(read(snapshot(2)))
            assertEquals(CartActionResult.Completed, f.repository.add(VARIANT, 1))
            assertEquals(2, f.repository.state.value.badgeQuantity)
            assertNotNull(f.store.cart)
            assertEquals(3, f.server.requestCount)
            assertEquals(1, f.requests().count { it.contains("CartLinesAdd") })
        }
    }

    @Test
    fun `unchanged ambiguous remove fails without replay even though a valid partial payload exists`() = runBlocking {
        withFixture { f ->
            f.reply(read(snapshot(1)))
            f.reply(partial(mutation("cartLinesRemove", snapshot(0))))
            f.reply(read(snapshot(1)))
            assertInstanceOf(CartActionResult.Failed::class.java, f.repository.remove(lineId()))
            assertEquals(CartFailureCategory.AMBIGUOUS_MUTATION, f.repository.state.value.failure?.category)
            assertEquals(1, f.repository.state.value.badgeQuantity)
            assertNotNull(f.store.cart)
            assertEquals(3, f.server.requestCount)
            assertEquals(1, f.requests().count { it.contains("CartLinesRemove") })
        }
    }

    @Test
    fun `ambiguous create does not replay and cannot claim a cart it never persisted`() = runBlocking {
        withFixture(persisted = false) { f ->
            f.reply(partial(mutation("cartCreate", snapshot(1))))
            val result = f.repository.add(VARIANT, 1)
            assertInstanceOf(CartActionResult.Failed::class.java, result)
            assertEquals(CartStatus.EMPTY, f.repository.state.value.status)
            assertEquals(CartFailureCategory.AMBIGUOUS_MUTATION, f.repository.state.value.failure?.category)
            assertEquals(false, f.repository.state.value.failure?.cartRetained)
            assertNull(f.store.cart)
            assertEquals(1, f.server.requestCount)
        }
    }

    @Test
    fun `missing cart after ambiguous mutation clears the reference but does not confirm the action`() = runBlocking {
        withFixture { f ->
            f.reply(read(snapshot(1)))
            f.reply(mutation("cartLinesAdd", "null"))
            f.reply(read("null"))
            assertInstanceOf(CartActionResult.Failed::class.java, f.repository.add(VARIANT, 1))
            assertEquals(CartStatus.EXPIRED, f.repository.state.value.status)
            assertEquals(CartFailureCategory.AMBIGUOUS_MUTATION, f.repository.state.value.failure?.category)
            assertNull(f.store.cart)
            assertEquals(1, f.store.clearCount)
            assertEquals(3, f.server.requestCount)
        }
    }

    @Test
    fun `successful remove publishes the empty returned cart while retaining its valid reference`() = runBlocking {
        withFixture { f ->
            f.reply(read(snapshot(1)))
            f.reply(mutation("cartLinesRemove", snapshot(0)))
            assertEquals(CartActionResult.Completed, f.repository.remove(lineId()))
            assertEquals(0, f.repository.state.value.badgeQuantity)
            assertEquals(CartStatus.ACTIVE, f.repository.state.value.status)
            assertNotNull(f.store.cart)
            f.reply(read(snapshot(0)))
            assertEquals(CartCheckoutResolution.Unavailable, f.repository.prepareCheckout())
            assertEquals(3, f.server.requestCount)
        }
    }

    @Test
    fun `expiry between restore and mutation fails the action without sending an add`() = runBlocking {
        withFixture { f ->
            f.store.onRead = { read -> if (read == 2) f.clock.current = NOW.plusSeconds(3601) }
            f.reply(read(snapshot(1)))
            assertInstanceOf(CartActionResult.Failed::class.java, f.repository.add(VARIANT, 1))
            assertEquals(CartStatus.EXPIRED, f.repository.state.value.status)
            assertNull(f.store.cart)
            assertEquals(1, f.store.clearCount)
            assertEquals(1, f.server.requestCount)
            assertTrue(f.requests().none { it.contains("CartLinesAdd") })
        }
    }

    @Test
    fun `cancellation preserves reference does not replay and a fresh read recovers possible server commit`() =
        runBlocking {
            withFixture { f ->
                f.reply(read(snapshot(1)))
                f.server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
                val action = launch { f.repository.add(VARIANT, 1) }
                withContext(Dispatchers.IO) {
                    assertNotNull(f.server.takeRequest(2, TimeUnit.SECONDS))
                    assertNotNull(f.server.takeRequest(2, TimeUnit.SECONDS))
                }
                action.cancelAndJoin()
                assertNotNull(f.store.cart)
                assertEquals(2, f.server.requestCount)
                assertNull(f.repository.state.value.mutation)
                assertEquals(CartFailureCategory.AMBIGUOUS_MUTATION, f.repository.state.value.failure?.category)
                assertEquals(true, f.repository.state.value.failure?.retryable)
                f.reply(read(snapshot(2)))
                f.repository.refresh()
                assertEquals(2, f.repository.state.value.badgeQuantity)
                assertNull(f.repository.state.value.mutation)
                assertEquals(3, f.server.requestCount)
            }
        }

    private suspend fun withFixture(
        persisted: Boolean = true,
        authenticated: Boolean = false,
        block: suspend (Fixture) -> Unit
    ) {
        val server = MockWebServer()
        server.start()
        val client = outcomeApolloClient(server.url("graphql").toString())
        try {
            val store =
                OutcomeChallengeStore(
                    if (persisted) {
                        PersistedCart(
                            cartId(),
                            NOW.plusSeconds(3600),
                            if (authenticated) CartOwnership.CUSTOMER_ASSOCIATED else CartOwnership.ANONYMOUS,
                            if (authenticated) customerId() else null
                        )
                    } else {
                        null
                    }
                )
            val clock = OutcomeClock(NOW)
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
                    ),
                    clock
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
        val repository: DefaultCartRepository,
        val clock: OutcomeClock
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
        var readCount = 0
        var onRead: ((Int) -> Unit)? = null
        override suspend fun read(): PersistedCart? {
            readCount++
            onRead?.invoke(readCount)
            return cart
        }
        override suspend fun write(cart: PersistedCart) {
            this.cart = cart
        }
        override suspend fun clear() {
            clearCount++
            cart = null
        }
    }

    private class OutcomeClock(var current: Instant) : Clock() {
        override fun getZone() = ZoneOffset.UTC
        override fun withZone(zone: java.time.ZoneId): Clock = this
        override fun instant() = current
    }

    private fun duplicateSnapshot(first: Int, second: Int): String =
        snapshot(first, otherQuantity = second).replace("gid://shopify/ProductVariant/other-outcome-challenge", VARIANT)

    private fun read(cart: String) = """{"data":{"cart":$cart}}"""
    private fun partial(body: String): String {
        val operation = listOf("cartCreate", "cartLinesAdd", "cartLinesRemove").first { body.contains("\"$it\"") }
        return body.dropLast(1) +
            """, "errors":[{"message":"Synthetic partial resolver failure",
                "path":["$operation","cart","buyerIdentity","customer"],
                "extensions":{"code":"SYNTHETIC_PARTIAL"}}]}"""
    }
    private fun mutation(operation: String, cart: String, error: String? = null, warning: String? = null): String {
        val errors = error?.let { """[{"code":"$it","field":["lines","0","id"]}]""" } ?: "[]"
        val warnings = warning?.let { """[{"code":"$it"}]""" } ?: "[]"
        return """{"data":{"$operation":{"cart":$cart,"userErrors":$errors,"warnings":$warnings}}}"""
    }
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
