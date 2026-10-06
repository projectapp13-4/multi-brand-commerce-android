package com.gurbakir.mobile.cart

import com.gurbakir.storefront.CartLineSummary
import com.gurbakir.storefront.CartOwnership
import com.gurbakir.storefront.CartQuantityRule
import com.gurbakir.storefront.CartReference
import com.gurbakir.storefront.CartSessionResolution
import com.gurbakir.storefront.SensitiveCartId
import com.gurbakir.storefront.SensitiveCartLineId
import com.gurbakir.storefront.SensitiveCheckoutUrl
import com.gurbakir.storefront.SensitiveCustomerId
import com.gurbakir.storefront.StorefrontMoney
import java.math.BigDecimal
import java.net.URI
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred

/** Controls only remote/storage suspension; the repository's planning and publication remain production code. */
internal class CartLifecycleOperations : CartOperations {
    var current: CartSessionResolution = lifecycleActive(2)
    var restoreGate: CompletableDeferred<Unit>? = null
    var mutationGate: CompletableDeferred<Unit>? = null
    var mutationCancellation: CancellationException? = null
    var clearGate: CompletableDeferred<Unit>? = null
    val mutationEntered = CompletableDeferred<Unit>()
    val restoreEntered = CompletableDeferred<Unit>()
    val clearEntered = CompletableDeferred<Unit>()
    val plans = mutableListOf<CartMutationPlan>()
    var restores = 0
    var clears = 0
    var cancelledMutations = 0

    override suspend fun restore(): CartSessionResolution {
        restores++
        restoreEntered.complete(Unit)
        restoreGate?.await()
        return current
    }

    override suspend fun mutate(plan: (CartSessionResolution) -> CartMutationPlan): CartMutationAttempt {
        val before = current
        val selected = plan(before)
        plans += selected
        mutationEntered.complete(Unit)
        try {
            mutationCancellation?.let { throw it }
            mutationGate?.await()
        } catch (cancelled: CancellationException) {
            cancelledMutations++
            throw cancelled
        }
        return CartMutationAttempt(before, selected, current)
    }

    override suspend fun clear(): Boolean {
        clears++
        clearEntered.complete(Unit)
        clearGate?.await()
        current = CartSessionResolution.Empty
        return true
    }
}

internal const val LIFECYCLE_VARIANT = "gid://shopify/ProductVariant/11"
internal const val LIFECYCLE_CART = "gid://shopify/Cart/lifecycle?key=synthetic-only"
internal const val LIFECYCLE_LINE = "gid://shopify/CartLine/lifecycle"
internal const val LIFECYCLE_CUSTOMER = "gid://shopify/Customer/lifecycle"

internal fun lifecycleActive(
    quantity: Int,
    ownership: CartOwnership = CartOwnership.ANONYMOUS
): CartSessionResolution.Active {
    val total = StorefrontMoney(BigDecimal.TEN.multiply(quantity.toBigDecimal()), "TRY")
    val customer = if (ownership == CartOwnership.CUSTOMER_ASSOCIATED) {
        SensitiveCustomerId.from(LIFECYCLE_CUSTOMER)
    } else {
        null
    }
    return CartSessionResolution.Active(
        CartReference(
            id = lifecycleCartId(),
            checkoutUrl = SensitiveCheckoutUrl::class.java.getDeclaredConstructor(URI::class.java).run {
                isAccessible = true
                newInstance(URI("https://gurbakir.com/cart/c/synthetic-lifecycle"))
            },
            totalQuantity = quantity,
            lines = listOf(
                CartLineSummary(
                    id = lifecycleLineId(),
                    merchandiseId = LIFECYCLE_VARIANT,
                    quantity = quantity,
                    productId = "gid://shopify/Product/1",
                    productTitle = "Synthetic product",
                    quantityRule = CartQuantityRule(1, null, 1),
                    unitPrice = StorefrontMoney(BigDecimal.TEN, "TRY"),
                    totalPrice = total
                )
            ),
            hasMoreLines = false,
            warningCodes = emptySet(),
            subtotal = total,
            total = total,
            customerId = customer
        ),
        ownership
    )
}

internal fun lifecycleCartId(): SensitiveCartId =
    SensitiveCartId::class.java.getDeclaredConstructor(String::class.java).run {
        isAccessible = true
        newInstance(LIFECYCLE_CART)
    }

internal fun lifecycleLineId(): SensitiveCartLineId =
    SensitiveCartLineId::class.java.getDeclaredConstructor(String::class.java).run {
        isAccessible = true
        newInstance(LIFECYCLE_LINE)
    }
