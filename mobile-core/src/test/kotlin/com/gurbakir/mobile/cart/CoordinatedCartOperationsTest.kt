package com.gurbakir.mobile.cart

import com.gurbakir.account.oauth.CustomerAccountAuthorizationGrant
import com.gurbakir.account.oauth.CustomerAccountTokenClient
import com.gurbakir.account.oauth.CustomerTokenFailure
import com.gurbakir.account.oauth.CustomerTokenResult
import com.gurbakir.account.session.CustomerAccountSessionCoordinator
import com.gurbakir.account.session.CustomerSession
import com.gurbakir.account.session.CustomerSessionStore
import com.gurbakir.account.session.SensitiveToken
import com.gurbakir.foundation.config.CustomerAccountCapability
import com.gurbakir.foundation.config.CustomerAccountConfiguration
import com.gurbakir.foundation.config.REQUIRED_CUSTOMER_ACCOUNT_SCOPES
import com.gurbakir.storefront.CartCoordinator
import com.gurbakir.storefront.CartLineInput
import com.gurbakir.storefront.CartLineUpdate
import com.gurbakir.storefront.CartOwnership
import com.gurbakir.storefront.CartReference
import com.gurbakir.storefront.CartSessionResolution
import com.gurbakir.storefront.CartSessionStore
import com.gurbakir.storefront.CatalogPage
import com.gurbakir.storefront.Cursor
import com.gurbakir.storefront.PersistedCart
import com.gurbakir.storefront.SensitiveBuyerAccessToken
import com.gurbakir.storefront.SensitiveCartId
import com.gurbakir.storefront.SensitiveCartLineId
import com.gurbakir.storefront.SensitiveCheckoutUrl
import com.gurbakir.storefront.ShopSummary
import com.gurbakir.storefront.StorefrontFailure
import com.gurbakir.storefront.StorefrontGateway
import com.gurbakir.storefront.StorefrontResult
import java.net.URI
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test

class CoordinatedCartOperationsTest {
    @Test
    fun `disabled account quarantines rejected detach and never exposes customer cart`() = runTest {
        val store = InMemoryCartStore(persisted(CartOwnership.CUSTOMER_ASSOCIATED))
        val gateway =
            SessionAwareGateway(detachResult = StorefrontResult.Failure(StorefrontFailure.Transport(retryable = false)))
        val operations = disabledOperations(store, gateway)
        assertEquals(CartSessionResolution.Restricted(CartOwnership.QUARANTINED), operations.restore())
        assertEquals(CartOwnership.QUARANTINED, store.cart?.ownership)
        assertEquals(CartSessionResolution.Restricted(CartOwnership.QUARANTINED), operations.restore())
        assertEquals(1, gateway.detachCalls)
        assertEquals(0, gateway.loadCalls)
    }

    @Test
    fun `disabled account detaches customer cart without opening customer storage`() = runTest {
        val store = InMemoryCartStore(persisted(CartOwnership.CUSTOMER_ASSOCIATED))
        val gateway = SessionAwareGateway(detachResult = StorefrontResult.Success(cart(customerAssociated = false)))
        val operations = disabledOperations(store, gateway)
        assertEquals(CartOwnership.ANONYMOUS, assertActive(operations.restore()).ownership)
        assertEquals(CartOwnership.ANONYMOUS, store.cart?.ownership)
        assertEquals(1, gateway.detachCalls)
        assertEquals(0, gateway.attachCalls)
    }

    @Test
    fun `disabled account retains detach pending cart and blocks mutation on uncertain detach`() = runTest {
        val store = InMemoryCartStore(persisted(CartOwnership.CUSTOMER_ASSOCIATED))
        val gateway =
            SessionAwareGateway(detachResult = StorefrontResult.Failure(StorefrontFailure.Transport(retryable = true)))
        val repository = DefaultCartRepository(disabledOperations(store, gateway))
        val result = repository.add("gid://shopify/ProductVariant/1", 1)
        assertEquals(CartActionResult.Restricted, result)
        assertEquals(CartStatus.RESTRICTED, repository.state.value.status)
        assertEquals(CartOwnership.DETACH_PENDING, store.cart?.ownership)
        assertEquals(0, gateway.addCalls)
        assertEquals(0, gateway.loadCalls)
    }

