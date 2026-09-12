package com.gurbakir.mobile.cart

import com.gurbakir.storefront.CartLineInput
import com.gurbakir.storefront.CartLineSummary
import com.gurbakir.storefront.CartLineUpdate
import com.gurbakir.storefront.CartOwnership
import com.gurbakir.storefront.CartQuantityRule
import com.gurbakir.storefront.CartReference
import com.gurbakir.storefront.CartSessionResolution
import com.gurbakir.storefront.SensitiveCartId
import com.gurbakir.storefront.SensitiveCartLineId
import com.gurbakir.storefront.SensitiveCheckoutUrl
import com.gurbakir.storefront.StorefrontFailure
import com.gurbakir.storefront.StorefrontMoney
import java.math.BigDecimal
import java.net.URI
import java.util.ArrayDeque
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class CartRepositoryTest {
    @Test
    fun `first add creates a remote cart and publishes only presentation state`() = runTest {
        val operations =
            FakeCartOperations(
                restores = ArrayDeque(listOf(CartSessionResolution.Empty)),
                createResult = active(cart(quantity = 1))
            )
        val repository = DefaultCartRepository(operations)

        assertEquals(CartActionResult.Completed, repository.add(VARIANT_ID, 1))

        assertEquals(1, operations.createCalls)
        assertEquals(CartStatus.ACTIVE, repository.state.value.status)
        assertEquals("Product", repository.state.value.cart?.lines?.single()?.productTitle)
        assertEquals(1, repository.state.value.badgeQuantity)
        assertNull(repository.state.value.failure)
    }

    @Test
    fun `ambiguous add rereads before retry and accepts a confirmed server mutation`() = runTest {
        val before = cart(quantity = 1)
        val after = cart(quantity = 2)
        val operations =
            FakeCartOperations(
                restores = ArrayDeque(listOf(active(before), active(after))),
                addResult =
                    CartSessionResolution.Failed(
                        StorefrontFailure.Transport(retryable = true),
                        persistedCartRetained = true
                    )
            )
        val repository = DefaultCartRepository(operations)

        assertEquals(CartActionResult.Completed, repository.add(VARIANT_ID, 1))

        assertEquals(1, operations.addCalls)
        assertEquals(1, operations.mutateCalls)
        assertEquals(1, operations.restoreCalls)
        assertEquals(2, repository.state.value.badgeQuantity)
        assertNull(repository.state.value.failure)
    }

    @Test
    fun `unconfirmed ambiguous add is not replayed and remains explicit`() = runTest {
        val unchanged = cart(quantity = 1)
        val operations =
            FakeCartOperations(
                restores = ArrayDeque(listOf(active(unchanged), active(unchanged))),
                addResult =
                    CartSessionResolution.Failed(
                        StorefrontFailure.GraphQl(setOf("UNCLASSIFIED")),
                        persistedCartRetained = true
                    )
            )
        val repository = DefaultCartRepository(operations)

        val result = repository.add(VARIANT_ID, 1)

        assertInstanceOf(CartActionResult.Failed::class.java, result)
        assertEquals(1, operations.addCalls)
        assertEquals(1, operations.mutateCalls)
        assertEquals(1, operations.restoreCalls)
        assertEquals(CartFailureCategory.AMBIGUOUS_MUTATION, repository.state.value.failure?.category)
        assertEquals(1, repository.state.value.badgeQuantity)
    }

    @Test
    fun `server quantity rule blocks an invalid local update without a mutation`() = runTest {
        val cart = cart(quantity = 2, rule = CartQuantityRule(minimum = 2, maximum = 6, increment = 2))
        val operations = FakeCartOperations(restores = ArrayDeque(listOf(active(cart))))
        val repository = DefaultCartRepository(operations)

        val result = repository.update(cart.lines.single().id, quantity = 3)

        assertInstanceOf(CartActionResult.Failed::class.java, result)
        assertEquals(0, operations.updateCalls)
        assertEquals(CartFailureCategory.QUANTITY_OR_AVAILABILITY, repository.state.value.failure?.category)
    }

    @Test
    fun `quarantined ownership never publishes cart lines`() = runTest {
        val operations =
            FakeCartOperations(
                restores =
                    ArrayDeque(
                        listOf(CartSessionResolution.Restricted(CartOwnership.QUARANTINED))
                    )
            )
        val repository = DefaultCartRepository(operations)

        repository.refresh()

        assertEquals(CartStatus.RESTRICTED, repository.state.value.status)
        assertEquals(CartOwnership.QUARANTINED, repository.state.value.ownership)
        assertNull(repository.state.value.cart)
    }

    @Test
    fun `incomplete active snapshot fails instead of reporting a completed action`() = runTest {
        val operations =
            FakeCartOperations(
                restores = ArrayDeque(listOf(CartSessionResolution.Empty)),
                createResult = active(cart(quantity = 1).copy(subtotal = null))
            )
        val repository = DefaultCartRepository(operations)

        val result = repository.add(VARIANT_ID, 1)

        assertInstanceOf(CartActionResult.Failed::class.java, result)
        assertEquals(CartStatus.ERROR, repository.state.value.status)
        assertEquals(CartFailureCategory.SERVICE, repository.state.value.failure?.category)
    }

    @Test
    fun `checkout preparation refreshes once and returns only protected launch values`() = runTest {
        val current = cart(quantity = 1)
        val operations = FakeCartOperations(restores = ArrayDeque(listOf(active(current))))
        val repository = DefaultCartRepository(operations)

        val result = repository.prepareCheckout()

        assertInstanceOf(CartCheckoutResolution.Eligible::class.java, result)
        assertEquals(1, operations.restoreCalls)
        assertEquals(CartStatus.ACTIVE, repository.state.value.status)
        assertEquals(
            "Eligible(cartId=<redacted>, checkoutUrl=<redacted>)",
            result.toString()
        )
    }

    @Test
    fun `checkout preparation rejects a refreshed unavailable line without clearing cart`() = runTest {
        val current = cart(quantity = 1)
        val unavailable = current.copy(lines = listOf(current.lines.single().copy(availableForSale = false)))
        val operations = FakeCartOperations(restores = ArrayDeque(listOf(active(unavailable))))
        val repository = DefaultCartRepository(operations)

        val result = repository.prepareCheckout()

        assertEquals(CartCheckoutResolution.Unavailable, result)
        assertEquals(CartStatus.ACTIVE, repository.state.value.status)
        assertEquals(1, repository.state.value.badgeQuantity)
    }

    private fun active(cart: CartReference): CartSessionResolution.Active =
        CartSessionResolution.Active(cart, CartOwnership.ANONYMOUS)

    private fun cart(quantity: Int, rule: CartQuantityRule = CartQuantityRule(1, null, 1)): CartReference {
        val unit = StorefrontMoney(BigDecimal("10.00"), "TRY")
        return CartReference(
            id = syntheticCartId("gid://shopify/Cart/test?key=synthetic-secret"),
            checkoutUrl = syntheticCheckoutUrl(URI("https://shop.example/cart/c/synthetic")),
            totalQuantity = quantity,
            lines =
                listOf(
                    CartLineSummary(
                        id = syntheticLineId("gid://shopify/CartLine/test"),
                        merchandiseId = VARIANT_ID,
                        quantity = quantity,
                        productId = PRODUCT_ID,
                        productTitle = "Product",
                        variantTitle = "Variant",
                        quantityRule = rule,
                        unitPrice = unit,
                        totalPrice = StorefrontMoney(BigDecimal.TEN.multiply(quantity.toBigDecimal()), "TRY")
                    )
                ),
            hasMoreLines = false,
            warningCodes = emptySet(),
            subtotal = StorefrontMoney(BigDecimal.TEN.multiply(quantity.toBigDecimal()), "TRY"),
            total = StorefrontMoney(BigDecimal.TEN.multiply(quantity.toBigDecimal()), "TRY")
        )
    }

    private companion object {
        const val PRODUCT_ID = "gid://shopify/Product/1"
        const val VARIANT_ID = "gid://shopify/ProductVariant/1"
    }
}

