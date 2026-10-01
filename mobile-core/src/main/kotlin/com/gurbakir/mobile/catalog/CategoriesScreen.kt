@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@file:Suppress("FunctionNaming")

package com.gurbakir.mobile.catalog

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gurbakir.foundation.ui.LocalBrandSpacing
import com.gurbakir.mobile.core.R
import com.gurbakir.mobile.home.HomeMedia
import com.gurbakir.mobile.ui.CommerceSkeleton
import com.gurbakir.mobile.ui.DestinationLevel
import com.gurbakir.mobile.ui.DestinationScaffold
import com.gurbakir.mobile.ui.DestinationTitleAlignment
import com.gurbakir.mobile.ui.LARGE_TEXT_FONT_SCALE
import com.gurbakir.mobile.ui.consumeDestinationInsets
import com.gurbakir.mobile.ui.withDestinationSpacing

@Composable
fun CategoriesScreen(state: CategoriesUiState, onRetry: () -> Unit, onOpenCollection: (String) -> Unit) {
    DestinationScaffold(
        title = stringResource(R.string.categories_title),
        level = DestinationLevel.PRIMARY,
        modifier = Modifier.testTag(CatalogTestTags.CATEGORIES_ROOT),
        titleAlignment = DestinationTitleAlignment.CENTER
    ) { padding ->
        CategoriesGrid(state, padding, onRetry, onOpenCollection)
    }
}

@Composable
private fun CategoriesGrid(
    state: CategoriesUiState,
    contentPadding: PaddingValues,
    onRetry: () -> Unit,
    onOpenCollection: (String) -> Unit
) {
    val spacing = LocalBrandSpacing.current
    BoxWithConstraints(
        modifier = Modifier.fillMaxSize().consumeDestinationInsets(contentPadding),
        contentAlignment = Alignment.TopCenter
    ) {
        val columnCount = categoryColumnCount(maxWidth, LocalDensity.current.fontScale)
        LazyVerticalGrid(
            columns = GridCells.Fixed(columnCount),
            modifier =
                Modifier.widthIn(max = CATEGORY_GRID_MAX_WIDTH)
                    .fillMaxWidth()
                    .testTag(CatalogTestTags.CATEGORIES_GRID)
                    .fillMaxHeight(),
            contentPadding =
                contentPadding.withDestinationSpacing(
                    horizontal = spacing.generousDp.dp,
                    vertical = spacing.normalDp.dp
                ),
            horizontalArrangement = Arrangement.spacedBy(spacing.normalDp.dp),
            verticalArrangement = Arrangement.spacedBy(spacing.normalDp.dp)
        ) {
            categoryStateItems(state, onRetry, onOpenCollection)
        }
    }
}

private fun LazyGridScope.categoryStateItems(
    state: CategoriesUiState,
    onRetry: () -> Unit,
    onOpenCollection: (String) -> Unit
) {
    when (state) {
        CategoriesUiState.Loading -> repeat(CATEGORY_SKELETON_COUNT) {
            item { CategoryTileSkeleton() }
        }

        CategoriesUiState.Empty -> item(span = { GridItemSpan(maxLineSpan) }) {
            Text(stringResource(R.string.categories_empty), Modifier.testTag(CatalogTestTags.CATEGORIES_EMPTY))
        }

        is CategoriesUiState.Error -> item(span = { GridItemSpan(maxLineSpan) }) {
            CatalogError(state.failure, onRetry, CatalogTestTags.CATEGORIES_ERROR)
        }

        is CategoriesUiState.Content -> {
            items(state.items, key = { it.stableId }) { item ->
                CategoryTile(
                    item = item,
                    onClick = { onOpenCollection(item.collection.handle) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            categoryRefreshItems(state, onRetry)
        }
    }
}

@Composable
private fun CategoryTile(item: CatalogCategoryItem, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val label = item.title
    val spacing = LocalBrandSpacing.current
    Column(
        modifier =
            modifier
                .clickable(role = Role.Button, onClick = onClick)
                .testTag(CatalogTestTags.category(item.collection.handle))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = spacing.normalDp.dp),
            horizontalArrangement = Arrangement.spacedBy(spacing.generousDp.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HomeMedia(
                media = item.collection.media,
                fallbackDescription = label,
                modifier = Modifier.size(CATEGORY_MEDIA_SIZE),
                contentScale = ContentScale.Fit,
                shape = MaterialTheme.shapes.small
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = if (LocalDensity.current.fontScale >= LARGE_TEXT_FONT_SCALE) Int.MAX_VALUE else 2,
                overflow = TextOverflow.Ellipsis,
                modifier =
                    Modifier.weight(1f)
                        .testTag(CatalogTestTags.categoryLabel(item.collection.handle))
            )
            Icon(
                painterResource(R.drawable.ic_chevron_right),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(CATEGORY_CHEVRON_SIZE)
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = CATEGORY_DIVIDER_ALPHA))
    }
}

@Composable
private fun CategoryTileSkeleton() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(LocalBrandSpacing.current.generousDp.dp)
    ) {
        CommerceSkeleton(modifier = Modifier.size(CATEGORY_MEDIA_SIZE))
        CommerceSkeleton(
            modifier =
                Modifier.weight(1f)
                    .height(CATEGORY_LABEL_MIN_HEIGHT)
        )
    }
}

internal fun categoryColumnCount(availableWidth: androidx.compose.ui.unit.Dp, fontScale: Float): Int =
    if (fontScale >= LARGE_TEXT_FONT_SCALE || availableWidth < CATEGORY_TWO_COLUMN_WIDTH) 1 else 2

private const val CATEGORY_SKELETON_COUNT = 4
private const val CATEGORY_DIVIDER_ALPHA = 0.55f
private val CATEGORY_MEDIA_SIZE = 80.dp
private val CATEGORY_CHEVRON_SIZE = 20.dp
private val CATEGORY_TWO_COLUMN_WIDTH = 600.dp
private val CATEGORY_GRID_MAX_WIDTH = 720.dp
private val CATEGORY_LABEL_MIN_HEIGHT = 48.dp

private fun LazyGridScope.categoryRefreshItems(state: CategoriesUiState.Content, onRetry: () -> Unit) {
    if (state.refreshing) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }
    }
    state.partialFailure?.let { failure ->
        item(span = { GridItemSpan(maxLineSpan) }) {
            CatalogError(
                failure,
                onRetry,
                CatalogTestTags.CATEGORIES_PARTIAL_ERROR,
                partial = true
            )
        }
    }
}
