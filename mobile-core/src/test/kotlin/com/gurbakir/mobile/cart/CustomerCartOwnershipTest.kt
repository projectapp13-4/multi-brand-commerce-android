package com.gurbakir.mobile.cart

import android.app.Activity
import com.gurbakir.account.CustomerAccountFailure
import com.gurbakir.account.CustomerAccountGateway
import com.gurbakir.account.CustomerAccountResult
import com.gurbakir.account.CustomerIdentity
import com.gurbakir.account.oauth.CustomerAccountAuthorizationCoordinator
import com.gurbakir.account.oauth.CustomerAccountAuthorizationGrant
import com.gurbakir.account.oauth.CustomerAccountDiscoveryClient
import com.gurbakir.account.oauth.CustomerAccountDiscoveryParser
import com.gurbakir.account.oauth.CustomerAccountLogoutClient
import com.gurbakir.account.oauth.CustomerAccountTokenClient
import com.gurbakir.account.oauth.CustomerLogoutResult
import com.gurbakir.account.oauth.CustomerTokenPayload
import com.gurbakir.account.oauth.CustomerTokenResult
import com.gurbakir.account.session.CustomerAccountSessionCoordinator
import com.gurbakir.account.session.CustomerSession
import com.gurbakir.account.session.CustomerSessionStore
import com.gurbakir.account.session.SensitiveToken
import com.gurbakir.checkout.CheckoutAdapter
import com.gurbakir.checkout.CheckoutFailure
import com.gurbakir.checkout.CheckoutResult
import com.gurbakir.checkout.CheckoutSessionId
import com.gurbakir.foundation.config.CustomerAccountConfiguration
import com.gurbakir.foundation.config.REQUIRED_CUSTOMER_ACCOUNT_SCOPES
import com.gurbakir.mobile.account.AccountEffect
import com.gurbakir.mobile.account.AccountFailure
import com.gurbakir.mobile.account.AccountPreparation
import com.gurbakir.mobile.account.AccountResult
import com.gurbakir.mobile.account.AccountViewModel
import com.gurbakir.mobile.account.DefaultAccountController
import com.gurbakir.mobile.checkout.CheckoutCartCompleter
import com.gurbakir.mobile.checkout.CheckoutController
import com.gurbakir.storefront.CartCompletionResolution
import com.gurbakir.storefront.CartCoordinator
import com.gurbakir.storefront.CartLineInput
import com.gurbakir.storefront.CartLineSummary
import com.gurbakir.storefront.CartLineUpdate
import com.gurbakir.storefront.CartOwnership
import com.gurbakir.storefront.CartReference
import com.gurbakir.storefront.CartSessionStore
import com.gurbakir.storefront.CatalogPage
import com.gurbakir.storefront.Cursor
import com.gurbakir.storefront.PersistedCart
import com.gurbakir.storefront.SensitiveBuyerAccessToken
import com.gurbakir.storefront.SensitiveCartId
import com.gurbakir.storefront.SensitiveCartLineId
import com.gurbakir.storefront.SensitiveCheckoutUrl
import com.gurbakir.storefront.SensitiveCustomerId
import com.gurbakir.storefront.ShopSummary
import com.gurbakir.storefront.StorefrontFailure
import com.gurbakir.storefront.StorefrontGateway
import com.gurbakir.storefront.StorefrontMoney
import com.gurbakir.storefront.StorefrontResult
import java.math.BigDecimal
import java.net.URI
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Base64
import javax.inject.Provider
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Connected account/session/cart regressions use only synthetic provider and protected-store fixtures. */
@OptIn(ExperimentalCoroutinesApi::class)
class CustomerCartOwnershipTest {
    @Test
    fun `cold identity failure retaining A cannot authorize customer replacement`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val fixture = Fixture()
            fixture.identityFailure = true
            val account = AccountViewModel(fixture.controller)
            advanceUntilIdle()

            assertNotNull(fixture.sessionStore.session)
            assertFalse(account.state.value.canSignIn)
            assertTrue(account.state.value.canUseSessionActions)
            account.startAuthorization()
            advanceUntilIdle()
            assertEquals(0, fixture.exchangeCalls)
            assertEquals("synthetic-A", fixture.sessionStore.session?.accessToken?.use { it })
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `valid hosted B callback after cold A identity failure cannot replace session or inherit cart`() =
        rejectRetainedCustomerReplacement(failedLogout = false)

