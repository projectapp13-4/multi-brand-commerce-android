@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@file:Suppress("FunctionNaming", "MagicNumber")

package com.gurbakir.mobile.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.gurbakir.foundation.ui.LocalBrandSpacing
import com.gurbakir.mobile.core.R
import com.gurbakir.mobile.ui.CommerceStatePanel
import com.gurbakir.mobile.ui.DestinationLevel
import com.gurbakir.mobile.ui.DestinationScaffold
import com.gurbakir.mobile.ui.DestinationTitleAlignment
import com.gurbakir.mobile.ui.centeredDestinationContent
import com.gurbakir.mobile.ui.consumeDestinationInsets
import com.gurbakir.mobile.ui.withDestinationSpacing
import com.gurbakir.mobile.wishlist.WishlistMembershipUiState
import com.gurbakir.mobile.wishlist.WishlistProductAction
import com.gurbakir.mobile.wishlist.WishlistProductIconButton
import com.gurbakir.mobile.wishlist.productAction

data class HomeActions(
    val retryProductRange: () -> Unit,
    val retryFeaturedProduct: () -> Unit,
    val openCategories: () -> Unit = {},
    val openCart: () -> Unit = {},
    val openLegalSupport: (() -> Unit)? = null,
    val openCollection: (String) -> Unit = {},
    val openProduct: (String) -> Unit = {},
    val onSetWishlist: ((String, Boolean) -> Unit)? = null
)

