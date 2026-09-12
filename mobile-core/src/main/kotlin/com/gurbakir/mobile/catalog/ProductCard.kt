@file:Suppress("FunctionNaming")

package com.gurbakir.mobile.catalog

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
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
@Suppress("LongMethod") // Card hierarchy is one visual and accessibility interaction surface.
internal fun CatalogProductCard(
    product: CatalogProductSummary,
    onClick: () -> Unit,
    wishlist: WishlistProductAction? = null
) {
    val spacing = LocalBrandSpacing.current
    val openLabel = stringResource(R.string.collection_open_product, product.title)
    Card(
        modifier =
            Modifier.fillMaxWidth()
                .clickable(onClickLabel = openLabel, role = Role.Button, onClick = onClick)
                .testTag(CatalogTestTags.product(product.handle))
    ) {
        Column {
            Box {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier =
                        Modifier.fillMaxWidth()
                            .aspectRatio(PRODUCT_MEDIA_RATIO)
                            .testTag(CatalogTestTags.productMedia(product.handle))
                ) {
                    product.media?.let { media ->
                        AsyncImage(
                            model = media.uri.toASCIIString(),
                            contentDescription = media.altText ?: product.title,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
                wishlist?.let { action ->
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surface.copy(alpha = WISHLIST_SURFACE_ALPHA),
                        tonalElevation = 1.dp,
                        modifier = Modifier.align(Alignment.TopEnd).padding(spacing.compactDp.dp)
                    ) {
                        WishlistProductIconButton(product.id, action)
                    }
                }
            }
            Column(modifier = Modifier.padding(spacing.normalDp.dp)) {
                Text(
                    text = product.title,
                    style = MaterialTheme.typography.titleMedium,
                    minLines = PRODUCT_TITLE_LINES,
                    maxLines = PRODUCT_TITLE_LINES,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag(CatalogTestTags.productTitle(product.handle))
                )
                PriceBlock(
                    minimumPrice = product.minimumPrice,
                    maximumPrice = product.maximumPrice,
                    availabilityText =
                        stringResource(
                            if (product.availableForSale) {
                                R.string.collection_available
                            } else {
                                R.string.collection_unavailable
                            }
                        ),
                    unavailable = !product.availableForSale,
                    emphasis = PriceBlockEmphasis.CARD,
                    priceTestTag = CatalogTestTags.productPrice(product.handle),
                    availabilityTestTag = CatalogTestTags.productAvailability(product.handle)
                )
            }
        }
    }
}

private const val PRODUCT_TITLE_LINES = 3
private const val PRODUCT_MEDIA_RATIO = 3f / 4f
private const val WISHLIST_SURFACE_ALPHA = 0.92f