    @Test
    fun `valid hosted B callback after failed A logout cannot replace session or inherit cart`() =
        rejectRetainedCustomerReplacement(failedLogout = true)

    private fun rejectRetainedCustomerReplacement(failedLogout: Boolean) = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val fixture = Fixture()
            val originalSession = fixture.sessionStore.session
            val originalCart = fixture.cartStore.cart
            fixture.identityFailure = true
            val account = AccountViewModel(fixture.controller)
            advanceUntilIdle()
            assertEquals(AccountFailure.IDENTITY_TRANSPORT, account.state.value.failure)
            assertFalse(account.state.value.canSignIn)
            if (failedLogout) {
                fixture.sessionStore.clearFails = true
                account.logout()
                advanceUntilIdle()
                assertEquals(AccountFailure.SECURE_STORAGE, account.state.value.failure)
                assertFalse(account.state.value.canSignIn)
            }

            // Exercise an outstanding hosted callback through the real parser despite the blocked UI affordance.
            val preparation = fixture.controller.prepareAuthorization() as AccountPreparation.Ready
            val result = fixture.controller.consumeCallback(
                configuration().redirectUri + "?code=synthetic-B&state=" + preparation.plan.transaction.state
            )

            assertEquals(AccountResult.Failed(AccountFailure.TOKEN_REJECTED, false, true), result)
            assertEquals(0, fixture.exchangeCalls)
            assertEquals(originalSession, fixture.sessionStore.session)
            assertEquals(originalCart, fixture.cartStore.cart)
            assertEquals(CartCheckoutResolution.Restricted, fixture.carts.prepareCheckout())
            assertEquals(CartActionResult.Restricted, fixture.carts.add("gid://shopify/ProductVariant/1", 1))
            assertEquals(0, fixture.storefront.loadCalls)
            assertEquals(0, fixture.storefront.buyerUpdates)
            assertEquals(0, fixture.storefront.addCalls)
            assertEquals(originalCart, fixture.cartStore.cart)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `failed logout before durable clear cannot authorize replacement`() = failedLogout(false)

    @Test
    fun `applied clear with failed receipt cannot authorize replacement`() = failedLogout(true)

    private fun failedLogout(clearApplies: Boolean) = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val fixture = Fixture()
            val account = AccountViewModel(fixture.controller)
            advanceUntilIdle()
            fixture.sessionStore.clearFails = true
            fixture.sessionStore.clearApplies = clearApplies
            account.logout()
            advanceUntilIdle()

            assertEquals(AccountFailure.SECURE_STORAGE, account.state.value.failure)
            assertFalse(account.state.value.canSignIn)
            assertTrue(account.state.value.canUseSessionActions)
            account.startAuthorization()
            advanceUntilIdle()
            assertEquals(0, fixture.exchangeCalls)
            assertNotNull(fixture.cartStore.cart)
            fixture.sessionStore.clearFails = false
            account.logout()
            advanceUntilIdle()
            assertTrue(account.state.value.canSignIn)
            assertFalse(account.state.value.canUseSessionActions)
            assertEquals(CartOwnership.ANONYMOUS, fixture.cartStore.cart?.ownership)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `restored B cannot mutate or check out legacy associated A cart`() = runTest {
        val fixture = Fixture()
        fixture.sessionStore.session = session("synthetic-B")

        assertEquals(CartCheckoutResolution.Restricted, fixture.carts.prepareCheckout())
        assertEquals(CartActionResult.Restricted, fixture.carts.add("gid://shopify/ProductVariant/1", 1))
        assertEquals(0, fixture.storefront.addCalls)
        assertEquals(0, fixture.storefront.buyerUpdates)
        assertEquals(CartOwnership.QUARANTINED, fixture.cartStore.cart?.ownership)
        assertEquals(CART_ID, fixture.cartStore.cart?.id)
    }

