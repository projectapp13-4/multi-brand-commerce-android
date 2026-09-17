@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@file:Suppress(
    "CyclomaticComplexMethod",
    "FunctionNaming",
    "LongMethod",
    "MagicNumber",
    "NestedBlockDepth"
) // One closed renderer makes every Home state share refresh and native support ownership.

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
    val refreshContent: () -> Unit,
    val openCategories: () -> Unit = {},
    val openCart: () -> Unit = {},
    val openLegalSupport: (() -> Unit)? = null,
    val openCollection: (String) -> Unit = {},
    val openProduct: (String) -> Unit = {},
    val onSetWishlist: ((String, Boolean) -> Unit)? = null,
    val playbackCoordinator: HomePlaybackCoordinator? = null
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
        actions = { HomeTopBarActions(actions, cartQuantity, state.requestActive) }
    ) { scaffoldPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize()
                .centeredDestinationContent(1200.dp)
                .consumeDestinationInsets(scaffoldPadding)
                .testTag(HomeTestTags.CONTENT),
            contentPadding = scaffoldPadding.withDestinationSpacing(vertical = spacing.compactDp.dp),
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
    val presentation = state.presentation
    when {
        presentation != null -> {
            when {
                presentation.editorial is HomeEditorialState.IntentionalEmpty -> item {
                    CommerceStatePanel(
                        title = stringResource(R.string.home_empty_title),
                        body = stringResource(R.string.home_empty),
                        primaryActionLabel = stringResource(R.string.home_browse_categories),
                        onPrimaryAction = actions.openCategories,
                        testTag = HomeTestTags.EMPTY
                    )
                }

                presentation.resourceStatus == HomeResourceStatus.NONE_RENDERABLE -> item {
                    CommerceStatePanel(
                        title = stringResource(R.string.home_unavailable_title),
                        body = stringResource(R.string.home_nonrenderable),
                        testTag = HomeTestTags.NONRENDERABLE
                    )
                }

                else -> presentation.renderedSections.forEach { section ->
                    when (section) {
                        is HomeRenderedSection.CollectionGrid -> collectionGridSection(
                            section,
                            actions.openCollection,
                            actions.openCategories
                        )

                        is HomeRenderedSection.FeaturedProduct -> featuredProductSection(
                            section,
                            actions.openProduct,
                            actions.onSetWishlist,
                            wishlist
                        )

                        is HomeRenderedSection.Image -> imageSection(section, actions, actions.playbackCoordinator)

                        is HomeRenderedSection.Video -> {
                            if (actions.playbackCoordinator != null) {
                                videoSection(section, actions, actions.playbackCoordinator)
                            }
                        }
                    }
                }
            }
            if (presentation.refreshing) {
                item {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth().testTag(HomeTestTags.REFRESHING)
                    )
                }
            }
            state.failure?.let { failure ->
                item {
                    HomeSectionError(failure, actions.refreshContent, HomeTestTags.REFRESH_FAILURE, partial = true)
                }
            }
        }

        state.slowLoading -> item { HomeSlowLoadingPanel(actions.refreshContent, HomeTestTags.SLOW_LOADING) }

        state.loading -> item { HomeSectionSkeleton(HomeTestTags.PRODUCT_RANGE_LOADING) }

        state.failure != null -> item {
            HomeSectionError(state.failure, actions.refreshContent, HomeTestTags.HOME_ERROR)
        }

        else -> item {
            CommerceStatePanel(
                title = stringResource(R.string.home_unavailable_title),
                body = stringResource(R.string.home_unavailable),
                testTag = HomeTestTags.HOME_ERROR
            )
        }
    }
    actions.openLegalSupport?.let { onOpen -> item { LegalSupportHomeCard(onOpen) } }
}

