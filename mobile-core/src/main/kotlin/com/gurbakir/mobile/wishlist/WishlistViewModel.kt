package com.gurbakir.mobile.wishlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class WishlistViewModel
@Inject
constructor(private val repository: WishlistRepository) : ViewModel() {
    private val _state = MutableStateFlow(WishlistUiState(loading = true))
    val state: StateFlow<WishlistUiState> = _state.asStateFlow()
    private var loadJob: Job? = null

    init {
        viewModelScope.launch {
            repository.observeMembership().collect { membership ->
                when (membership) {
                    is WishlistMembershipState.Available -> load(forceRefresh = false)

                    WishlistMembershipState.StorageUnavailable -> {
                        loadJob?.cancel()
                        _state.value = WishlistUiState(loading = false, storageAvailable = false)
                    }
                }
            }
        }
    }

    fun refresh() {
        load(forceRefresh = true)
    }

    private fun load(forceRefresh: Boolean) {
        loadJob?.cancel()
        _state.value = _state.value.copy(loading = true, storageAvailable = true)
        loadJob =
            viewModelScope.launch {
                _state.value =
                    when (val result = repository.load(forceRefresh)) {
                        is WishlistLoadResult.Content ->
                            WishlistUiState(entries = result.entries, loading = false)

                        WishlistLoadResult.StorageUnavailable ->
                            WishlistUiState(loading = false, storageAvailable = false)
                    }
            }
    }

    fun remove(productId: String) {
        mutate(
            operation = { repository.setSaved(productId, false) },
            onSuccess = { state ->
                state.copy(entries = state.entries.filterNot { it.productId == productId })
            }
        )
    }

    fun clear() {
        mutate(repository::clear) { it.copy(entries = emptyList()) }
    }

    private fun mutate(
        operation: suspend () -> WishlistMutationResult,
        onSuccess: (WishlistUiState) -> WishlistUiState
    ) {
        if (_state.value.mutating || !_state.value.storageAvailable) return
        _state.value = _state.value.copy(mutating = true)
        viewModelScope.launch {
            _state.value =
                when (operation()) {
                    WishlistMutationResult.Success -> onSuccess(_state.value).copy(mutating = false)

                    WishlistMutationResult.InvalidProduct -> _state.value.copy(mutating = false)

                    WishlistMutationResult.StorageUnavailable ->
                        _state.value.copy(mutating = false, storageAvailable = false)
                }
        }
    }
}

data class WishlistUiState(
    val entries: List<WishlistResolvedEntry> = emptyList(),
    val loading: Boolean = false,
    val mutating: Boolean = false,
    val storageAvailable: Boolean = true
) {
    val hasRetryableItems: Boolean
        get() = entries.any { it.issue?.retryable == true }
}