    @Test
    fun `same customer restore and mutation retain cart and verify ownership`() = runTest {
        val fixture = Fixture()

        assertTrue(fixture.carts.prepareCheckout() is CartCheckoutResolution.Eligible)
        assertEquals(CartActionResult.Completed, fixture.carts.add("gid://shopify/ProductVariant/1", 1))
        assertEquals(1, fixture.storefront.addCalls)
        assertEquals(CART_ID, fixture.cartStore.cart?.id)
        assertEquals(CartOwnership.CUSTOMER_ASSOCIATED, fixture.cartStore.cart?.ownership)
        assertEquals(SensitiveCustomerId.from("synthetic-A"), fixture.cartStore.cart?.customerId)
    }

    @Test
    fun `same customer renewed token is rebound before eligible checkout`() = runTest {
        val fixture = Fixture()
        fixture.sessionStore.session = session("synthetic-A").copy(expiresAt = NOW.minusSeconds(1))

        assertTrue(fixture.carts.prepareCheckout() is CartCheckoutResolution.Eligible)
        assertEquals("synthetic-A-renewed", fixture.storefront.lastBuyerToken)
        assertEquals(SensitiveCustomerId.from("synthetic-A"), fixture.cartStore.cart?.customerId)
    }

    @Test
    fun `successful logout then B authorization detaches before associating the retained cart`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val fixture = Fixture()
            val account = AccountViewModel(fixture.controller)
            advanceUntilIdle()
            account.logout()
            advanceUntilIdle()
            assertTrue(account.state.value.canSignIn)
            assertEquals(CartOwnership.ANONYMOUS, fixture.cartStore.cart?.ownership)
            account.startAuthorization()
            advanceUntilIdle()
            val effect = account.effects.first() as AccountEffect.LaunchAuthorization
            account.consumeAuthorizationResult(
                configuration().redirectUri + "?code=synthetic-B&state=" + effect.plan.transaction.state
            )
            advanceUntilIdle()

            assertEquals("synthetic-B", fixture.sessionStore.session?.accessToken?.use { it })
            assertTrue(fixture.carts.prepareCheckout() is CartCheckoutResolution.Eligible)
            assertEquals(SensitiveCustomerId.from("synthetic-B"), fixture.cartStore.cart?.customerId)
            assertEquals(CART_ID, fixture.cartStore.cart?.id)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `restart under B keeps exact A ownership restricted without provider writes`() = runTest {
        val fixture = Fixture()
        fixture.carts.refresh()
        val retained = requireNotNull(fixture.cartStore.cart)
        fixture.sessionStore.session = session("synthetic-B")
        fixture.storefront.buyerUpdates = 0
        val restarted = fixture.restartedCartRepository()

        assertEquals(CartCheckoutResolution.Restricted, restarted.prepareCheckout())
        assertEquals(CartActionResult.Restricted, restarted.add("gid://shopify/ProductVariant/1", 1))
        assertEquals(0, fixture.storefront.buyerUpdates)
        assertEquals(retained.id, fixture.cartStore.cart?.id)
        assertEquals(retained.customerId, fixture.cartStore.cart?.customerId)
    }

    @Test
    fun `ambiguous rebind stays protected across restart until same customer verifies`() = runTest {
        val fixture = Fixture()
        fixture.storefront.rebindFails = true

        assertEquals(CartCheckoutResolution.Restricted, fixture.carts.prepareCheckout())
        assertEquals(CartOwnership.VERIFY_PENDING, fixture.cartStore.cart?.ownership)
        assertEquals(CartActionResult.Restricted, fixture.carts.add("gid://shopify/ProductVariant/1", 1))
        assertEquals(0, fixture.storefront.addCalls)
        val restarted = fixture.restartedCartRepository()
        assertEquals(CartCheckoutResolution.Restricted, restarted.prepareCheckout())
        assertNotNull(fixture.cartStore.cart)
        fixture.storefront.rebindFails = false

        assertTrue(restarted.prepareCheckout() is CartCheckoutResolution.Eligible)
        assertEquals(SensitiveCustomerId.from("synthetic-A"), fixture.cartStore.cart?.customerId)
    }

