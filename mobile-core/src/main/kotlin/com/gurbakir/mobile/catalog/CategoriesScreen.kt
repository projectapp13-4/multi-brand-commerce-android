@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@file:Suppress("FunctionNaming")

package com.gurbakir.mobile.catalog

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
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
    Box(
        modifier = Modifier.fillMaxSize().consumeDestinationInsets(contentPadding),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(CATEGORY_COLUMN_COUNT),
            modifier =
                Modifier.widthIn(max = CATEGORY_GRID_MAX_WIDTH)
                    .fillMaxWidth()
                    .fillMaxHeight(),
            contentPadding =
                contentPadding.withDestinationSpacing(
                    horizontal = spacing.sectionDp.dp,
                    vertical = spacing.normalDp.dp
                ),
            horizontalArrangement = Arrangement.spacedBy(spacing.normalDp.dp),
            verticalArrangement = Arrangement.spacedBy(spacing.generousDp.dp)
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
            val centeredFinalItem = state.items.lastOrNull().takeIf { state.items.size % 2 == 1 }
            val pairedItems = if (centeredFinalItem == null) state.items else state.items.dropLast(1)
            items(pairedItems, key = { it.stableId }) { item ->
                CategoryTile(
                    item = item,
                    onClick = { onOpenCollection(item.collection.handle) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            centeredFinalItem?.let { centeredItem ->
                item(span = { GridItemSpan(maxLineSpan) }, key = centeredItem.stableId) {
                    BoxWithConstraints(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        val tileWidth =
                            (maxWidth - LocalBrandSpacing.current.normalDp.dp) / CATEGORY_COLUMN_COUNT
                        CategoryTile(
                            item = centeredItem,
                            onClick = { onOpenCollection(centeredItem.collection.handle) },
                            modifier = Modifier.width(tileWidth)
                        )
                    }
                }
            }
            categoryRefreshItems(state, onRetry)
        }
    }
}

@Composable
private fun CategoryTile(item: CatalogCategoryItem, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val label = item.title
    Column(
        modifier =
            modifier
                .clickable(role = Role.Button, onClick = onClick)
                .testTag(CatalogTestTags.category(item.collection.handle)),
        verticalArrangement = Arrangement.spacedBy(LocalBrandSpacing.current.compactDp.dp)
    ) {
        HomeMedia(
            media = item.collection.media,
            fallbackDescription = label,
            modifier = Modifier.fillMaxWidth().aspectRatio(CATEGORY_MEDIA_ASPECT_RATIO),
            contentScale = ContentScale.Crop,
            shape = MaterialTheme.shapes.medium
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier =
                Modifier.fillMaxWidth()
                    .heightIn(min = CATEGORY_LABEL_MIN_HEIGHT)
                    .testTag(CatalogTestTags.categoryLabel(item.collection.handle))
        )
    }
}

@Composable
private fun CategoryTileSkeleton() {
    Column(verticalArrangement = Arrangement.spacedBy(LocalBrandSpacing.current.normalDp.dp)) {
        CommerceSkeleton(modifier = Modifier.fillMaxWidth().aspectRatio(CATEGORY_MEDIA_ASPECT_RATIO))
        CommerceSkeleton(
            modifier =
                Modifier.fillMaxWidth(CATEGORY_LABEL_SKELETON_WIDTH_FRACTION)
                    .height(CATEGORY_LABEL_MIN_HEIGHT)
        )
    }
}

private const val CATEGORY_COLUMN_COUNT = 2
private const val CATEGORY_MEDIA_ASPECT_RATIO = 1f
private const val CATEGORY_SKELETON_COUNT = 4
private const val CATEGORY_LABEL_SKELETON_WIDTH_FRACTION = 0.72f
private val CATEGORY_GRID_MAX_WIDTH = 360.dp
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
