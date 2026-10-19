@file:Suppress("FunctionNaming")

package com.gurbakir.mobile.catalog

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.gurbakir.foundation.ui.LocalBrandSpacing
import com.gurbakir.mobile.core.R
import com.gurbakir.mobile.ui.PriceBlock
import com.gurbakir.mobile.ui.PriceBlockEmphasis
import com.gurbakir.mobile.wishlist.WishlistProductAction
import com.gurbakir.mobile.wishlist.WishlistProductIconButton
import com.gurbakir.storefront.CatalogProductSummary

@Composable
@Suppress("LongMethod") // Tile hierarchy is one visual and accessibility interaction surface.
internal fun CatalogProductTile(
    product: CatalogProductSummary,
    onClick: () -> Unit,
    wishlist: WishlistProductAction? = null,
    gallery: Boolean = false
) {
    val spacing = LocalBrandSpacing.current
    val openLabel = stringResource(R.string.collection_open_product, product.title)
    Column(
        modifier =
            Modifier.fillMaxWidth()
                .clickable(onClickLabel = openLabel, role = Role.Button, onClick = onClick)
                .testTag(CatalogTestTags.product(product.handle))
    ) {
        Box {
            CatalogProductMedia(product, gallery)
            wishlist?.takeUnless { gallery }?.let { action ->
                WishlistProductIconButton(
                    product.id,
                    action,
                    modifier = Modifier.align(Alignment.TopEnd),
                    onMedia = true
                )
            }
        }
        Column(
            modifier = Modifier.padding(top = if (gallery) spacing.compactDp.dp else spacing.normalDp.dp),
            verticalArrangement = Arrangement.spacedBy(spacing.compactDp.dp)
        ) {
            if (gallery) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                    CatalogProductTitle(product, gallery = true, modifier = Modifier.weight(1f))
                    wishlist?.let { WishlistProductIconButton(product.id, it) }
                }
            } else {
                CatalogProductTitle(product, gallery = false)
            }
            PriceBlock(
                minimumPrice = product.minimumPrice,
                maximumPrice = product.maximumPrice,
                availabilityText =
                    if (product.availableForSale) null else stringResource(R.string.collection_unavailable),
                unavailable = !product.availableForSale,
                emphasis = PriceBlockEmphasis.CARD,
                stackedRange = gallery,
                priceTestTag = CatalogTestTags.productPrice(product.handle),
                availabilityTestTag = CatalogTestTags.productAvailability(product.handle)
            )
        }
    }
}

@Composable
private fun CatalogProductTitle(product: CatalogProductSummary, gallery: Boolean, modifier: Modifier = Modifier) {
    Text(
        text = product.title,
        style = if (gallery) {
            MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
        } else {
            MaterialTheme.typography.bodyMedium
        },
        minLines = if (gallery) PRODUCT_TITLE_LINES else 1,
        maxLines = if (gallery) Int.MAX_VALUE else PRODUCT_TITLE_LINES,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.testTag(CatalogTestTags.productTitle(product.handle))
    )
}

@Composable
private fun CatalogProductMedia(product: CatalogProductSummary, gallery: Boolean) {
    var mediaFailed by remember(product.media?.uri) { mutableStateOf(false) }
    Surface(
        color = if (gallery) {
            MaterialTheme.colorScheme.surfaceContainerLow
        } else {
            MaterialTheme.colorScheme.surface
        },
        shape = if (gallery) MaterialTheme.shapes.small else MaterialTheme.shapes.medium,
        modifier =
            Modifier.fillMaxWidth()
                .aspectRatio(if (gallery) GALLERY_PRODUCT_MEDIA_RATIO else PRODUCT_MEDIA_RATIO)
                .testTag(CatalogTestTags.productMedia(product.handle))
    ) {
        Box(contentAlignment = Alignment.Center) {
            product.media?.let { media ->
                AsyncImage(
                    model = media.uri.toASCIIString(),
                    contentDescription = media.altText ?: product.title,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                    onError = { mediaFailed = true },
                    onSuccess = { mediaFailed = false }
                )
            }
            if (gallery && (product.media == null || mediaFailed)) {
                Text(
                    stringResource(R.string.gallery_image_unavailable),
                    Modifier.padding(8.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private const val PRODUCT_TITLE_LINES = 2
private const val PRODUCT_MEDIA_RATIO = 1f
private const val GALLERY_PRODUCT_MEDIA_RATIO = 4f / 5f