    @Test
    fun `pending secure write failure suppresses rebind mutation and checkout while preserving cart`() = runTest {
        val fixture = Fixture()
        fixture.cartStore.writeFails = true

        assertTrue(fixture.carts.prepareCheckout() is CartCheckoutResolution.Failed)
        assertTrue(fixture.carts.add("gid://shopify/ProductVariant/1", 1) is CartActionResult.Failed)
        assertEquals(0, fixture.storefront.buyerUpdates)
        assertEquals(0, fixture.storefront.addCalls)
        assertEquals(CART_ID, fixture.cartStore.cart?.id)
    }

    @Test
    fun `provider rebind response with different exact owner quarantines without additive mutation`() = runTest {
        val fixture = Fixture()
        fixture.storefront.returnWrongOwner = true

        assertEquals(CartActionResult.Restricted, fixture.carts.add("gid://shopify/ProductVariant/1", 1))
        assertEquals(CartCheckoutResolution.Restricted, fixture.carts.prepareCheckout())
        assertEquals(0, fixture.storefront.addCalls)
        assertEquals(CartOwnership.QUARANTINED, fixture.cartStore.cart?.ownership)
        assertEquals(SensitiveCustomerId.from("synthetic-A"), fixture.cartStore.cart?.customerId)
    }

    @Test
    fun `provider rebind returning another cart preserves and quarantines original handle`() = runTest {
        val fixture = Fixture()
        fixture.storefront.returnWrongCart = true

        assertEquals(CartCheckoutResolution.Restricted, fixture.carts.prepareCheckout())
        assertEquals(CART_ID, fixture.cartStore.cart?.id)
        assertEquals(CartOwnership.QUARANTINED, fixture.cartStore.cart?.ownership)
        assertEquals(CartActionResult.Restricted, fixture.carts.add("gid://shopify/ProductVariant/1", 1))
        assertEquals(0, fixture.storefront.addCalls)
    }

    @Test
    fun `uncertain cart read retains exact ownership and healthy retry recovers`() = runTest {
        val fixture = Fixture()
        fixture.carts.refresh()
        val original = requireNotNull(fixture.cartStore.cart)
        fixture.cartStore.readFails = true

        val failed = fixture.carts.prepareCheckout()
        assertTrue(failed is CartCheckoutResolution.Failed)
        assertTrue((failed as CartCheckoutResolution.Failed).failure.cartRetained)
        assertEquals(original, fixture.cartStore.cart)
        fixture.cartStore.readFails = false

        assertTrue(fixture.carts.prepareCheckout() is CartCheckoutResolution.Eligible)
        assertEquals(original.customerId, fixture.cartStore.cart?.customerId)
    }

    @Test
    fun `missing current identity suppresses every cart provider call and never becomes anonymous`() = runTest {
        val fixture = Fixture()
        fixture.identityFailure = true

        assertEquals(CartCheckoutResolution.Restricted, fixture.carts.prepareCheckout())
        assertEquals(CartActionResult.Restricted, fixture.carts.add("gid://shopify/ProductVariant/1", 1))
        assertEquals(0, fixture.storefront.loadCalls)
        assertEquals(0, fixture.storefront.buyerUpdates)
        assertEquals(CartOwnership.CUSTOMER_ASSOCIATED, fixture.cartStore.cart?.ownership)
    }

    @Test
    fun `oversized current identity stays restricted and preserves cart without provider calls`() = runTest {
        val fixture = Fixture()
        val retained = fixture.cartStore.cart
        fixture.identityIdOverride = "x".repeat(64 * 1024 + 1)

        assertEquals(CartCheckoutResolution.Restricted, fixture.carts.prepareCheckout())
        assertEquals(CartActionResult.Restricted, fixture.carts.add("gid://shopify/ProductVariant/1", 1))
        assertEquals(retained, fixture.cartStore.cart)
        assertEquals(0, fixture.storefront.loadCalls)
        assertEquals(0, fixture.storefront.buyerUpdates)
        assertEquals(0, fixture.storefront.addCalls)
    }