    private fun disabledOperations(store: InMemoryCartStore, gateway: SessionAwareGateway) = CoordinatedCartOperations(
        CartCoordinator(gateway, store, FIXED_CLOCK),
        CustomerAccountSessionCoordinator(
            capability = CustomerAccountCapability.Disabled,
            tokenClient = { error("disabled client requested") },
            sessionStore = { error("disabled encrypted store requested") }
        )
    )

    @Test
    fun `authenticated restore associates an anonymous cart exactly once`() = runTest {
        val cartStore = InMemoryCartStore(persisted(CartOwnership.ANONYMOUS))
        val gateway = SessionAwareGateway(attachResult = StorefrontResult.Success(cart(customerAssociated = true)))
        val operations = operations(activeSession(), StableTokenClient, cartStore, gateway)

        val first = operations.restore()
        val second = operations.restore()

        assertEquals(CartOwnership.CUSTOMER_ASSOCIATED, assertActive(first).ownership)
        assertEquals(CartOwnership.CUSTOMER_ASSOCIATED, assertActive(second).ownership)
        assertEquals(CartOwnership.CUSTOMER_ASSOCIATED, cartStore.cart?.ownership)
        assertEquals(1, gateway.attachCalls)
        assertEquals(1, gateway.loadCalls)
    }

    @Test
    fun `signed out restore keeps an uncertain customer cart detach pending`() = runTest {
        val cartStore = InMemoryCartStore(persisted(CartOwnership.CUSTOMER_ASSOCIATED))
        val gateway =
            SessionAwareGateway(
                detachResult = StorefrontResult.Failure(StorefrontFailure.Transport(retryable = true))
            )
        val operations = operations(null, StableTokenClient, cartStore, gateway)

        val result = operations.restore()

        assertEquals(
            CartSessionResolution.Restricted(CartOwnership.DETACH_PENDING),
            result
        )
        assertEquals(CartOwnership.DETACH_PENDING, cartStore.cart?.ownership)
        assertEquals(1, gateway.detachCalls)
    }

    @Test
    fun `transient session refresh failure never exposes a customer cart anonymously`() = runTest {
        val cartStore = InMemoryCartStore(persisted(CartOwnership.CUSTOMER_ASSOCIATED))
        val operations =
            operations(
                expiredSession(),
                TransientTokenClient,
                cartStore,
                SessionAwareGateway()
            )

        val result = operations.restore()

        assertEquals(
            CartSessionResolution.Restricted(CartOwnership.CUSTOMER_ASSOCIATED),
            result
        )
        assertEquals(CartOwnership.CUSTOMER_ASSOCIATED, cartStore.cart?.ownership)
    }

    @Test
    fun `authenticated mutation prepares ownership once before applying its plan`() = runTest {
        val cartStore = InMemoryCartStore(persisted(CartOwnership.ANONYMOUS))
        val gateway =
            SessionAwareGateway(
                attachResult = StorefrontResult.Success(cart(customerAssociated = true)),
                addResult = StorefrontResult.Success(cart(customerAssociated = true))
            )
        val operations = operations(activeSession(), StableTokenClient, cartStore, gateway)

        val attempt = operations.mutate { resolution ->
            assertEquals(CartOwnership.CUSTOMER_ASSOCIATED, assertActive(resolution).ownership)
            CartMutationPlan.Add(listOf(CartLineInput("gid://shopify/ProductVariant/1", 1)))
        }

        assertEquals(CartOwnership.CUSTOMER_ASSOCIATED, assertActive(requireNotNull(attempt.result)).ownership)
        assertEquals(1, gateway.attachCalls)
        assertEquals(1, gateway.addCalls)
        assertEquals(0, gateway.loadCalls)
    }

