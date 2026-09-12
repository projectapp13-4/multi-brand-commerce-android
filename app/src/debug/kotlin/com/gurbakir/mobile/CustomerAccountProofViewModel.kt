package com.gurbakir.mobile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gurbakir.account.oauth.CustomerAccountAuthorizationPlan
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

@HiltViewModel
class CustomerAccountProofViewModel
@Inject
constructor(private val controller: CustomerAccountProofController) :
    ViewModel() {
    private val _state = MutableStateFlow(CustomerAccountProofUiState())
    val state: StateFlow<CustomerAccountProofUiState> = _state.asStateFlow()

    private val effectChannel = Channel<CustomerAccountProofEffect>(Channel.BUFFERED)
    val effects: Flow<CustomerAccountProofEffect> = effectChannel.receiveAsFlow()

    init {
        runAction(CustomerAccountProofPhase.RESTORING) { controller.restore() }
    }

    fun startAuthorization() {
        if (_state.value.busy) return
        _state.value = CustomerAccountProofUiState(CustomerAccountProofPhase.PREPARING)
        viewModelScope.launch {
            when (val preparation = controller.prepareAuthorization()) {
                is CustomerAccountProofPreparation.Ready -> {
                    _state.value = CustomerAccountProofUiState(CustomerAccountProofPhase.AWAITING_BROWSER)
                    effectChannel.send(CustomerAccountProofEffect.LaunchAuthorization(preparation.plan))
                }

                is CustomerAccountProofPreparation.Failed -> {
                    _state.value = preparation.reason.toUiState(CustomerAccountProofPhase.DISCOVERY_FAILED)
                }
            }
        }
    }

    fun authorizationLaunchFailed() {
        if (_state.value.phase != CustomerAccountProofPhase.AWAITING_BROWSER) return
        controller.cancelAuthorization()
        _state.value = CustomerAccountProofUiState(CustomerAccountProofPhase.LAUNCH_FAILED)
    }

    fun consumeAuthorizationResult(rawRedirectUri: String?) {
        if (_state.value.phase != CustomerAccountProofPhase.AWAITING_BROWSER) return
        if (rawRedirectUri == null) {
            controller.cancelAuthorization()
            _state.value = CustomerAccountProofUiState(CustomerAccountProofPhase.CANCELLED)
            return
        }
        runAction(CustomerAccountProofPhase.EXCHANGING) {
            controller.consumeCallback(rawRedirectUri)
        }
    }

    fun refresh() {
        if (_state.value.phase != CustomerAccountProofPhase.AUTHENTICATED) return
        runAction(CustomerAccountProofPhase.REFRESHING) { controller.refresh() }
    }

    fun logout() {
        if (_state.value.phase != CustomerAccountProofPhase.AUTHENTICATED) return
        runAction(CustomerAccountProofPhase.LOGGING_OUT) { controller.logout() }
    }

    private fun runAction(activePhase: CustomerAccountProofPhase, action: suspend () -> CustomerAccountProofResult) {
        _state.value = CustomerAccountProofUiState(activePhase)
        viewModelScope.launch {
            _state.value = action().toUiState()
        }
    }
}

sealed interface CustomerAccountProofEffect {
    data class LaunchAuthorization(val plan: CustomerAccountAuthorizationPlan) : CustomerAccountProofEffect {
        override fun toString(): String = "LaunchAuthorization(<redacted>)"
    }
}

data class CustomerAccountProofUiState(
    val phase: CustomerAccountProofPhase = CustomerAccountProofPhase.RESTORING,
    val failure: CustomerAccountProofFailure? = null
) {
    val busy: Boolean
        get() = phase in BUSY_PHASES

    val canSignIn: Boolean
        get() = !busy && phase != CustomerAccountProofPhase.AUTHENTICATED

    val canUseSessionActions: Boolean
        get() = phase == CustomerAccountProofPhase.AUTHENTICATED
}

enum class CustomerAccountProofPhase {
    RESTORING,
    SIGNED_OUT,
    PREPARING,
    AWAITING_BROWSER,
    EXCHANGING,
    AUTHENTICATED,
    REFRESHING,
    LOGGING_OUT,
    CANCELLED,
    DISCOVERY_FAILED,
    CALLBACK_FAILED,
    TOKEN_FAILED,
    IDENTITY_FAILED,
    LAUNCH_FAILED,
    LOGOUT_REMOTE_FAILED
}

private val BUSY_PHASES = setOf(
    CustomerAccountProofPhase.RESTORING,
    CustomerAccountProofPhase.PREPARING,
    CustomerAccountProofPhase.AWAITING_BROWSER,
    CustomerAccountProofPhase.EXCHANGING,
    CustomerAccountProofPhase.REFRESHING,
    CustomerAccountProofPhase.LOGGING_OUT
)

private fun CustomerAccountProofResult.toUiState(): CustomerAccountProofUiState = when (this) {
    CustomerAccountProofResult.Authenticated ->
        CustomerAccountProofUiState(CustomerAccountProofPhase.AUTHENTICATED)

    CustomerAccountProofResult.SignedOut ->
        CustomerAccountProofUiState(CustomerAccountProofPhase.SIGNED_OUT)

    CustomerAccountProofResult.Cancelled ->
        CustomerAccountProofUiState(CustomerAccountProofPhase.CANCELLED)

    is CustomerAccountProofResult.Failed -> reason.toUiState()
}

private fun CustomerAccountProofFailure.toUiState(
    defaultPhase: CustomerAccountProofPhase? = null
): CustomerAccountProofUiState = CustomerAccountProofUiState(
    phase = defaultPhase ?: when (this) {
        CustomerAccountProofFailure.DISCOVERY -> CustomerAccountProofPhase.DISCOVERY_FAILED

        CustomerAccountProofFailure.CALLBACK -> CustomerAccountProofPhase.CALLBACK_FAILED

        CustomerAccountProofFailure.TOKEN_REJECTED,
        CustomerAccountProofFailure.TOKEN_TRANSIENT,
        CustomerAccountProofFailure.TOKEN_INVALID,
        CustomerAccountProofFailure.TOKEN_MISSING_FIELDS,
        CustomerAccountProofFailure.TOKEN_TYPE,
        CustomerAccountProofFailure.TOKEN_SCOPE,
        CustomerAccountProofFailure.TOKEN_EXPIRED,
        CustomerAccountProofFailure.TOKEN_IDENTITY -> CustomerAccountProofPhase.TOKEN_FAILED

        CustomerAccountProofFailure.IDENTITY_AUTHENTICATION,
        CustomerAccountProofFailure.IDENTITY_DISCOVERY,
        CustomerAccountProofFailure.IDENTITY_GRAPHQL,
        CustomerAccountProofFailure.IDENTITY_TRANSPORT -> CustomerAccountProofPhase.IDENTITY_FAILED

        CustomerAccountProofFailure.REMOTE_LOGOUT -> CustomerAccountProofPhase.LOGOUT_REMOTE_FAILED
    },
    failure = this
)
