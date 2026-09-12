package com.gurbakir.mobile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gurbakir.firebase.RemoteConfigResult
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class FirebaseProofPhase {
    READY,
    REFRESHING_REMOTE_CONFIG,
    REMOTE_CONFIG_FETCHED,
    REMOTE_CONFIG_LOCAL_DEFAULTS,
    AWAITING_NOTIFICATION_PERMISSION,
    NOTIFICATION_PERMISSION_DENIED,
    REGISTERING_PUSH,
    PUSH_REGISTERED,
    PUSH_REGISTRATION_FAILED,
    PUSH_NOTIFICATION_OPENED,
    UNREGISTERING_PUSH,
    PUSH_UNREGISTERED,
    PUSH_UNREGISTER_FAILED
}

data class FirebaseProofUiState(
    val phase: FirebaseProofPhase = FirebaseProofPhase.READY,
    val busy: Boolean = false,
    val pushRegistered: Boolean = false
)

@HiltViewModel
class FirebaseProofViewModel
@Inject
constructor(private val controller: FirebaseProofController) : ViewModel() {
    private val _state =
        MutableStateFlow(
            FirebaseProofUiState(pushRegistered = controller.hasStoredPushConsent())
        )
    val state: StateFlow<FirebaseProofUiState> = _state.asStateFlow()

    fun refreshRemoteConfig() = launchExclusive(FirebaseProofPhase.REFRESHING_REMOTE_CONFIG) {
        val result = controller.refreshRemoteConfig()
        _state.update {
            it.copy(
                phase =
                    when (result) {
                        is RemoteConfigResult.Fetched -> FirebaseProofPhase.REMOTE_CONFIG_FETCHED
                        RemoteConfigResult.LocalDefaults -> FirebaseProofPhase.REMOTE_CONFIG_LOCAL_DEFAULTS
                    },
                busy = false
            )
        }
    }

    fun requestPushPermission() {
        if (_state.value.busy) return
        _state.update { it.copy(phase = FirebaseProofPhase.AWAITING_NOTIFICATION_PERMISSION) }
    }

    fun notificationPermissionResult(granted: Boolean) {
        if (granted) {
            registerPush()
        } else {
            _state.update { it.copy(phase = FirebaseProofPhase.NOTIFICATION_PERMISSION_DENIED) }
        }
    }

    fun registerPush() = launchExclusive(FirebaseProofPhase.REGISTERING_PUSH) {
        val registered = controller.registerPushAfterConsent()
        _state.update {
            it.copy(
                phase =
                    if (registered) {
                        FirebaseProofPhase.PUSH_REGISTERED
                    } else {
                        FirebaseProofPhase.PUSH_REGISTRATION_FAILED
                    },
                busy = false,
                pushRegistered = registered
            )
        }
    }

    fun notificationOpened() {
        _state.update {
            it.copy(
                phase = FirebaseProofPhase.PUSH_NOTIFICATION_OPENED,
                pushRegistered = true
            )
        }
    }

    fun unregisterPush() = launchExclusive(FirebaseProofPhase.UNREGISTERING_PUSH) {
        val unregistered = controller.unregisterPush()
        _state.update {
            it.copy(
                phase =
                    if (unregistered) {
                        FirebaseProofPhase.PUSH_UNREGISTERED
                    } else {
                        FirebaseProofPhase.PUSH_UNREGISTER_FAILED
                    },
                busy = false,
                pushRegistered = !unregistered && it.pushRegistered
            )
        }
    }

    private fun launchExclusive(phase: FirebaseProofPhase, operation: suspend () -> Unit) {
        if (_state.value.busy) return
        _state.update { it.copy(phase = phase, busy = true) }
        viewModelScope.launch { operation() }
    }
}
