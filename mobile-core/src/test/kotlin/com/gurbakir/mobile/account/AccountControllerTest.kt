package com.gurbakir.mobile.account

import com.gurbakir.account.CustomerAccountFailure
import com.gurbakir.account.CustomerAccountGateway
import com.gurbakir.account.CustomerAccountResult
import com.gurbakir.account.CustomerIdentity
import com.gurbakir.account.oauth.CustomerAccountAuthorizationCoordinator
import com.gurbakir.account.oauth.CustomerAccountAuthorizationGrant
import com.gurbakir.account.oauth.CustomerAccountLogoutClient
import com.gurbakir.account.oauth.CustomerAccountTokenClient
import com.gurbakir.account.oauth.CustomerLogoutResult
import com.gurbakir.account.oauth.CustomerTokenFailure
import com.gurbakir.account.oauth.CustomerTokenResult
import com.gurbakir.account.oauth.UnconfiguredCustomerAccountDiscoveryClient
import com.gurbakir.account.session.CustomerAccountSessionCoordinator
import com.gurbakir.account.session.CustomerSession
import com.gurbakir.account.session.CustomerSessionStore
import com.gurbakir.account.session.SensitiveToken
import com.gurbakir.foundation.config.CustomerAccountConfiguration
import com.gurbakir.foundation.config.REQUIRED_CUSTOMER_ACCOUNT_SCOPES
import com.gurbakir.mobile.cart.CartActionResult
import com.gurbakir.mobile.cart.CartCheckoutResolution
import com.gurbakir.mobile.cart.CartRepository
import com.gurbakir.mobile.cart.CartState
import com.gurbakir.mobile.cart.CartStatus
import com.gurbakir.storefront.CartOwnership
import com.gurbakir.storefront.SensitiveCartLineId
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class AccountControllerTest {
    @Test
    fun `restore exposes only the in memory display summary and reconciles cart ownership`() = runTest {
        val cart =
            FakeCartRepository(CartState(status = CartStatus.ACTIVE, ownership = CartOwnership.CUSTOMER_ASSOCIATED))
        val controller = controller(
            session = activeSession(),
            gatewayResult = CustomerAccountResult.Success(CustomerIdentity(CUSTOMER_ID, "Test Customer")),
            cart = cart
        )

        val result = controller.restore()

        val authenticated = assertInstanceOf(AccountResult.Authenticated::class.java, result)
        assertEquals(AccountSummary("Test Customer"), authenticated.summary)
        assertFalse(authenticated.toString().contains(CUSTOMER_ID))
        assertEquals(1, cart.refreshCount)
        assertEquals(emptySet<AccountNotice>(), authenticated.notices)
    }

    @Test
    fun `terminal expiry clears the session and protects the cart before signed out state`() = runTest {
        val sessionStore = InMemorySessionStore(expiredSession())
        val cart =
            FakeCartRepository(CartState(status = CartStatus.RESTRICTED, ownership = CartOwnership.DETACH_PENDING))
        val controller = controller(sessionStore = sessionStore, cart = cart)

        val result = controller.restore()

        val signedOut = assertInstanceOf(AccountResult.SignedOut::class.java, result)
        assertEquals(setOf(AccountNotice.SESSION_EXPIRED, AccountNotice.CART_PROTECTED), signedOut.notices)
        assertNull(sessionStore.session)
        assertEquals(1, cart.refreshCount)
    }

    @Test
    fun `logout always clears local session and reports remote and cart uncertainty separately`() = runTest {
        val sessionStore = InMemorySessionStore(activeSession())
        val cart = FakeCartRepository(CartState(status = CartStatus.RESTRICTED, ownership = CartOwnership.QUARANTINED))
        val controller =
            controller(
                sessionStore = sessionStore,
                cart = cart,
                logoutResult = CustomerLogoutResult.Failure(CustomerTokenFailure.Transient)
            )

        val result = controller.logout()

        val signedOut = assertInstanceOf(AccountResult.SignedOut::class.java, result)
        assertEquals(
            setOf(AccountNotice.REMOTE_LOGOUT_UNVERIFIED, AccountNotice.CART_PROTECTED),
            signedOut.notices
        )
        assertNull(sessionStore.session)
    }

    @Test
    fun `identity transport failure retains the encrypted session for retry`() = runTest {
        val controller =
            controller(
                session = activeSession(),
                gatewayResult = CustomerAccountResult.Failure(CustomerAccountFailure.Transport(retryable = true))
            )

        val result = controller.restore()

        assertEquals(
            AccountResult.Failed(
                reason = AccountFailure.IDENTITY_TRANSPORT,
                retryable = true,
                sessionRetained = true
            ),
            result
        )
    }

    @Test
    fun `unauthenticated GraphQL result clears local session and protects the cart`() = runTest {
        val sessionStore = InMemorySessionStore(activeSession())
        val cart =
            FakeCartRepository(
                CartState(
                    status = CartStatus.RESTRICTED,
                    ownership = CartOwnership.DETACH_PENDING
                )
            )
        val controller =
            controller(
                sessionStore = sessionStore,
                gatewayResult =
                    CustomerAccountResult.Failure(
                        CustomerAccountFailure.GraphQl(setOf("UNAUTHENTICATED"))
                    ),
                cart = cart
            )

        val result = controller.restore()

        val signedOut = assertInstanceOf(AccountResult.SignedOut::class.java, result)
        assertEquals(setOf(AccountNotice.SESSION_EXPIRED, AccountNotice.CART_PROTECTED), signedOut.notices)
        assertNull(sessionStore.session)
    }

    private fun controller(
        session: CustomerSession? = null,
        sessionStore: InMemorySessionStore = InMemorySessionStore(session),
        gatewayResult: CustomerAccountResult<CustomerIdentity> =
            CustomerAccountResult.Failure(CustomerAccountFailure.SignedOut),
        cart: FakeCartRepository = FakeCartRepository(CartState(status = CartStatus.EMPTY)),
        logoutResult: CustomerLogoutResult = CustomerLogoutResult.Success
    ): DefaultAccountController {
        val configuration = configuration()
        val sessionCoordinator =
            CustomerAccountSessionCoordinator(
                configuration = configuration,
                tokenClient = RejectingTokenClient,
                sessionStore = sessionStore,
                logoutClient = CustomerAccountLogoutClient { logoutResult },
                clock = FIXED_CLOCK
            )
        return DefaultAccountController(
            authorizationCoordinator =
                CustomerAccountAuthorizationCoordinator(
                    configuration,
                    UnconfiguredCustomerAccountDiscoveryClient()
                ),
            sessionCoordinator = sessionCoordinator,
            gateway =
                object : CustomerAccountGateway {
                    override suspend fun loadIdentity(): CustomerAccountResult<CustomerIdentity> = gatewayResult
                },
            cartRepository = cart
        )
    }

    private fun activeSession(): CustomerSession = CustomerSession(
        accessToken = SensitiveToken.from("access-token"),
        refreshToken = SensitiveToken.from("refresh-token"),
        idToken = SensitiveToken.from("id-token"),
        expiresAt = NOW.plusSeconds(3600)
    )

    private fun expiredSession(): CustomerSession = CustomerSession(
        accessToken = SensitiveToken.from("expired-access-token"),
        refreshToken = null,
        idToken = SensitiveToken.from("expired-id-token"),
        expiresAt = NOW.minusSeconds(1)
    )

    private fun configuration(): CustomerAccountConfiguration = CustomerAccountConfiguration(
        clientId = "public-client-id",
        issuer = "https://shop.example/customer-account",
        authorizationEndpoint = "https://shop.example/authentication/oauth/authorize",
        tokenEndpoint = "https://shop.example/authentication/oauth/token",
        logoutEndpoint = "https://shop.example/authentication/logout",
        graphqlEndpoint = "https://shop.example/customer/api/2026-07/graphql",
        redirectUri = "shop.123456.example://oauth/callback",
        userAgent = "Test-Android",
        scopes = REQUIRED_CUSTOMER_ACCOUNT_SCOPES
    )

    private class InMemorySessionStore(var session: CustomerSession?) : CustomerSessionStore {
        override suspend fun read(): CustomerSession? = session

        override suspend fun write(session: CustomerSession) {
            this.session = session
        }

        override suspend fun clear() {
            session = null
        }
    }

    private class FakeCartRepository(initialState: CartState) : CartRepository {
        private val mutableState = MutableStateFlow(initialState)
        override val state: StateFlow<CartState> = mutableState
        var refreshCount = 0

        override suspend fun refresh() {
            refreshCount += 1
        }

        override suspend fun add(merchandiseId: String, quantity: Int): CartActionResult = CartActionResult.Completed

        override suspend fun update(lineId: SensitiveCartLineId, quantity: Int): CartActionResult =
            CartActionResult.Completed

        override suspend fun remove(lineId: SensitiveCartLineId): CartActionResult = CartActionResult.Completed

        override suspend fun discard(): CartActionResult = CartActionResult.Completed

        override suspend fun prepareCheckout(): CartCheckoutResolution = CartCheckoutResolution.Unavailable
    }

    private object RejectingTokenClient : CustomerAccountTokenClient {
        override suspend fun exchange(grant: CustomerAccountAuthorizationGrant): CustomerTokenResult =
            CustomerTokenResult.Failure(CustomerTokenFailure.Rejected)

        override suspend fun refresh(refreshToken: SensitiveToken): CustomerTokenResult =
            CustomerTokenResult.Failure(CustomerTokenFailure.Rejected)
    }

    private companion object {
        const val CUSTOMER_ID = "gid://shopify/Customer/private"
        val NOW: Instant = Instant.parse("2026-08-11T12:00:00Z")
        val FIXED_CLOCK: Clock = Clock.fixed(NOW, ZoneOffset.UTC)
    }
}
