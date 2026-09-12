@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.gurbakir.mobile.product

import androidx.lifecycle.SavedStateHandle
import com.gurbakir.mobile.cart.CartActionResult
import com.gurbakir.mobile.cart.CartCheckoutResolution
import com.gurbakir.mobile.cart.CartRepository
import com.gurbakir.mobile.cart.CartState
import com.gurbakir.storefront.SensitiveCartLineId
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
            viewModel.start(productFixture().id, null)
            advanceUntilIdle()

            viewModel.selectOption("Color", "Blue")
            viewModel.selectOption("Size", "Large")
            assertEquals(mapOf("Color" to "Blue"), viewModel.state.value.selectedOptions)
            assertNull(viewModel.state.value.selectedVariant)

            viewModel.selectOption("Color", "Red")
            viewModel.selectOption("Size", "Large")
            assertEquals("120.00", viewModel.state.value.selectedVariant?.price?.amount.toString())
            assertEquals("large-red", viewModel.state.value.displayMedia.first().altText)
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
        cartRepository: CartRepository = NoOpCartRepository()
    ): ProductDetailViewModel = ProductDetailViewModel(
        QueueProductRepository(ArrayDeque(listOf(ProductDetailLoad.Content(productFixture())))),
        cartRepository,
        handle
    )
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
