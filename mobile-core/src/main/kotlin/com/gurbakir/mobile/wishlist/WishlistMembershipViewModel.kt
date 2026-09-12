package com.gurbakir.mobile.wishlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class WishlistMembershipViewModel
@Inject
constructor(private val repository: WishlistRepository) : ViewModel() {
    private val _state = MutableStateFlow(WishlistMembershipUiState())
    val state: StateFlow<WishlistMembershipUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeMembership().collect { membership ->
                _state.value =
                    when (membership) {
                        is WishlistMembershipState.Available ->
                            _state.value.copy(productIds = membership.productIds, storageAvailable = true)

                        WishlistMembershipState.StorageUnavailable ->
                            _state.value.copy(storageAvailable = false, updatingProductIds = emptySet())
                    }
            }
        }
    }

    fun setSaved(productId: String, saved: Boolean) {
        if (!_state.value.storageAvailable || productId in _state.value.updatingProductIds) return
        _state.value = _state.value.copy(updatingProductIds = _state.value.updatingProductIds + productId)
        viewModelScope.launch {
            when (repository.setSaved(productId, saved)) {
                WishlistMutationResult.Success -> Unit

                WishlistMutationResult.InvalidProduct -> Unit

                WishlistMutationResult.StorageUnavailable ->
                    _state.value = _state.value.copy(storageAvailable = false)
            }
            _state.value =
                _state.value.copy(updatingProductIds = _state.value.updatingProductIds - productId)
        }
    }
}

data class WishlistMembershipUiState(
    val productIds: Set<String> = emptySet(),
    val updatingProductIds: Set<String> = emptySet(),
    val storageAvailable: Boolean = true
) {
    fun isSaved(productId: String): Boolean = productId in productIds

    fun isUpdating(productId: String): Boolean = productId in updatingProductIds
}