    @Test
    fun `unapplied anonymous attach stays protected and same customer retries after restart`() = runTest {
        val fixture = Fixture()
        fixture.cartStore.cart = fixture.cartStore.cart?.copy(ownership = CartOwnership.ANONYMOUS)
        fixture.storefront.owner = null
        fixture.storefront.rebindFails = true
        assertEquals(CartCheckoutResolution.Restricted, fixture.carts.prepareCheckout())
        assertEquals(CartOwnership.VERIFY_PENDING, fixture.cartStore.cart?.ownership)
        val restarted = fixture.restartedCartRepository()
        fixture.storefront.rebindFails = false

        assertTrue(restarted.prepareCheckout() is CartCheckoutResolution.Eligible)
        assertEquals(CART_ID, fixture.cartStore.cart?.id)
        assertEquals(SensitiveCustomerId.from("synthetic-A"), fixture.cartStore.cart?.customerId)
    }

    @Test
    fun `applied anonymous attach with lost response keeps exact A pending and rejects B after restart`() = runTest {
        val fixture = Fixture()
        fixture.cartStore.cart = fixture.cartStore.cart?.copy(ownership = CartOwnership.ANONYMOUS)
        fixture.storefront.owner = null
        fixture.storefront.rebindAppliesBeforeFailure = true
        fixture.storefront.rebindFails = true
        assertEquals(CartCheckoutResolution.Restricted, fixture.carts.prepareCheckout())
        fixture.sessionStore.session = session("synthetic-B")
        fixture.storefront.buyerUpdates = 0
        fixture.storefront.rebindFails = false
        val restarted = fixture.restartedCartRepository()

        assertEquals(CartCheckoutResolution.Restricted, restarted.prepareCheckout())
        assertEquals(0, fixture.storefront.buyerUpdates)
        assertEquals(SensitiveCustomerId.from("synthetic-A"), fixture.cartStore.cart?.customerId)
    }

    @Test
    fun `cancellation during buyer rebind leaves durable pending identity for verified restart`() = runTest {
        val fixture = Fixture()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        fixture.storefront.beforeRebind = {
            entered.complete(Unit)
            release.await()
        }
        val pending = async { fixture.carts.prepareCheckout() }
        entered.await()
        pending.cancel()
        pending.join()
        assertEquals(CartOwnership.VERIFY_PENDING, fixture.cartStore.cart?.ownership)
        assertEquals(CART_ID, fixture.cartStore.cart?.id)
        fixture.storefront.beforeRebind = null

        assertTrue(fixture.restartedCartRepository().prepareCheckout() is CartCheckoutResolution.Eligible)
    }

    @Test
    fun `logout cannot overtake a leased same customer cart mutation`() = runTest {
        val fixture = Fixture()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        fixture.storefront.beforeLoad = {
            entered.complete(Unit)
            release.await()
        }
        val mutation = async { fixture.carts.add("gid://shopify/ProductVariant/1", 1) }
        entered.await()
        val logout = async { fixture.sessions.logout() }
        runCurrent()
        assertFalse(logout.isCompleted)
        assertNotNull(fixture.sessionStore.session)
        release.complete(Unit)

        assertEquals(CartActionResult.Completed, mutation.await())
        logout.await()
        assertEquals(null, fixture.sessionStore.session)
        assertEquals(1, fixture.storefront.addCalls)
    }

    @Test
    fun `checkout launch callback holds exact session until bounded provider launch completes`() = runTest {
        val fixture = Fixture()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val launch = async {
            fixture.carts.withPreparedCheckout { resolution ->
                assertTrue(resolution is CartCheckoutResolution.Eligible)
                entered.complete(Unit)
                release.await()
            }
        }
        entered.await()
        val logout = async { fixture.sessions.logout() }
        runCurrent()
        val logoutOvertookLaunch = logout.isCompleted
        release.complete(Unit)
        launch.await()
        logout.await()

        assertFalse(logoutOvertookLaunch)
    }

    @Test
    fun `real checkout controller launch keeps logout behind verified current customer`() = runTest {
        val fixture = Fixture()
        val controller = checkoutController(fixture)
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var launches = 0
        val launch = async {
            controller.startWithPresentation {
                launches++
                entered.complete(Unit)
                release.await()
                CheckoutResult.Rejected(CheckoutFailure.SDK_UNAVAILABLE)
            }
        }
        entered.await()
        val logout = async { fixture.sessions.logout() }
        runCurrent()
        val logoutOvertookLaunch = logout.isCompleted
        release.complete(Unit)
        launch.await()
        logout.await()
        assertFalse(logoutOvertookLaunch)
        assertEquals(1, launches)
    }