private fun androidx.compose.foundation.lazy.LazyListScope.collectionGridSection(
    section: HomeRenderedSection.CollectionGrid,
    onOpenCollection: (String) -> Unit,
    onOpenCategories: () -> Unit
) {
    item {
        Column(
            modifier = Modifier.fillMaxWidth().testTag(HomeTestTags.PRODUCT_RANGE),
            verticalArrangement = Arrangement.spacedBy(LocalBrandSpacing.current.generousDp.dp)
        ) {
            HomeSectionHeading(section.title, R.string.home_view_all_collections, onOpenCategories)
            ProductRange(section.items, onOpenCollection)
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.featuredProductSection(
    section: HomeRenderedSection.FeaturedProduct,
    onOpenProduct: (String) -> Unit,
    onSetWishlist: ((String, Boolean) -> Unit)?,
    wishlist: WishlistMembershipUiState?
) {
    item {
        Column(
            modifier = Modifier.fillMaxWidth().testTag(HomeTestTags.FEATURED),
            verticalArrangement = Arrangement.spacedBy(LocalBrandSpacing.current.generousDp.dp)
        ) {
            HomeSectionHeading(section.title)
            FeaturedProductCard(
                section.item,
                onClick = { onOpenProduct(section.item.summary.id) },
                wishlist = wishlist.productAction(section.item.summary.id, onSetWishlist)
            )
        }
    }
}

@Composable
internal fun HomeSectionHeading(title: HomeText, actionResourceId: Int? = null, onAction: () -> Unit = {}) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(LocalBrandSpacing.current.normalDp.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title.resolve(),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.weight(1f).semantics { heading() }.testTag(HomeTestTags.TITLE)
        )
        actionResourceId?.let { resourceId -> TextButton(onClick = onAction) { Text(stringResource(resourceId)) } }
    }
}

@Composable
private fun HomeText.resolve(): String = when (this) {
    is HomeText.Packaged -> stringResource(resourceId)
    is HomeText.Remote -> value
}

@Composable
private fun ProductRange(items: List<HomeCollectionItem>, onOpenCollection: (String) -> Unit) {
    if (items.isEmpty()) return
    val spacing = LocalBrandSpacing.current
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
                        CollectionTile(item, { onOpenCollection(item.summary.handle) }, Modifier.width(itemWidth))
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
    val label = item.label.resolve()
    Column(
        modifier = modifier.clickable(role = Role.Button, onClick = onClick)
            .testTag(HomeTestTags.collection(item.stableId)),
        verticalArrangement = Arrangement.spacedBy(LocalBrandSpacing.current.compactDp.dp)
    ) {
        HomeMedia(
            item.summary.media,
            label,
            Modifier.fillMaxWidth().aspectRatio(1f),
            ContentScale.Crop,
            MaterialTheme.shapes.medium
        )
        Text(
            label,
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
        modifier = Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick)
            .testTag(HomeTestTags.FEATURED_CARD),
        verticalArrangement = Arrangement.spacedBy(spacing.normalDp.dp)
    ) {
        HomeMedia(
            media,
            item.summary.title,
            Modifier.fillMaxWidth().aspectRatio(HOME_FEATURED_ASPECT_RATIO),
            ContentScale.Crop,
            MaterialTheme.shapes.large
        )
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(item.summary.title, style = MaterialTheme.typography.titleLarge, maxLines = 3)
                Text(
                    item.summary.price.localizedText(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.testTag(HomeTestTags.FEATURED_PRICE)
                )
            }
            WishlistProductIconButton(item.summary.id, wishlist)
        }
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
    const val REFRESH = "home-refresh"
    const val REFRESHING = "home-refreshing"
    const val REFRESH_FAILURE = "home-refresh-failure"
    const val LEGAL_SUPPORT = "home-legal-support"
    const val EMPTY = "home-empty"
    const val NONRENDERABLE = "home-nonrenderable"
    const val HOME_ERROR = "home-error"
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
    const val VIDEO_POSTER = "home-video-poster"
    const val VIDEO_PLAYER = "home-video-player"

    fun collection(stableId: String): String = "home-collection-${stableId.lowercase()}"

    fun image(stableId: String): String = "home-image-${stableId.lowercase()}"

    fun video(stableId: String): String = "home-video-${stableId.lowercase()}"

    fun videoPlay(stableId: String): String = "home-video-play-${stableId.lowercase()}"

    fun videoError(stableId: String): String = "home-video-error-${stableId.lowercase()}"
}
