@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@file:Suppress("FunctionNaming", "MagicNumber")

package com.gurbakir.mobile.catalog

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.gurbakir.foundation.ui.LocalBrandSpacing
import com.gurbakir.mobile.core.R
import com.gurbakir.mobile.ui.DestinationLevel
import com.gurbakir.mobile.ui.DestinationScaffold
import com.gurbakir.mobile.ui.DestinationTitleAlignment
import com.gurbakir.mobile.ui.consumeDestinationInsets
import com.gurbakir.mobile.ui.withDestinationSpacing
import com.gurbakir.mobile.wishlist.WishlistMembershipUiState
import com.gurbakir.mobile.wishlist.WishlistProductAction
import com.gurbakir.mobile.wishlist.productAction
import com.gurbakir.storefront.CollectionCatalogSort

@Composable
fun CollectionScreen(
    state: CollectionUiState,
    actions: CollectionActions,
    wishlist: WishlistMembershipUiState? = null
) {
    DestinationScaffold(
        title = state.title ?: stringResource(R.string.collection_title_loading),
        level = DestinationLevel.SECONDARY,
        modifier = Modifier.testTag(CatalogTestTags.COLLECTION_ROOT),
        titleAlignment = DestinationTitleAlignment.CENTER,
        onNavigateUp = actions.onBack
    ) { padding ->
        CollectionGrid(state = state, contentPadding = padding, actions = actions, wishlist = wishlist)
    }
}

@Composable
private fun CollectionGrid(
    state: CollectionUiState,
    contentPadding: PaddingValues,
    actions: CollectionActions,
    wishlist: WishlistMembershipUiState?
) {
    val spacing = LocalBrandSpacing.current
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 160.dp),
        modifier =
            Modifier.fillMaxSize()
                .consumeDestinationInsets(contentPadding)
                .testTag(CatalogTestTags.COLLECTION_GRID),
        contentPadding = contentPadding.withDestinationSpacing(),
        horizontalArrangement = Arrangement.spacedBy(spacing.normalDp.dp),
        verticalArrangement = Arrangement.spacedBy(spacing.generousDp.dp)
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            CollectionControls(state = state, actions = actions)
        }
        collectionStateItems(state, actions, wishlist, spacing.normalDp.dp)
    }
}

private fun LazyGridScope.collectionStateItems(
    state: CollectionUiState,
    actions: CollectionActions,
    wishlist: WishlistMembershipUiState?,
    itemSpacing: Dp
) {
    when {
        state.loadingInitial -> item(span = { GridItemSpan(maxLineSpan) }) {
            LinearProgressIndicator(Modifier.fillMaxWidth().testTag(CatalogTestTags.COLLECTION_LOADING))
        }

        state.notFound -> item(span = { GridItemSpan(maxLineSpan) }) {
            Text(
                stringResource(R.string.collection_not_found),
                Modifier.testTag(CatalogTestTags.COLLECTION_NOT_FOUND)
            )
        }

        state.initialFailure != null -> item(span = { GridItemSpan(maxLineSpan) }) {
            CatalogError(state.initialFailure, actions.onRetry, CatalogTestTags.COLLECTION_ERROR)
        }

        state.isEmpty -> item(span = { GridItemSpan(maxLineSpan) }) {
            Text(
                text =
                    if (state.selectedProductTypes.isEmpty()) {
                        stringResource(R.string.collection_empty)
                    } else {
                        stringResource(R.string.collection_filtered_empty)
                    },
                modifier = Modifier.testTag(CatalogTestTags.COLLECTION_EMPTY)
            )
        }

        else ->
            loadedProductItems(
                state,
                actions,
                wishlist,
                itemSpacing
            )
    }
}

private fun LazyGridScope.loadedProductItems(
    state: CollectionUiState,
    actions: CollectionActions,
    wishlist: WishlistMembershipUiState?,
    itemSpacing: Dp
) {
    items(state.products, key = { it.id }) { product ->
        CatalogProductCard(
            product,
            onClick = { actions.onOpenProduct(product.id) },
            wishlist =
                wishlist.productAction(product.id, actions.onSetWishlist)
        )
    }
    item(span = { GridItemSpan(maxLineSpan) }) {
        Column(verticalArrangement = Arrangement.spacedBy(itemSpacing)) {
            when {
                state.loadingNext ->
                    LinearProgressIndicator(
                        Modifier.fillMaxWidth().testTag(CatalogTestTags.COLLECTION_NEXT_LOADING)
                    )

                state.nextPageFailure != null ->
                    CatalogError(state.nextPageFailure, actions.onLoadMore, CatalogTestTags.COLLECTION_NEXT_ERROR)

                state.hasNextPage ->
                    Button(
                        onClick = actions.onLoadMore,
                        modifier = Modifier.fillMaxWidth().testTag(CatalogTestTags.COLLECTION_LOAD_MORE)
                    ) {
                        Text(stringResource(R.string.collection_load_more))
                    }
            }
        }
    }
}

@Composable
private fun CollectionControls(state: CollectionUiState, actions: CollectionActions) {
    val spacing = LocalBrandSpacing.current
    var sortExpanded by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(spacing.normalDp.dp)
    ) {
        Text(
            text = stringResource(R.string.collection_loaded_count, state.products.size),
            style = MaterialTheme.typography.bodyMedium,
            modifier =
                Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                    .testTag(CatalogTestTags.COLLECTION_RESULT_COUNT)
        )
        Box {
            TextButton(
                onClick = { sortExpanded = true },
                modifier = Modifier.testTag(CatalogTestTags.COLLECTION_SORT)
            ) {
                Text(stringResource(R.string.collection_sort_label, stringResource(state.sort.labelResourceId())))
            }
            DropdownMenu(expanded = sortExpanded, onDismissRequest = { sortExpanded = false }) {
                CollectionCatalogSort.entries.forEach { sort ->
                    DropdownMenuItem(
                        text = { Text(stringResource(sort.labelResourceId())) },
                        onClick = {
                            sortExpanded = false
                            actions.onSortSelected(sort)
                        }
                    )
                }
            }
        }
        state.productTypeFilter?.let { filter ->
            Text(filter.label, style = MaterialTheme.typography.titleSmall)
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(spacing.compactDp.dp)
            ) {
                filter.values.forEach { value ->
                    FilterChip(
                        selected = value.value in state.selectedProductTypes,
                        onClick = { actions.onProductTypeToggled(value.value) },
                        label = {
                            Text(
                                stringResource(
                                    R.string.collection_filter_value,
                                    value.label,
                                    value.count
                                )
                            )
                        },
                        modifier = Modifier.testTag(CatalogTestTags.filter(value.value))
                    )
                }
            }
        }
    }
}

private fun CollectionCatalogSort.labelResourceId(): Int = when (this) {
    CollectionCatalogSort.COLLECTION_DEFAULT -> R.string.collection_sort_featured
    CollectionCatalogSort.BEST_SELLING -> R.string.collection_sort_best_selling
    CollectionCatalogSort.NEWEST -> R.string.collection_sort_newest
    CollectionCatalogSort.PRICE_LOW_TO_HIGH -> R.string.collection_sort_price_low_high
    CollectionCatalogSort.PRICE_HIGH_TO_LOW -> R.string.collection_sort_price_high_low
    CollectionCatalogSort.TITLE_A_TO_Z -> R.string.collection_sort_title_a_z
    CollectionCatalogSort.TITLE_Z_TO_A -> R.string.collection_sort_title_z_a
}