    @Test
    fun `real checkout controller releases session lease while presented event collection remains active`() = runTest {
        val fixture = Fixture()
        val collectionEntered = CompletableDeferred<Unit>()
        val collectionFinished = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val launch = async {
            checkoutController(fixture).startWithPresentation {
                CheckoutResult.Presented(
                    CheckoutSessionId(1),
                    flow {
                        collectionEntered.complete(Unit)
                        try {
                            release.await()
                        } finally {
                            collectionFinished.complete(Unit)
                        }
                    },
                    com.gurbakir.checkout.CheckoutPresentationOwner {}
                )
            }
        }
        collectionEntered.await()
        val logout = async { fixture.sessions.logout() }
        runCurrent()
        val logoutCompletedDuringCollection = logout.isCompleted
        val collectionWasStillActive = !launch.isCompleted && !collectionFinished.isCompleted
        launch.cancel()
        launch.join()
        logout.await()

        assertTrue(logoutCompletedDuringCollection)
        assertTrue(collectionWasStillActive)
        assertTrue(launch.isCancelled)
        assertTrue(collectionFinished.isCompleted)
    }

    @Test
    fun `real checkout controller never launches SDK for B retaining A cart`() = runTest {
        val fixture = Fixture()
        fixture.sessionStore.session = session("synthetic-B")
        var launches = 0

        checkoutController(fixture).startWithPresentation {
            launches++
            CheckoutResult.Rejected(CheckoutFailure.SDK_UNAVAILABLE)
        }

        assertEquals(0, launches)
        assertEquals(CART_ID, fixture.cartStore.cart?.id)
    }

    private fun checkoutController(fixture: Fixture) = CheckoutController(
        fixture.carts,
        object : CheckoutCartCompleter {
            override suspend fun complete(cartId: SensitiveCartId): CartCompletionResolution = error("no completion")
        },
        object : CheckoutAdapter {
            override suspend fun preload(activity: Activity, checkoutUrl: URI): CheckoutResult = error("no preload")
            override suspend fun present(activity: Activity, checkoutUrl: URI): CheckoutResult =
                error("use bounded launch seam")
            override fun invalidate() = Unit
        }
    )

    private class Fixture {
        val sessionStore = MemorySessionStore(session("synthetic-A"))
        val cartStore =
            MemoryCartStore(PersistedCart(CART_ID, NOW.plusSeconds(86400), CartOwnership.CUSTOMER_ASSOCIATED))
        val storefront = OwnedCartGateway()
        var identityFailure = false
        var identityIdOverride: String? = null
        var exchangeCalls = 0
        private val tokenClient = object : CustomerAccountTokenClient {
            override suspend fun exchange(grant: CustomerAccountAuthorizationGrant): CustomerTokenResult {
                exchangeCalls++
                return CustomerTokenResult.Success(
                    CustomerTokenPayload(
                        SensitiveToken.from("synthetic-B"),
                        SensitiveToken.from("refresh-B"),
                        SensitiveToken.from(idToken(grant.expectedNonce.use { it })),
                        NOW.plusSeconds(3600)
                    )
                )
            }

            override suspend fun refresh(refreshToken: SensitiveToken): CustomerTokenResult =
                CustomerTokenResult.Success(
                    CustomerTokenPayload(
                        SensitiveToken.from("synthetic-A-renewed"),
                        SensitiveToken.from("refresh-A-renewed"),
                        null,
                        NOW.plusSeconds(3600)
                    )
                )
        }
        val sessions = CustomerAccountSessionCoordinator(
            configuration(),
            tokenClient,
            sessionStore,
            logoutClient = CustomerAccountLogoutClient { CustomerLogoutResult.Success },
            clock = CLOCK
        )
        val identities = object : CustomerAccountGateway {
            override suspend fun loadIdentity(session: CustomerSession): CustomerAccountResult<CustomerIdentity> =
                if (identityFailure) {
                    CustomerAccountResult.Failure(CustomerAccountFailure.Transport(true))
                } else {
                    CustomerAccountResult.Success(
                        CustomerIdentity(
                            identityIdOverride ?: customerForToken(
                                session.accessToken.use {
                                    it
                                }
                            ),
                            "Synthetic"
                        )
                    )
                }
            override suspend fun loadIdentity(): CustomerAccountResult<CustomerIdentity> = if (identityFailure) {
                CustomerAccountResult.Failure(CustomerAccountFailure.Transport(true))
            } else {
                val owner = sessionStore.session?.accessToken?.use { it } ?: "signed-out"
                CustomerAccountResult.Success(CustomerIdentity(owner, owner))
            }
        }
        val carts =
            DefaultCartRepository(
                CoordinatedCartOperations(
                    CartCoordinator(storefront, cartStore, CLOCK),
                    sessions,
                    Provider {
                        identities
                    }
                )
            )
        fun restartedCartRepository(): DefaultCartRepository = DefaultCartRepository(
            CoordinatedCartOperations(
                CartCoordinator(storefront, cartStore, CLOCK),
                CustomerAccountSessionCoordinator(
                    configuration(),
                    tokenClient,
                    sessionStore,
                    clock = CLOCK
                ),
                Provider { identities }
            )
        )
        val controller = DefaultAccountController(
            CustomerAccountAuthorizationCoordinator(
                configuration(),
                CustomerAccountDiscoveryClient {
                    CustomerAccountDiscoveryParser().parse(
                        configuration().issuer,
                        OPEN_ID,
                        """{"graphql_api":"https://shop.example/customer/api/2026-07/graphql"}"""
                    )
                }
            ),
            sessions,
            identities,
            carts
        )
    }

