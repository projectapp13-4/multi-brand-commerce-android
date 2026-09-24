package com.gurbakir.mobile.cart

import android.app.Activity
import com.gurbakir.account.CustomerAccountFailure
import com.gurbakir.account.CustomerAccountGateway
import com.gurbakir.account.CustomerAccountResult
import com.gurbakir.account.CustomerIdentity
import com.gurbakir.account.oauth.CustomerAccountAuthorizationCoordinator
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
import com.gurbakir.checkout.CheckoutAdapter
import com.gurbakir.checkout.CheckoutResult
import com.gurbakir.foundation.config.CustomerAccountConfiguration
import com.gurbakir.foundation.config.REQUIRED_CUSTOMER_ACCOUNT_SCOPES
import com.gurbakir.mobile.account.AccountNotice
import com.gurbakir.mobile.account.AccountResult
import com.gurbakir.mobile.account.DefaultAccountController
import com.gurbakir.mobile.checkout.CheckoutController
import com.gurbakir.mobile.checkout.CoordinatedCheckoutCartCompleter
import com.gurbakir.storefront.CartCoordinator
import com.gurbakir.storefront.CartLineSummary
import com.gurbakir.storefront.CartOwnership
import com.gurbakir.storefront.CartReference
import com.gurbakir.storefront.CartSessionStore
import com.gurbakir.storefront.PersistedCart
import com.gurbakir.storefront.SensitiveBuyerAccessToken
import com.gurbakir.storefront.SensitiveCartId
import com.gurbakir.storefront.SensitiveCartLineId
import com.gurbakir.storefront.SensitiveCheckoutUrl
import com.gurbakir.storefront.StorefrontFailure
import com.gurbakir.storefront.StorefrontGateway
import com.gurbakir.storefront.StorefrontMoney
import com.gurbakir.storefront.StorefrontResult
import com.gurbakir.storefront.UnconfiguredStorefrontGateway
import java.math.BigDecimal
import java.net.URI
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Regression coverage for failed ownership reconciliation and safe checkout. */
@OptIn(ExperimentalCoroutinesApi::class)
class CartReconciliationFailureTest {
    @Test
    fun `commit false equivalent during pending write never emits associated cart as anonymous`() = runTest {
        val fixture = fixture()
        val emissions = mutableListOf<String>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            fixture.repository.state.collect { emissions += it.toTrace() }
        }
        fixture.repository.refresh()
        assertEquals(CartOwnership.CUSTOMER_ASSOCIATED, fixture.repository.state.value.ownership)

        fixture.cartStore.writeFault = IllegalStateException("synthetic commit false")
        val logout = fixture.controller.logout()
        val afterLogout = fixture.repository.state.value
        val checkout = fixture.repository.prepareCheckout()
        val storedAfterFault = fixture.cartStore.cart?.ownership
        val emissionsBeforeRetry = emissions.toList()
        fixture.cartStore.writeFault = null
        fixture.repository.refresh()
        val recovered = fixture.repository.state.value

