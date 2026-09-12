package com.gurbakir.mobile.search

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gurbakir.mobile.catalog.CatalogLoadFailure
import com.gurbakir.storefront.CatalogProductSummary
import com.gurbakir.storefront.Cursor
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val SEARCH_DEBOUNCE_MILLIS = 400L

@HiltViewModel
class SearchViewModel
@Inject
constructor(
    private val searchRepository: ProductSearchRepository,
    private val historyRepository: SearchHistoryRepository,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val restoredQuery = savedStateHandle.get<String>(KEY_QUERY).orEmpty().take(SEARCH_MAXIMUM_LENGTH)
    private val _state = MutableStateFlow(SearchUiState(query = restoredQuery))
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    private var searchJob: Job? = null
    private var historyJob: Job? = null
    private var nextCursor: Cursor? = null
    private var requestGeneration = 0L

    init {
        mutateHistory { historyRepository.load() }
        if (restoredQuery.isSearchableQuery()) scheduleSearch()
    }

    fun onQueryChanged(value: String) {
        val bounded = value.take(SEARCH_MAXIMUM_LENGTH)
        if (bounded == _state.value.query) return
        savedStateHandle[KEY_QUERY] = bounded
        nextCursor = null
        requestGeneration += 1
        searchJob?.cancel()
        _state.value =
            _state.value.copy(
                query = bounded,
                products = emptyList(),
                totalCount = null,
                hasNextPage = false,
                loadingInitial = false,
                loadingNext = false,
                hasSearched = false,
                initialFailure = null,
                nextPageFailure = null
            )
        if (bounded.isSearchableQuery()) scheduleSearch()
    }

    fun submit() {
        if (!_state.value.query.isSearchableQuery()) return
        searchJob?.cancel()
        requestGeneration += 1
        val generation = requestGeneration
        searchJob = viewModelScope.launch { runSearch(recordHistory = true, generation = generation) }
    }

    fun retry() {
        if (!_state.value.query.isSearchableQuery()) return
        searchJob?.cancel()
        requestGeneration += 1
        val generation = requestGeneration
        searchJob = viewModelScope.launch { runSearch(recordHistory = false, generation = generation) }
    }

    fun loadNextPage() {
        val cursor = nextCursor ?: return
        val current = _state.value
        if (!current.canLoadNextPage) return
        searchJob?.cancel()
        val generation = requestGeneration
        _state.value = current.copy(loadingNext = true, nextPageFailure = null)
        searchJob =
            viewModelScope.launch {
                when (val result = searchRepository.search(current.query.toDisplaySearchQuery(), cursor)) {
                    is SearchPageLoad.Content -> if (generation == requestGeneration) {
                        nextCursor = result.page.endCursor
                        _state.value = _state.value.withAppendedPage(result.page)
                    }

                    is SearchPageLoad.Error -> if (generation == requestGeneration) {
                        _state.value =
                            _state.value.copy(loadingNext = false, nextPageFailure = result.failure)
                    }
                }
            }
    }

    fun removeHistory(normalizedQuery: String) = mutateHistory {
        historyRepository.remove(normalizedQuery)
    }

    fun clearHistory() = mutateHistory { historyRepository.clear() }

    fun setHistoryEnabled(enabled: Boolean) = mutateHistory {
        historyRepository.setEnabled(enabled)
    }

    private fun scheduleSearch() {
        requestGeneration += 1
        val generation = requestGeneration
        searchJob =
            viewModelScope.launch {
                delay(SEARCH_DEBOUNCE_MILLIS)
                runSearch(recordHistory = false, generation = generation)
            }
    }

    private suspend fun runSearch(recordHistory: Boolean, generation: Long) {
        val query = _state.value.query.toDisplaySearchQuery()
        if (!query.isSearchableQuery()) return
        nextCursor = null
        _state.value =
            _state.value.copy(
                loadingInitial = true,
                loadingNext = false,
                hasSearched = true,
                initialFailure = null,
                nextPageFailure = null,
                products = emptyList(),
                totalCount = null,
                hasNextPage = false
            )
        if (recordHistory) {
            _state.value = _state.value.withHistory(historyRepository.record(query))
        }
        when (val result = searchRepository.search(query)) {
            is SearchPageLoad.Content -> if (generation == requestGeneration) {
                nextCursor = result.page.endCursor
                _state.value = _state.value.withReplacementPage(result.page)
            }

            is SearchPageLoad.Error -> if (generation == requestGeneration) {
                _state.value =
                    _state.value.copy(loadingInitial = false, initialFailure = result.failure)
            }
        }
    }

    private fun mutateHistory(block: suspend () -> SearchHistoryState) {
        historyJob?.cancel()
        _state.value = _state.value.copy(historyLoading = true)
        historyJob = viewModelScope.launch { _state.value = _state.value.withHistory(block()) }
    }

    private companion object {
        const val KEY_QUERY = "search.query"
    }
}

private fun SearchUiState.withReplacementPage(page: com.gurbakir.storefront.ProductSearchPage) = copy(
    products = page.products.distinctBy { it.id },
    totalCount = page.totalCount,
    hasNextPage = page.hasNextPage,
    loadingInitial = false,
    initialFailure = null
)

private fun SearchUiState.withAppendedPage(page: com.gurbakir.storefront.ProductSearchPage) = copy(
    products = (products + page.products).distinctBy { it.id },
    totalCount = page.totalCount,
    hasNextPage = page.hasNextPage,
    loadingNext = false,
    nextPageFailure = null
)

private fun SearchUiState.withHistory(history: SearchHistoryState) = copy(
    historyEnabled = history.enabled,
    history = history.entries,
    historyStorageAvailable = history.storageAvailable,
    historyLoading = false
)

data class SearchUiState(
    val query: String = "",
    val products: List<CatalogProductSummary> = emptyList(),
    val totalCount: Int? = null,
    val hasNextPage: Boolean = false,
    val loadingInitial: Boolean = false,
    val loadingNext: Boolean = false,
    val hasSearched: Boolean = false,
    val initialFailure: CatalogLoadFailure? = null,
    val nextPageFailure: CatalogLoadFailure? = null,
    val historyEnabled: Boolean = true,
    val history: List<StoredSearchQuery> = emptyList(),
    val historyStorageAvailable: Boolean = true,
    val historyLoading: Boolean = true
) {
    val isQueryTooShort: Boolean
        get() = query.isNotBlank() && !query.isSearchableQuery()

    val isEmptyResult: Boolean
        get() = hasSearched && !loadingInitial && initialFailure == null && products.isEmpty()

    val canLoadNextPage: Boolean
        get() = !loadingInitial && !loadingNext && hasNextPage
}