    private class MemorySessionStore(var session: CustomerSession?) : CustomerSessionStore {
        var clearFails = false
        var clearApplies = false
        override suspend fun read(): CustomerSession? = session
        override suspend fun write(session: CustomerSession) {
            this.session = session
        }
        override suspend fun clear() {
            if (!clearFails || clearApplies) session = null
            check(!clearFails) { "synthetic failed clear receipt" }
        }
    }

    private class MemoryCartStore(var cart: PersistedCart?) : CartSessionStore {
        var writeFails = false
        var readFails = false
        override suspend fun read(): PersistedCart? {
            check(!readFails)
            return cart
        }
        override suspend fun write(cart: PersistedCart) {
            check(!writeFails)
            this.cart = cart
        }
        override suspend fun clear() {
            cart = null
        }
    }

    private class OwnedCartGateway : StorefrontGateway {
        var buyerUpdates = 0
        var addCalls = 0
        var loadCalls = 0
        var rebindFails = false
        var rebindAppliesBeforeFailure = false
        var returnWrongOwner = false
        var returnWrongCart = false
        var beforeLoad: (suspend () -> Unit)? = null
        var beforeRebind: (suspend () -> Unit)? = null
        var owner: String? = "synthetic-A"
        var lastBuyerToken: String? = null
        private var quantity = 1
        override suspend fun loadCart(cartId: SensitiveCartId): StorefrontResult<CartReference> {
            loadCalls++
            beforeLoad?.invoke()
            return StorefrontResult.Success(cart())
        }
        override suspend fun updateBuyerIdentity(
            cartId: SensitiveCartId,
            buyerAccessToken: SensitiveBuyerAccessToken?
        ): StorefrontResult<CartReference> {
            buyerUpdates++
            beforeRebind?.invoke()
            lastBuyerToken = buyerAccessToken?.use { it }
            if (rebindAppliesBeforeFailure) owner = lastBuyerToken?.let(::customerForToken)
            if (rebindFails) return StorefrontResult.Failure(StorefrontFailure.Transport(true))
            owner = lastBuyerToken?.let(::customerForToken)
            val returned = when {
                returnWrongOwner -> cart().copy(customerId = SensitiveCustomerId.from("synthetic-B"))
                returnWrongCart -> cart().copy(id = construct(SensitiveCartId::class.java, "synthetic-other-cart"))
                else -> cart()
            }
            return StorefrontResult.Success(returned)
        }
        override suspend fun addCartLines(
            cartId: SensitiveCartId,
            lines: List<CartLineInput>
        ): StorefrontResult<CartReference> {
            addCalls++
            quantity += lines.sumOf { it.quantity }
            return StorefrontResult.Success(cart())
        }
        private fun cart(): CartReference {
            val money = StorefrontMoney(BigDecimal.TEN, "TRY")
            return CartReference(
                CART_ID, CHECKOUT_URL, quantity,
                listOf(
                    CartLineSummary(
                        LINE_ID,
                        "gid://shopify/ProductVariant/1",
                        quantity,
                        productId = "gid://shopify/Product/1",
                        productTitle = "Synthetic product",
                        unitPrice = money,
                        totalPrice = money
                    )
                ),
                false, emptySet(), money, money, customerId = owner?.let(SensitiveCustomerId::from)
            )
        }
        override suspend fun loadShopSummary(): StorefrontResult<ShopSummary> = error("unused")
        override suspend fun loadCatalogPage(after: Cursor?): StorefrontResult<CatalogPage> = error("unused")
        override suspend fun createCart(
            lines: List<CartLineInput>,
            buyerAccessToken: SensitiveBuyerAccessToken?
        ): StorefrontResult<CartReference> = error("unused")
        override suspend fun updateCartLines(
            cartId: SensitiveCartId,
            lines: List<CartLineUpdate>
        ): StorefrontResult<CartReference> = error("unused")
        override suspend fun removeCartLines(
            cartId: SensitiveCartId,
            lineIds: List<SensitiveCartLineId>
        ): StorefrontResult<CartReference> = error("unused")
    }

