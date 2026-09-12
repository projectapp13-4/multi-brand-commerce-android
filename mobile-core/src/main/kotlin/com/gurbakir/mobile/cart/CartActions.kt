package com.gurbakir.mobile.cart

import com.gurbakir.storefront.SensitiveCartLineId

data class CartActions(
    val onBack: () -> Unit,
    val onBrowse: () -> Unit,
    val onRetry: () -> Unit,
    val onOpenProduct: (String) -> Unit,
    val onIncrease: (CartLine) -> Unit,
    val onDecrease: (CartLine) -> Unit,
    val onRemove: (SensitiveCartLineId) -> Unit,
    val onDiscard: () -> Unit,
    val onCheckout: () -> Unit
)
