package com.gurbakir.mobile.checkout

import com.gurbakir.checkout.CheckoutFailure as SdkCheckoutFailure
import com.gurbakir.mobile.cart.CartFailure
import com.gurbakir.mobile.cart.CartFailureCategory
import com.gurbakir.mobile.cart.CartRepository
import com.gurbakir.mobile.cart.CartStatus

internal fun CartRepository.hasProtectedCart(): Boolean =
    state.value.status == CartStatus.ACTIVE || state.value.status == CartStatus.RESTRICTED ||
        state.value.failure?.cartRetained == true

internal fun CartFailure.toCheckoutFailure(): CheckoutFailure = CheckoutFailure(
    category =
        when (category) {
            CartFailureCategory.CONNECTION -> CheckoutFailureCategory.CONNECTION

            CartFailureCategory.CONFIGURATION -> CheckoutFailureCategory.CONFIGURATION

            CartFailureCategory.SERVICE -> CheckoutFailureCategory.SERVICE

            CartFailureCategory.SECURE_STORAGE -> CheckoutFailureCategory.SECURE_STORAGE

            CartFailureCategory.QUANTITY_OR_AVAILABILITY,
            CartFailureCategory.AMBIGUOUS_MUTATION -> CheckoutFailureCategory.CART_UNAVAILABLE
        },
    retryable = retryable,
    cartRetained = cartRetained
)

internal fun SdkCheckoutFailure.toProjectFailure(retained: Boolean): CheckoutFailure =
    CheckoutFailure(toFailureCategory(), toFailureCategory().isRetryable(), retained)

internal fun SdkCheckoutFailure.toFailureCategory(): CheckoutFailureCategory = when (this) {
    SdkCheckoutFailure.INVALID_CHECKOUT_URL -> CheckoutFailureCategory.INVALID_URL
    SdkCheckoutFailure.SDK_UNAVAILABLE -> CheckoutFailureCategory.SDK_UNAVAILABLE
    SdkCheckoutFailure.NETWORK -> CheckoutFailureCategory.NETWORK
    SdkCheckoutFailure.EXPIRED_OR_COMPLETED_CART -> CheckoutFailureCategory.EXPIRED
    SdkCheckoutFailure.CONFIGURATION -> CheckoutFailureCategory.CONFIGURATION
    SdkCheckoutFailure.RECOVERABLE -> CheckoutFailureCategory.RECOVERABLE
    SdkCheckoutFailure.FATAL -> CheckoutFailureCategory.FATAL
}

internal fun CheckoutFailureCategory.isRetryable(): Boolean =
    this == CheckoutFailureCategory.CONNECTION || this == CheckoutFailureCategory.SERVICE ||
        this == CheckoutFailureCategory.SECURE_STORAGE || this == CheckoutFailureCategory.SDK_UNAVAILABLE ||
        this == CheckoutFailureCategory.NETWORK || this == CheckoutFailureCategory.EXPIRED ||
        this == CheckoutFailureCategory.RECOVERABLE