    @Test
    fun `signed out mutation restores anonymous cart once before applying its plan`() = runTest {
        val cartStore = InMemoryCartStore(persisted(CartOwnership.ANONYMOUS))
        val gateway =
            SessionAwareGateway(
                loadResult = StorefrontResult.Success(cart(customerAssociated = false)),
                addResult = StorefrontResult.Success(cart(customerAssociated = false))
            )
        val operations = operations(null, StableTokenClient, cartStore, gateway)

        val attempt = operations.mutate {
            CartMutationPlan.Add(listOf(CartLineInput("gid://shopify/ProductVariant/1", 1)))
        }

        assertEquals(CartOwnership.ANONYMOUS, assertActive(requireNotNull(attempt.result)).ownership)
        assertEquals(1, gateway.loadCalls)
        assertEquals(1, gateway.addCalls)
        assertEquals(0, gateway.attachCalls)
        assertEquals(0, gateway.detachCalls)
    }

    @Test
    fun `retained session failure cannot plan or execute an anonymous mutation`() = runTest {
        val cartStore = InMemoryCartStore(persisted(CartOwnership.CUSTOMER_ASSOCIATED))
        val gateway = SessionAwareGateway()
        val operations = operations(expiredSession(), TransientTokenClient, cartStore, gateway)
        var plannerCalls = 0

        val attempt = operations.mutate {
            plannerCalls += 1
            CartMutationPlan.Add(listOf(CartLineInput("gid://shopify/ProductVariant/1", 1)))
        }

        assertEquals(CartSessionResolution.Restricted(CartOwnership.CUSTOMER_ASSOCIATED), attempt.before)
        assertEquals(CartMutationPlan.None, attempt.plan)
        assertEquals(null, attempt.result)
        assertEquals(0, plannerCalls)
        assertEquals(0, gateway.addCalls)
    }

    private fun operations(
        session: CustomerSession?,
        tokenClient: CustomerAccountTokenClient,
        cartStore: InMemoryCartStore,
        gateway: SessionAwareGateway
    ): CoordinatedCartOperations = CoordinatedCartOperations(
        coordinator = CartCoordinator(gateway, cartStore, FIXED_CLOCK),
        sessionCoordinator =
            CustomerAccountSessionCoordinator(
                configuration = configuration(),
                tokenClient = tokenClient,
                sessionStore = InMemoryCustomerSessionStore(session),
                clock = FIXED_CLOCK
            )
    )

    private fun assertActive(result: CartSessionResolution): CartSessionResolution.Active =
        assertInstanceOf(CartSessionResolution.Active::class.java, result)

    private fun persisted(ownership: CartOwnership): PersistedCart =
        PersistedCart(CART_ID, NOW.plusSeconds(3600), ownership)

    private fun cart(customerAssociated: Boolean): CartReference = CartReference(
        id = CART_ID,
        checkoutUrl = CHECKOUT_URL,
        totalQuantity = 0,
        lines = emptyList(),
        hasMoreLines = false,
        warningCodes = emptySet(),
        customerAssociated = customerAssociated
    )

    private fun activeSession(): CustomerSession = CustomerSession(
        accessToken = SensitiveToken.from("synthetic-access-token"),
        refreshToken = SensitiveToken.from("synthetic-refresh-token"),
        idToken = SensitiveToken.from("synthetic-id-token"),
        expiresAt = NOW.plusSeconds(3600)
    )

