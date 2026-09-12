package com.gurbakir.mobile.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class HomeViewModel
@Inject
constructor(
    private val repository: HomeContentRepository,
    private val loadingClock: HomeLoadingClock
) : ViewModel() {
    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    private var productRangeJob: Job? = null
    private var featuredProductJob: Job? = null

    init {
        retryProductRange()
        retryFeaturedProduct()
    }

    fun retryProductRange() {
        productRangeJob?.cancel()
        _state.update { state -> state.copy(productRange = state.productRange.refreshing()) }
        productRangeJob =
            viewModelScope.launch {
                val slowLoadingJob =
                    launch {
                        loadingClock.awaitSlowLoading()
                        _state.update { state ->
                            state.copy(productRange = state.productRange.markSlowLoading())
                        }
                    }
                val result = repository.loadProductRange().toUiState()
                slowLoadingJob.cancel()
                _state.update { state -> state.copy(productRange = result) }
            }
    }

    fun retryFeaturedProduct() {
        featuredProductJob?.cancel()
        _state.update { state -> state.copy(featuredProduct = state.featuredProduct.refreshing()) }
        featuredProductJob =
            viewModelScope.launch {
                val slowLoadingJob =
                    launch {
                        loadingClock.awaitSlowLoading()
                        _state.update { state ->
                            state.copy(featuredProduct = state.featuredProduct.markSlowLoading())
                        }
                    }
                val result = repository.loadFeaturedProduct().toUiState()
                slowLoadingJob.cancel()
                _state.update { state -> state.copy(featuredProduct = result) }
            }
    }
}

data class HomeUiState(
    val productRange: HomeSectionUiState<List<HomeCollectionItem>> = HomeSectionUiState.Loading,
    val featuredProduct: HomeSectionUiState<HomeFeaturedItem> = HomeSectionUiState.Loading
) {
    val showsWholePageEmpty: Boolean
        get() = productRange is HomeSectionUiState.Empty && featuredProduct is HomeSectionUiState.Empty

    val showsWholePageSlowLoading: Boolean
        get() =
            productRange is HomeSectionUiState.SlowLoading &&
                featuredProduct is HomeSectionUiState.SlowLoading
}

sealed interface HomeSectionUiState<out T> {
    data object Loading : HomeSectionUiState<Nothing>

    data object SlowLoading : HomeSectionUiState<Nothing>

    data object Empty : HomeSectionUiState<Nothing>

    data class Content<T>(
        val value: T,
        val partialFailure: HomeLoadFailure?,
        val refreshing: Boolean = false,
        val slowLoading: Boolean = false
    ) : HomeSectionUiState<T>

    data class Error(val failure: HomeLoadFailure) : HomeSectionUiState<Nothing>
}

private fun <T> HomeSectionUiState<T>.refreshing(): HomeSectionUiState<T> = if (this is HomeSectionUiState.Content) {
    copy(refreshing = true, slowLoading = false)
} else {
    HomeSectionUiState.Loading
}

private fun <T> HomeSectionUiState<T>.markSlowLoading(): HomeSectionUiState<T> = when (this) {
    HomeSectionUiState.Loading -> HomeSectionUiState.SlowLoading

    is HomeSectionUiState.Content ->
        if (refreshing) copy(slowLoading = true) else this

    else -> this
}

private fun <T> HomeSectionLoad<T>.toUiState(): HomeSectionUiState<T> = when (this) {
    is HomeSectionLoad.Content -> HomeSectionUiState.Content(value, partialFailure)
    is HomeSectionLoad.Error -> HomeSectionUiState.Error(failure)
    HomeSectionLoad.Empty -> HomeSectionUiState.Empty
}
