@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.gurbakir.mobile.cart

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import com.gurbakir.mobile.product.ProductDetailLoad
import com.gurbakir.mobile.product.ProductDetailRepository
import com.gurbakir.mobile.product.ProductDetailViewModel
import com.gurbakir.mobile.product.productFixture
import com.gurbakir.storefront.CartOwnership
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CartCancellationConnectedTest {
    @Test
    fun `actual Product clear keeps protected cart and one complete Add while retained Cart retry rereads commit`() =
        runBlocking {
            Dispatchers.setMain(UnconfinedTestDispatcher())
            val productStore = ViewModelStore()
            val cartStore = ViewModelStore()
            try {
                CartLifecycleHttpFixture().use { fixture ->
                    fixture.replyRead(2)
                    val cart = CartViewModel(fixture.repository)
                    cartStore.put("retained-cart", cart)
                    withTimeout(5000) { cart.state.first { it.status == CartStatus.ACTIVE } }
                    assertEquals("CartById", operation(fixture.takeRequest()))
                    val originalHandle = requireNotNull(fixture.store.cart).id
                    val product = ProductDetailViewModel(
                        object : ProductDetailRepository {
                            override suspend fun load(productId: String) = ProductDetailLoad.Content(productFixture())
                        },
                        fixture.repository,
                        SavedStateHandle()
                    )
                    productStore.put("product", product)
                    product.start(productFixture().id, LIFECYCLE_VARIANT)
                    fixture.replyRead(2)
                    fixture.holdMutation()
                    product.addToCart()
                    assertEquals("CartById", operation(fixture.takeRequest()))
                    val add = Json.parseToJsonElement(fixture.takeRequest()).jsonObject
                    assertEquals("CartLinesAdd", add.getValue("operationName").jsonPrimitive.content)
                    assertTrue(
                        add.getValue("variables").toString().contains("\"merchandiseId\":\"$LIFECYCLE_VARIANT\"")
                    )
                    assertTrue(add.getValue("variables").toString().contains("\"quantity\":1"))
                    assertEquals(CartMutation.ADDING, cart.state.value.mutation)
                    val originalWrites = fixture.store.writes
                    val ownerJob = product.viewModelScope.coroutineContext.job
                    productStore.clear()
                    ownerJob.join()

                    assertNull(product.state.value.cartFeedback)
                    assertNull(product.state.value.cartAdjustment)
                    assertNull(cart.state.value.mutation)
                    assertEquals(CartFailureCategory.AMBIGUOUS_MUTATION, cart.state.value.failure?.category)
                    assertEquals(2, cart.state.value.badgeQuantity)
                    assertEquals(originalHandle, fixture.store.cart?.id)
                    assertEquals(originalWrites, fixture.store.writes)
                    assertEquals(0, fixture.store.clears)
                    assertEquals(3, fixture.server.requestCount)
                    fixture.replyRead(3)
                    cart.refresh()
                    withTimeout(5000) { cart.state.first { it.cart?.totalQuantity == 3 && it.failure == null } }
                    assertEquals("CartById", operation(fixture.takeRequest()))
                    assertNull(cart.state.value.mutation)
                    assertEquals(4, fixture.server.requestCount)
                    assertEquals(originalHandle, fixture.store.cart?.id)
                    assertEquals(0, fixture.store.clears)
                }
            } finally {
                productStore.clear()
                cartStore.clear()
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `interrupted leased customer verification hides private summary and preserves exact handle`() = runBlocking {
        CartLifecycleHttpFixture(authenticated = true).use { fixture ->
            fixture.replyRead(2)
            fixture.replyBuyerUpdate(2)
            fixture.repository.refresh()
            assertEquals("CartById", operation(fixture.takeRequest()))
            assertEquals("CartBuyerIdentityUpdate", operation(fixture.takeRequest()))
            assertEquals(2, fixture.repository.state.value.badgeQuantity)
            assertEquals(CartOwnership.CUSTOMER_ASSOCIATED, fixture.repository.state.value.ownership)
            val original = fixture.store.cart
            fixture.identityGate = CompletableDeferred()
            val owner = launch { fixture.repository.add(LIFECYCLE_VARIANT, 1) }
            fixture.identityWaiting.await()
            owner.cancel()
            owner.join()
            assertEquals(CartStatus.ERROR, fixture.repository.state.value.status)
            assertEquals(CartOwnership.CUSTOMER_ASSOCIATED, fixture.repository.state.value.ownership)
            assertNull(fixture.repository.state.value.cart)
            assertNull(fixture.repository.state.value.mutation)
            assertEquals(true, fixture.repository.state.value.failure?.retryable)
            assertNotNull(fixture.store.cart)
            assertEquals(original, fixture.store.cart)
            assertEquals(0, fixture.store.clears)
            assertEquals(2, fixture.server.requestCount)
            requireNotNull(fixture.identityGate).complete(Unit)
            fixture.replyRead(2)
            fixture.replyBuyerUpdate(2)
            fixture.repository.refresh()
            assertEquals("CartById", operation(fixture.takeRequest()))
            assertEquals("CartBuyerIdentityUpdate", operation(fixture.takeRequest()))
            assertEquals(2, fixture.repository.state.value.badgeQuantity)
            assertEquals(CartOwnership.CUSTOMER_ASSOCIATED, fixture.repository.state.value.ownership)
            assertEquals(4, fixture.server.requestCount)
        }
    }

    @Test
    fun `prior anonymous cart becomes hidden when cancellation interrupts pending customer buyer rebind`() =
        runBlocking {
            CartLifecycleHttpFixture(authenticated = true, initiallyAnonymous = true).use { fixture ->
                fixture.replyRead(2)
                fixture.repository.refresh()
                assertEquals("CartById", operation(fixture.takeRequest()))
                assertEquals(CartOwnership.ANONYMOUS, fixture.repository.state.value.ownership)
                assertEquals(2, fixture.repository.state.value.badgeQuantity)
                val handle = requireNotNull(fixture.store.cart).id
                fixture.sessionAvailable = true
                fixture.replyRead(2, owned = false)
                fixture.holdMutation()
                val owner = launch { fixture.repository.add(LIFECYCLE_VARIANT, 1) }
                assertEquals("CartById", operation(fixture.takeRequest()))
                assertEquals("CartBuyerIdentityUpdate", operation(fixture.takeRequest()))
                assertEquals(CartOwnership.VERIFY_PENDING, fixture.store.cart?.ownership)
                assertEquals(LIFECYCLE_CUSTOMER, fixture.store.cart?.customerId?.use { it })
                val writes = fixture.store.writes
                owner.cancel()
                owner.join()

                assertHiddenPending(fixture)
                assertEquals(handle, fixture.store.cart?.id)
                assertEquals(writes, fixture.store.writes)
                assertEquals(0, fixture.store.clears)
                assertEquals(3, fixture.server.requestCount)
                assertVerifiedRetry(fixture, quantity = 2)
                assertEquals(5, fixture.server.requestCount)
                assertEquals(handle, fixture.store.cart?.id)
            }
        }

    @Test
    fun `fallback read reacquiring a changed customer lease cannot reuse anonymous planner proof on cancellation`() =
        runBlocking {
            CartLifecycleHttpFixture(authenticated = true, initiallyAnonymous = true).use { fixture ->
                fixture.replyRead(2)
                fixture.repository.refresh()
                assertEquals("CartById", operation(fixture.takeRequest()))
                val handle = requireNotNull(fixture.store.cart).id
                fixture.signInOnSessionRead = 3
                fixture.replyRead(2, owned = false)
                fixture.replyAmbiguousAdd()
                fixture.replyRead(2, owned = false)
                fixture.holdMutation()
                val owner = launch { fixture.repository.add(LIFECYCLE_VARIANT, 1) }
                val ledger = List(4) { operation(fixture.takeRequest()) }
                assertEquals(
                    listOf("CartById", "CartLinesAdd", "CartById", "CartBuyerIdentityUpdate"),
                    ledger
                )
                assertEquals(CartOwnership.VERIFY_PENDING, fixture.store.cart?.ownership)
                val writes = fixture.store.writes
                owner.cancel()
                owner.join()

                assertHiddenPending(fixture)
                assertEquals(handle, fixture.store.cart?.id)
                assertEquals(writes, fixture.store.writes)
                assertEquals(0, fixture.store.clears)
                assertEquals(5, fixture.server.requestCount)
                assertVerifiedRetry(fixture, quantity = 3)
                assertEquals(7, fixture.server.requestCount)
                assertEquals(handle, fixture.store.cart?.id)
            }
        }

    @Test
    fun `cancelled prepared checkout callback retains latest verified anonymous quantity rather than rolling back`() =
        runBlocking {
            CartLifecycleHttpFixture().use { fixture ->
                fixture.replyRead(2)
                fixture.repository.refresh()
                assertEquals("CartById", operation(fixture.takeRequest()))
                fixture.replyRead(3)
                val entered = CompletableDeferred<Unit>()
                val gate = CompletableDeferred<Unit>()
                val owner = launch {
                    fixture.repository.withPreparedCheckout { prepared ->
                        assertTrue(prepared is CartCheckoutResolution.Eligible)
                        entered.complete(Unit)
                        gate.await()
                    }
                }
                entered.await()
                assertEquals("CartById", operation(fixture.takeRequest()))
                assertEquals(3, fixture.repository.state.value.badgeQuantity)
                owner.cancel()
                owner.join()
                assertEquals(3, fixture.repository.state.value.badgeQuantity)
                assertEquals(CartOwnership.ANONYMOUS, fixture.repository.state.value.ownership)
                assertNull(fixture.repository.state.value.mutation)
                assertEquals(CartFailureCategory.SERVICE, fixture.repository.state.value.failure?.category)
                assertEquals(2, fixture.server.requestCount)
                assertEquals(0, fixture.store.clears)
            }
        }

    private fun assertHiddenPending(fixture: CartLifecycleHttpFixture) {
        val state = fixture.repository.state.value
        assertEquals(CartStatus.ERROR, state.status)
        assertEquals(CartOwnership.VERIFY_PENDING, state.ownership)
        assertNotEquals(CartOwnership.ANONYMOUS, state.ownership)
        assertNull(state.cart)
        assertEquals(0, state.badgeQuantity)
        assertNull(state.mutation)
        assertEquals(true, state.failure?.retryable)
        assertEquals(true, state.failure?.cartRetained)
        assertEquals(CartOwnership.VERIFY_PENDING, fixture.store.cart?.ownership)
        assertEquals(LIFECYCLE_CUSTOMER, fixture.store.cart?.customerId?.use { it })
    }

    private suspend fun assertVerifiedRetry(fixture: CartLifecycleHttpFixture, quantity: Int) {
        fixture.replyRead(quantity, owned = true)
        fixture.replyBuyerUpdate(quantity)
        fixture.repository.refresh()
        assertEquals("CartById", operation(fixture.takeRequest()))
        assertEquals("CartBuyerIdentityUpdate", operation(fixture.takeRequest()))
        assertEquals(quantity, fixture.repository.state.value.badgeQuantity)
        assertEquals(CartOwnership.CUSTOMER_ASSOCIATED, fixture.repository.state.value.ownership)
        assertEquals(CartOwnership.CUSTOMER_ASSOCIATED, fixture.store.cart?.ownership)
        assertEquals(LIFECYCLE_CUSTOMER, fixture.store.cart?.customerId?.use { it })
        assertNull(fixture.repository.state.value.failure)
    }

    private fun operation(body: String): String =
        Json.parseToJsonElement(body).jsonObject.getValue("operationName").jsonPrimitive.content
}
