package com.gurbakir.mobile.cart

internal object CartTestTags {
    const val ROOT = "cart-root"
    const val LOADING = "cart-loading"
    const val LOADING_STATE = "cart-loading-state"
    const val EMPTY = "cart-empty"
    const val ERROR = "cart-error"
    const val EXPIRED = "cart-expired"
    const val RESTRICTED = "cart-restricted"
    const val BROWSE = "cart-browse"
    const val RETRY = "cart-retry"
    const val OWNERSHIP = "cart-ownership"
    const val TOTAL = "cart-total"
    const val DISCARD = "cart-discard"
    const val DISCARD_CONFIRM = "cart-discard-confirm"
    const val CHECKOUT = "cart-checkout"
    const val CHECKOUT_FEEDBACK = "cart-checkout-feedback"
    const val CHECKOUT_FEEDBACK_ACTION = "cart-checkout-feedback-action"

    fun line(productId: String): String = "cart-line-${productId.hashCode().toUInt()}"

    fun increase(productId: String): String = "cart-increase-${productId.hashCode().toUInt()}"

    fun decrease(productId: String): String = "cart-decrease-${productId.hashCode().toUInt()}"

    fun remove(productId: String): String = "cart-remove-${productId.hashCode().toUInt()}"
}
