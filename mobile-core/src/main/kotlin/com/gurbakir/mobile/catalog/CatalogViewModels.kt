package com.gurbakir.mobile.catalog

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gurbakir.storefront.CatalogProductSummary
import com.gurbakir.storefront.CatalogProductTypeFilter
import com.gurbakir.storefront.CollectionCatalogSort
import com.gurbakir.storefront.Cursor
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class CategoriesViewModel
@Inject
constructor(private val repository: CatalogRepository) : ViewModel() {
    private val _state = MutableStateFlow<CategoriesUiState>(CategoriesUiState.Loading)
    val state: StateFlow<CategoriesUiState> = _state.asStateFlow()
    private var loadJob: Job? = null

    init {
        retry()
    }

    fun retry() {
        loadJob?.cancel()
        _state.value = (_state.value as? CategoriesUiState.Content)?.copy(refreshing = true)
            ?: CategoriesUiState.Loading
        loadJob =
            viewModelScope.launch {
                _state.value = repository.loadCategories().toUiState()
            }
    }
}

sealed interface CategoriesUiState {
    data object Loading : CategoriesUiState

    data object Empty : CategoriesUiState

    data class Content(
        val items: List<CatalogCategoryItem>,
        val partialFailure: CatalogLoadFailure?,
        val refreshing: Boolean = false
    ) : CategoriesUiState

    data class Error(val failure: CatalogLoadFailure) : CategoriesUiState
}

@HiltViewModel
class CollectionViewModel
@Inject
constructor(
    private val repository: CatalogRepository,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val restoredSort =
        savedStateHandle.get<String>(KEY_SORT)
            ?.let { name -> CollectionCatalogSort.entries.firstOrNull { it.name == name } }
            ?: CollectionCatalogSort.COLLECTION_DEFAULT
    private val restoredProductTypes =
        savedStateHandle.get<ArrayList<String>>(KEY_PRODUCT_TYPES)?.toSet().orEmpty()

    private val _state =
        MutableStateFlow(
            CollectionUiState(
                sort = restoredSort,
                selectedProductTypes = restoredProductTypes
            )
        )
    val state: StateFlow<CollectionUiState> = _state.asStateFlow()

    private var requestJob: Job? = null
    private var nextCursor: Cursor? = null

    fun start(handle: String) {
        if (_state.value.handle == handle) return
        savedStateHandle[KEY_HANDLE] = handle
        _state.value = _state.value.copy(handle = handle)
        reload()
    }

    fun retry() = reload()

    fun selectSort(sort: CollectionCatalogSort) {
        if (_state.value.sort == sort) return
        savedStateHandle[KEY_SORT] = sort.name
        _state.value = _state.value.copy(sort = sort)
        reload()
    }

    fun toggleProductType(value: String) {
        val current = _state.value.selectedProductTypes
        val updated = if (value in current) current - value else current + value
        savedStateHandle[KEY_PRODUCT_TYPES] = ArrayList(updated.sorted())
        _state.value = _state.value.copy(selectedProductTypes = updated)
        reload()
    }

    fun loadNextPage() {
        val state = _state.value
        if (!state.canLoadNextPage) return
        val cursor = nextCursor ?: return
        requestJob?.cancel()
        _state.value = state.copy(loadingNext = true, nextPageFailure = null)
        requestJob =
            viewModelScope.launch {
                when (
                    val result =
                        repository.loadCollectionPage(
                            handle = state.handle.orEmpty(),
                            after = cursor,
                            sort = state.sort,
                            productTypes = state.selectedProductTypes
                        )
                ) {
                    is CatalogPageLoad.Content -> {
                        nextCursor = result.page.endCursor
                        _state.value =
                            _state.value.copy(
                                title = result.page.title,
                                products = (_state.value.products + result.page.products).distinctBy { it.id },
                                productTypeFilter = result.page.productTypeFilter ?: _state.value.productTypeFilter,
                                hasNextPage = result.page.hasNextPage,
                                loadingNext = false,
                                nextPageFailure = null
                            )
                    }

                    is CatalogPageLoad.Error ->
                        _state.value = _state.value.copy(loadingNext = false, nextPageFailure = result.failure)

                    CatalogPageLoad.NotFound ->
                        _state.value = _state.value.copy(loadingNext = false, hasNextPage = false)
                }
            }
    }

    private fun reload() {
        val handle = _state.value.handle ?: return
        requestJob?.cancel()
        nextCursor = null
        _state.value =
            _state.value.copy(
                title = null,
                products = emptyList(),
                productTypeFilter = null,
                hasNextPage = false,
                loadingInitial = true,
                loadingNext = false,
                initialFailure = null,
                nextPageFailure = null,
                notFound = false
            )
        val requestedSort = _state.value.sort
        val requestedTypes = _state.value.selectedProductTypes
        requestJob =
            viewModelScope.launch {
                when (
                    val result =
                        repository.loadCollectionPage(
                            handle = handle,
                            after = null,
                            sort = requestedSort,
                            productTypes = requestedTypes
                        )
                ) {
                    is CatalogPageLoad.Content -> {
                        nextCursor = result.page.endCursor
                        _state.value =
                            _state.value.copy(
                                title = result.page.title,
                                products = result.page.products.distinctBy { it.id },
                                productTypeFilter = result.page.productTypeFilter,
                                hasNextPage = result.page.hasNextPage,
                                loadingInitial = false
                            )
                    }

                    is CatalogPageLoad.Error ->
                        _state.value =
                            _state.value.copy(loadingInitial = false, initialFailure = result.failure)

                    CatalogPageLoad.NotFound ->
                        _state.value = _state.value.copy(loadingInitial = false, notFound = true)
                }
            }
    }

    private companion object {
        const val KEY_HANDLE = "catalog.handle"
        const val KEY_SORT = "catalog.sort"
        const val KEY_PRODUCT_TYPES = "catalog.productTypes"
    }
}

data class CollectionUiState(
    val handle: String? = null,
    val title: String? = null,
    val products: List<CatalogProductSummary> = emptyList(),
    val productTypeFilter: CatalogProductTypeFilter? = null,
    val selectedProductTypes: Set<String> = emptySet(),
    val sort: CollectionCatalogSort = CollectionCatalogSort.COLLECTION_DEFAULT,
    val hasNextPage: Boolean = false,
    val loadingInitial: Boolean = false,
    val loadingNext: Boolean = false,
    val initialFailure: CatalogLoadFailure? = null,
    val nextPageFailure: CatalogLoadFailure? = null,
    val notFound: Boolean = false
) {
    val isEmpty: Boolean
        get() = !loadingInitial && initialFailure == null && !notFound && products.isEmpty()

    val canLoadNextPage: Boolean
        get() = !loadingInitial && !loadingNext && hasNextPage
}

private fun CatalogCategoryLoad.toUiState(): CategoriesUiState = when (this) {
    is CatalogCategoryLoad.Content -> CategoriesUiState.Content(items, partialFailure)
    is CatalogCategoryLoad.Error -> CategoriesUiState.Error(failure)
    CatalogCategoryLoad.Empty -> CategoriesUiState.Empty
}
