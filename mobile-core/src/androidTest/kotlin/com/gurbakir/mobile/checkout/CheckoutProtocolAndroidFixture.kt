package com.gurbakir.mobile.checkout

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
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
import com.gurbakir.checkout.CheckoutEvent
import com.gurbakir.checkout.CheckoutKitClient
import com.gurbakir.checkout.CheckoutPresentationOwner
import com.gurbakir.checkout.CheckoutUrlPolicy
import com.gurbakir.checkout.ShopifyCheckoutAdapter
import com.gurbakir.foundation.config.CustomerAccountConfiguration
import com.gurbakir.foundation.config.REQUIRED_CUSTOMER_ACCOUNT_SCOPES
import com.gurbakir.mobile.cart.CartViewModel
import com.gurbakir.mobile.cart.CoordinatedCartOperations
import com.gurbakir.mobile.cart.DefaultCartRepository
import com.gurbakir.storefront.CartCompletionResolution
import com.gurbakir.storefront.CartCoordinator
import com.gurbakir.storefront.CartLineInput
import com.gurbakir.storefront.CartLineSummary
import com.gurbakir.storefront.CartLineUpdate
import com.gurbakir.storefront.CartOwnership
import com.gurbakir.storefront.CartReference
import com.gurbakir.storefront.CartSessionStore
import com.gurbakir.storefront.PersistedCart
import com.gurbakir.storefront.SensitiveBuyerAccessToken
import com.gurbakir.storefront.SensitiveCartId
import com.gurbakir.storefront.SensitiveCartLineId
import com.gurbakir.storefront.SensitiveCheckoutUrl
import com.gurbakir.storefront.SensitiveCustomerId
import com.gurbakir.storefront.StorefrontGateway
import com.gurbakir.storefront.StorefrontMoney
import com.gurbakir.storefront.StorefrontResult
import com.gurbakir.storefront.UnconfiguredStorefrontGateway
import java.math.BigDecimal
import java.net.URI
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import javax.inject.Provider
import kotlinx.coroutines.CompletableDeferred

/** Plain owner uses preseeded real ViewModels; CartDestination keeps its actual hiltViewModel/action wiring. */
internal class CheckoutProtocolAndroidFixture {
    private val clock = Clock.fixed(Instant.parse("2026-10-06T12:00:00Z"), ZoneOffset.UTC)
    val cartA = androidProtocolCartId("A")
    val cartB = androidProtocolCartId("B")
    val sessionStore = AndroidProtocolCustomerStore(clock)
    val cartStore = AndroidProtocolCartStore(persisted(cartA))
    val gateway = AndroidProtocolGateway(cartA, cartB)
    val sdk = AndroidProtocolSdk()
    private val sessions = CustomerAccountSessionCoordinator(
        androidProtocolConfiguration(),
        object : CustomerAccountTokenClient {
            override suspend fun exchange(grant: CustomerAccountAuthorizationGrant): CustomerTokenResult =
                error("no authorization exchange")

            override suspend fun refresh(refreshToken: SensitiveToken): CustomerTokenResult = error("no renewal")
        },
        sessionStore,
        clock = clock
    )
    private val coordinator = CartCoordinator(gateway, cartStore, clock)
    val repository = DefaultCartRepository(
        CoordinatedCartOperations(
            coordinator,
            sessions,
            Provider {
                object : CustomerAccountGateway {
                    override suspend fun loadIdentity(): CustomerAccountResult<CustomerIdentity> =
                        error("explicit leased session required")

                    override suspend fun loadIdentity(
                        session: CustomerSession
                    ): CustomerAccountResult<CustomerIdentity> {
                        check(session == sessionStore.session)
                        return CustomerAccountResult.Success(CustomerIdentity(ANDROID_PROTOCOL_CUSTOMER, "Synthetic"))
                    }
                }
            }
        )
    )
    val completionAttempts = mutableListOf<SensitiveCartId>()
    private val actualCompleter = CoordinatedCheckoutCartCompleter(coordinator)
    val controller = CheckoutController(
        repository,
        object : CheckoutCartCompleter {
            override suspend fun complete(cartId: SensitiveCartId): CartCompletionResolution {
                completionAttempts += cartId
                return actualCompleter.complete(cartId)
            }
        },
        ShopifyCheckoutAdapter(sdk, CheckoutUrlPolicy(setOf("gurbakir.com")))
    )
    val store = ViewModelStore()
    val owner = object : ViewModelStoreOwner {
        override val viewModelStore: ViewModelStore = store
    }
    private val factory = AndroidProtocolViewModelFactory(repository, controller)
    val cart = ViewModelProvider(store, factory)[CartViewModel::class.java]
    val checkout = ViewModelProvider(store, factory)[CheckoutViewModel::class.java]

    fun installReplacement() {
        cartStore.cart = persisted(cartB)
    }

    fun close() {
        store.clear()
    }

    private fun persisted(id: SensitiveCartId) = PersistedCart(
        id,
        clock.instant().plusSeconds(3600),
        CartOwnership.CUSTOMER_ASSOCIATED,
        SensitiveCustomerId.from(ANDROID_PROTOCOL_CUSTOMER)
    )
}

private class AndroidProtocolViewModelFactory(
    private val repository: DefaultCartRepository,
    private val controller: CheckoutController
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T = requireNotNull(
        modelClass.cast(
            when (modelClass) {
                CartViewModel::class.java -> CartViewModel(repository)
                CheckoutViewModel::class.java -> CheckoutViewModel(controller)
                else -> error("unexpected ViewModel factory request")
            }
        )
    )
}

