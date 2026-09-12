package com.gurbakir.mobile.checkout

enum class CheckoutStatus {
    IDLE,
    PREPARING,
    PRESENTING,
    IN_PROGRESS,
    CANCELLED,
    COMPLETED,
    COMPLETED_CURRENT_CART_PRESERVED,
    CLEANUP_REQUIRED,
    EXTERNAL_LINK_BLOCKED,
    FAILED
}

enum class CheckoutFailureCategory {
    CART_EMPTY,
    CART_RESTRICTED,
    CART_UNAVAILABLE,
    CONNECTION,
    CONFIGURATION,
    SERVICE,
    SECURE_STORAGE,
    INVALID_URL,
    SDK_UNAVAILABLE,
    NETWORK,
    EXPIRED,
    RECOVERABLE,
    FATAL
}

data class CheckoutFailure(val category: CheckoutFailureCategory, val retryable: Boolean, val cartRetained: Boolean)

data class CheckoutState(
    val status: CheckoutStatus = CheckoutStatus.IDLE,
    val failure: CheckoutFailure? = null,
    val cartRetained: Boolean = true
) {
    val busy: Boolean
        get() =
            status == CheckoutStatus.PREPARING || status == CheckoutStatus.PRESENTING ||
                status == CheckoutStatus.IN_PROGRESS || status == CheckoutStatus.EXTERNAL_LINK_BLOCKED
}
