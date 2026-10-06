package com.gurbakir.mobile.checkout

import com.gurbakir.account.CustomerAccountGateway
import com.gurbakir.account.CustomerAccountResult
import com.gurbakir.account.CustomerIdentity
import com.gurbakir.account.oauth.CustomerAccountAuthorizationGrant
import com.gurbakir.account.oauth.CustomerAccountTokenClient
import com.gurbakir.account.oauth.CustomerTokenResult
import com.gurbakir.account.session.CustomerAccountSessionCoordinator
import com.gurbakir.account.session.CustomerSession
import com.gurbakir.account.session.CustomerSessionStore
import com.gurbakir.account.session.SensitiveToken
import com.gurbakir.checkout.CheckoutUrlPolicy
import com.gurbakir.checkout.ShopifyCheckoutAdapter
import com.gurbakir.foundation.config.CustomerAccountConfiguration
import com.gurbakir.foundation.config.REQUIRED_CUSTOMER_ACCOUNT_SCOPES
import com.gurbakir.mobile.cart.CoordinatedCartOperations
import com.gurbakir.mobile.cart.DefaultCartRepository
import com.gurbakir.mobile.cart.LIFECYCLE_CUSTOMER
import com.gurbakir.mobile.cart.lifecycleActive
import com.gurbakir.storefront.CartCoordinator
import com.gurbakir.storefront.CartLineInput
import com.gurbakir.storefront.CartLineUpdate
import com.gurbakir.storefront.CartOwnership
import com.gurbakir.storefront.CartReference
import com.gurbakir.storefront.CartSessionStore
import com.gurbakir.storefront.PersistedCart
import com.gurbakir.storefront.SensitiveBuyerAccessToken
import com.gurbakir.storefront.SensitiveCartId
import com.gurbakir.storefront.SensitiveCartLineId
import com.gurbakir.storefront.SensitiveCustomerId
import com.gurbakir.storefront.StorefrontGateway
import com.gurbakir.storefront.StorefrontResult
import com.gurbakir.storefront.UnconfiguredStorefrontGateway
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import javax.inject.Provider
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Actual lease, cart planning, adapter stream and exact completer; controlled stores/provider/SDK callbacks only. */
internal class CheckoutProtocolFixture {
    private val clock = Clock.fixed(Instant.parse("2026-10-06T12:00:00Z"), ZoneOffset.UTC)
    val cartA = protocolCartId("A")
    val cartB = protocolCartId("B")
    val sessionStore = ProtocolCustomerSessionStore(clock)
    val cartStore = ProtocolCartSessionStore(persisted(cartA))
    val gateway = ProtocolStorefrontGateway(cartA, cartB)
    val sdk = CheckoutSdkTestDriver()
    val adapter = ShopifyCheckoutAdapter(sdk, CheckoutUrlPolicy(setOf("gurbakir.com")))
    private val sessionCoordinator = CustomerAccountSessionCoordinator(
        protocolAccountConfiguration(),
        object : CustomerAccountTokenClient {
            override suspend fun exchange(grant: CustomerAccountAuthorizationGrant): CustomerTokenResult =
                error("no authorization exchange in checkout protocol fixture")

            override suspend fun refresh(refreshToken: SensitiveToken): CustomerTokenResult =
                error("healthy unexpired session must not refresh")
        },
        sessionStore,
        clock = clock
    )
    val cartCoordinator = CartCoordinator(gateway, cartStore, clock)
    val repository = DefaultCartRepository(
        CoordinatedCartOperations(
            cartCoordinator,
            sessionCoordinator,
            Provider {
                object : CustomerAccountGateway {
                    override suspend fun loadIdentity(): CustomerAccountResult<CustomerIdentity> =
                        error("use the supplied lease; do not resolve a second session")

                    override suspend fun loadIdentity(
                        session: CustomerSession
                    ): CustomerAccountResult<CustomerIdentity> {
                        check(session == sessionStore.session)
                        return CustomerAccountResult.Success(CustomerIdentity(LIFECYCLE_CUSTOMER, "Synthetic"))
                    }
                }
            }
        )
    )
    val controller = CheckoutController(repository, CoordinatedCheckoutCartCompleter(cartCoordinator), adapter)

    fun launch(scope: CoroutineScope): Job = scope.launch {
        controller.startWithPresentation { url -> sdk.capture(adapter, url) }
    }

    fun installReplacement() {
        cartStore.cart = persisted(cartB)
    }

    private fun persisted(id: SensitiveCartId) = PersistedCart(
        id,
        clock.instant().plusSeconds(3600),
        CartOwnership.CUSTOMER_ASSOCIATED,
        SensitiveCustomerId.from(LIFECYCLE_CUSTOMER)
    )
}

