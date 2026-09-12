package com.gurbakir.mobile.product

data class ProductDetailActions(
    val onBack: () -> Unit,
    val onBrowse: () -> Unit,
    val onRetry: () -> Unit,
    val onSelectOption: (String, String) -> Unit,
    val onSelectMedia: (Int) -> Unit,
    val onOpenMediaViewer: () -> Unit,
    val onCloseMediaViewer: () -> Unit,
    val onAddToCart: () -> Unit = {},
    val onOpenCart: () -> Unit = {},
    val onSetWishlist: ((String, Boolean) -> Unit)? = null
)
