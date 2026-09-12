package com.gurbakir.mobile.product

internal object ProductDetailTestTags {
    const val ROOT = "product-detail-root"
    const val CONTENT = "product-detail-content"
    const val LOADING = "product-detail-loading"
    const val ERROR = "product-detail-error"
    const val NOT_FOUND = "product-detail-not-found"
    const val TITLE = "product-detail-title"
    const val PRICE = "product-detail-price"
    const val AVAILABILITY = "product-detail-availability"
    const val INVALID_VARIANT = "product-detail-invalid-variant"
    const val MEDIA = "product-detail-media"
    const val MEDIA_OPEN = "product-detail-media-open"
    const val MEDIA_VIEWER = "product-detail-media-viewer"
    const val MEDIA_CLOSE = "product-detail-media-close"
    const val MEDIA_PREVIOUS = "product-detail-media-previous"
    const val MEDIA_NEXT = "product-detail-media-next"
    const val DESCRIPTION = "product-detail-description"
    const val PURCHASE_BAR = "product-detail-purchase-bar"
    const val PURCHASE_PRICE = "product-detail-purchase-price"
    const val PURCHASE_AVAILABILITY = "product-detail-purchase-availability"
    const val ADD_TO_CART = "product-detail-add-to-cart"
    const val CART_FEEDBACK = "product-detail-cart-feedback"
    const val OPEN_CART = "product-detail-open-cart"

    fun option(name: String, value: String): String =
        "product-option-${name.hashCode().toUInt()}-${value.hashCode().toUInt()}"
}