@Composable
fun HomeScreen(
    state: HomeUiState,
    brandDisplayName: String,
    actions: HomeActions,
    wishlist: WishlistMembershipUiState? = null,
    cartQuantity: Int = 0
) {
    val spacing = LocalBrandSpacing.current
    DestinationScaffold(
        title = brandDisplayName,
        level = DestinationLevel.PRIMARY,
        modifier = Modifier.testTag(HomeTestTags.ROOT),
        titleAlignment = DestinationTitleAlignment.CENTER,
        titleTestTag = HomeTestTags.WORDMARK,
        actions = { HomeTopBarActions(actions, cartQuantity) }
    ) { scaffoldPadding ->
        LazyColumn(
            modifier =
                Modifier.fillMaxSize()
                    .centeredDestinationContent(1200.dp)
                    .consumeDestinationInsets(scaffoldPadding)
                    .testTag(HomeTestTags.CONTENT),
            contentPadding =
                scaffoldPadding.withDestinationSpacing(vertical = spacing.compactDp.dp),
            verticalArrangement = Arrangement.spacedBy(spacing.sectionDp.dp)
        ) {
            homeStateItems(state, actions, wishlist)
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.homeStateItems(
    state: HomeUiState,
    actions: HomeActions,
    wishlist: WishlistMembershipUiState?
) {
    if (state.showsWholePageEmpty) {
        item {
            CommerceStatePanel(
                title = stringResource(R.string.home_empty_title),
                body = stringResource(R.string.home_empty),
                primaryActionLabel = stringResource(R.string.home_browse_categories),
                onPrimaryAction = actions.openCategories,
                testTag = HomeTestTags.EMPTY
            )
        }
    } else if (state.showsWholePageSlowLoading) {
        item {
            HomeSlowLoadingPanel(
                onRetry = {
                    actions.retryProductRange()
                    actions.retryFeaturedProduct()
                },
                tag = HomeTestTags.SLOW_LOADING
            )
        }
    } else {
        productRangeSection(
            state = state.productRange,
            onRetry = actions.retryProductRange,
            onOpenCollection = actions.openCollection,
            onOpenCategories = actions.openCategories
        )
        featuredProductSection(
            state.featuredProduct,
            actions.retryFeaturedProduct,
            actions.openProduct,
            actions.onSetWishlist,
            wishlist
        )
    }
    actions.openLegalSupport?.let { onOpen ->
        item { LegalSupportHomeCard(onOpen) }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.productRangeSection(
    state: HomeSectionUiState<List<HomeCollectionItem>>,
    onRetry: () -> Unit,
    onOpenCollection: (String) -> Unit,
    onOpenCategories: () -> Unit
) {
    when (state) {
        HomeSectionUiState.Loading -> item { HomeSectionSkeleton(HomeTestTags.PRODUCT_RANGE_LOADING) }

        HomeSectionUiState.SlowLoading -> item {
            HomeSlowLoadingPanel(onRetry, HomeTestTags.PRODUCT_RANGE_SLOW_LOADING)
        }

        HomeSectionUiState.Empty -> Unit

        is HomeSectionUiState.Error -> item {
            HomeSectionError(
                failure = state.failure,
                onRetry = onRetry,
                tag = HomeTestTags.PRODUCT_RANGE_ERROR
            )
        }

        is HomeSectionUiState.Content -> {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth().testTag(HomeTestTags.PRODUCT_RANGE),
                    verticalArrangement = Arrangement.spacedBy(LocalBrandSpacing.current.generousDp.dp)
                ) {
                    HomeSectionHeading(
                        titleResourceId = R.string.home_product_range_title,
                        actionResourceId = R.string.home_view_all_collections,
                        onAction = onOpenCategories
                    )
                    ProductRange(items = state.value, onOpenCollection = onOpenCollection)
                    if (state.slowLoading) {
                        HomeSlowLoadingPanel(onRetry, HomeTestTags.PRODUCT_RANGE_SLOW_LOADING)
                    } else if (state.refreshing) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    state.partialFailure?.let { failure ->
                        HomeSectionError(
                            failure = failure,
                            onRetry = onRetry,
                            tag = HomeTestTags.PRODUCT_RANGE_PARTIAL_ERROR,
                            partial = true
                        )
                    }
                }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.featuredProductSection(
    state: HomeSectionUiState<HomeFeaturedItem>,
    onRetry: () -> Unit,
    onOpenProduct: (String) -> Unit,
    onSetWishlist: ((String, Boolean) -> Unit)?,
    wishlist: WishlistMembershipUiState?
) {
    when (state) {
        HomeSectionUiState.Loading -> item { HomeSectionSkeleton(HomeTestTags.FEATURED_LOADING) }

        HomeSectionUiState.SlowLoading -> item {
            HomeSlowLoadingPanel(onRetry, HomeTestTags.FEATURED_SLOW_LOADING)
        }

        HomeSectionUiState.Empty -> Unit

        is HomeSectionUiState.Error -> item {
            HomeSectionError(
                failure = state.failure,
                onRetry = onRetry,
                tag = HomeTestTags.FEATURED_ERROR
            )
        }

        is HomeSectionUiState.Content -> {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth().testTag(HomeTestTags.FEATURED),
                    verticalArrangement = Arrangement.spacedBy(LocalBrandSpacing.current.generousDp.dp)
                ) {
                    HomeSectionHeading(state.value.source.titleResourceId)
                    FeaturedProductCard(
                        item = state.value,
                        onClick = { onOpenProduct(state.value.summary.id) },
                        wishlist =
                            wishlist.productAction(state.value.summary.id, onSetWishlist)
                    )
                    if (state.slowLoading) {
                        HomeSlowLoadingPanel(onRetry, HomeTestTags.FEATURED_SLOW_LOADING)
                    } else if (state.refreshing) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeSectionHeading(titleResourceId: Int, actionResourceId: Int? = null, onAction: () -> Unit = {}) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(LocalBrandSpacing.current.normalDp.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(titleResourceId),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.weight(1f).semantics { heading() }.testTag(HomeTestTags.TITLE)
        )
        actionResourceId?.let { resourceId ->
            TextButton(onClick = onAction) {
                Text(stringResource(resourceId))
            }
        }
    }
}

@Composable
private fun ProductRange(items: List<HomeCollectionItem>, onOpenCollection: (String) -> Unit) {
    val spacing = LocalBrandSpacing.current
    if (items.isEmpty()) return
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val columns = homeProductRangeColumnCount(maxWidth)
        val gap = spacing.normalDp.dp
        val itemWidth = (maxWidth - gap * (columns - 1)) / columns
        Column(verticalArrangement = Arrangement.spacedBy(spacing.generousDp.dp)) {
            items.chunked(columns).forEach { rowItems ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(gap, Alignment.CenterHorizontally)
                ) {
                    rowItems.forEach { item ->
                        CollectionTile(
                            item = item,
                            onClick = { onOpenCollection(item.summary.handle) },
                            modifier = Modifier.width(itemWidth)
                        )
                    }
                }
            }
        }
    }
}

internal fun homeProductRangeColumnCount(availableWidth: Dp): Int = when {
    availableWidth >= 960.dp -> 4
    availableWidth >= 600.dp -> 3
    else -> 2
}

@Composable
private fun CollectionTile(item: HomeCollectionItem, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier =
            modifier
                .clickable(role = Role.Button, onClick = onClick)
                .testTag(HomeTestTags.collection(item.source.stableId)),
        verticalArrangement = Arrangement.spacedBy(LocalBrandSpacing.current.compactDp.dp)
    ) {
        HomeMedia(
            media = item.summary.media,
            fallbackDescription = stringResource(item.source.labelResourceId),
            modifier = Modifier.fillMaxWidth().aspectRatio(1f),
            contentScale = ContentScale.Crop,
            shape = MaterialTheme.shapes.medium
        )
        Text(
            text = stringResource(item.source.labelResourceId),
            style = MaterialTheme.typography.titleMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth().heightIn(min = HOME_COLLECTION_LABEL_MIN_HEIGHT)
        )
    }
}

@Composable
private fun FeaturedProductCard(item: HomeFeaturedItem, onClick: () -> Unit, wishlist: WishlistProductAction?) {
    val media = requireNotNull(item.summary.media)
    val spacing = LocalBrandSpacing.current
    Column(
        modifier =
            Modifier.fillMaxWidth()
                .clickable(role = Role.Button, onClick = onClick)
                .testTag(HomeTestTags.FEATURED_CARD),
        verticalArrangement = Arrangement.spacedBy(spacing.normalDp.dp)
    ) {
        HomeMedia(
            media = media,
            fallbackDescription = item.summary.title,
            modifier = Modifier.fillMaxWidth().aspectRatio(HOME_FEATURED_ASPECT_RATIO),
            contentScale = ContentScale.Crop,
            shape = MaterialTheme.shapes.large
        )
        FeaturedProductDetails(item, wishlist)
    }
}

@Composable
private fun FeaturedProductDetails(
    item: HomeFeaturedItem,
    wishlist: WishlistProductAction?,
    modifier: Modifier = Modifier
) {
    val spacing = LocalBrandSpacing.current
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(spacing.compactDp.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = item.summary.title,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(end = spacing.normalDp.dp)
            )
            WishlistProductIconButton(item.summary.id, wishlist)
        }
        Text(
            text = item.summary.price.localizedText(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.testTag(HomeTestTags.FEATURED_PRICE)
        )
    }
}

private const val HOME_FEATURED_ASPECT_RATIO = 16f / 10f
private val HOME_COLLECTION_LABEL_MIN_HEIGHT = 48.dp

object HomeTestTags {
    const val ROOT = "home-root"
    const val CONTENT = "home-content"
    const val WORDMARK = "home-wordmark"
    const val TITLE = "home-title"
    const val CATEGORIES = "home-categories"
    const val SEARCH = "home-search"
    const val WISHLIST = "home-wishlist"
    const val CART = "home-cart"
    const val LEGAL_SUPPORT = "home-legal-support"
    const val EMPTY = "home-empty"
    const val SLOW_LOADING = "home-slow-loading"
    const val PRODUCT_RANGE = "home-product-range"
    const val PRODUCT_RANGE_LOADING = "home-product-range-loading"
    const val PRODUCT_RANGE_SLOW_LOADING = "home-product-range-slow-loading"
    const val PRODUCT_RANGE_ERROR = "home-product-range-error"
    const val PRODUCT_RANGE_PARTIAL_ERROR = "home-product-range-partial-error"
    const val FEATURED = "home-featured"
    const val FEATURED_LOADING = "home-featured-loading"
    const val FEATURED_SLOW_LOADING = "home-featured-slow-loading"
    const val FEATURED_ERROR = "home-featured-error"
    const val FEATURED_CARD = "home-featured-card"
    const val FEATURED_PRICE = "home-featured-price"

    fun collection(stableId: String): String = "home-collection-${stableId.lowercase()}"
}
