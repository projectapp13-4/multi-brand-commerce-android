package com.gurbakir.mobile.wishlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
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
    private var localEntries: List<StoredWishlistEntry>? = null
    private var localJob: Job? = null
    private var loadJob: Job? = null
    private var presentationToken = 0L
    private var intervalOpen = true
    private var firstResumePending = true
    private var freshPending = true
    private var cleared = false

    init {
        // One fresh constructor bootstrap is adopted by the first actual resumed interval.
        collectLocal()
    }

    private fun collectLocal() {
        localJob?.cancel()
        localJob = viewModelScope.launch {
            repository.observeLocalEntries().collect { local ->
                currentCoroutineContext().ensureActive()
                if (cleared) return@collect
                when (local) {
                    is WishlistLocalState.Available -> {
                        val changed = localEntries != local.entries
                        val recovered = !_state.value.storageAvailable
                        if (changed || recovered) invalidate()
                        localEntries = local.entries
                        _state.value = _state.value.withLocalRows(local.entries, intervalOpen, freshPending)
                        val needsHydration = _state.value.entries.requiresHydration(freshPending, recovered, changed)
                        if (intervalOpen && !_state.value.mutating && needsHydration) {
                            startLoad(forceRefresh = freshPending || recovered)
                        }
                    }

                    WishlistLocalState.StorageUnavailable -> {
                        invalidate()
                        localEntries = null
                        freshPending = intervalOpen
                        _state.value = _state.value.copy(
                            entries = emptyList(),
                            loading = false,
                            storageAvailable = false
                        )
                    }
                }
            }
        }
    }

    fun onResumed() {
        if (cleared) return
        if (firstResumePending) {
            firstResumePending = false
        }
        if (intervalOpen) return
        intervalOpen = true
        freshPending = true
        invalidate()
        _state.value = _state.value.copy(
            entries = localEntries.orEmpty().projectRows(),
            loading = localEntries == null
        )
        if (_state.value.storageAvailable) startLoad(forceRefresh = true) else collectLocal()
    }

    fun onHidden() {
        if (cleared || !intervalOpen) return
        intervalOpen = false
        freshPending = false
        invalidate()
        _state.value = _state.value.copy(entries = localEntries.orEmpty().projectRows(), loading = false)
    }

    fun refresh() {
        if (cleared || !intervalOpen) return
        invalidate()
        freshPending = true
        _state.value = _state.value.copy(entries = localEntries.orEmpty().projectRows())
        if (_state.value.storageAvailable) startLoad(forceRefresh = true) else collectLocal()
    }

    private fun startLoad(forceRefresh: Boolean) {
        val capturedEntries = localEntries ?: return
        invalidate()
        freshPending = false
        val capturedToken = presentationToken
        _state.value = _state.value.copy(loading = false, refreshing = true)
        loadJob = viewModelScope.launch {
            try {
                val result = repository.load(forceRefresh)
                currentCoroutineContext().ensureActive()
                val current = _state.value
                val ownsPresentation = !cleared && intervalOpen && capturedToken == presentationToken
                if (!ownsPresentation || !current.storageAvailable || capturedEntries != localEntries) return@launch
                when (result) {
                    is WishlistLoadResult.Content -> {
                        // Membership/order/timestamps come from the local feed, never from a late remote callback.
                        val resultIdentity = result.entries.map {
                            StoredWishlistEntry(it.productId, it.addedAtEpochMillis)
                        }
                        if (resultIdentity == capturedEntries) {
                            _state.value = current.copy(entries = capturedEntries.projectRows(result.entries, true))
                        }
                    }

                    WishlistLoadResult.StorageUnavailable -> {
                        presentationToken++
                        localEntries = null
                        freshPending = true
                        _state.value = current.copy(
                            entries = emptyList(),
                            loading = false,
                            refreshing = false,
                            storageAvailable = false
                        )
                    }
                }
            } finally {
                if (!cleared && capturedToken == presentationToken) {
                    _state.value = _state.value.copy(refreshing = false)
                }
            }
        }
    }

    private fun invalidate() {
        presentationToken++
        val obsolete = loadJob
        loadJob = null
        _state.value = _state.value.copy(refreshing = false)
        obsolete?.cancel()
    }

    fun remove(productId: String) {
        val captured = localEntries?.firstOrNull { it.productId == productId }
        mutate(
            operation = { repository.setSaved(productId, false) },
            retain = { it != captured }
        )
    }

    fun clear() {
        val captured = localEntries.orEmpty().toSet()
        mutate(repository::clear) { it !in captured }
    }

    private fun mutate(operation: suspend () -> WishlistMutationResult, retain: (StoredWishlistEntry) -> Boolean) {
        if (cleared || _state.value.mutating || !_state.value.storageAvailable) return
        _state.value = _state.value.copy(mutating = true)
        viewModelScope.launch {
            try {
                val result = operation()
                currentCoroutineContext().ensureActive()
                if (cleared) return@launch
                when (result) {
                    WishlistMutationResult.Success -> {
                        invalidate()
                        localEntries = localEntries?.filter(retain)
                        _state.value = _state.value.copy(
                            entries = localEntries.orEmpty().projectRows(_state.value.entries, intervalOpen),
                            mutating = false
                        )
                        if (intervalOpen && _state.value.storageAvailable && _state.value.entries.hasPendingRows()) {
                            startLoad(forceRefresh = false)
                        }
                    }

                    WishlistMutationResult.InvalidProduct -> _state.value = _state.value.copy(mutating = false)

                    WishlistMutationResult.StorageUnavailable -> {
                        invalidate()
                        localEntries = null
                        freshPending = intervalOpen
                        _state.value = _state.value.copy(
                            entries = emptyList(),
                            loading = false,
                            mutating = false,
                            storageAvailable = false
                        )
                    }
                }
            } finally {
                if (!cleared) _state.value = _state.value.copy(mutating = false)
            }
        }
    }

    override fun onCleared() {
        cleared = true
        intervalOpen = false
        freshPending = false
        invalidate()
        localJob?.cancel()
        super.onCleared()
    }
}

data class WishlistUiState(
    val entries: List<WishlistResolvedEntry> = emptyList(),
    val loading: Boolean = false,
    val mutating: Boolean = false,
    val storageAvailable: Boolean = true,
    val refreshing: Boolean = false
) {
    val hasRetryableItems: Boolean
        get() = entries.any { it.issue?.retryable == true }
}

private fun List<StoredWishlistEntry>.projectRows(
    resolved: List<WishlistResolvedEntry> = emptyList(),
    preserveProducts: Boolean = false
): List<WishlistResolvedEntry> {
    val available = if (preserveProducts) resolved.associateBy { it.productId to it.addedAtEpochMillis } else emptyMap()
    return map { stored ->
        available[stored.productId to stored.addedAtEpochMillis]
            ?: WishlistResolvedEntry(stored.productId, stored.addedAtEpochMillis)
    }
}

private fun List<WishlistResolvedEntry>.hasPendingRows(): Boolean = any { it.product == null && it.issue == null }

private fun WishlistUiState.withLocalRows(
    rows: List<StoredWishlistEntry>,
    intervalOpen: Boolean,
    freshPending: Boolean
): WishlistUiState = copy(
    entries = rows.projectRows(entries, preserveProducts = intervalOpen && !freshPending && storageAvailable),
    loading = false,
    storageAvailable = true
)

private fun List<WishlistResolvedEntry>.requiresHydration(
    freshPending: Boolean,
    recovered: Boolean,
    changed: Boolean
): Boolean = freshPending || recovered || (changed && hasPendingRows())
