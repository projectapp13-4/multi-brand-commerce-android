@file:Suppress("FunctionNaming", "MagicNumber")

package com.gurbakir.mobile.catalog

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.gurbakir.foundation.ui.LocalBrandEditorialTypography
import com.gurbakir.mobile.core.R
import com.gurbakir.mobile.ui.CommerceSkeleton
import com.gurbakir.mobile.ui.LARGE_TEXT_FONT_SCALE
import com.gurbakir.mobile.ui.consumeDestinationInsets
import com.gurbakir.mobile.ui.withDestinationSpacing
import com.gurbakir.storefront.CatalogDiscoveryMediaSource

@Composable
internal fun GalleryCategoriesGrid(
    state: CategoriesUiState,
    padding: PaddingValues,
    onRetry: () -> Unit,
    onOpenCollection: (String) -> Unit,
    policy: CatalogPresentation.Gallery
) {
    BoxWithConstraints(
        Modifier.fillMaxSize().consumeDestinationInsets(padding),
        contentAlignment = Alignment.TopCenter
    ) {
        val fontScale = LocalDensity.current.fontScale
        val sections = (state as? CategoriesUiState.Content)?.let { gallerySections(it.items, policy) }
        val stacked = fontScale >= LARGE_TEXT_FONT_SCALE || (maxWidth < 360.dp && fontScale >= 1.3f)
        LazyVerticalGrid(
            columns = GridCells.Fixed(galleryColumnCount(maxWidth, fontScale)),
            modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth().fillMaxHeight()
                .testTag(CatalogTestTags.CATEGORIES_GRID),
            contentPadding = padding.withDestinationSpacing(horizontal = 16.dp, vertical = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) { GalleryIntroduction(policy.copy) }
            if (state is CategoriesUiState.Content && sections != null) {
                sections.featured?.let { item ->
                    item(key = item.stableId, span = { GridItemSpan(maxLineSpan) }) {
                        Box(Modifier.testTag(CatalogTestTags.GALLERY_FEATURED)) {
                            GalleryFeatured(item, stacked) { onOpenCollection(item.collection.handle) }
                        }
                    }
                }
                items(sections.covers, key = { it.stableId }) { item ->
                    GalleryCover(item) { onOpenCollection(item.collection.handle) }
                }
                galleryContinuation(sections, policy.copy, onOpenCollection)
                categoryRefreshItems(state, onRetry)
            } else if (state == CategoriesUiState.Loading) {
                repeat(4) {
                    item { CommerceSkeleton(Modifier.fillMaxWidth().aspectRatio(.8f)) }
                }
            } else {
                categoryStateItems(state, onRetry, onOpenCollection)
            }
        }
    }
}

@Composable
private fun GalleryIntroduction(copy: CatalogGalleryCopy) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(copy.introKicker),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary
        )
        Text(stringResource(copy.introTitle), style = LocalBrandEditorialTypography.current.hero)
        Text(
            stringResource(copy.introBody),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun GalleryFeatured(item: CatalogCategoryItem, stacked: Boolean, onClick: () -> Unit) {
    val modifier = Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick)
        .testTag(CatalogTestTags.category(item.collection.handle))
    if (stacked) {
        Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            GalleryMedia(item, Modifier.fillMaxWidth().height(184.dp))
            GalleryFeaturedCopy(item)
        }
    } else {
        Row(
            modifier,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            GalleryMedia(item, Modifier.weight(.43f).aspectRatio(.88f))
            Box(Modifier.weight(.57f)) { GalleryFeaturedCopy(item) }
        }
    }
}

@Composable
private fun GalleryFeaturedCopy(item: CatalogCategoryItem) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(R.string.gallery_featured),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary
        )
        GalleryLabel(item, MaterialTheme.typography.titleLarge)
        item.collection.description?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = if (LocalDensity.current.fontScale >= LARGE_TEXT_FONT_SCALE) Int.MAX_VALUE else 3
            )
        }
        Text(
            stringResource(R.string.gallery_discover),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.border(1.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.small)
                .heightIn(min = 48.dp).padding(horizontal = 12.dp, vertical = 12.dp)
        )
    }
}

@Composable
private fun GalleryCover(item: CatalogCategoryItem, onClick: () -> Unit) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopStart) {
        Column(
            Modifier.widthIn(
                max = if (LocalDensity.current.fontScale >= LARGE_TEXT_FONT_SCALE) {
                    androidx.compose.ui.unit.Dp.Infinity
                } else {
                    152.dp
                }
            ).fillMaxWidth()
                .clickable(role = Role.Button, onClick = onClick)
                .testTag(CatalogTestTags.category(item.collection.handle)),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            GalleryMedia(item, Modifier.fillMaxWidth().aspectRatio(.8f))
            GalleryLabel(item, MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun GalleryMedia(item: CatalogCategoryItem, modifier: Modifier) {
    var failed by remember(item.collection.media.uri) { mutableStateOf(false) }
    Surface(
        modifier.testTag(CatalogTestTags.categoryMedia(item.collection.handle)),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Box(contentAlignment = Alignment.Center) {
            AsyncImage(
                model = item.collection.media.uri.toASCIIString(),
                contentDescription = null,
                contentScale = if (item.collection.mediaSource == CatalogDiscoveryMediaSource.COLLECTION) {
                    ContentScale.Crop
                } else {
                    ContentScale.Fit
                },
                onError = { failed = true },
                onSuccess = { failed = false },
                modifier = Modifier.fillMaxSize()
            )
            if (failed) {
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

@Composable
private fun GalleryLabel(item: CatalogCategoryItem, style: androidx.compose.ui.text.TextStyle) {
    Text(item.title, style = style, modifier = Modifier.testTag(CatalogTestTags.categoryLabel(item.collection.handle)))
}

private fun LazyGridScope.galleryContinuation(
    sections: CatalogGallerySections,
    copy: CatalogGalleryCopy,
    onOpen: (String) -> Unit
) {
    if (sections.children.isNotEmpty() || sections.remaining.isNotEmpty() || sections.allProducts != null) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Text(stringResource(copy.continuationTitle), style = LocalBrandEditorialTypography.current.title)
            }
        }
    }
    if (sections.children.isNotEmpty()) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Text(
                stringResource(copy.childrenTitle),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    items(
        sections.children + sections.remaining + listOfNotNull(sections.allProducts),
        key = { it.stableId },
        span = { GridItemSpan(maxLineSpan) }
    ) { item ->
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp)
                .clickable(role = Role.Button) { onOpen(item.collection.handle) }
                .testTag(CatalogTestTags.category(item.collection.handle)),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.weight(1f)) { GalleryLabel(item, MaterialTheme.typography.bodyLarge) }
            Icon(
                painterResource(R.drawable.ic_chevron_right),
                null,
                Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
