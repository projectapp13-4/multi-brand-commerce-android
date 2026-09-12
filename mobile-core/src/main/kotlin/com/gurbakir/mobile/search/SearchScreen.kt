@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@file:Suppress("FunctionNaming", "MagicNumber")

package com.gurbakir.mobile.search

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gurbakir.foundation.ui.LocalBrandSpacing
import com.gurbakir.mobile.catalog.CatalogError
import com.gurbakir.mobile.catalog.CatalogProductCard
import com.gurbakir.mobile.core.R
import com.gurbakir.mobile.ui.DestinationLevel
import com.gurbakir.mobile.ui.DestinationScaffold
import com.gurbakir.mobile.ui.DestinationTitleAlignment
import com.gurbakir.mobile.ui.consumeDestinationInsets
import com.gurbakir.mobile.ui.withDestinationSpacing
import com.gurbakir.mobile.wishlist.WishlistMembershipUiState
import com.gurbakir.mobile.wishlist.WishlistProductAction
import com.gurbakir.mobile.wishlist.productAction

@Composable
fun SearchScreen(state: SearchUiState, actions: SearchActions, wishlist: WishlistMembershipUiState? = null) {
    DestinationScaffold(
        title = stringResource(R.string.search_title),
        level = DestinationLevel.PRIMARY,
        modifier = Modifier.testTag(SearchTestTags.ROOT),
        titleAlignment = DestinationTitleAlignment.CENTER
    ) { padding ->
        SearchGrid(state, actions, wishlist, padding)
    }
}

@Composable
private fun SearchGrid(
    state: SearchUiState,
    actions: SearchActions,
    wishlist: WishlistMembershipUiState?,
    padding: PaddingValues
) {
    val spacing = LocalBrandSpacing.current
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 160.dp),
        modifier =
            Modifier.fillMaxSize()
                .consumeDestinationInsets(padding)
                .testTag(SearchTestTags.GRID),
        contentPadding =
            padding.withDestinationSpacing(
                horizontal = spacing.generousDp.dp,
                vertical = spacing.normalDp.dp
            ),
        horizontalArrangement = Arrangement.spacedBy(spacing.normalDp.dp),
        verticalArrangement = Arrangement.spacedBy(spacing.generousDp.dp)
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            SearchInput(state, actions)
        }
        searchStateItems(state, actions, wishlist)
    }
}

@Composable
private fun SearchInput(state: SearchUiState, actions: SearchActions) {
    val spacing = LocalBrandSpacing.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    Column(verticalArrangement = Arrangement.spacedBy(spacing.compactDp.dp)) {
        OutlinedTextField(
            value = state.query,
            onValueChange = actions.onQueryChanged,
            modifier = Modifier.fillMaxWidth().testTag(SearchTestTags.INPUT),
            placeholder = { Text(stringResource(R.string.search_input_label)) },
            leadingIcon = {
                Icon(
                    painter = painterResource(R.drawable.ic_nav_search),
                    contentDescription = null
                )
            },
            trailingIcon = {
                if (state.query.isNotBlank()) {
                    IconButton(
                        onClick = { actions.onQueryChanged("") },
                        modifier = Modifier.size(MINIMUM_TOUCH_TARGET_SIZE)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_search_clear),
                            contentDescription = stringResource(R.string.search_clear_query)
                        )
                    }
                }
            },
            singleLine = true,
            shape = MaterialTheme.shapes.large,
            colors =
                OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions =
                KeyboardActions(
                    onSearch = {
                        actions.onSubmit()
                        focusManager.clearFocus()
                        keyboardController?.hide()
                    }
                )
        )
        if (state.isQueryTooShort) {
            Text(
                text = stringResource(R.string.search_minimum_length),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier =
                    Modifier.padding(horizontal = spacing.normalDp.dp)
                        .testTag(SearchTestTags.TOO_SHORT)
            )
        }
    }
}

private fun LazyGridScope.searchStateItems(
    state: SearchUiState,
    actions: SearchActions,
    wishlist: WishlistMembershipUiState?
) {
    when {
        state.query.isBlank() -> item(span = { GridItemSpan(maxLineSpan) }) {
            SearchHistory(state, actions)
        }

        state.isQueryTooShort -> Unit

        state.loadingInitial -> item(span = { GridItemSpan(maxLineSpan) }) {
            LinearProgressIndicator(Modifier.fillMaxWidth().testTag(SearchTestTags.LOADING))
        }

        state.initialFailure != null -> item(span = { GridItemSpan(maxLineSpan) }) {
            CatalogError(state.initialFailure, actions.onRetry, SearchTestTags.ERROR)
        }

        state.isEmptyResult -> item(span = { GridItemSpan(maxLineSpan) }) {
            Text(stringResource(R.string.search_empty), modifier = Modifier.testTag(SearchTestTags.EMPTY))
        }

        state.hasSearched -> loadedSearchItems(state, actions, wishlist)
    }
}

