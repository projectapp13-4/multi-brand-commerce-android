@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@file:Suppress("FunctionNaming")

package com.gurbakir.mobile.wishlist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.gurbakir.foundation.ui.LocalBrandSpacing
import com.gurbakir.mobile.catalog.CatalogProductCard
import com.gurbakir.mobile.core.R
import com.gurbakir.mobile.ui.CommerceStatePanel
import com.gurbakir.mobile.ui.DestinationLevel
import com.gurbakir.mobile.ui.DestinationScaffold
import com.gurbakir.mobile.ui.DestinationTitleAlignment
import com.gurbakir.mobile.ui.centeredDestinationContent
import com.gurbakir.mobile.ui.consumeDestinationInsets
import com.gurbakir.mobile.ui.withDestinationSpacing
import com.gurbakir.storefront.CatalogProductSummary
import com.gurbakir.storefront.StorefrontProductDetail

@Composable
fun WishlistScreen(state: WishlistUiState, actions: WishlistActions) {
    var confirmClear by remember { mutableStateOf(false) }
    DestinationScaffold(
        title = stringResource(R.string.wishlist_title),
        level = DestinationLevel.PRIMARY,
        modifier = Modifier.fillMaxSize().testTag(WishlistTestTags.ROOT),
        titleAlignment = DestinationTitleAlignment.CENTER
    ) { padding ->
        WishlistBody(state, actions, { confirmClear = true }, padding)
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.wishlist_clear_title)) },
            text = { Text(stringResource(R.string.wishlist_clear_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmClear = false
                        actions.onClear()
                    },
                    modifier = Modifier.testTag(WishlistTestTags.CLEAR_CONFIRM)
                ) {
                    Text(stringResource(R.string.wishlist_clear))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) {
                    Text(stringResource(R.string.wishlist_clear_cancel))
                }
            }
        )
    }
}

