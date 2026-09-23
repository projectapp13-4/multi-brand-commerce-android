package com.gurbakir.mobile.account

import com.gurbakir.account.CustomerAccountGateway
import com.gurbakir.account.CustomerAccountResult
import com.gurbakir.account.CustomerIdentity
import com.gurbakir.account.oauth.CustomerAccountAuthorizationCoordinator
import com.gurbakir.account.oauth.CustomerAccountAuthorizationGrant
import com.gurbakir.account.oauth.CustomerAccountLogoutClient
import com.gurbakir.account.oauth.CustomerAccountTokenClient
import com.gurbakir.account.oauth.CustomerLogoutResult
import com.gurbakir.account.oauth.CustomerTokenPayload
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
import com.gurbakir.storefront.SensitiveCartLineId
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Exercises account actions through deterministic secure-session-store failures. */
@OptIn(ExperimentalCoroutinesApi::class)
class AccountSessionStorageFailureTest {
    @Test
    fun `read failure leaves restore with a typed nonbusy failure`() = runTest {
        val store = FaultSessionStore(session())
        store.readFault = IllegalStateException("synthetic read failure")
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val viewModel = AccountViewModel(fixture(store).controller)
            advanceUntilIdle()
            assertFalse(viewModel.state.value.busy, "Read exception left the real AccountViewModel in a busy phase")
            assertEquals(AccountPhase.FAILED, viewModel.state.value.phase)
            assertEquals(AccountFailure.SECURE_STORAGE, viewModel.state.value.failure)
            assertEquals(listOf("read"), store.events)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `commit false equivalent write failure leaves restore with a typed failure`() = runTest {
        val store = FaultSessionStore(session(expiresAt = now.minusSeconds(1)))
        store.writeFault = IllegalStateException("synthetic commit false")
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val viewModel = AccountViewModel(fixture(store).controller)
            advanceUntilIdle()
            assertFalse(viewModel.state.value.busy, "Write exception left the real AccountViewModel in a busy phase")
            assertEquals(AccountPhase.FAILED, viewModel.state.value.phase)
            assertEquals(AccountFailure.SECURE_STORAGE, viewModel.state.value.failure)
            assertEquals(listOf("read", "write"), store.events)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `crypto write failure leaves restore with a typed failure`() = runTest {
        val store = FaultSessionStore(session(expiresAt = now.minusSeconds(1)))
        store.writeFault = java.security.GeneralSecurityException("synthetic crypto access failure")
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val viewModel = AccountViewModel(fixture(store).controller)
            advanceUntilIdle()
            assertFalse(viewModel.state.value.busy, "Crypto exception left the real AccountViewModel in a busy phase")
            assertEquals(AccountPhase.FAILED, viewModel.state.value.phase)
            assertEquals(AccountFailure.SECURE_STORAGE, viewModel.state.value.failure)
            assertEquals(listOf("read", "write"), store.events)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `clear failure does not report signed out or remain busy`() = runTest {
        val store = FaultSessionStore(session())
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val fixture = fixture(store)
            val viewModel = AccountViewModel(fixture.controller)
            advanceUntilIdle()
            assertEquals(AccountPhase.AUTHENTICATED, viewModel.state.value.phase)
            store.clearFault = IllegalStateException("synthetic clear commit false")
            viewModel.logout()
            advanceUntilIdle()
            assertTrue(store.session != null)
            assertFalse(viewModel.state.value.busy, "Clear exception left the real AccountViewModel in a busy phase")
            assertEquals(AccountPhase.FAILED, viewModel.state.value.phase)
            assertEquals(AccountFailure.SECURE_STORAGE, viewModel.state.value.failure)
            assertEquals(listOf("read", "read", "clear"), store.events)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `failed clear retains session on reconstruction and cancellation propagates`() = runTest {
        val store = FaultSessionStore(session())
        val first = fixture(store)
        store.clearFault = IllegalStateException("synthetic clear failure")
        val logout = first.controller.logout()
        val reconstructed = fixture(store).controller.restore()
        assertInstanceOf(AccountResult.Failed::class.java, logout)
        assertEquals(AccountFailure.SECURE_STORAGE, (logout as AccountResult.Failed).reason)
        assertTrue(logout.sessionRetained)
        assertInstanceOf(AccountResult.Authenticated::class.java, reconstructed)

        store.readFault = CancellationException("synthetic cancelled read")
        val cancellation = runCatching { fixture(store).sessionCoordinator.restore() }.exceptionOrNull()
        assertInstanceOf(CancellationException::class.java, cancellation)
        assertNotNull(store.session)
    }

    @Test
    fun `refresh storage failure does not keep an authenticated UI`() = runTest {
        val store = FaultSessionStore(session())
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val viewModel = AccountViewModel(fixture(store).controller)
            advanceUntilIdle()
            assertEquals(AccountPhase.AUTHENTICATED, viewModel.state.value.phase)

            store.writeFault = IllegalStateException("synthetic refresh write failure")
            viewModel.refresh()
            advanceUntilIdle()

            assertFalse(viewModel.state.value.busy)
            assertEquals(AccountPhase.FAILED, viewModel.state.value.phase)
            assertEquals(AccountFailure.SECURE_STORAGE, viewModel.state.value.failure)
            assertNotNull(store.session)
        } finally {
            Dispatchers.resetMain()
        }
    }

    private fun fixture(store: FaultSessionStore): Fixture {
        val configuration = configuration()
        val sessionCoordinator = CustomerAccountSessionCoordinator(
            configuration = configuration,
            tokenClient = object : CustomerAccountTokenClient {
                override suspend fun exchange(grant: CustomerAccountAuthorizationGrant): CustomerTokenResult =
                    CustomerTokenResult.Success(refreshedPayload())
                override suspend fun refresh(refreshToken: SensitiveToken): CustomerTokenResult =
                    CustomerTokenResult.Success(refreshedPayload())
            },
            sessionStore = store,
            logoutClient = CustomerAccountLogoutClient { CustomerLogoutResult.Success },
            clock = clock
        )
        val controller = DefaultAccountController(
            authorizationCoordinator = CustomerAccountAuthorizationCoordinator(
                configuration,
                UnconfiguredCustomerAccountDiscoveryClient()
            ),
            sessionCoordinator = sessionCoordinator,
            gateway = object : CustomerAccountGateway {
                override suspend fun loadIdentity(): CustomerAccountResult<CustomerIdentity> =
                    CustomerAccountResult.Success(
                        CustomerIdentity("gid://shopify/Customer/synthetic", "Synthetic Customer")
                    )
            },
            cartRepository = EmptyCartRepository()
        )
        return Fixture(sessionCoordinator, controller)
    }

    private data class Fixture(
        val sessionCoordinator: CustomerAccountSessionCoordinator,
        val controller: DefaultAccountController
    )

    private class FaultSessionStore(var session: CustomerSession?) : CustomerSessionStore {
        var readFault: Exception? = null
        var writeFault: Exception? = null
        var clearFault: Exception? = null
        val events = mutableListOf<String>()
        override suspend fun read(): CustomerSession? {
            events += "read"
            readFault?.let { throw it }
            return session
        }
        override suspend fun write(session: CustomerSession) {
            events += "write"
            writeFault?.let { throw it }
            this.session = session
        }
        override suspend fun clear() {
            events += "clear"
            clearFault?.let { throw it }
            session = null
        }
    }

    private class EmptyCartRepository : CartRepository {
        override val state: StateFlow<CartState> = MutableStateFlow(CartState(status = CartStatus.EMPTY))
        override suspend fun refresh() = Unit
        override suspend fun add(merchandiseId: String, quantity: Int): CartActionResult = CartActionResult.Restricted
        override suspend fun update(lineId: SensitiveCartLineId, quantity: Int): CartActionResult =
            CartActionResult.Restricted
        override suspend fun remove(lineId: SensitiveCartLineId): CartActionResult = CartActionResult.Restricted
        override suspend fun discard(): CartActionResult = CartActionResult.Restricted
        override suspend fun prepareCheckout(): CartCheckoutResolution = CartCheckoutResolution.Restricted
    }

    private fun refreshedPayload() = CustomerTokenPayload(
        accessToken = SensitiveToken.from("synthetic-refreshed-access"),
        refreshToken = null,
        idToken = null,
        expiresAt = now.plusSeconds(3_600)
    )

    private fun session(expiresAt: Instant = now.plusSeconds(3_600)) = CustomerSession(
        accessToken = SensitiveToken.from("synthetic-access"),
        refreshToken = SensitiveToken.from("synthetic-refresh"),
        idToken = SensitiveToken.from("synthetic-id"),
        expiresAt = expiresAt
    )

    private fun configuration() = CustomerAccountConfiguration(
        clientId = "synthetic-public-client",
        issuer = "https://shop.example/customer-account",
        authorizationEndpoint = "https://shop.example/authentication/oauth/authorize",
        tokenEndpoint = "https://shop.example/authentication/oauth/token",
        logoutEndpoint = "https://shop.example/authentication/logout",
        graphqlEndpoint = "https://shop.example/customer/api/2026-07/graphql",
        redirectUri = "shop.123456.example://oauth/callback",
        userAgent = "Synthetic-Android",
        scopes = REQUIRED_CUSTOMER_ACCOUNT_SCOPES
    )

    private companion object {
        val now = Instant.parse("2026-09-23T12:00:00Z")
        val clock = Clock.fixed(now, ZoneOffset.UTC)
    }
}