    private fun expiredSession(): CustomerSession = CustomerSession(
        accessToken = SensitiveToken.from("expired-access-token"),
        refreshToken = SensitiveToken.from("expired-refresh-token"),
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

    private class InMemoryCustomerSessionStore(private var session: CustomerSession?) : CustomerSessionStore {
        override suspend fun read(): CustomerSession? = session

        override suspend fun write(session: CustomerSession) {
            this.session = session
        }

        override suspend fun clear() {
            session = null
        }
    }

    private class InMemoryCartStore(var cart: PersistedCart?) : CartSessionStore {
        override suspend fun read(): PersistedCart? = cart

        override suspend fun write(cart: PersistedCart) {
            this.cart = cart
        }

        override suspend fun clear() {
            cart = null
        }
    }

    private class SessionAwareGateway(
        private val attachResult: StorefrontResult<CartReference> =
            StorefrontResult.Failure(StorefrontFailure.Transport(retryable = false)),
        private val detachResult: StorefrontResult<CartReference> =
            StorefrontResult.Failure(StorefrontFailure.Transport(retryable = false)),
        private val loadResult: StorefrontResult<CartReference> = attachResult,
        private val addResult: StorefrontResult<CartReference> = attachResult
    ) : StorefrontGateway {
        var attachCalls = 0
        var detachCalls = 0
        var loadCalls = 0
        var addCalls = 0

        override suspend fun updateBuyerIdentity(
            cartId: SensitiveCartId,
            buyerAccessToken: SensitiveBuyerAccessToken?
        ): StorefrontResult<CartReference> = if (buyerAccessToken == null) {
            detachCalls += 1
            detachResult
        } else {
            attachCalls += 1
            attachResult
        }

        override suspend fun loadCart(cartId: SensitiveCartId): StorefrontResult<CartReference> {
            loadCalls += 1
            return loadResult
        }

        override suspend fun loadShopSummary(): StorefrontResult<ShopSummary> = unused()

        override suspend fun loadCatalogPage(after: Cursor?): StorefrontResult<CatalogPage> = unused()

        override suspend fun createCart(
            lines: List<CartLineInput>,
            buyerAccessToken: SensitiveBuyerAccessToken?
        ): StorefrontResult<CartReference> = unused()

        override suspend fun addCartLines(
            cartId: SensitiveCartId,
            lines: List<CartLineInput>
        ): StorefrontResult<CartReference> {
            addCalls += 1
            return addResult
        }

        override suspend fun updateCartLines(
            cartId: SensitiveCartId,
            lines: List<CartLineUpdate>
        ): StorefrontResult<CartReference> = unused()

        override suspend fun removeCartLines(
            cartId: SensitiveCartId,
            lineIds: List<SensitiveCartLineId>
        ): StorefrontResult<CartReference> = unused()

        private fun <T> unused(): StorefrontResult<T> =
            StorefrontResult.Failure(StorefrontFailure.Transport(retryable = false))
    }

    private object StableTokenClient : CustomerAccountTokenClient {
        override suspend fun exchange(grant: CustomerAccountAuthorizationGrant): CustomerTokenResult =
            CustomerTokenResult.Failure(CustomerTokenFailure.InvalidResponse)

        override suspend fun refresh(refreshToken: SensitiveToken): CustomerTokenResult =
            CustomerTokenResult.Failure(CustomerTokenFailure.InvalidResponse)
    }

    private object TransientTokenClient : CustomerAccountTokenClient {
        override suspend fun exchange(grant: CustomerAccountAuthorizationGrant): CustomerTokenResult =
            CustomerTokenResult.Failure(CustomerTokenFailure.Transient)

        override suspend fun refresh(refreshToken: SensitiveToken): CustomerTokenResult =
            CustomerTokenResult.Failure(CustomerTokenFailure.Transient)
    }

    private companion object {
        val NOW: Instant = Instant.parse("2026-08-11T12:00:00Z")
        val FIXED_CLOCK: Clock = Clock.fixed(NOW, ZoneOffset.UTC)
        val CART_ID: SensitiveCartId = syntheticCartId("gid://shopify/Cart/test?key=synthetic-secret")
        val CHECKOUT_URL: SensitiveCheckoutUrl = syntheticCheckoutUrl(URI("https://shop.example/cart/c/synthetic"))
    }
}

private fun syntheticCartId(value: String): SensitiveCartId =
    SensitiveCartId::class.java.getDeclaredConstructor(String::class.java).run {
        isAccessible = true
        newInstance(value)
    }

private fun syntheticCheckoutUrl(value: URI): SensitiveCheckoutUrl =
    SensitiveCheckoutUrl::class.java.getDeclaredConstructor(URI::class.java).run {
        isAccessible = true
        newInstance(value)
    }
