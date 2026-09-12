package com.gurbakir.mobile.update

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal sealed interface UpdatePolicyNotice {
    data class Maintenance(val policyRevision: Long) : UpdatePolicyNotice

    data class OptionalUpdate(val recommendedVersionCode: Int) : UpdatePolicyNotice
}

internal data class UpdatePolicyUiState(
    val presentation: UpdatePolicyPresentation = UpdatePolicyPresentation(),
    val refreshing: Boolean = false,
    val dismissedMaintenanceRevision: Long? = null,
    val locallyDeferredVersionCode: Int? = null
) {
    val notice: UpdatePolicyNotice?
        get() = when {
            presentation.maintenanceMessageEnabled &&
                dismissedMaintenanceRevision != presentation.policyRevision ->
                UpdatePolicyNotice.Maintenance(presentation.policyRevision)

            presentation.optionalUpdateVersionCode != null &&
                locallyDeferredVersionCode != presentation.optionalUpdateVersionCode ->
                UpdatePolicyNotice.OptionalUpdate(presentation.optionalUpdateVersionCode)

            else -> null
        }
}

@HiltViewModel
internal class UpdatePolicyViewModel
@Inject
constructor(
    private val controller: UpdatePolicyController,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val _state =
        MutableStateFlow(
            UpdatePolicyUiState(
                presentation = controller.loadCachedPolicy(),
                dismissedMaintenanceRevision = savedStateHandle[KEY_DISMISSED_MAINTENANCE_REVISION],
                locallyDeferredVersionCode = savedStateHandle[KEY_LOCALLY_DEFERRED_VERSION]
            )
        )
    val state: StateFlow<UpdatePolicyUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        if (_state.value.refreshing) return
        _state.value = _state.value.copy(refreshing = true)
        viewModelScope.launch {
            val presentation = controller.refreshPolicy()
            _state.value = _state.value.copy(presentation = presentation, refreshing = false)
        }
    }

    fun deferUpdate() {
        val notice = _state.value.notice as? UpdatePolicyNotice.OptionalUpdate ?: return
        controller.deferUpdate(notice.recommendedVersionCode)
        savedStateHandle[KEY_LOCALLY_DEFERRED_VERSION] = notice.recommendedVersionCode
        _state.value =
            _state.value.copy(locallyDeferredVersionCode = notice.recommendedVersionCode)
    }

    fun dismissMaintenance() {
        val notice = _state.value.notice as? UpdatePolicyNotice.Maintenance ?: return
        savedStateHandle[KEY_DISMISSED_MAINTENANCE_REVISION] = notice.policyRevision
        _state.value =
            _state.value.copy(dismissedMaintenanceRevision = notice.policyRevision)
    }

    private companion object {
        const val KEY_DISMISSED_MAINTENANCE_REVISION = "dismissed-maintenance-policy-revision"
        const val KEY_LOCALLY_DEFERRED_VERSION = "locally-deferred-version-code"
    }
}
