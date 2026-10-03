@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.gurbakir.mobile.product

import androidx.lifecycle.SavedStateHandle
import com.gurbakir.mobile.cart.CartActionResult
import com.gurbakir.mobile.cart.CartCheckoutResolution
import com.gurbakir.mobile.cart.CartRepository
import com.gurbakir.mobile.cart.CartState
import com.gurbakir.storefront.SensitiveCartLineId
import com.gurbakir.storefront.StorefrontProductDetail
import com.gurbakir.storefront.StorefrontSelectedOption
import java.util.ArrayDeque
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ProductDetailViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `requested variant is validated and unavailable variant has no purchase intent`() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.start(productFixture().id, "gid://shopify/ProductVariant/12")
        advanceUntilIdle()

        assertEquals("gid://shopify/ProductVariant/12", viewModel.state.value.selectedVariant?.id)
        assertNull(viewModel.state.value.purchaseIntent)
        assertFalse(viewModel.state.value.invalidRequestedVariant)
    }

    @Test
    fun `stale requested variant requires a fresh explicit valid selection`() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.start(productFixture().id, "gid://shopify/ProductVariant/999")
        advanceUntilIdle()

        assertTrue(viewModel.state.value.invalidRequestedVariant)
        assertNull(viewModel.state.value.selectedVariant)
        viewModel.selectOption("Color", "Red")
        viewModel.selectOption("Size", "Large")
        assertEquals("gid://shopify/ProductVariant/13", viewModel.state.value.selectedVariant?.id)
        assertEquals("gid://shopify/ProductVariant/13", viewModel.state.value.purchaseIntent?.merchandiseId)
    }

    @Test
    fun `impossible combination is ignored while valid selection changes exact price and media`() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            viewModel.start(productFixture().id, "gid://shopify/ProductVariant/12")
            advanceUntilIdle()

            viewModel.selectOption("Size", "Large")
            assertEquals(mapOf("Size" to "Small", "Color" to "Blue"), viewModel.state.value.selectedOptions)
            assertEquals("gid://shopify/ProductVariant/12", viewModel.state.value.selectedVariant?.id)
            assertNull(viewModel.state.value.purchaseIntent)

            viewModel.selectOption("Color", "Red")
            viewModel.selectOption("Size", "Large")
            assertEquals("120.00", viewModel.state.value.selectedVariant?.price?.amount.toString())
            assertEquals("large-red", viewModel.state.value.displayMedia.first().altText)
        }

    @Test
    fun `new sold out choice is ignored while a sellable partial choice is retained`() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.start(productFixture().id, null)
        advanceUntilIdle()

        viewModel.selectOption("Color", "Blue")
        assertTrue(viewModel.state.value.selectedOptions.isEmpty())
        assertNull(viewModel.state.value.purchaseIntent)

        viewModel.selectOption("Size", "Large")
        assertEquals(mapOf("Size" to "Large"), viewModel.state.value.selectedOptions)
        assertNull(viewModel.state.value.selectedVariant)
        assertNull(viewModel.state.value.purchaseIntent)
    }

    @Test
    fun `bounded option and media state restores after process recreation`() = runTest(dispatcher) {
        val handle = SavedStateHandle()
        val first = viewModel(handle)
        first.start(productFixture().id, null)
        advanceUntilIdle()
        first.selectOption("Color", "Red")
        first.selectOption("Size", "Large")
        first.selectMedia(1)

        val restored = viewModel(handle)
        restored.start(productFixture().id, null)
        advanceUntilIdle()

        assertEquals("gid://shopify/ProductVariant/13", restored.state.value.selectedVariant?.id)
        assertEquals(1, restored.state.value.mediaIndex)
    }

    @Test
    fun `clearing choices makes a separate sellable combination reachable without selecting sold out variants`() =
        runTest(dispatcher) {
            val product = diagonalAvailabilityProduct()
            val cart = RecordingCartRepository()
            val viewModel = viewModel(cartRepository = cart, product = product)
            viewModel.start(product.id, null)
            advanceUntilIdle()
            viewModel.selectOption("Size", "Small")
            viewModel.selectOption("Color", "Red")

            viewModel.selectOption("Size", "Large")
            viewModel.selectOption("Color", "Blue")
            assertEquals("gid://shopify/ProductVariant/11", viewModel.state.value.selectedVariant?.id)

            viewModel.clearSelection()
            assertTrue(viewModel.state.value.selectedOptions.isEmpty())
            assertNull(viewModel.state.value.purchaseIntent)
            viewModel.selectOption("Size", "Large")
            viewModel.selectOption("Color", "Blue")

            assertEquals("gid://shopify/ProductVariant/14", viewModel.state.value.selectedVariant?.id)
            assertEquals("gid://shopify/ProductVariant/14", viewModel.state.value.purchaseIntent?.merchandiseId)
            assertEquals("120.00", viewModel.state.value.selectedVariant?.price?.amount.toString())
            assertTrue(cart.adds.isEmpty())
        }

    @Test
    fun `cleared selection and media restore as empty without repeating a prior cart action`() = runTest(dispatcher) {
        val handle = SavedStateHandle()
        val cart = RecordingCartRepository()
        val first = viewModel(handle, cart)
        first.start(productFixture().id, null)
        advanceUntilIdle()
        first.selectOption("Color", "Red")
        first.selectOption("Size", "Large")
        first.selectMedia(1)
        first.addToCart()
        advanceUntilIdle()
        assertEquals(ProductCartFeedback.ADDED, first.state.value.cartFeedback)

        first.clearSelection()
        assertTrue(first.state.value.selectedOptions.isEmpty())
        assertNull(first.state.value.selectedVariant)
        assertNull(first.state.value.purchaseIntent)
        assertNull(first.state.value.cartFeedback)
        assertNull(first.state.value.cartFailure)
        assertFalse(first.state.value.invalidRequestedVariant)
        assertEquals(0, first.state.value.mediaIndex)

        val restored = viewModel(handle, cart)
        restored.start(productFixture().id, null)
        advanceUntilIdle()
        assertTrue(restored.state.value.selectedOptions.isEmpty())
        assertNull(restored.state.value.purchaseIntent)
        assertEquals(0, restored.state.value.mediaIndex)
        assertEquals(listOf("gid://shopify/ProductVariant/13" to 1), cart.adds)
    }

    @Test
    fun `clearing is ignored while the captured cart mutation is pending`() = runTest(dispatcher) {
        val cart = RecordingCartRepository()
        val viewModel = viewModel(cartRepository = cart)
        viewModel.start(productFixture().id, null)
        advanceUntilIdle()
        viewModel.selectOption("Color", "Red")
        viewModel.selectOption("Size", "Large")
        val selection = viewModel.state.value.selectedOptions
        viewModel.addToCart()

        viewModel.clearSelection()
        assertTrue(viewModel.state.value.addingToCart)
        assertEquals(selection, viewModel.state.value.selectedOptions)
        advanceUntilIdle()
        assertEquals(listOf("gid://shopify/ProductVariant/13" to 1), cart.adds)
        viewModel.clearSelection()
        assertTrue(viewModel.state.value.selectedOptions.isEmpty())
    }

    @Test
    fun `clearing a requested variant remains cleared after retry and same route recreation`() = runTest(dispatcher) {
        listOf("gid://shopify/ProductVariant/12", "gid://shopify/ProductVariant/13").forEach { request ->
            val handle = SavedStateHandle()
            val cart = RecordingCartRepository()
            val repository = QueueProductRepository(ArrayDeque(List(2) { ProductDetailLoad.Content(productFixture()) }))
            val first = ProductDetailViewModel(repository, cart, handle)
            first.start(productFixture().id, request)
            advanceUntilIdle()
            assertEquals(request, first.state.value.selectedVariant?.id)
            first.selectMedia(1)
            first.clearSelection()
            first.retry()
            advanceUntilIdle()
            assertTrue(first.state.value.selectedOptions.isEmpty())
            assertNull(first.state.value.purchaseIntent)
            assertFalse(first.state.value.invalidRequestedVariant)
            assertEquals(0, first.state.value.mediaIndex)

            val restored = viewModel(handle, cart)
            restored.start(productFixture().id, request)
            advanceUntilIdle()
            assertTrue(restored.state.value.selectedOptions.isEmpty())
            assertNull(restored.state.value.selectedVariant)
            assertNull(restored.state.value.purchaseIntent)
            assertFalse(restored.state.value.invalidRequestedVariant)
            assertEquals(0, restored.state.value.mediaIndex)
            assertTrue(cart.adds.isEmpty())
        }
    }

    @Test
    fun `explicit choice restores for its original stale request while new requests are freshly validated`() =
        runTest(dispatcher) {
            val handle = SavedStateHandle()
            val originalRequest = "gid://shopify/ProductVariant/999"
            val first = viewModel(handle)
            first.start(productFixture().id, originalRequest)
            advanceUntilIdle()
            first.selectOption("Color", "Red")
            first.selectOption("Size", "Large")

            val restored = viewModel(handle)
            restored.start(productFixture().id, originalRequest)
            advanceUntilIdle()
            assertEquals("gid://shopify/ProductVariant/13", restored.state.value.selectedVariant?.id)
            assertFalse(restored.state.value.invalidRequestedVariant)

            val differentRequest = viewModel(handle)
            differentRequest.start(productFixture().id, "gid://shopify/ProductVariant/12")
            advanceUntilIdle()
            assertEquals("gid://shopify/ProductVariant/12", differentRequest.state.value.selectedVariant?.id)
            assertNull(differentRequest.state.value.purchaseIntent)
            assertFalse(differentRequest.state.value.invalidRequestedVariant)

            val staleRequest = viewModel(handle)
            staleRequest.start(productFixture().id, originalRequest)
            advanceUntilIdle()
            assertTrue(staleRequest.state.value.selectedOptions.isEmpty())
            assertTrue(staleRequest.state.value.invalidRequestedVariant)
            assertNull(staleRequest.state.value.purchaseIntent)
        }

    @Test
    fun `restored explicit choices are still validated against current provider options`() = runTest(dispatcher) {
        val handle = SavedStateHandle()
        val request = "gid://shopify/ProductVariant/12"
        val first = viewModel(handle)
        first.start(productFixture().id, request)
        advanceUntilIdle()
        first.selectOption("Color", "Red")
        first.selectOption("Size", "Large")

        val original = productFixture()
        val changedProduct = original.copy(variants = original.variants.dropLast(1))
        val restored = viewModel(handle, product = changedProduct)
        restored.start(original.id, request)
        advanceUntilIdle()
        assertTrue(restored.state.value.selectedOptions.isEmpty())
        assertNull(restored.state.value.selectedVariant)
        assertNull(restored.state.value.purchaseIntent)
    }

    @Test
    fun `valid selected variant is submitted once and confirms without forced navigation`() = runTest(dispatcher) {
        val cart = RecordingCartRepository()
        val viewModel = viewModel(cartRepository = cart)
        viewModel.start(productFixture().id, null)
        advanceUntilIdle()
        viewModel.selectOption("Color", "Red")
        viewModel.selectOption("Size", "Large")

        viewModel.addToCart()
        viewModel.addToCart()
        advanceUntilIdle()

        assertEquals(listOf("gid://shopify/ProductVariant/13" to 1), cart.adds)
        assertEquals(ProductCartFeedback.ADDED, viewModel.state.value.cartFeedback)
        assertFalse(viewModel.state.value.addingToCart)
    }

    private fun viewModel(
        handle: SavedStateHandle = SavedStateHandle(),
        cartRepository: CartRepository = NoOpCartRepository(),
        product: StorefrontProductDetail = productFixture()
    ): ProductDetailViewModel = ProductDetailViewModel(
        QueueProductRepository(ArrayDeque(listOf(ProductDetailLoad.Content(product)))),
        cartRepository,
        handle
    )

    private fun diagonalAvailabilityProduct(): StorefrontProductDetail {
        val original = productFixture()
        val large = original.variants.last()
        return original.copy(
            variants = original.variants.map { variant ->
                if (variant.id == large.id) variant.copy(availableForSale = false) else variant
            } + large.copy(
                id = "gid://shopify/ProductVariant/14",
                title = "Large / Blue",
                selectedOptions = listOf(
                    StorefrontSelectedOption("Size", "Large"),
                    StorefrontSelectedOption("Color", "Blue")
                )
            )
        )
    }
}

private class QueueProductRepository(private val results: ArrayDeque<ProductDetailLoad>) : ProductDetailRepository {
    override suspend fun load(productId: String): ProductDetailLoad = results.removeFirst()
}

private open class NoOpCartRepository : CartRepository {
    override val state: StateFlow<CartState> = MutableStateFlow(CartState())

    override suspend fun refresh() = Unit

    override suspend fun add(merchandiseId: String, quantity: Int): CartActionResult = CartActionResult.Completed

    override suspend fun update(lineId: SensitiveCartLineId, quantity: Int): CartActionResult =
        CartActionResult.Completed

    override suspend fun remove(lineId: SensitiveCartLineId): CartActionResult = CartActionResult.Completed

    override suspend fun discard(): CartActionResult = CartActionResult.Completed

    override suspend fun prepareCheckout(): CartCheckoutResolution = CartCheckoutResolution.Empty
}

private class RecordingCartRepository : NoOpCartRepository() {
    val adds = mutableListOf<Pair<String, Int>>()

    override suspend fun add(merchandiseId: String, quantity: Int): CartActionResult {
        adds += merchandiseId to quantity
        return CartActionResult.Completed
    }
}