    private companion object {
        val NOW: Instant = Instant.parse("2026-10-06T12:00:00Z")
        val CLOCK: Clock = Clock.fixed(NOW, ZoneOffset.UTC)
        val CART_ID = construct(SensitiveCartId::class.java, "gid://shopify/Cart/synthetic-A?key=synthetic-only")
        val CHECKOUT_URL = construct(SensitiveCheckoutUrl::class.java, URI("https://shop.example/cart/c/synthetic-A"))
        val LINE_ID = construct(SensitiveCartLineId::class.java, "gid://shopify/CartLine/synthetic-A")
        fun session(owner: String) = CustomerSession(
            SensitiveToken.from(owner),
            SensitiveToken.from("refresh-$owner"),
            SensitiveToken.from("id-$owner"),
            NOW.plusSeconds(3600)
        )
        fun configuration() = CustomerAccountConfiguration(
            "synthetic-public-client", "https://shopify.com/authentication/123456",
            "https://shop.example/authentication/oauth/authorize", "https://shop.example/authentication/oauth/token",
            "https://shop.example/authentication/logout", "https://shop.example/customer/api/2026-07/graphql",
            "shop.123456.synthetic://oauth/callback", "Test-Android", REQUIRED_CUSTOMER_ACCOUNT_SCOPES
        )
        fun idToken(nonce: String): String {
            fun encode(value: String) = Base64.getUrlEncoder().withoutPadding().encodeToString(value.toByteArray())
            return encode("""{"alg":"RS256"}""") + "." + encode(
                """{
                    "iss":"https://shopify.com/authentication/123456","sub":"synthetic-B",
                    "aud":"synthetic-public-client","nonce":"$nonce",
                    "iat":${NOW.epochSecond},"exp":${NOW.plusSeconds(3660).epochSecond}
                }"""
            ) + ".synthetic-signature"
        }
        fun customerForToken(token: String): String = token.removeSuffix("-renewed")
        fun <T> construct(type: Class<T>, value: Any): T = type.getDeclaredConstructor(value.javaClass).run {
            isAccessible = true
            newInstance(value)
        }
        const val OPEN_ID = """{
            "issuer":"https://shopify.com/authentication/123456",
            "authorization_endpoint":"https://shop.example/authentication/oauth/authorize",
            "token_endpoint":"https://shop.example/authentication/oauth/token",
            "end_session_endpoint":"https://shop.example/authentication/logout",
            "jwks_uri":"https://shop.example/authentication/.well-known/jwks.json",
            "grant_types_supported":["authorization_code","refresh_token"],
            "code_challenge_methods_supported":["S256"],"id_token_signing_alg_values_supported":["RS256"]
        }"""
    }
}
