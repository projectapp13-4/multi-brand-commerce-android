package com.gurbakir.mobile.wishlist

data class WishlistActions(
    val onBrowse: () -> Unit,
    val onRetry: () -> Unit,
    val onOpenProduct: (String) -> Unit,
    val onRemove: (String) -> Unit,
    val onClear: () -> Unit
)
