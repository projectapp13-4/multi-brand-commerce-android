package com.gurbakir.mobile.cart

import com.gurbakir.storefront.CartReference
import com.gurbakir.storefront.SensitiveCartLineId

enum class CartActionKind {
    ADD,
    UPDATE,
    REMOVE
}

data class CartActionAdjustment(
    val action: CartActionKind,
    val previousQuantity: Long,
    val requestedQuantity: Long,
    val observedQuantity: Long
)

internal class CartMutationIntent(
    val action: CartActionKind,
    private val target: CartMutationTarget,
    val previousQuantity: Long,
    val requestedQuantity: Long
) {
    fun isComplete(cart: CartReference): Boolean =
        if (action == CartActionKind.REMOVE) !target.existsIn(cart) else target.quantityIn(cart) == requestedQuantity

    fun adjustment(cart: CartReference): CartActionAdjustment? {
        val observed = target.quantityIn(cart)
        return if (observed != previousQuantity) {
            CartActionAdjustment(action, previousQuantity, requestedQuantity, observed)
        } else {
            null
        }
    }
}

internal sealed interface CartMutationTarget {
    fun quantityIn(cart: CartReference): Long

    fun existsIn(cart: CartReference): Boolean

    class Merchandise(private val id: String) : CartMutationTarget {
        override fun quantityIn(cart: CartReference): Long = cart.quantityOf(id)

        override fun existsIn(cart: CartReference): Boolean = cart.lines.any { it.merchandiseId == id }
    }

    class Line(private val id: SensitiveCartLineId) : CartMutationTarget {
        override fun quantityIn(cart: CartReference): Long =
            cart.lines.firstOrNull { it.id == id }?.quantity?.toLong() ?: 0L

        override fun existsIn(cart: CartReference): Boolean = cart.lines.any { it.id == id }
    }
}

internal fun CartReference.quantityOf(merchandiseId: String): Long =
    lines.filter { it.merchandiseId == merchandiseId }.sumOf { it.quantity.toLong() }
