@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.gurbakir.mobile.cart

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import com.gurbakir.mobile.product.ProductDetailLoad
import com.gurbakir.mobile.product.ProductDetailRepository
import com.gurbakir.mobile.product.ProductDetailViewModel
import com.gurbakir.mobile.product.productFixture
import com.gurbakir.storefront.CartOwnership
import com.gurbakir.storefront.CartSessionResolution
import com.gurbakir.storefront.ShopifyUserError
import com.gurbakir.storefront.StorefrontFailure
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CartCancellationOwnershipTest {
    @Test
    fun `cleared Product owner releases retained Cart progress and explicit refresh recovers truth`() = lifecycleTest {
        val operations = CartLifecycleOperations()
        val repository = DefaultCartRepository(operations)
        val cartStore = ViewModelStore()
        val productStore = ViewModelStore()
        try {
            val cart = CartViewModel(repository)
            cartStore.put("retained-cart", cart)
            advanceUntilIdle()
            val product = ProductDetailViewModel(
                object : ProductDetailRepository {
                    override suspend fun load(productId: String) = ProductDetailLoad.Content(productFixture())
                },
                repository,
                SavedStateHandle()
            )
            productStore.put("product", product)
            product.start(productFixture().id, LIFECYCLE_VARIANT)
            advanceUntilIdle()
            operations.mutationGate = CompletableDeferred()
            product.addToCart()
            runCurrent()
            assertEquals(CartMutation.ADDING, cart.state.value.mutation)
            productStore.clear()
            advanceUntilIdle()

            assertEquals(1, operations.cancelledMutations)
            assertEquals(1, operations.plans.size)
            assertEquals(1, operations.restores)
            assertEquals(0, operations.clears)
            assertNull(product.state.value.cartFeedback)
            assertNull(product.state.value.cartAdjustment)
            assertNull(cart.state.value.mutation)
            assertEquals(CartStatus.ACTIVE, cart.state.value.status)
            assertEquals(CartOwnership.ANONYMOUS, cart.state.value.ownership)
            assertEquals(2, cart.state.value.badgeQuantity)
            assertEquals(CartFailureCategory.AMBIGUOUS_MUTATION, cart.state.value.failure?.category)
            assertEquals(true, cart.state.value.failure?.retryable)
            operations.current = lifecycleActive(3)
            cart.refresh()
            advanceUntilIdle()
            assertEquals(3, cart.state.value.badgeQuantity)
            assertNull(cart.state.value.failure)
            assertNull(cart.state.value.mutation)
            assertEquals(2, operations.restores)
            assertEquals(1, operations.plans.size)
        } finally {
            productStore.clear()
            cartStore.clear()
        }
    }

    @Test
    fun `cancelled waiter never clears the live mutation or adds a failure`() = lifecycleTest {
        val operations = CartLifecycleOperations()
        val repository = DefaultCartRepository(operations)
        repository.refresh()
        val gate = CompletableDeferred<Unit>()
        operations.mutationGate = gate
        val owner = async { repository.add(LIFECYCLE_VARIANT, 1) }
        runCurrent()
        val waiter = launch { repository.remove(lifecycleLineId()) }
        runCurrent()
        waiter.cancel()
        waiter.join()
        assertEquals(CartMutation.ADDING, repository.state.value.mutation)
        assertNull(repository.state.value.failure)
        assertEquals(1, operations.plans.size)
        assertEquals(0, operations.cancelledMutations)
        operations.current = lifecycleActive(3)
        gate.complete(Unit)
        assertEquals(CartActionResult.Completed, owner.await())
        assertNull(repository.state.value.mutation)
    }

    @Test
    fun `old cancellation finalizer cannot clear the next queued owner`() = lifecycleTest {
        val operations = CartLifecycleOperations()
        val repository = DefaultCartRepository(operations)
        repository.refresh()
        operations.mutationGate = CompletableDeferred()
        val old = launch { repository.add(LIFECYCLE_VARIANT, 1) }
        runCurrent()
        val nextGate = CompletableDeferred<Unit>()
        operations.mutationGate = nextGate
        val next = async { repository.update(lifecycleLineId(), 4) }
        runCurrent()
        old.cancel()
        old.join()
        runCurrent()
        assertEquals(CartMutation.UPDATING, repository.state.value.mutation)
        assertNull(repository.state.value.failure)
        assertEquals(2, operations.plans.size)
        operations.current = lifecycleActive(4)
        nextGate.complete(Unit)
        assertEquals(CartActionResult.Completed, next.await())
        assertNull(repository.state.value.mutation)
    }

    @Test
    fun `cancelled update remove and discard leave retryable recovery without false completion`() = lifecycleTest {
        for (mutation in listOf(CartMutation.UPDATING, CartMutation.REMOVING, CartMutation.DISCARDING)) {
            val operations = CartLifecycleOperations()
            val repository = DefaultCartRepository(operations)
            repository.refresh()
            operations.mutationGate = CompletableDeferred()
            operations.clearGate = CompletableDeferred()
            var completed = false
            val owner = launch {
                when (mutation) {
                    CartMutation.UPDATING -> repository.update(lifecycleLineId(), 3)
                    CartMutation.REMOVING -> repository.remove(lifecycleLineId())
                    CartMutation.DISCARDING -> repository.discard()
                    CartMutation.ADDING -> error("separate Product ownership case")
                }
                completed = true
            }
            runCurrent()
            assertEquals(mutation, repository.state.value.mutation)
            owner.cancel()
            owner.join()
            assertFalse(completed)
            assertNull(repository.state.value.mutation)
            assertEquals(2, repository.state.value.badgeQuantity)
            val expected = if (mutation == CartMutation.DISCARDING) {
                CartFailureCategory.SECURE_STORAGE
            } else {
                CartFailureCategory.AMBIGUOUS_MUTATION
            }
            assertEquals(expected, repository.state.value.failure?.category)
            assertEquals(true, repository.state.value.failure?.retryable)
            assertEquals(1, operations.restores)
        }
    }

    @Test
    fun `cancelled cold refresh and checkout preparation leave error with a recovery action`() = lifecycleTest {
        for (checkout in listOf(false, true)) {
            val operations = CartLifecycleOperations()
            operations.restoreGate = CompletableDeferred()
            val repository = DefaultCartRepository(operations)
            var completed = false
            val owner = launch {
                if (checkout) repository.prepareCheckout() else repository.refresh()
                completed = true
            }
            runCurrent()
            assertEquals(CartStatus.LOADING, repository.state.value.status)
            owner.cancel()
            owner.join()
            assertFalse(completed)
            assertEquals(CartStatus.ERROR, repository.state.value.status)
            assertNull(repository.state.value.cart)
            assertNull(repository.state.value.mutation)
            assertEquals(CartFailureCategory.SERVICE, repository.state.value.failure?.category)
            assertEquals(true, repository.state.value.failure?.retryable)
            assertEquals(1, operations.restores)
            assertEquals(0, operations.clears)
            assertTrue(operations.plans.isEmpty())
        }
    }

    @Test
    fun `cancelled associated operation hides private summary without downgrading ownership`() = lifecycleTest {
        val operations = CartLifecycleOperations()
        operations.current = lifecycleActive(2, CartOwnership.CUSTOMER_ASSOCIATED)
        val repository = DefaultCartRepository(operations)
        repository.refresh()
        assertEquals(2, repository.state.value.badgeQuantity)
        operations.mutationGate = CompletableDeferred()
        val owner = launch { repository.add(LIFECYCLE_VARIANT, 1) }
        runCurrent()
        owner.cancel()
        owner.join()
        assertEquals(CartStatus.ERROR, repository.state.value.status)
        assertEquals(CartOwnership.CUSTOMER_ASSOCIATED, repository.state.value.ownership)
        assertNull(repository.state.value.cart)
        assertNull(repository.state.value.mutation)
        assertEquals(true, repository.state.value.failure?.cartRetained)
        assertEquals(0, operations.clears)
        repository.refresh()
        assertEquals(CartOwnership.CUSTOMER_ASSOCIATED, repository.state.value.ownership)
        assertEquals(2, repository.state.value.badgeQuantity)
        assertNull(repository.state.value.failure)
    }

    @Test
    fun `cancelled restricted action keeps restriction and cancellation exception semantics`() = lifecycleTest {
        val operations = CartLifecycleOperations()
        operations.current = CartSessionResolution.Restricted(CartOwnership.VERIFY_PENDING)
        val repository = DefaultCartRepository(operations)
        repository.refresh()
        operations.mutationGate = CompletableDeferred()
        var cancellation: Throwable? = null
        val owner = launch { repository.add(LIFECYCLE_VARIANT, 1) }
        owner.invokeOnCompletion { cancellation = it }
        runCurrent()
        owner.cancel(CancellationException("Product owner cleared"))
        owner.join()
        assertTrue(cancellation is CancellationException)
        assertEquals(CartStatus.RESTRICTED, repository.state.value.status)
        assertEquals(CartOwnership.VERIFY_PENDING, repository.state.value.ownership)
        assertNull(repository.state.value.cart)
        assertNull(repository.state.value.mutation)
        assertNull(repository.state.value.failure)
        assertEquals(0, operations.clears)
    }

    @Test
    fun `cancelling fallback reread publishes no confirmation or replay for ambiguous and rejected payloads`() =
        lifecycleTest {
            val errors = listOf(
                StorefrontFailure.Transport(retryable = true),
                StorefrontFailure.UserErrors(listOf(ShopifyUserError("MAXIMUM_EXCEEDED", listOf("lines"))))
            )
            for (error in errors) {
                val operations = CartLifecycleOperations()
                val repository = DefaultCartRepository(operations)
                repository.refresh()
                val mutationGate = CompletableDeferred<Unit>()
                operations.mutationGate = mutationGate
                var completed = false
                val owner = launch {
                    repository.add(LIFECYCLE_VARIANT, 1)
                    completed = true
                }
                runCurrent()
                operations.current = CartSessionResolution.Failed(error, persistedCartRetained = true)
                operations.restoreGate = CompletableDeferred()
                mutationGate.complete(Unit)
                runCurrent()
                assertEquals(2, operations.restores)
                owner.cancel()
                owner.join()
                assertFalse(completed)
                assertEquals(1, operations.plans.size)
                assertEquals(0, operations.clears)
                assertEquals(0, repository.state.value.badgeQuantity)
                assertEquals(CartStatus.ERROR, repository.state.value.status)
                assertEquals(CartOwnership.VERIFY_PENDING, repository.state.value.ownership)
                assertNull(repository.state.value.mutation)
                assertNull(repository.state.value.adjustment)
                assertEquals(CartFailureCategory.AMBIGUOUS_MUTATION, repository.state.value.failure?.category)
                assertEquals(2, operations.restores)
            }
        }

    @Test
    fun `repository propagates the same cancellation object after synchronous owned cleanup`() = lifecycleTest {
        val operations = CartLifecycleOperations()
        val repository = DefaultCartRepository(operations)
        repository.refresh()
        val original = CancellationException("synthetic remote boundary cancellation")
        operations.mutationCancellation = original
        var observed: CancellationException? = null
        try {
            repository.add(LIFECYCLE_VARIANT, 1)
        } catch (cancelled: CancellationException) {
            observed = cancelled
        }
        assertSame(original, observed)
        assertNull(repository.state.value.mutation)
        assertEquals(CartFailureCategory.AMBIGUOUS_MUTATION, repository.state.value.failure?.category)
        assertEquals(0, operations.clears)
        assertEquals(1, operations.plans.size)
    }

    private fun lifecycleTest(action: suspend TestScope.() -> Unit) = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            action()
        } finally {
            Dispatchers.resetMain()
        }
    }
}