        assertInstanceOf(CartCheckoutResolution.Failed::class.java, checkout)
        assertEquals(CartOwnership.CUSTOMER_ASSOCIATED, storedAfterFault)
        assertEquals(CartStatus.ERROR, afterLogout.status)
        assertEquals(CartOwnership.CUSTOMER_ASSOCIATED, afterLogout.ownership)
        assertEquals(CartFailureCategory.SECURE_STORAGE, afterLogout.failure?.category)
        assertTrue(fixture.cartStore.writeTrace.contains("write(DETACH_PENDING)"))
        assertEquals(CartStatus.ACTIVE, recovered.status)
        assertEquals(CartOwnership.ANONYMOUS, recovered.ownership)
        assertEquals(1, recovered.cart?.totalQuantity)
        assertEquals(CartOwnership.ANONYMOUS, fixture.cartStore.cart?.ownership)
        assertFalse(
            emissionsBeforeRetry.any { it.contains("ACTIVE/ANONYMOUS") },
            "Customer-associated retained content was emitted as ACTIVE/ANONYMOUS"
        )
        assertTrue((logout as AccountResult.SignedOut).notices.contains(AccountNotice.CART_RECONCILIATION_FAILED))
    }

    @Test
    fun `crypto exception during pending write never emits associated cart as anonymous`() = runTest {
        val fixture = fixture()
        fixture.repository.refresh()
        fixture.cartStore.writeFault = java.security.GeneralSecurityException("synthetic crypto failure")

        val logout = fixture.controller.logout()
        val afterLogout = fixture.repository.state.value
        val checkout = fixture.repository.prepareCheckout()

        assertInstanceOf(CartCheckoutResolution.Failed::class.java, checkout)
        assertEquals(CartStatus.ERROR, afterLogout.status)
        assertEquals(CartOwnership.CUSTOMER_ASSOCIATED, afterLogout.ownership)
        assertTrue((logout as AccountResult.SignedOut).notices.contains(AccountNotice.CART_RECONCILIATION_FAILED))
        assertFalse(
            afterLogout.status == CartStatus.ACTIVE && afterLogout.ownership == CartOwnership.ANONYMOUS,
            "Customer-associated retained content was emitted as ACTIVE/ANONYMOUS"
        )
    }

    @Test
    fun `network detach failure restricts cart and healthy retry detaches without deleting content`() = runTest {
        val fixture = fixture()
        fixture.repository.refresh()
        fixture.gateway.detachResult = StorefrontResult.Failure(StorefrontFailure.Transport(retryable = true))

        val logout = fixture.controller.logout()
        val restricted = fixture.repository.state.value
        val checkoutWhilePending = fixture.repository.prepareCheckout()
        fixture.gateway.detachResult = StorefrontResult.Success(cart(customerAssociated = false))
        fixture.repository.refresh()
        val recovered = fixture.repository.state.value

        assertEquals(CartStatus.RESTRICTED, restricted.status)
        assertEquals(CartOwnership.DETACH_PENDING, restricted.ownership)
        assertTrue((logout as AccountResult.SignedOut).notices.contains(AccountNotice.CART_PROTECTED))
        assertEquals(CartCheckoutResolution.Restricted, checkoutWhilePending)
        assertEquals(CartStatus.ACTIVE, recovered.status)
        assertEquals(CartOwnership.ANONYMOUS, recovered.ownership)
        assertEquals(1, recovered.cart?.totalQuantity)
    }

    @Test
    fun `failed pending write blocks checkout controller before SDK presentation`() = runTest {
        val fixture = fixture()
        fixture.repository.refresh()
        fixture.cartStore.writeFault = IllegalStateException("synthetic pending write failure")
        fixture.controller.logout()
        val adapter = CountingCheckoutAdapter()
        val checkout = CheckoutController(
            fixture.repository,
            CoordinatedCheckoutCartCompleter(fixture.coordinator),
            adapter
        )

        checkout.start(Activity())
        assertEquals(0, adapter.presentCalls)
        assertEquals(0, adapter.invalidateCalls)
    }

    private fun fixture(): Fixture {
        val cartStore = FaultCartStore(
            PersistedCart(cartId, now.plusSeconds(3_600), CartOwnership.CUSTOMER_ASSOCIATED)
        )
        val sessionStore = MemorySessionStore(
            CustomerSession(
                accessToken = SensitiveToken.from("synthetic-access"),
                refreshToken = SensitiveToken.from("synthetic-refresh"),
                idToken = SensitiveToken.from("synthetic-id"),
                expiresAt = now.plusSeconds(3_600)
            )
        )
        val configuration = configuration()
        val sessionCoordinator = CustomerAccountSessionCoordinator(
            configuration = configuration,
            tokenClient = object : CustomerAccountTokenClient {
                override suspend fun exchange(grant: com.gurbakir.account.oauth.CustomerAccountAuthorizationGrant) =
                    CustomerTokenResult.Failure(CustomerTokenFailure.Rejected)
                override suspend fun refresh(refreshToken: SensitiveToken) =
                    CustomerTokenResult.Failure(CustomerTokenFailure.Rejected)
            },
            sessionStore = sessionStore,
            logoutClient = CustomerAccountLogoutClient { CustomerLogoutResult.Success },
            clock = clock
        )
        val gateway = FaultGateway()
        val coordinator = CartCoordinator(gateway, cartStore, clock)
        val repository = DefaultCartRepository(CoordinatedCartOperations(coordinator, sessionCoordinator))
        val controller = DefaultAccountController(
            authorizationCoordinator = CustomerAccountAuthorizationCoordinator(
                configuration,
                UnconfiguredCustomerAccountDiscoveryClient()
            ),
            sessionCoordinator = sessionCoordinator,
            gateway = object : CustomerAccountGateway {
                override suspend fun loadIdentity(): CustomerAccountResult<CustomerIdentity> =
                    CustomerAccountResult.Failure(CustomerAccountFailure.SignedOut)
            },
            cartRepository = repository
        )
        return Fixture(cartStore, gateway, coordinator, repository, controller)
    }

    private data class Fixture(
        val cartStore: FaultCartStore,
        val gateway: FaultGateway,
        val coordinator: CartCoordinator,
        val repository: DefaultCartRepository,
        val controller: DefaultAccountController
    )

    private class CountingCheckoutAdapter : CheckoutAdapter {
        var presentCalls = 0
        var invalidateCalls = 0
        override suspend fun preload(activity: Activity, checkoutUrl: URI): CheckoutResult = CheckoutResult.Preloaded
        override suspend fun present(activity: Activity, checkoutUrl: URI): CheckoutResult {
            presentCalls++
            return CheckoutResult.Rejected(com.gurbakir.checkout.CheckoutFailure.SDK_UNAVAILABLE)
        }
        override fun invalidate() {
            invalidateCalls++
        }
    }

    private class FaultCartStore(var cart: PersistedCart?) : CartSessionStore {
        var writeFault: Exception? = null
        val writeTrace = mutableListOf<String>()
        override suspend fun read(): PersistedCart? = cart
        override suspend fun write(cart: PersistedCart) {
            writeTrace += "write(${cart.ownership})"
            if (cart.ownership == CartOwnership.DETACH_PENDING) writeFault?.let { throw it }
            this.cart = cart
        }
        override suspend fun clear() {
            writeTrace += "clear"
            cart = null
        }
    }

    private class MemorySessionStore(var session: CustomerSession?) : CustomerSessionStore {
        override suspend fun read(): CustomerSession? = session
        override suspend fun write(session: CustomerSession) {
            this.session = session
        }
        override suspend fun clear() {
            session = null
        }
    }

    private class FaultGateway : StorefrontGateway by UnconfiguredStorefrontGateway() {
        var detachResult: StorefrontResult<CartReference> = StorefrontResult.Success(cart(customerAssociated = false))
        var detachCalls = 0
        override suspend fun loadCart(cartId: SensitiveCartId): StorefrontResult<CartReference> =
            StorefrontResult.Success(cart(customerAssociated = true))
        override suspend fun updateBuyerIdentity(
            cartId: SensitiveCartId,
            buyerAccessToken: SensitiveBuyerAccessToken?
        ): StorefrontResult<CartReference> {
            if (buyerAccessToken != null) return StorefrontResult.Success(cart(customerAssociated = true))
            detachCalls++
            return detachResult
        }
    }

    private fun CartState.toTrace() = "$status/$ownership/q${cart?.totalQuantity ?: 0}/failure=${failure?.category}"

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
        val cartId = syntheticCartId("gid://shopify/Cart/synthetic?key=synthetic-only")
        fun cart(customerAssociated: Boolean): CartReference {
            val money = StorefrontMoney(BigDecimal("10.00"), "TRY")
            return CartReference(
                id = cartId,
                checkoutUrl = syntheticCheckoutUrl(URI("https://shop.example/cart/c/synthetic")),
                totalQuantity = 1,
                lines = listOf(
                    CartLineSummary(
                        id = syntheticLineId("gid://shopify/CartLine/synthetic"),
                        merchandiseId = "gid://shopify/ProductVariant/synthetic",
                        quantity = 1,
                        productId = "gid://shopify/Product/synthetic",
                        productTitle = "Synthetic Product",
                        variantTitle = "Synthetic Variant",
                        unitPrice = money,
                        totalPrice = money
                    )
                ),
                hasMoreLines = false,
                warningCodes = emptySet(),
                subtotal = money,
                total = money,
                customerAssociated = customerAssociated
            )
        }
    }
}

private fun syntheticCartId(value: String): SensitiveCartId =
    SensitiveCartId::class.java.getDeclaredConstructor(String::class.java).run {
        isAccessible = true
        newInstance(value)
    }

private fun syntheticLineId(value: String): SensitiveCartLineId =
    SensitiveCartLineId::class.java.getDeclaredConstructor(String::class.java).run {
        isAccessible = true
        newInstance(value)
    }

private fun syntheticCheckoutUrl(value: URI): SensitiveCheckoutUrl =
    SensitiveCheckoutUrl::class.java.getDeclaredConstructor(URI::class.java).run {
        isAccessible = true
        newInstance(value)
    }