internal class AndroidProtocolCustomerStore(clock: Clock) : CustomerSessionStore {
    val session = CustomerSession(
        SensitiveToken.from("synthetic-checkout-access"),
        SensitiveToken.from("synthetic-checkout-refresh"),
        SensitiveToken.from("synthetic-checkout-id"),
        clock.instant().plusSeconds(3600)
    )
    var nextReadGate: CompletableDeferred<Unit>? = null
    var readFailure: Exception? = null
    val readEntered = CompletableDeferred<Unit>()
    var reads = 0

    override suspend fun read(): CustomerSession {
        reads++
        val gate = nextReadGate
        nextReadGate = null
        gate?.let {
            readEntered.complete(Unit)
            it.await()
        }
        readFailure?.let { throw it }
        return session
    }

    override suspend fun write(session: CustomerSession) = error("no session replacement")

    override suspend fun clear() = error("no logout")
}

internal class AndroidProtocolCartStore(var cart: PersistedCart?) : CartSessionStore {
    var clearFailure: Exception? = null
    var clears = 0

    override suspend fun read(): PersistedCart? = cart

    override suspend fun write(cart: PersistedCart) {
        this.cart = cart
    }

    override suspend fun clear() {
        clears++
        clearFailure?.let { throw it }
        cart = null
    }
}

internal class AndroidProtocolSdk : CheckoutKitClient {
    private var sink: ((CheckoutEvent) -> Unit)? = null
    var presentations = 0
    var disposals = 0

    override fun preload(activity: Activity, checkoutUrl: URI, eventSink: (CheckoutEvent) -> Unit): Boolean = true

    override fun present(
        activity: Activity,
        checkoutUrl: URI,
        eventSink: (CheckoutEvent) -> Unit
    ): CheckoutPresentationOwner {
        presentations++
        sink = eventSink
        return CheckoutPresentationOwner { disposals++ }
    }

    override fun invalidate() = Unit

    fun emit(event: CheckoutEvent) {
        checkNotNull(sink)(event)
    }
}

internal class AndroidProtocolGateway(cartA: SensitiveCartId, cartB: SensitiveCartId) :
    StorefrontGateway by UnconfiguredStorefrontGateway() {
    private val snapshots = listOf(cartA, cartB).associateWith(::androidProtocolCart)
    val buyerRebinds = mutableListOf<SensitiveCartId>()
    var merchandiseMutations = 0

    override suspend fun loadCart(cartId: SensitiveCartId): StorefrontResult<CartReference> =
        StorefrontResult.Success(snapshots.getValue(cartId))

    override suspend fun updateBuyerIdentity(
        cartId: SensitiveCartId,
        buyerAccessToken: SensitiveBuyerAccessToken?
    ): StorefrontResult<CartReference> {
        check(buyerAccessToken != null)
        buyerRebinds += cartId
        return StorefrontResult.Success(snapshots.getValue(cartId))
    }

    override suspend fun createCart(
        lines: List<CartLineInput>,
        buyerAccessToken: SensitiveBuyerAccessToken?
    ): StorefrontResult<CartReference> = unexpectedMutation()

    override suspend fun addCartLines(
        cartId: SensitiveCartId,
        lines: List<CartLineInput>
    ): StorefrontResult<CartReference> = unexpectedMutation()

    override suspend fun updateCartLines(
        cartId: SensitiveCartId,
        lines: List<CartLineUpdate>
    ): StorefrontResult<CartReference> = unexpectedMutation()

    override suspend fun removeCartLines(
        cartId: SensitiveCartId,
        lineIds: List<SensitiveCartLineId>
    ): StorefrontResult<CartReference> = unexpectedMutation()

    private fun unexpectedMutation(): Nothing {
        merchandiseMutations++
        error("no merchandise mutation permitted by checkout fixture")
    }
}

private fun androidProtocolCart(id: SensitiveCartId): CartReference {
    val money = StorefrontMoney(BigDecimal("20.00"), "TRY")
    return CartReference(
        id = id,
        checkoutUrl = SensitiveCheckoutUrl::class.java.getDeclaredConstructor(URI::class.java).run {
            isAccessible = true
            newInstance(URI("https://gurbakir.com/cart/c/synthetic-checkout"))
        },
        totalQuantity = 2,
        lines = listOf(
            CartLineSummary(
                id = SensitiveCartLineId::class.java.getDeclaredConstructor(String::class.java).run {
                    isAccessible = true
                    newInstance("gid://shopify/CartLine/synthetic-checkout")
                },
                merchandiseId = "gid://shopify/ProductVariant/synthetic-checkout",
                quantity = 2,
                productId = "gid://shopify/Product/synthetic-checkout",
                productTitle = "Synthetic checkout product",
                unitPrice = StorefrontMoney(BigDecimal.TEN, "TRY"),
                totalPrice = money
            )
        ),
        hasMoreLines = false,
        warningCodes = emptySet(),
        subtotal = money,
        total = money,
        customerId = SensitiveCustomerId.from(ANDROID_PROTOCOL_CUSTOMER)
    )
}

private fun androidProtocolCartId(suffix: String): SensitiveCartId =
    SensitiveCartId::class.java.getDeclaredConstructor(String::class.java).run {
        isAccessible = true
        newInstance("gid://shopify/Cart/android-checkout-$suffix?key=synthetic-only")
    }

private fun androidProtocolConfiguration() = CustomerAccountConfiguration(
    "synthetic-public-client", "https://shopify.com/authentication/123456",
    "https://shop.example/authentication/oauth/authorize", "https://shop.example/authentication/oauth/token",
    "https://shop.example/authentication/logout", "https://shop.example/customer/api/2026-07/graphql",
    "shop.123456.synthetic://oauth/callback", "Test-Android", REQUIRED_CUSTOMER_ACCOUNT_SCOPES
)

private const val ANDROID_PROTOCOL_CUSTOMER = "gid://shopify/Customer/synthetic-checkout"