@Composable
private fun WishlistBody(
    state: WishlistUiState,
    actions: WishlistActions,
    onRequestClear: () -> Unit,
    padding: PaddingValues
) {
    val spacing = LocalBrandSpacing.current
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = SAVED_CARD_MINIMUM_WIDTH),
        modifier =
            Modifier.fillMaxSize()
                .centeredDestinationContent(720.dp)
                .consumeDestinationInsets(padding)
                .testTag(WishlistTestTags.CONTENT),
        contentPadding = padding.withDestinationSpacing(),
        horizontalArrangement = Arrangement.spacedBy(spacing.normalDp.dp),
        verticalArrangement = Arrangement.spacedBy(spacing.generousDp.dp)
    ) {
        when {
            state.loading -> item(span = { GridItemSpan(maxLineSpan) }) { WishlistLoading() }

            !state.storageAvailable -> item(span = { GridItemSpan(maxLineSpan) }) {
                WishlistStorageError(actions.onRetry)
            }

            state.entries.isEmpty() -> item(span = { GridItemSpan(maxLineSpan) }) {
                WishlistEmpty(actions.onBrowse)
            }

            else -> {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    WishlistSummary(state.entries.size)
                }
                if (state.hasRetryableItems) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        WishlistPartialError(actions.onRetry)
                    }
                }
                items(
                    items = state.entries,
                    key = WishlistResolvedEntry::productId,
                    span = { entry ->
                        GridItemSpan(if (entry.product == null) maxLineSpan else 1)
                    }
                ) { entry ->
                    WishlistEntry(entry, actions, state.mutating)
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(
                            onClick = onRequestClear,
                            enabled = !state.mutating,
                            modifier = Modifier.testTag(WishlistTestTags.CLEAR)
                        ) {
                            Text(stringResource(R.string.wishlist_clear))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WishlistSummary(productCount: Int) {
    val spacing = LocalBrandSpacing.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(spacing.normalDp.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = pluralStringResource(R.plurals.wishlist_product_count, productCount, productCount),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = stringResource(R.string.wishlist_device_only),
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.End,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun WishlistEmpty(onBrowse: () -> Unit) {
    val spacing = LocalBrandSpacing.current
    Column(
        modifier =
            Modifier.fillMaxWidth()
                .heightIn(min = EMPTY_STATE_MINIMUM_HEIGHT)
                .padding(horizontal = spacing.sectionDp.dp, vertical = spacing.generousDp.dp)
                .testTag(WishlistTestTags.EMPTY),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(spacing.normalDp.dp, Alignment.CenterVertically)
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.size(EMPTY_ICON_CONTAINER_SIZE)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    painter = painterResource(R.drawable.ic_nav_wishlist),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(EMPTY_ICON_SIZE)
                )
            }
        }
        Text(
            text = stringResource(R.string.wishlist_empty),
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() }
        )
        Text(
            text = stringResource(R.string.wishlist_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Button(
            onClick = onBrowse,
            modifier = Modifier.testTag(WishlistTestTags.BROWSE)
        ) {
            Text(stringResource(R.string.wishlist_browse))
        }
        Text(
            text = stringResource(R.string.wishlist_device_only),
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun WishlistStorageError(onRetry: () -> Unit) {
    CommerceStatePanel(
        title = stringResource(R.string.wishlist_storage_title),
        body = stringResource(R.string.wishlist_storage_unavailable),
        icon = painterResource(R.drawable.ic_nav_wishlist),
        primaryActionLabel = stringResource(R.string.retry),
        onPrimaryAction = onRetry,
        primaryActionTestTag = WishlistTestTags.RETRY,
        testTag = WishlistTestTags.STORAGE_ERROR
    )
}

@Composable
private fun WishlistLoading() {
    CommerceStatePanel(
        title = stringResource(R.string.wishlist_loading_title),
        body = stringResource(R.string.wishlist_loading_body),
        icon = painterResource(R.drawable.ic_nav_wishlist),
        testTag = WishlistTestTags.LOADING
    )
}

@Composable
private fun WishlistPartialError(onRetry: () -> Unit) {
    CommerceStatePanel(
        title = stringResource(R.string.wishlist_partial_title),
        body = stringResource(R.string.wishlist_partial_body),
        primaryActionLabel = stringResource(R.string.wishlist_retry_products),
        onPrimaryAction = onRetry,
        primaryActionTestTag = WishlistTestTags.RETRY,
        testTag = WishlistTestTags.PARTIAL_ERROR
    )
}

@Composable
private fun WishlistEntry(entry: WishlistResolvedEntry, actions: WishlistActions, mutating: Boolean) {
    val product = entry.product
    if (product != null) {
        CatalogProductCard(
            product = product.toCatalogSummary(),
            onClick = { actions.onOpenProduct(entry.productId) },
            wishlist =
                WishlistProductAction(
                    saved = true,
                    updating = mutating,
                    onSetSaved = { actions.onRemove(entry.productId) }
                )
        )
    } else {
        Surface(
            modifier =
                Modifier.fillMaxWidth()
                    .testTag(WishlistTestTags.item(entry.productId)),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = MaterialTheme.shapes.large
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(LocalBrandSpacing.current.sectionDp.dp),
                verticalArrangement = Arrangement.spacedBy(LocalBrandSpacing.current.normalDp.dp)
            ) {
                Text(
                    stringResource(entry.issue.messageResourceId()),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.semantics { heading() }
                )
                TextButton(onClick = { actions.onRemove(entry.productId) }, enabled = !mutating) {
                    Text(stringResource(R.string.wishlist_remove))
                }
            }
        }
    }
}

private fun StorefrontProductDetail.toCatalogSummary(): CatalogProductSummary {
    val minimum = variants.minBy { it.price.amount }.price
    val maximum = variants.maxBy { it.price.amount }.price
    return CatalogProductSummary(
        id = id,
        handle = handle,
        title = title,
        availableForSale = availableForSale,
        media = media.firstOrNull()?.image ?: variants.firstNotNullOfOrNull { it.image },
        minimumPrice = minimum,
        maximumPrice = maximum
    )
}

private fun WishlistItemIssue?.messageResourceId(): Int = when (this) {
    WishlistItemIssue.REMOVED -> R.string.wishlist_product_removed
    WishlistItemIssue.CONNECTION -> R.string.wishlist_product_connection
    WishlistItemIssue.CONFIGURATION -> R.string.wishlist_product_configuration
    WishlistItemIssue.SERVICE, null -> R.string.wishlist_product_service
}

private val SAVED_CARD_MINIMUM_WIDTH = 160.dp
private val EMPTY_STATE_MINIMUM_HEIGHT = 280.dp
private val EMPTY_ICON_CONTAINER_SIZE = 64.dp
private val EMPTY_ICON_SIZE = 32.dp
