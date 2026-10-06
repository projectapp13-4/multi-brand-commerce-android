@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.gurbakir.mobile.product

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import com.gurbakir.mobile.cart.CartActionAdjustment
import com.gurbakir.mobile.cart.CartActionKind
import com.gurbakir.mobile.cart.CartActionResult
import com.gurbakir.mobile.cart.CartCheckoutResolution
import com.gurbakir.mobile.cart.CartFailure
import com.gurbakir.mobile.cart.CartFailureCategory
import com.gurbakir.mobile.cart.CartLifecycleOperations
import com.gurbakir.mobile.cart.CartMutationPlan
import com.gurbakir.mobile.cart.CartRepository
import com.gurbakir.mobile.cart.CartState
import com.gurbakir.mobile.cart.DefaultCartRepository
import com.gurbakir.mobile.cart.lifecycleActive
import com.gurbakir.mobile.catalog.CatalogLoadFailure
import com.gurbakir.mobile.catalog.CatalogLoadFailureCategory
import com.gurbakir.storefront.CartLineInput
import com.gurbakir.storefront.SensitiveCartLineId
import com.gurbakir.storefront.StorefrontProductDetail
import java.util.ArrayDeque
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ProductCartAttributionTest {
    private val dispatcher = StandardTestDispatcher()
    private val stores = mutableListOf<ViewModelStore>()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterEach
    fun tearDown() {
        stores.forEach(ViewModelStore::clear)
        dispatcher.scheduler.runCurrent()
        Dispatchers.resetMain()
    }

    @Test
    fun `completed add for A does not confirm the now selected B`() = runTest(dispatcher) {
        assertPendingSelectionChange(CartActionResult.Completed)
    }

    @Test
    fun `adjusted add for A does not attach its quantities to the now selected B`() = runTest(dispatcher) {
        assertPendingSelectionChange(CartActionResult.Adjusted(ADJUSTMENT))
    }

    @Test
    fun `failed add for A does not attach its failure to the now selected B`() = runTest(dispatcher) {
        assertPendingSelectionChange(CartActionResult.Failed(FAILURE))
    }

    @Test
    fun `restricted add for A does not attach its feedback to the now selected B`() = runTest(dispatcher) {
        assertPendingSelectionChange(CartActionResult.Restricted)
    }

    @Test
    fun `returning from B to A cannot revive any previous submission result`() = runTest(dispatcher) {
        for (outcome in outcomes()) {
            val fixture = fixture()
            fixture.model.addToCart()
            runCurrent()
            fixture.model.selectOption("Size", "Large")
            fixture.model.selectOption("Size", "Small")
            assertEquals(ProductPurchaseIntent(VARIANT_A, 1), fixture.model.state.value.purchaseIntent)

            fixture.cart.first.complete(outcome.result)
            runCurrent()

            assertNoCartPresentation(fixture.model.state.value)
            assertFalse(fixture.model.state.value.addingToCart)
            assertEquals(listOf(ProductPurchaseIntent(VARIANT_A, 1)), fixture.cart.adds)

            val fresh = fixture.cart.enqueue()
            fixture.model.addToCart()
            runCurrent()
            fresh.complete(CartActionResult.Completed)
            runCurrent()
            assertEquals(ProductCartFeedback.ADDED, fixture.model.state.value.cartFeedback)
            assertEquals(List(2) { ProductPurchaseIntent(VARIANT_A, 1) }, fixture.cart.adds)
        }
    }

    @Test
    fun `effective option changes immediately clear all settled Product cart presentation`() = runTest(dispatcher) {
        for (outcome in outcomes()) {
            val fixture = fixture()
            fixture.model.addToCart()
            runCurrent()
            fixture.cart.first.complete(outcome.result)
            runCurrent()
            assertOutcome(outcome, fixture.model.state.value)

            fixture.model.selectOption("Size", "Large")

            assertSelectedB(fixture.model.state.value)
            assertNoCartPresentation(fixture.model.state.value)
        }
    }

    @Test
    fun `unchanged selection retains each correctly attributed typed outcome`() = runTest(dispatcher) {
        for (outcome in outcomes()) {
            val fixture = fixture()
            fixture.model.addToCart()
            runCurrent()
            fixture.cart.first.complete(outcome.result)
            runCurrent()

            assertOutcome(outcome, fixture.model.state.value)
            assertFalse(fixture.model.state.value.addingToCart)
            assertEquals(listOf(ProductPurchaseIntent(VARIANT_A, 1)), fixture.cart.adds)
        }
    }

    @Test
    fun `same option invalid choice and media changes retain unchanged intent attribution`() = runTest(dispatcher) {
        for (outcome in outcomes()) {
            val fixture = fixture()
            fixture.model.addToCart()
            runCurrent()
            fixture.model.selectOption("Size", "Small")
            fixture.model.selectOption("Color", "Blue")
            fixture.model.selectOption("Unknown", "Small")
            fixture.model.selectMedia(1)
            fixture.model.setMediaViewer(true)
            fixture.cart.first.complete(outcome.result)
            runCurrent()

            assertOutcome(outcome, fixture.model.state.value)
            assertEquals(ProductPurchaseIntent(VARIANT_A, 1), fixture.model.state.value.purchaseIntent)
            assertEquals(1, fixture.model.state.value.mediaIndex)
            assertTrue(fixture.model.state.value.mediaViewerOpen)

            fixture.model.selectOption("Size", "Small")
            fixture.model.selectOption("Size", "Unknown")
            fixture.model.selectMedia(0)
            assertOutcome(outcome, fixture.model.state.value)
        }
    }

    @Test
    fun `rapid taps coalesce A including taps after selecting B and fresh B can submit later`() = runTest(dispatcher) {
        val fixture = fixture()
        fixture.model.addToCart()
        fixture.model.addToCart()
        fixture.model.selectOption("Size", "Large")
        fixture.model.addToCart()
        runCurrent()
        fixture.model.addToCart()
        assertEquals(listOf(ProductPurchaseIntent(VARIANT_A, 1)), fixture.cart.adds)
        assertTrue(fixture.model.state.value.addingToCart)

        fixture.cart.first.complete(CartActionResult.Completed)
        runCurrent()
        assertNoCartPresentation(fixture.model.state.value)
        assertFalse(fixture.model.state.value.addingToCart)

        val fresh = fixture.cart.enqueue()
        fixture.model.addToCart()
        runCurrent()
        fresh.complete(CartActionResult.Completed)
        runCurrent()
        assertEquals(
            listOf(ProductPurchaseIntent(VARIANT_A, 1), ProductPurchaseIntent(VARIANT_B, 1)),
            fixture.cart.adds
        )
        assertEquals(ProductCartFeedback.ADDED, fixture.model.state.value.cartFeedback)
        assertSelectedB(fixture.model.state.value)
        assertFalse(fixture.model.state.value.addingToCart)
    }

    @Test
    fun `synchronous completed actions release ownership for a later action`() = runTest(dispatcher) {
        val cart = AttributionCartRepository()
        cart.first.complete(CartActionResult.Completed)
        val fixture = fixture(cart)
        fixture.model.addToCart()
        runCurrent()
        assertEquals(ProductCartFeedback.ADDED, fixture.model.state.value.cartFeedback)
        assertFalse(fixture.model.state.value.addingToCart)

        cart.enqueue().complete(CartActionResult.Adjusted(ADJUSTMENT))
        fixture.model.addToCart()
        runCurrent()
        assertEquals(ProductCartFeedback.ADJUSTED, fixture.model.state.value.cartFeedback)
        assertEquals(ADJUSTMENT, fixture.model.state.value.cartAdjustment)
        assertFalse(fixture.model.state.value.addingToCart)
        assertEquals(List(2) { ProductPurchaseIntent(VARIANT_A, 1) }, cart.adds)
    }

    @Test
    fun `retry keeps the pending owner visible during loading and after content returns`() = runTest(dispatcher) {
        val fixture = fixture()
        fixture.model.addToCart()
        runCurrent()
        val reload = fixture.loads.holdNext()

        fixture.model.retry()

        assertTrue(fixture.model.state.value.loading)
        assertTrue(fixture.model.state.value.addingToCart)
        assertNoCartPresentation(fixture.model.state.value)
        runCurrent()
        reload.complete(ProductDetailLoad.Content(productFixture()))
        runCurrent()
        assertTrue(fixture.model.state.value.addingToCart)
        fixture.model.addToCart()
        assertEquals(listOf(ProductPurchaseIntent(VARIANT_A, 1)), fixture.cart.adds)

        fixture.cart.first.complete(CartActionResult.Completed)
        runCurrent()
        assertFalse(fixture.model.state.value.addingToCart)
        assertNoCartPresentation(fixture.model.state.value)
    }

    @Test
    fun `same route retry to A invalidates all results from the old A submission`() = runTest(dispatcher) {
        for (outcome in outcomes()) {
            val fixture = fixture()
            fixture.model.addToCart()
            runCurrent()
            fixture.model.retry()
            runCurrent()
            assertEquals(ProductPurchaseIntent(VARIANT_A, 1), fixture.model.state.value.purchaseIntent)

            fixture.cart.first.complete(outcome.result)
            runCurrent()

            assertNoCartPresentation(fixture.model.state.value)
            assertFalse(fixture.model.state.value.addingToCart)
            assertEquals(listOf(ProductPurchaseIntent(VARIANT_A, 1)), fixture.cart.adds)
            assertEquals(listOf(PRODUCT, PRODUCT), fixture.loads.loads)
        }
    }

    @Test
    fun `changed product route retains pending owner and suppresses the old product result`() = runTest(dispatcher) {
        val fixture = fixture()
        fixture.model.addToCart()
        runCurrent()
        fixture.model.start(OTHER_PRODUCT, VARIANT_B)
        assertTrue(fixture.model.state.value.loading)
        assertTrue(fixture.model.state.value.addingToCart)
        runCurrent()
        assertEquals(OTHER_PRODUCT, fixture.model.state.value.product?.id)
        assertSelectedB(fixture.model.state.value)
        assertTrue(fixture.model.state.value.addingToCart)
        fixture.model.addToCart()

        fixture.cart.first.complete(CartActionResult.Adjusted(ADJUSTMENT))
        runCurrent()

        assertNoCartPresentation(fixture.model.state.value)
        assertFalse(fixture.model.state.value.addingToCart)
        assertEquals(listOf(ProductPurchaseIntent(VARIANT_A, 1)), fixture.cart.adds)
        assertEquals(listOf(PRODUCT, OTHER_PRODUCT), fixture.loads.loads)
    }

    @Test
    fun `pending owner survives error and not found reload results until settlement`() = runTest(dispatcher) {
        val loadFailure = CatalogLoadFailure(CatalogLoadFailureCategory.SERVICE, retryable = true)
        for (loadResult in listOf(ProductDetailLoad.Error(loadFailure), ProductDetailLoad.NotFound)) {
            val fixture = fixture()
            fixture.model.addToCart()
            runCurrent()
            val reload = fixture.loads.holdNext()
            fixture.model.retry()
            runCurrent()
            reload.complete(loadResult)
            runCurrent()

            assertTrue(fixture.model.state.value.addingToCart)
            assertNull(fixture.model.state.value.product)
            assertNoCartPresentation(fixture.model.state.value)
            fixture.cart.first.complete(CartActionResult.Restricted)
            runCurrent()
            assertFalse(fixture.model.state.value.addingToCart)
            assertNoCartPresentation(fixture.model.state.value)
        }
    }

    @Test
    fun `settlement during reload does not confirm loading state or leave returned content busy`() =
        runTest(dispatcher) {
            val fixture = fixture()
            fixture.model.addToCart()
            runCurrent()
            val reload = fixture.loads.holdNext()
            fixture.model.retry()
            runCurrent()

            fixture.cart.first.complete(CartActionResult.Completed)
            runCurrent()
            assertTrue(fixture.model.state.value.loading)
            assertNoCartPresentation(fixture.model.state.value)
            assertFalse(fixture.model.state.value.addingToCart)

            reload.complete(ProductDetailLoad.Content(productFixture()))
            runCurrent()
            assertEquals(ProductPurchaseIntent(VARIANT_A, 1), fixture.model.state.value.purchaseIntent)
            assertNoCartPresentation(fixture.model.state.value)
            assertFalse(fixture.model.state.value.addingToCart)
        }

    @Test
    fun `boundary cancellation releases its child owner and permits a fresh action`() = runTest(dispatcher) {
        val fixture = fixture()
        fixture.model.addToCart()
        runCurrent()
        fixture.cart.first.completeExceptionally(CancellationException("Synthetic action cancellation"))
        runCurrent()

        assertEquals(1, fixture.cart.cancellations)
        assertFalse(fixture.model.state.value.addingToCart)
        assertNoCartPresentation(fixture.model.state.value)

        val fresh = fixture.cart.enqueue()
        fixture.model.selectOption("Size", "Large")
        fixture.model.addToCart()
        runCurrent()
        assertTrue(fixture.model.state.value.addingToCart)
        fresh.complete(CartActionResult.Completed)
        runCurrent()
        assertEquals(ProductCartFeedback.ADDED, fixture.model.state.value.cartFeedback)
        assertFalse(fixture.model.state.value.addingToCart)
        assertEquals(
            listOf(ProductPurchaseIntent(VARIANT_A, 1), ProductPurchaseIntent(VARIANT_B, 1)),
            fixture.cart.adds
        )
    }

    @Test
    fun `clearing actual Product store cancels without confirmation and a fresh owner can submit`() =
        runTest(dispatcher) {
            val fixture = fixture()
            fixture.model.addToCart()
            runCurrent()

            fixture.store.clear()
            runCurrent()

            assertEquals(1, fixture.cart.cancellations)
            assertFalse(fixture.cart.first.isCompleted)
            assertFalse(fixture.model.state.value.addingToCart)
            assertNoCartPresentation(fixture.model.state.value)
            assertEquals(listOf(ProductPurchaseIntent(VARIANT_A, 1)), fixture.cart.adds)

            fixture.cart.enqueue().complete(CartActionResult.Completed)
            val reopened = fixture(fixture.cart)
            reopened.model.addToCart()
            runCurrent()
            assertEquals(ProductCartFeedback.ADDED, reopened.model.state.value.cartFeedback)
            assertFalse(reopened.model.state.value.addingToCart)
            assertEquals(List(2) { ProductPurchaseIntent(VARIANT_A, 1) }, fixture.cart.adds)
        }

    @Test
    fun `clear selection remains guarded during add and clears settled attribution afterward`() = runTest(dispatcher) {
        val fixture = fixture()
        fixture.model.addToCart()
        runCurrent()
        fixture.model.clearSelection()
        assertEquals(ProductPurchaseIntent(VARIANT_A, 1), fixture.model.state.value.purchaseIntent)
        assertTrue(fixture.model.state.value.addingToCart)

        fixture.cart.first.complete(CartActionResult.Adjusted(ADJUSTMENT))
        runCurrent()
        fixture.model.clearSelection()
        assertTrue(fixture.model.state.value.selectedOptions.isEmpty())
        assertNull(fixture.model.state.value.purchaseIntent)
        assertNoCartPresentation(fixture.model.state.value)
    }

    @Test
    fun `suppressing stale completed Product feedback keeps the real repositories completed cart`() =
        runTest(dispatcher) {
            assertSharedCartTruth(observedQuantity = 3, expectedAdjustment = null)
        }

    @Test
    fun `suppressing stale adjusted Product feedback keeps the real repositories adjusted cart`() =
        runTest(dispatcher) {
            assertSharedCartTruth(observedQuantity = 4, expectedAdjustment = ADJUSTMENT)
        }

    private fun TestScope.assertPendingSelectionChange(result: CartActionResult) {
        val fixture = fixture()
        fixture.model.addToCart()
        runCurrent()
        assertTrue(fixture.model.state.value.addingToCart)
        fixture.model.selectOption("Size", "Large")
        assertSelectedB(fixture.model.state.value)

        fixture.cart.first.complete(result)
        runCurrent()

        assertSelectedB(fixture.model.state.value)
        assertNoCartPresentation(fixture.model.state.value)
        assertFalse(fixture.model.state.value.addingToCart)
        assertEquals(listOf(ProductPurchaseIntent(VARIANT_A, 1)), fixture.cart.adds)
    }

    private suspend fun TestScope.assertSharedCartTruth(
        observedQuantity: Int,
        expectedAdjustment: CartActionAdjustment?
    ) {
        val operations = CartLifecycleOperations()
        operations.current = lifecycleActive(2)
        val gate = CompletableDeferred<Unit>()
        operations.mutationGate = gate
        val repository = DefaultCartRepository(operations)
        repository.refresh()
        val model = model(AttributionProductLoads(), repository)
        runCurrent()
        model.addToCart()
        runCurrent()
        model.selectOption("Size", "Large")
        operations.current = lifecycleActive(observedQuantity)

        gate.complete(Unit)
        runCurrent()

        assertEquals(observedQuantity, repository.state.value.cart?.totalQuantity)
        assertEquals(VARIANT_A, repository.state.value.cart?.lines?.single()?.merchandiseId)
        assertEquals(expectedAdjustment, repository.state.value.adjustment)
        assertNull(repository.state.value.failure)
        assertEquals(listOf(CartMutationPlan.Add(listOf(CartLineInput(VARIANT_A, 1)))), operations.plans)
        assertEquals(1, operations.restores)
        assertEquals(0, operations.clears)
        assertNoCartPresentation(model.state.value)
        assertSelectedB(model.state.value)
        assertFalse(model.state.value.addingToCart)
    }

    private fun TestScope.fixture(cart: AttributionCartRepository = AttributionCartRepository()): AttributionFixture {
        val loads = AttributionProductLoads()
        val model = model(loads, cart)
        runCurrent()
        assertEquals(ProductPurchaseIntent(VARIANT_A, 1), model.state.value.purchaseIntent)
        return AttributionFixture(model, cart, loads, stores.last())
    }

    private fun model(loads: ProductDetailRepository, cart: CartRepository): ProductDetailViewModel {
        val model = ProductDetailViewModel(loads, cart, SavedStateHandle())
        val store = ViewModelStore()
        store.put("product", model)
        stores += store
        model.start(PRODUCT, VARIANT_A)
        return model
    }

    private fun assertSelectedB(state: ProductDetailUiState) {
        assertEquals(ProductPurchaseIntent(VARIANT_B, 1), state.purchaseIntent)
        assertEquals("120.00", state.selectedVariant?.price?.amount.toString())
        assertEquals("large-red", state.displayMedia.first().altText)
    }

    private fun assertNoCartPresentation(state: ProductDetailUiState) {
        assertNull(state.cartFeedback)
        assertNull(state.cartFailure)
        assertNull(state.cartAdjustment)
    }

    private fun assertOutcome(outcome: AttributionOutcome, state: ProductDetailUiState) {
        assertEquals(outcome.feedback, state.cartFeedback)
        assertEquals(outcome.failure, state.cartFailure)
        assertEquals(outcome.adjustment, state.cartAdjustment)
    }

    private fun outcomes(): List<AttributionOutcome> = listOf(
        AttributionOutcome(CartActionResult.Completed, feedback = ProductCartFeedback.ADDED),
        AttributionOutcome(
            CartActionResult.Adjusted(ADJUSTMENT),
            feedback = ProductCartFeedback.ADJUSTED,
            adjustment = ADJUSTMENT
        ),
        AttributionOutcome(CartActionResult.Failed(FAILURE), failure = FAILURE),
        AttributionOutcome(CartActionResult.Restricted, feedback = ProductCartFeedback.RESTRICTED)
    )

    private data class AttributionFixture(
        val model: ProductDetailViewModel,
        val cart: AttributionCartRepository,
        val loads: AttributionProductLoads,
        val store: ViewModelStore
    )

    private data class AttributionOutcome(
        val result: CartActionResult,
        val feedback: ProductCartFeedback? = null,
        val failure: CartFailure? = null,
        val adjustment: CartActionAdjustment? = null
    )

    private class AttributionProductLoads : ProductDetailRepository {
        val loads = mutableListOf<String>()
        private val pending = ArrayDeque<CompletableDeferred<ProductDetailLoad>>()

        fun holdNext(): CompletableDeferred<ProductDetailLoad> = CompletableDeferred<ProductDetailLoad>().also {
            pending.addLast(it)
        }

        override suspend fun load(productId: String): ProductDetailLoad {
            loads += productId
            return if (pending.isEmpty()) {
                ProductDetailLoad.Content(productFixture().copy(id = productId))
            } else {
                pending.removeFirst().await()
            }
        }
    }

    private class AttributionCartRepository : CartRepository {
        override val state: StateFlow<CartState> = MutableStateFlow(CartState())
        val first = CompletableDeferred<CartActionResult>()
        val adds = mutableListOf<ProductPurchaseIntent>()
        var cancellations = 0
            private set
        private val pending = ArrayDeque(listOf(first))

        fun enqueue(): CompletableDeferred<CartActionResult> = CompletableDeferred<CartActionResult>().also {
            pending.addLast(it)
        }

        override suspend fun add(merchandiseId: String, quantity: Int): CartActionResult {
            adds += ProductPurchaseIntent(merchandiseId, quantity)
            try {
                return pending.removeFirst().await()
            } catch (cancelled: CancellationException) {
                cancellations++
                throw cancelled
            }
        }

        override suspend fun refresh() = Unit
        override suspend fun update(lineId: SensitiveCartLineId, quantity: Int): CartActionResult =
            error("Unexpected quantity update from Product")
        override suspend fun remove(lineId: SensitiveCartLineId): CartActionResult =
            error("Unexpected remove from Product")
        override suspend fun discard(): CartActionResult = error("Unexpected discard from Product")
        override suspend fun prepareCheckout(): CartCheckoutResolution = error("Unexpected checkout from Product")
    }

    private companion object {
        const val PRODUCT = "gid://shopify/Product/1"
        const val OTHER_PRODUCT = "gid://shopify/Product/2"
        const val VARIANT_A = "gid://shopify/ProductVariant/11"
        const val VARIANT_B = "gid://shopify/ProductVariant/13"
        val ADJUSTMENT = CartActionAdjustment(CartActionKind.ADD, 2, 3, 4)
        val FAILURE = CartFailure(CartFailureCategory.QUANTITY_OR_AVAILABILITY, retryable = true, cartRetained = true)
    }
}
