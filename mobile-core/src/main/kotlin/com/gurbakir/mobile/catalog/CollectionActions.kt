package com.gurbakir.mobile.catalog

import com.gurbakir.storefront.CollectionCatalogSort

data class CollectionActions(
    val onBack: () -> Unit,
    val onRetry: () -> Unit,
    val onSortSelected: (CollectionCatalogSort) -> Unit,
    val onProductTypeToggled: (String) -> Unit,
    val onLoadMore: () -> Unit,
    val onOpenProduct: (String) -> Unit = {},
    val onSetWishlist: ((String, Boolean) -> Unit)? = null
)
