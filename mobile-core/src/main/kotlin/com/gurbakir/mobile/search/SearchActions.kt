package com.gurbakir.mobile.search

data class SearchActions(
    val onQueryChanged: (String) -> Unit,
    val onSubmit: () -> Unit,
    val onRetry: () -> Unit,
    val onLoadMore: () -> Unit,
    val onSelectHistory: (String) -> Unit,
    val onRemoveHistory: (String) -> Unit,
    val onClearHistory: () -> Unit,
    val onHistoryEnabledChanged: (Boolean) -> Unit,
    val onOpenProduct: (String) -> Unit = {},
    val onSetWishlist: ((String, Boolean) -> Unit)? = null
)