internal class ProtocolCustomerSessionStore(clock: Clock) : CustomerSessionStore {
    var session: CustomerSession? = CustomerSession(
        SensitiveToken.from("synthetic-checkout-access"),
        SensitiveToken.from("synthetic-checkout-refresh"),
        SensitiveToken.from("synthetic-checkout-id"),
        clock.instant().plusSeconds(3600)
    )
    var readGate: CompletableDeferred<Unit>? = null
    val readEntered = CompletableDeferred<Unit>()
    var readFailure: Exception? = null
    var writeFailure: Exception? = null
    var clearFailure: Exception? = null
    var clearAppliesBeforeFailure = false
    var reads = 0
    var writes = 0
    var clears = 0

    override suspend fun read(): CustomerSession? {
        reads++
        readGate?.let {
            readEntered.complete(Unit)
            it.await()
        }
        readFailure?.let { throw it }
        return session
    }

    override suspend fun write(session: CustomerSession) {
        writes++
        writeFailure?.let { throw it }
        this.session = session
    }

    override suspend fun clear() {
        clears++
        if (clearAppliesBeforeFailure) session = null
        clearFailure?.let { throw it }
        session = null
    }
}

internal class ProtocolCartSessionStore(var cart: PersistedCart?) : CartSessionStore {
    var clearGate: CompletableDeferred<Unit>? = null
    val clearEntered = CompletableDeferred<Unit>()
    var readFailure: Exception? = null
    var clearFailure: Exception? = null
    var clearAppliesBeforeFailure = false
    var reads = 0
    var writes = 0
    var clears = 0

    override suspend fun read(): PersistedCart? {
        reads++
        readFailure?.let { throw it }
        return cart
    }

    override suspend fun write(cart: PersistedCart) {
        writes++
        this.cart = cart
    }

    override suspend fun clear() {
        clears++
        clearGate?.let {
            clearEntered.complete(Unit)
            it.await()
        }
        if (clearAppliesBeforeFailure) cart = null
        clearFailure?.let { throw it }
        cart = null
    }
}

internal class ProtocolStorefrontGateway(cartA: SensitiveCartId, cartB: SensitiveCartId) :
    StorefrontGateway by UnconfiguredStorefrontGateway() {
    private val snapshots = listOf(cartA, cartB).associateWith {
        lifecycleActive(2, CartOwnership.CUSTOMER_ASSOCIATED).cart.copy(id = it)
    }
    val loads = mutableListOf<SensitiveCartId>()
    val buyerRebinds = mutableListOf<SensitiveCartId>()
    var merchandiseMutations = 0

    override suspend fun loadCart(cartId: SensitiveCartId): StorefrontResult<CartReference> {
        loads += cartId
        return StorefrontResult.Success(snapshots.getValue(cartId))
    }

    override suspend fun updateBuyerIdentity(
        cartId: SensitiveCartId,
        buyerAccessToken: SensitiveBuyerAccessToken?
    ): StorefrontResult<CartReference> {
        check(buyerAccessToken != null) { "fixture only permits checked same-owner token rebind" }
        buyerRebinds += cartId
        return StorefrontResult.Success(snapshots.getValue(cartId))
    }

    override suspend fun createCart(
        lines: List<CartLineInput>,
        buyerAccessToken: SensitiveBuyerAccessToken?
    ): StorefrontResult<CartReference> = unexpectedMerchandiseMutation()

    override suspend fun addCartLines(
        cartId: SensitiveCartId,
        lines: List<CartLineInput>
    ): StorefrontResult<CartReference> = unexpectedMerchandiseMutation()

    override suspend fun updateCartLines(
        cartId: SensitiveCartId,
        lines: List<CartLineUpdate>
    ): StorefrontResult<CartReference> = unexpectedMerchandiseMutation()

    override suspend fun removeCartLines(
        cartId: SensitiveCartId,
        lineIds: List<SensitiveCartLineId>
    ): StorefrontResult<CartReference> = unexpectedMerchandiseMutation()

    private fun unexpectedMerchandiseMutation(): Nothing {
        merchandiseMutations++
        error("checkout callback fixture must never create/add/update/remove merchandise")
    }
}

private fun protocolCartId(suffix: String): SensitiveCartId =
    SensitiveCartId::class.java.getDeclaredConstructor(String::class.java).run {
        isAccessible = true
        newInstance("gid://shopify/Cart/checkout-$suffix?key=synthetic-only")
    }

private fun protocolAccountConfiguration() = CustomerAccountConfiguration(
    "synthetic-public-client", "https://shopify.com/authentication/123456",
    "https://shop.example/authentication/oauth/authorize",
    "https://shop.example/authentication/oauth/token",
    "https://shop.example/authentication/logout", "https://shop.example/customer/api/2026-07/graphql",
    "shop.123456.synthetic://oauth/callback", "Test-Android", REQUIRED_CUSTOMER_ACCOUNT_SCOPES
)
