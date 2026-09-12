package com.gurbakir.mobile.order

import com.gurbakir.account.CustomerAccountFailure
import com.gurbakir.account.CustomerAccountResult
import com.gurbakir.account.CustomerOrderDetail
import com.gurbakir.account.CustomerOrderFinancialStatus
import com.gurbakir.account.CustomerOrderFulfillmentStatus
import com.gurbakir.account.CustomerOrderGateway
import com.gurbakir.account.CustomerOrderMoney
import com.gurbakir.account.CustomerOrderPage
import com.gurbakir.account.CustomerOrderSummary
import com.gurbakir.account.oauth.CustomerAccountLogoutClient
import com.gurbakir.account.oauth.CustomerLogoutResult
import com.gurbakir.account.oauth.CustomerTokenFailure
import com.gurbakir.account.oauth.UnconfiguredCustomerAccountTokenClient
import com.gurbakir.account.session.CustomerAccountSessionCoordinator
import com.gurbakir.account.session.CustomerSession
import com.gurbakir.account.session.CustomerSessionStore
import com.gurbakir.account.session.SensitiveToken
import com.gurbakir.foundation.config.CustomerAccountConfiguration
import com.gurbakir.foundation.config.REQUIRED_CUSTOMER_ACCOUNT_SCOPES
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class OrderControllerTest {
    @Test
    fun `page maps canonical Shopify order ids to bounded route ids`() = runTest {
        val gateway = FakeOrderGateway(pageResult = successPage(listOf(summary("1001")), "next"))

        val result = controller(gateway).loadPage() as OrderPageResult.Content

        assertEquals("1001", result.page.orders.single().routeId)
        assertEquals("next", result.page.nextCursor)
    }

    @Test
    fun `noncanonical server order id fails closed before navigation`() = runTest {
        val invalid = summary("1001").copy(id = "gid://shopify/DraftOrder/1001")

        assertEquals(
            OrderPageResult.Failed(OrderFailure.SERVICE),
            controller(FakeOrderGateway(pageResult = successPage(listOf(invalid)))).loadPage()
        )
    }

    @Test
    fun `detail access denial is generic unavailable and does not destroy a valid session`() = runTest {
        val store = InMemorySessionStore(activeSession())
        val gateway =
            FakeOrderGateway(
                detailResult =
                    CustomerAccountResult.Failure(
                        CustomerAccountFailure.GraphQl(setOf("ACCESS_DENIED"))
                    )
            )

        assertEquals(OrderDetailResult.Unavailable, controller(gateway, store).loadDetail(orderGid("1001")))
        assertNotNull(store.session)
    }

    @Test
    fun `terminal authentication clears protected session and returns to account`() = runTest {
        val store = InMemorySessionStore(activeSession())
        val gateway =
            FakeOrderGateway(
                pageResult =
                    CustomerAccountResult.Failure(
                        CustomerAccountFailure.Authentication(CustomerTokenFailure.Rejected)
                    )
            )

        assertEquals(OrderPageResult.SignedOut, controller(gateway, store).loadPage())
        assertNull(store.session)
    }

    @Test
    fun `retryable transport is recoverable without stale content`() = runTest {
        val gateway =
            FakeOrderGateway(
                detailResult =
                    CustomerAccountResult.Failure(
                        CustomerAccountFailure.Transport(retryable = true)
                    )
            )

        assertEquals(
            OrderDetailResult.Failed(OrderFailure.CONNECTION),
            controller(gateway).loadDetail(orderGid("1001"))
        )
    }

    private fun controller(
        gateway: CustomerOrderGateway,
        store: InMemorySessionStore = InMemorySessionStore(activeSession())
    ) = DefaultOrderController(
        gateway,
        CustomerAccountSessionCoordinator(
            configuration = configuration(),
            tokenClient = UnconfiguredCustomerAccountTokenClient(),
            sessionStore = store,
            logoutClient = CustomerAccountLogoutClient { CustomerLogoutResult.Success },
            clock = FIXED_CLOCK
        )
    )

    private class FakeOrderGateway(
        private val pageResult: CustomerAccountResult<CustomerOrderPage> = successPage(emptyList()),
        private val detailResult: CustomerAccountResult<CustomerOrderDetail?> = CustomerAccountResult.Success(null)
    ) : CustomerOrderGateway {
        override suspend fun loadOrderPage(after: String?): CustomerAccountResult<CustomerOrderPage> = pageResult

        override suspend fun loadOrder(orderId: String): CustomerAccountResult<CustomerOrderDetail?> = detailResult
    }

    private class InMemorySessionStore(var session: CustomerSession?) : CustomerSessionStore {
        override suspend fun read(): CustomerSession? = session

        override suspend fun write(session: CustomerSession) {
            this.session = session
        }

        override suspend fun clear() {
            session = null
        }
    }

    private companion object {
        val NOW: Instant = Instant.parse("2026-08-12T12:00:00Z")
        val FIXED_CLOCK: Clock = Clock.fixed(NOW, ZoneOffset.UTC)

        fun orderGid(id: String) = "gid://shopify/Order/$id"

        fun summary(id: String) = CustomerOrderSummary(
            id = orderGid(id),
            name = "#$id",
            processedAt = "2026-08-12T10:00:00Z",
            financialStatus = CustomerOrderFinancialStatus.PAID,
            fulfillmentStatus = CustomerOrderFulfillmentStatus.UNFULFILLED,
            totalPrice = CustomerOrderMoney("100.00", "TRY")
        )

        fun successPage(orders: List<CustomerOrderSummary>, cursor: String? = null) =
            CustomerAccountResult.Success(CustomerOrderPage(orders, cursor))

        fun activeSession(): CustomerSession = CustomerSession(
            accessToken = SensitiveToken.from("synthetic-access-token"),
            refreshToken = SensitiveToken.from("synthetic-refresh-token"),
            idToken = SensitiveToken.from("synthetic-id-token"),
            expiresAt = NOW.plusSeconds(3_600)
        )

        fun configuration(): CustomerAccountConfiguration = CustomerAccountConfiguration(
            clientId = "public-client-id",
            issuer = "https://shop.example/customer-account",
            authorizationEndpoint = "https://shop.example/authentication/oauth/authorize",
            tokenEndpoint = "https://shop.example/authentication/oauth/token",
            logoutEndpoint = "https://shop.example/authentication/logout",
            graphqlEndpoint = "https://shop.example/customer/api/2026-07/graphql",
            redirectUri = "shop.123456.example://oauth/callback",
            scopes = REQUIRED_CUSTOMER_ACCOUNT_SCOPES
        )
    }
}
