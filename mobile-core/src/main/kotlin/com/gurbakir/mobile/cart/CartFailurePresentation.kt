package com.gurbakir.mobile.cart

import com.gurbakir.mobile.core.R

internal fun CartFailure.messageResource(): Int = if (!cartRetained && category.mayClaimRetainedCart()) {
    R.string.cart_error_not_retained
} else {
    when (category) {
        CartFailureCategory.CONNECTION -> R.string.cart_error_connection
        CartFailureCategory.CONFIGURATION -> R.string.cart_error_configuration
        CartFailureCategory.SERVICE -> R.string.cart_error_service
        CartFailureCategory.QUANTITY_OR_AVAILABILITY -> R.string.cart_error_quantity
        CartFailureCategory.SECURE_STORAGE -> R.string.cart_error_storage
        CartFailureCategory.AMBIGUOUS_MUTATION -> R.string.cart_error_ambiguous
    }
}

internal fun serviceFailure(): CartFailure =
    CartFailure(CartFailureCategory.SERVICE, retryable = true, cartRetained = true)

private fun CartFailureCategory.mayClaimRetainedCart(): Boolean = this == CartFailureCategory.CONNECTION ||
    this == CartFailureCategory.SERVICE ||
    this == CartFailureCategory.QUANTITY_OR_AVAILABILITY
