package com.gurbakir.storefront

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.net.URI
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CartCoordinatorTest {
    @Test
    fun `create persists only the redacted cart id with a bounded lifetime`() = runTest {
        val store = RecordingCartStore()
        val gateway = FakeStorefrontGateway(createResult = StorefrontResult.Success(cart()))
        val coordinator = CartCoordinator(gateway, store, clock = fixedClock())

        val result = coordinator.create(listOf(CartLineInput(VARIANT_ID, 1)))

        assertInstanceOf(CartSessionResolution.Active::class.java, result)
        assertEquals(CLOCK_INSTANT.plus(Duration.ofDays(30)), store.cart?.expiresAt)
        assertEquals("<redacted-cart-id>", store.cart?.id.toString())
        assertFalse(store.cart.toString().contains(CART_SECRET))
    }

    @Test
    fun `expired local cart is cleared without contacting Shopify`() = runTest {
        val store =
            RecordingCartStore(
                PersistedCart(
                    SensitiveCartId.from("gid://shopify/Cart/test?key=$CART_SECRET"),
                    CLOCK_INSTANT.minusSeconds(1),
                    CartOwnership.ANONYMOUS
                )
            )
        val gateway = FakeStorefrontGateway(loadResult = StorefrontResult.Success(cart()))

        val result = CartCoordinator(gateway, store, fixedClock()).restore()

        assertEquals(CartSessionResolution.Expired, result)
        assertEquals(1, store.clearCount)
        assertEquals(0, gateway.loadCount)
    }

    @Test
    fun `transient restore failure retains encrypted cart while definitive invalid cart clears it`() = runTest {
        val persisted =
            PersistedCart(
                SensitiveCartId.from("gid://shopify/Cart/test?key=$CART_SECRET"),
                CLOCK_INSTANT.plusSeconds(3600),
                CartOwnership.ANONYMOUS
            )
        val transientStore = RecordingCartStore(persisted)
        val transient =
            CartCoordinator(
                FakeStorefrontGateway(
                    loadResult = StorefrontResult.Failure(StorefrontFailure.Transport(true))
                ),
                transientStore,
                fixedClock()
            ).restore()

        assertEquals(
            CartSessionResolution.Failed(StorefrontFailure.Transport(true), persistedCartRetained = true),
            transient
        )
        assertEquals(0, transientStore.clearCount)

        val invalidStore = RecordingCartStore(persisted)
        val invalid =
            CartCoordinator(
                FakeStorefrontGateway(
                    loadResult =
                        StorefrontResult.Failure(
                            StorefrontFailure.InvalidCart(InvalidCartReason.NOT_FOUND)
                        )
                ),
                invalidStore,
                fixedClock()
            ).restore()

        assertEquals(CartSessionResolution.Expired, invalid)
        assertEquals(1, invalidStore.clearCount)
    }

    @Test
    fun `encrypted payload codec rejects trailing or malformed data and never renders cart secret`() {
        val persisted =
            PersistedCart(
                SensitiveCartId.from("gid://shopify/Cart/test?key=$CART_SECRET"),
                CLOCK_INSTANT.plusSeconds(3600),
                CartOwnership.ANONYMOUS
            )
        val encoded = CartSessionPayloadCodec.encode(persisted)
        val decoded = CartSessionPayloadCodec.decode(encoded)

        assertEquals(persisted, decoded)
        assertFalse(decoded.toString().contains(CART_SECRET))
        assertThrows(IllegalArgumentException::class.java) {
            CartSessionPayloadCodec.decode(encoded + byteArrayOf(1))
        }
    }

    @Test
    fun `cart v2 plaintext wire vector remains byte exact`() {
        val encoded =
            CartSessionPayloadCodec.encode(
                PersistedCart(
                    id = SensitiveCartId.from("cart-vector"),
                    expiresAt = Instant.ofEpochSecond(1_700_000_000L, 123_456_789L),
                    ownership = CartOwnership.CUSTOMER_ASSOCIATED
                )
            )

        assertEquals(
            "000000020000000b636172742d766563746f72000000006553f100075bcd1500000002",
            encoded.toHexString()
        )
    }

    @Test
    fun `legacy payload migrates to quarantined instead of silently becoming anonymous`() {
        val cartId = "gid://shopify/Cart/test?key=$CART_SECRET"
        val bytes = cartId.toByteArray(Charsets.UTF_8)
        val output = ByteArrayOutputStream()
        DataOutputStream(output).use { stream ->
            stream.writeInt(1)
            stream.writeInt(bytes.size)
            stream.write(bytes)
            stream.writeLong(CLOCK_INSTANT.plusSeconds(3600).epochSecond)
            stream.writeInt(0)
        }

        val decoded = CartSessionPayloadCodec.decode(output.toByteArray())

        assertEquals(CartOwnership.QUARANTINED, decoded.ownership)
        assertFalse(decoded.toString().contains(CART_SECRET))
    }

    @Test
    fun `remote association mismatch quarantines anonymous cart and blocks later reads`() = runTest {
        val persisted = persisted(CartOwnership.ANONYMOUS)
        val store = RecordingCartStore(persisted)
        val gateway = FakeStorefrontGateway(loadResult = StorefrontResult.Success(cart(customerAssociated = true)))
        val coordinator = CartCoordinator(gateway, store, fixedClock())

        assertEquals(
            CartSessionResolution.Restricted(CartOwnership.QUARANTINED),
            coordinator.restore()
        )
        assertEquals(CartOwnership.QUARANTINED, store.cart?.ownership)
        assertEquals(1, gateway.loadCount)

        assertEquals(
            CartSessionResolution.Restricted(CartOwnership.QUARANTINED),
            coordinator.restore()
        )
        assertEquals(1, gateway.loadCount)
    }

    @Test
    fun `verified attach and detach persist the four state ownership contract`() = runTest {
        val store = RecordingCartStore(persisted(CartOwnership.ANONYMOUS))
        val gateway =
            FakeStorefrontGateway(
                identityResult = StorefrontResult.Success(cart(customerAssociated = true)),
                clearIdentityResult = StorefrontResult.Success(cart(customerAssociated = false))
            )
        val coordinator = CartCoordinator(gateway, store, fixedClock())

        val attached = coordinator.authenticate(SensitiveBuyerAccessToken.from("synthetic-token"))
        assertEquals(CartOwnership.CUSTOMER_ASSOCIATED, (attached as CartSessionResolution.Active).ownership)
        assertEquals(CartOwnership.CUSTOMER_ASSOCIATED, store.cart?.ownership)

        val detached = coordinator.detach()
        assertEquals(CartOwnership.ANONYMOUS, (detached as CartSessionResolution.Active).ownership)
        assertEquals(CartOwnership.ANONYMOUS, store.cart?.ownership)
    }

    @Test
    fun `authenticated restore attaches once then verifies the associated cart`() = runTest {
        val store = RecordingCartStore(persisted(CartOwnership.ANONYMOUS))
        val gateway =
            FakeStorefrontGateway(
                loadResult = StorefrontResult.Success(cart(customerAssociated = true)),
                identityResult = StorefrontResult.Success(cart(customerAssociated = true))
            )
        val coordinator = CartCoordinator(gateway, store, fixedClock())
        val token = SensitiveBuyerAccessToken.from("synthetic-token")

        assertEquals(
            CartOwnership.CUSTOMER_ASSOCIATED,
            (coordinator.restoreAuthenticated(token) as CartSessionResolution.Active).ownership
        )
        assertEquals(1, gateway.identityCount)
        assertEquals(0, gateway.loadCount)

        assertEquals(
            CartOwnership.CUSTOMER_ASSOCIATED,
            (coordinator.restoreAuthenticated(token) as CartSessionResolution.Active).ownership
        )
        assertEquals(1, gateway.identityCount)
        assertEquals(1, gateway.loadCount)
    }

    @Test
    fun `authenticated mutation preserves customer ownership and quarantines a mismatched response`() = runTest {
        val successfulStore = RecordingCartStore(persisted(CartOwnership.CUSTOMER_ASSOCIATED))
        val successful =
            CartCoordinator(
                FakeStorefrontGateway(loadResult = StorefrontResult.Success(cart(customerAssociated = true))),
                successfulStore,
                fixedClock()
            ).addAuthenticated(listOf(CartLineInput(VARIANT_ID, 1)))

        assertEquals(CartOwnership.CUSTOMER_ASSOCIATED, (successful as CartSessionResolution.Active).ownership)
        assertEquals(CartOwnership.CUSTOMER_ASSOCIATED, successfulStore.cart?.ownership)

        val mismatchedStore = RecordingCartStore(persisted(CartOwnership.CUSTOMER_ASSOCIATED))
        val mismatched =
            CartCoordinator(
                FakeStorefrontGateway(loadResult = StorefrontResult.Success(cart(customerAssociated = false))),
                mismatchedStore,
                fixedClock()
            ).addAuthenticated(listOf(CartLineInput(VARIANT_ID, 1)))

        assertEquals(CartSessionResolution.Restricted(CartOwnership.QUARANTINED), mismatched)
        assertEquals(CartOwnership.QUARANTINED, mismatchedStore.cart?.ownership)
    }

    @Test
    fun `offline detach remains detach pending and never exposes customer cart`() = runTest {
        val store = RecordingCartStore(persisted(CartOwnership.CUSTOMER_ASSOCIATED))
        val gateway =
            FakeStorefrontGateway(
                clearIdentityResult = StorefrontResult.Failure(StorefrontFailure.Transport(retryable = true))
            )
        val coordinator = CartCoordinator(gateway, store, fixedClock())

        assertEquals(
            CartSessionResolution.Restricted(CartOwnership.DETACH_PENDING),
            coordinator.detach()
        )
        assertEquals(CartOwnership.DETACH_PENDING, store.cart?.ownership)
        assertEquals(
            CartSessionResolution.Restricted(CartOwnership.DETACH_PENDING),
            coordinator.restore()
        )
    }

    @Test
    fun `concurrent cart mutations are serialized`() = runTest {
        val store = RecordingCartStore(persisted(CartOwnership.ANONYMOUS))
        val gateway = SerialMutationGateway(cart())
        val coordinator = CartCoordinator(gateway, store, fixedClock())

        coroutineScope {
            listOf(
                async { coordinator.add(listOf(CartLineInput(VARIANT_ID, 1))) },
                async { coordinator.add(listOf(CartLineInput(VARIANT_ID, 1))) }
            ).awaitAll()
        }

        assertEquals(1, gateway.maximumConcurrentMutations.get())
    }

    @Test
    fun `completion clears only the exact launched cart reference`() = runTest {
        val expected = persisted(CartOwnership.ANONYMOUS)
        val matchingStore = RecordingCartStore(expected)
        val matchingCoordinator = CartCoordinator(FakeStorefrontGateway(), matchingStore, fixedClock())

        assertEquals(CartCompletionResolution.CLEARED, matchingCoordinator.completion.complete(expected.id))
        assertEquals(null, matchingStore.cart)

        val differentStore = RecordingCartStore(expected)
        val differentCoordinator = CartCoordinator(FakeStorefrontGateway(), differentStore, fixedClock())
        val differentId = SensitiveCartId.from("gid://shopify/Cart/different?key=synthetic")

        assertEquals(CartCompletionResolution.DIFFERENT_CART, differentCoordinator.completion.complete(differentId))
        assertEquals(expected, differentStore.cart)
        assertEquals(0, differentStore.clearCount)
    }

    @Test
    fun `completion is idempotent when protected reference is already absent`() = runTest {
        val coordinator = CartCoordinator(FakeStorefrontGateway(), RecordingCartStore(), fixedClock())

        assertEquals(
            CartCompletionResolution.ALREADY_ABSENT,
            coordinator.completion.complete(SensitiveCartId.from("gid://shopify/Cart/absent?key=synthetic"))
        )
    }

    private fun cart(customerAssociated: Boolean = false): CartReference = CartReference(
        id = SensitiveCartId.from("gid://shopify/Cart/test?key=$CART_SECRET"),
        checkoutUrl = SensitiveCheckoutUrl.from(URI("https://gurbakir.com/cart/c/synthetic")),
        totalQuantity = 1,
        lines =
            listOf(
                CartLineSummary(
                    SensitiveCartLineId.from("gid://shopify/CartLine/synthetic"),
                    VARIANT_ID,
                    1
                )
            ),
        hasMoreLines = false,
        warningCodes = emptySet(),
        customerAssociated = customerAssociated
    )

    private fun persisted(ownership: CartOwnership): PersistedCart = PersistedCart(
        SensitiveCartId.from("gid://shopify/Cart/test?key=$CART_SECRET"),
        CLOCK_INSTANT.plusSeconds(3600),
        ownership
    )

    private fun fixedClock(): Clock = Clock.fixed(CLOCK_INSTANT, ZoneOffset.UTC)

    private class RecordingCartStore(var cart: PersistedCart? = null) : CartSessionStore {
        var clearCount = 0

        override suspend fun read(): PersistedCart? = cart

        override suspend fun write(cart: PersistedCart) {
            this.cart = cart
        }

        override suspend fun clear() {
            clearCount += 1
            cart = null
        }
    }

    private class FakeStorefrontGateway(
        private val createResult: StorefrontResult<CartReference> =
            StorefrontResult.Failure(StorefrontFailure.Transport(false)),
        private val loadResult: StorefrontResult<CartReference> =
            StorefrontResult.Failure(StorefrontFailure.Transport(false)),
        private val identityResult: StorefrontResult<CartReference> = loadResult,
        private val clearIdentityResult: StorefrontResult<CartReference> = loadResult
    ) : StorefrontGateway {
        var loadCount = 0
        var identityCount = 0

        override suspend fun loadShopSummary(): StorefrontResult<ShopSummary> =
            StorefrontResult.Failure(StorefrontFailure.Transport(false))

        override suspend fun loadCatalogPage(after: Cursor?): StorefrontResult<CatalogPage> =
            StorefrontResult.Failure(StorefrontFailure.Transport(false))

        override suspend fun createCart(
            lines: List<CartLineInput>,
            buyerAccessToken: SensitiveBuyerAccessToken?
        ): StorefrontResult<CartReference> = createResult

        override suspend fun loadCart(cartId: SensitiveCartId): StorefrontResult<CartReference> {
            loadCount += 1
            return loadResult
        }

        override suspend fun addCartLines(
            cartId: SensitiveCartId,
            lines: List<CartLineInput>
        ): StorefrontResult<CartReference> = loadResult

        override suspend fun updateCartLines(
            cartId: SensitiveCartId,
            lines: List<CartLineUpdate>
        ): StorefrontResult<CartReference> = loadResult

        override suspend fun removeCartLines(
            cartId: SensitiveCartId,
            lineIds: List<SensitiveCartLineId>
        ): StorefrontResult<CartReference> = loadResult

        override suspend fun updateBuyerIdentity(
            cartId: SensitiveCartId,
            buyerAccessToken: SensitiveBuyerAccessToken?
        ): StorefrontResult<CartReference> = if (buyerAccessToken == null) {
            clearIdentityResult
        } else {
            identityCount += 1
            identityResult
        }
    }

    private class SerialMutationGateway(private val resultCart: CartReference) : StorefrontGateway {
        private val activeMutations = AtomicInteger()
        val maximumConcurrentMutations = AtomicInteger()

        override suspend fun addCartLines(
            cartId: SensitiveCartId,
            lines: List<CartLineInput>
        ): StorefrontResult<CartReference> {
            val active = activeMutations.incrementAndGet()
            maximumConcurrentMutations.updateAndGet { current -> maxOf(current, active) }
            delay(10)
            activeMutations.decrementAndGet()
            return StorefrontResult.Success(resultCart)
        }

        override suspend fun loadShopSummary(): StorefrontResult<ShopSummary> = unused()
        override suspend fun loadCatalogPage(after: Cursor?): StorefrontResult<CatalogPage> = unused()
        override suspend fun createCart(
            lines: List<CartLineInput>,
            buyerAccessToken: SensitiveBuyerAccessToken?
        ): StorefrontResult<CartReference> = unused()
        override suspend fun loadCart(cartId: SensitiveCartId): StorefrontResult<CartReference> = unused()
        override suspend fun updateCartLines(
            cartId: SensitiveCartId,
            lines: List<CartLineUpdate>
        ): StorefrontResult<CartReference> = unused()
        override suspend fun removeCartLines(
            cartId: SensitiveCartId,
            lineIds: List<SensitiveCartLineId>
        ): StorefrontResult<CartReference> = unused()
        override suspend fun updateBuyerIdentity(
            cartId: SensitiveCartId,
            buyerAccessToken: SensitiveBuyerAccessToken?
        ): StorefrontResult<CartReference> = unused()

        private fun <T> unused(): StorefrontResult<T> = StorefrontResult.Failure(StorefrontFailure.Transport(false))
    }

    private companion object {
        const val CART_SECRET = "synthetic-secret-never-render"
        const val VARIANT_ID = "gid://shopify/ProductVariant/synthetic"
        val CLOCK_INSTANT: Instant = Instant.parse("2026-08-06T10:00:00Z")
    }
}

private fun ByteArray.toHexString(): String = joinToString(separator = "") { byte -> "%02x".format(byte) }
