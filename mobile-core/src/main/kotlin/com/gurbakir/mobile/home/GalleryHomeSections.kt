@file:Suppress("FunctionNaming", "LongMethod", "MagicNumber")

package com.gurbakir.mobile.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.gurbakir.foundation.ui.LocalBrandEditorialTypography
import com.gurbakir.foundation.ui.LocalBrandProductTypography
import com.gurbakir.foundation.ui.LocalBrandSpacing
import com.gurbakir.mobile.core.R
import com.gurbakir.mobile.product.ArtworkImage
import com.gurbakir.mobile.wishlist.WishlistMembershipUiState
import com.gurbakir.mobile.wishlist.WishlistProductIconButton
import com.gurbakir.mobile.wishlist.productAction

internal fun LazyListScope.galleryCollectionSection(section: HomeRenderedSection.CollectionGrid, actions: HomeActions) {
    item(key = section.stableId) {
        val spacing = LocalBrandSpacing.current
        Column(
            Modifier.fillMaxWidth().testTag(HomeTestTags.PRODUCT_RANGE),
            verticalArrangement = Arrangement.spacedBy(spacing.generousDp.dp)
        ) {
            GalleryHomeHeading(section.title)
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val columns = homeProductRangeColumnCount(maxWidth, LocalDensity.current.fontScale)
                val gap = spacing.normalDp.dp
                val itemWidth = (maxWidth - gap * (columns - 1)) / columns
                Column(verticalArrangement = Arrangement.spacedBy(spacing.generousDp.dp)) {
                    section.items.chunked(columns).forEach { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                            row.forEach { item ->
                                Column(
                                    Modifier.width(itemWidth).heightIn(min = 48.dp)
                                        .clickable(role = Role.Button) { actions.openCollection(item.summary.handle) },
                                    verticalArrangement = Arrangement.spacedBy(spacing.compactDp.dp)
                                ) {
                                    // The contract may supply collection art or a first-product image. Fit preserves both.
                                    HomeMedia(
                                        item.summary.media,
                                        item.label.resolveHomeText(),
                                        Modifier.fillMaxWidth().aspectRatio(1f),
                                        ContentScale.Fit
                                    )
                                    Text(item.label.resolveHomeText(), style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                }
            }
            TextButton(onClick = actions.openCategories, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.home_view_all_collections))
            }
        }
    }
}

internal fun LazyListScope.galleryFeaturedSection(
    section: HomeRenderedSection.FeaturedProduct,
    actions: HomeActions,
    wishlist: WishlistMembershipUiState?
) {
    item(key = "${section.stableId}:art") {
        Column(
            Modifier.fillMaxWidth().testTag(HomeTestTags.FEATURED),
            verticalArrangement = Arrangement.spacedBy(LocalBrandSpacing.current.generousDp.dp)
        ) {
            GalleryHomeHeading(section.title)
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.widthIn(max = 520.dp).fillMaxWidth().aspectRatio(1f)
                        .testTag("gallery-home-featured-image")
                ) {
                    ArtworkImage(
                        requireNotNull(section.item.summary.media),
                        section.item.summary.title,
                        Modifier.padding(LocalBrandSpacing.current.normalDp.dp)
                    )
                }
            }
        }
    }
    // Separate lazy item keeps the complete title, price and action reachable with enlarged text.
    item(key = "${section.stableId}:details") {
        val product = section.item.summary
        Column(
            Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(LocalBrandSpacing.current.normalDp.dp)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Text(
                    product.title,
                    style = LocalBrandProductTypography.current.title,
                    modifier = Modifier.weight(1f).testTag("gallery-home-artwork-title")
                )
                WishlistProductIconButton(product.id, wishlist.productAction(product.id, actions.onSetWishlist))
            }
            Text(
                stringResource(R.string.gallery_home_price_from, product.price.localizedText()),
                style = LocalBrandProductTypography.current.price,
                modifier = Modifier.testTag(HomeTestTags.FEATURED_PRICE)
            )
            OutlinedButton(
                onClick = { actions.openProduct(product.id) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("gallery-home-open-artwork")
            ) {
                Text(stringResource(R.string.gallery_home_view_artwork))
            }
        }
    }
}

@Composable
internal fun GalleryHomePartialNotice() {
    Text(
        stringResource(R.string.home_error_partial),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }.testTag("gallery-home-partial")
    )
}

@Composable
private fun GalleryHomeHeading(title: HomeText) {
    Text(
        title.resolveHomeText(),
        style = LocalBrandEditorialTypography.current.title,
        modifier = Modifier.semantics { heading() }.testTag(HomeTestTags.TITLE)
    )
}

@Composable
internal fun HomeText.resolveHomeText(): String = when (this) {
    is HomeText.Packaged -> stringResource(resourceId)
    is HomeText.Remote -> value
}
