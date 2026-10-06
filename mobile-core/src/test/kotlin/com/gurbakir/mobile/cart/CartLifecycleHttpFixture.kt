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
import com.gurbakir.storefront.SensitiveCustomerId
import com.gurbakir.storefront.StorefrontMediaPolicy
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.TimeUnit
import javax.inject.Provider
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.jupiter.api.Assertions.assertNotNull

/** Real pinned transport/parser, coordinator, protected-store contract and customer lease; only HTTPS peer is local. */
internal class CartLifecycleHttpFixture(
    private val authenticated: Boolean = false,
    initiallyAnonymous: Boolean = false
) : AutoCloseable {
    val server = MockWebServer().apply { start() }
    private val client = outcomeApolloClient(server.url("graphql").toString())
    private val clock = Clock.fixed(Instant.parse("2026-10-06T12:00:00Z"), ZoneOffset.UTC)
    val store = LifecycleProtectedStore(
        PersistedCart(
            lifecycleCartId(),
            clock.instant().plusSeconds(3600),
            if (authenticated && !initiallyAnonymous) CartOwnership.CUSTOMER_ASSOCIATED else CartOwnership.ANONYMOUS,
            if (authenticated && !initiallyAnonymous) SensitiveCustomerId.from(LIFECYCLE_CUSTOMER) else null
        )
    )
    var identityGate: CompletableDeferred<Unit>? = null
    val identityWaiting = CompletableDeferred<Unit>()
    var sessionAvailable = authenticated && !initiallyAnonymous
    var signInOnSessionRead: Int? = null
    private var sessionReads = 0
    val repository = DefaultCartRepository(
        CoordinatedCartOperations(
            CartCoordinator(
                ApolloStorefrontGateway(client, StorefrontMediaPolicy("gurbakir.com"), requestTimeoutMillis = 10000),
                store,
                clock
            ),
            if (authenticated) {
                authenticatedOutcomeSessions(clock) {
                    sessionReads++
                    if (sessionReads == signInOnSessionRead) sessionAvailable = true
                    sessionAvailable
                }
            } else {
                CustomerAccountSessionCoordinator(
                    capability = CustomerAccountCapability.Disabled,
                    tokenClient = { error("disabled token client requested") },
                    sessionStore = { error("disabled customer store requested") }
                )
            },
            Provider {
                check(authenticated) { "disabled identity gateway requested" }
                object : CustomerAccountGateway {
                    override suspend fun loadIdentity(): CustomerAccountResult<CustomerIdentity> =
                        error("leased session required")

                    override suspend fun loadIdentity(
                        session: CustomerSession
                    ): CustomerAccountResult<CustomerIdentity> {
                        identityGate?.let {
                            identityWaiting.complete(Unit)
                            it.await()
                        }
                        return CustomerAccountResult.Success(CustomerIdentity(LIFECYCLE_CUSTOMER, "Synthetic"))
                    }
                }
            }
        )
    )

    fun replyRead(quantity: Int, owned: Boolean = sessionAvailable) {
        server.enqueue(MockResponse().setBody("""{"data":{"cart":${snapshot(quantity, owned)}}}"""))
    }

    fun holdMutation() {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
    }

    fun replyBuyerUpdate(quantity: Int) {
        server.enqueue(
            MockResponse().setBody(
                """{"data":{"cartBuyerIdentityUpdate":{
                    "cart":${snapshot(quantity, owned = true)},"userErrors":[],"warnings":[]}}}"""
            )
        )
    }

    fun replyAmbiguousAdd() {
        server.enqueue(
            MockResponse().setBody(
                """{"errors":[{"message":"Synthetic uncertain resolver response",
                    "extensions":{"code":"SYNTHETIC_UNCERTAIN"}}]}"""
            )
        )
    }

    suspend fun takeRequest(): String = withContext(Dispatchers.IO) {
        val request = server.takeRequest(5, TimeUnit.SECONDS)
        assertNotNull(request, "complete local HTTP request must arrive")
        requireNotNull(request).body.readUtf8()
    }

    override fun close() {
        client.close()
        server.shutdown()
    }

    private fun snapshot(quantity: Int, owned: Boolean): String {
        val customer = if (owned) """{"id":"$LIFECYCLE_CUSTOMER"}""" else "null"
        return """{"__typename":"Cart","id":"$LIFECYCLE_CART",
            "checkoutUrl":"https://gurbakir.com/cart/c/synthetic-lifecycle","totalQuantity":$quantity,
            "buyerIdentity":{"customer":$customer},
            "cost":{"subtotalAmount":{"amount":"${quantity * 10}.00","currencyCode":"TRY"},
                "totalAmount":{"amount":"${quantity * 10}.00","currencyCode":"TRY"}},
            "lines":{"nodes":[{"__typename":"CartLine","id":"$LIFECYCLE_LINE","quantity":$quantity,
                "instructions":{"canRemove":true,"canUpdateQuantity":true},
                "cost":{"amountPerQuantity":{"amount":"10.00","currencyCode":"TRY"},
                    "totalAmount":{"amount":"${quantity * 10}.00","currencyCode":"TRY"}},
                "merchandise":{"__typename":"ProductVariant","id":"$LIFECYCLE_VARIANT",
                    "title":"Synthetic variant","availableForSale":true,"currentlyNotInStock":false,
                    "quantityRule":{"minimum":1,"maximum":null,"increment":1},"image":null,
                    "product":{"id":"gid://shopify/Product/1","title":"Synthetic product"}}}],
                "pageInfo":{"endCursor":null,"hasNextPage":false}}}"""
    }
}

internal class LifecycleProtectedStore(var cart: PersistedCart?) : CartSessionStore {
    var writes = 0
    var clears = 0
    override suspend fun read(): PersistedCart? = cart
    override suspend fun write(cart: PersistedCart) {
        writes++
        this.cart = cart
    }
    override suspend fun clear() {
        clears++
        cart = null
    }
}