private fun syntheticCartId(value: String): SensitiveCartId =
    SensitiveCartId::class.java.getDeclaredConstructor(String::class.java).run {
        isAccessible = true
        newInstance(value)
    }

private fun syntheticLineId(value: String): SensitiveCartLineId =
    SensitiveCartLineId::class.java.getDeclaredConstructor(String::class.java).run {
        isAccessible = true
        newInstance(value)
    }

private fun syntheticCheckoutUrl(value: URI): SensitiveCheckoutUrl =
    SensitiveCheckoutUrl::class.java.getDeclaredConstructor(URI::class.java).run {
        isAccessible = true
        newInstance(value)
    }

private class FakeCartOperations(
    private val restores: ArrayDeque<CartSessionResolution> = ArrayDeque(),
    private val createResult: CartSessionResolution = CartSessionResolution.Empty,
    private val addResult: CartSessionResolution = CartSessionResolution.Empty,
    private val updateResult: CartSessionResolution = CartSessionResolution.Empty,
    private val removeResult: CartSessionResolution = CartSessionResolution.Empty,
    private val clearResult: Boolean = true
) : CartOperations {
    var restoreCalls = 0
    var mutateCalls = 0
    var createCalls = 0
    var addCalls = 0
    var updateCalls = 0

    override suspend fun restore(): CartSessionResolution {
        restoreCalls += 1
        return restores.removeFirst()
    }

    override suspend fun mutate(plan: (CartSessionResolution) -> CartMutationPlan): CartMutationAttempt {
        mutateCalls += 1
        val before = restores.removeFirst()
        val selected = plan(before)
        val result = when (selected) {
            CartMutationPlan.None,
            CartMutationPlan.Invalid -> null

            is CartMutationPlan.Create -> {
                createCalls += 1
                createResult
            }

            is CartMutationPlan.Add -> {
                addCalls += 1
                addResult
            }

            is CartMutationPlan.Update -> {
                updateCalls += 1
                updateResult
            }

            is CartMutationPlan.Remove -> removeResult
        }
        return CartMutationAttempt(before, selected, result)
    }

    override suspend fun clear(): Boolean = clearResult
}