private fun LazyGridScope.loadedSearchItems(
    state: SearchUiState,
    actions: SearchActions,
    wishlist: WishlistMembershipUiState?
) {
    item(span = { GridItemSpan(maxLineSpan) }) {
        Text(
            text = stringResource(R.string.search_result_count, state.totalCount ?: state.products.size),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier =
                Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                    .testTag(SearchTestTags.RESULT_COUNT)
        )
    }
    items(state.products, key = { it.id }) { product ->
        CatalogProductCard(
            product,
            onClick = { actions.onOpenProduct(product.id) },
            wishlist =
                wishlist.productAction(product.id, actions.onSetWishlist)
        )
    }
    item(span = { GridItemSpan(maxLineSpan) }) {
        when {
            state.loadingNext -> LinearProgressIndicator(Modifier.fillMaxWidth())

            state.nextPageFailure != null ->
                CatalogError(state.nextPageFailure, actions.onLoadMore, SearchTestTags.NEXT_ERROR)

            state.hasNextPage ->
                Button(
                    onClick = actions.onLoadMore,
                    modifier = Modifier.fillMaxWidth().testTag(SearchTestTags.LOAD_MORE)
                ) {
                    Text(stringResource(R.string.search_load_more))
                }
        }
    }
}

@Composable
private fun SearchHistory(state: SearchUiState, actions: SearchActions) {
    val spacing = LocalBrandSpacing.current
    var settingsExpanded by rememberSaveable { mutableStateOf(false) }
    Column(
        modifier = Modifier.fillMaxWidth().testTag(SearchTestTags.HISTORY),
        verticalArrangement = Arrangement.spacedBy(spacing.normalDp.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                stringResource(R.string.search_history_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f).semantics { heading() }
            )
            SearchHistorySettingsButton(
                expanded = settingsExpanded,
                onExpandedChanged = { settingsExpanded = it }
            )
        }
        when {
            !state.historyStorageAvailable ->
                Text(
                    stringResource(R.string.search_history_unavailable),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag(SearchTestTags.HISTORY_UNAVAILABLE)
                )

            !state.historyEnabled ->
                Text(
                    stringResource(R.string.search_history_disabled),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

            state.historyLoading -> LinearProgressIndicator(Modifier.fillMaxWidth())

            state.history.isEmpty() ->
                Text(
                    stringResource(R.string.search_history_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

            else -> HistoryEntries(state.history, actions)
        }
        if (settingsExpanded) {
            SearchHistorySettingsPanel(state, actions.onHistoryEnabledChanged)
        }
    }
}

@Composable
private fun SearchHistorySettingsButton(expanded: Boolean, onExpandedChanged: (Boolean) -> Unit) {
    val expandedDescription = stringResource(R.string.state_expanded)
    val collapsedDescription = stringResource(R.string.state_collapsed)
    TextButton(
        onClick = { onExpandedChanged(!expanded) },
        modifier =
            Modifier.semantics {
                stateDescription = if (expanded) expandedDescription else collapsedDescription
            }.testTag(SearchTestTags.HISTORY_SETTINGS)
    ) {
        Text(
            stringResource(
                if (expanded) R.string.search_history_settings_hide else R.string.search_history_settings
            )
        )
    }
}

@Composable
private fun SearchHistorySettingsPanel(state: SearchUiState, onHistoryEnabledChanged: (Boolean) -> Unit) {
    val spacing = LocalBrandSpacing.current
    Card(
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ),
        modifier = Modifier.fillMaxWidth().testTag(SearchTestTags.HISTORY_SETTINGS_PANEL)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(spacing.generousDp.dp),
            verticalArrangement = Arrangement.spacedBy(spacing.normalDp.dp)
        ) {
            Text(
                stringResource(R.string.search_privacy_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.search_history_enabled),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = state.historyEnabled,
                    onCheckedChange = onHistoryEnabledChanged,
                    enabled = state.historyStorageAvailable && !state.historyLoading,
                    modifier = Modifier.testTag(SearchTestTags.HISTORY_TOGGLE)
                )
            }
        }
    }
}

@Composable
private fun HistoryEntries(entries: List<StoredSearchQuery>, actions: SearchActions) {
    val spacing = LocalBrandSpacing.current
    entries.forEach { entry ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(spacing.compactDp.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(
                onClick = { actions.onSelectHistory(entry.display) },
                modifier =
                    Modifier.weight(1f)
                        .heightIn(min = MINIMUM_TOUCH_TARGET_SIZE)
                        .testTag(SearchTestTags.historyItem(entry.normalized)),
                contentPadding = PaddingValues(horizontal = spacing.normalDp.dp)
            ) {
                Text(
                    text = entry.display,
                    textAlign = TextAlign.Start,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            TextButton(
                onClick = { actions.onRemoveHistory(entry.normalized) },
                modifier =
                    Modifier.heightIn(min = MINIMUM_TOUCH_TARGET_SIZE)
                        .testTag(SearchTestTags.historyRemove(entry.normalized)),
                contentPadding = PaddingValues(horizontal = spacing.normalDp.dp)
            ) {
                Text(stringResource(R.string.search_history_remove))
            }
        }
        HorizontalDivider()
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End
    ) {
        TextButton(
            onClick = actions.onClearHistory,
            modifier = Modifier.testTag(SearchTestTags.HISTORY_CLEAR)
        ) {
            Text(stringResource(R.string.search_history_clear))
        }
    }
}

private val MINIMUM_TOUCH_TARGET_SIZE = 48.dp
