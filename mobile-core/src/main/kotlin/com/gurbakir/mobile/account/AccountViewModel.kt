package com.gurbakir.mobile.account

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

enum class AccountPhase {
    RESTORING,
    SIGNED_OUT,
    PREPARING,
    AWAITING_BROWSER,
    EXCHANGING,
    AUTHENTICATED,
    REFRESHING,
    LOGGING_OUT,
    FAILED
}

data class AccountUiState(
    val phase: AccountPhase = AccountPhase.RESTORING,
    val summary: AccountSummary? = null,
    val notices: Set<AccountNotice> = emptySet(),
    val failure: AccountFailure? = null,
    val retryable: Boolean = false
) {
    val busy: Boolean
        get() = phase in BUSY_PHASES

    val canSignIn: Boolean
        get() = !busy && summary == null

    val canUseSessionActions: Boolean
        get() = !busy && summary != null
}

sealed interface AccountEffect {
    data class LaunchAuthorization(val plan: CustomerAccountAuthorizationPlan) : AccountEffect {
        override fun toString(): String = "LaunchAuthorization(<redacted>)"
    }
}

@HiltViewModel
class AccountViewModel
@Inject
constructor(private val controller: AccountController) : ViewModel() {
    private val _state = MutableStateFlow(AccountUiState())
    val state: StateFlow<AccountUiState> = _state.asStateFlow()

    private val effectChannel = Channel<AccountEffect>(Channel.BUFFERED)
    val effects: Flow<AccountEffect> = effectChannel.receiveAsFlow()
    private var actionGeneration = 0L

    init {
        runAction(AccountPhase.RESTORING, preserveSummary = false) { controller.restore() }
    }

    fun startAuthorization() {
        if (!_state.value.canSignIn) return
        val generation = ++actionGeneration
        _state.value = AccountUiState(AccountPhase.PREPARING)
        viewModelScope.launch {
            when (val preparation = controller.prepareAuthorization()) {
                is AccountPreparation.Ready -> {
                    if (generation != actionGeneration) return@launch
                    _state.value = AccountUiState(AccountPhase.AWAITING_BROWSER)
                    effectChannel.send(AccountEffect.LaunchAuthorization(preparation.plan))
                }

                is AccountPreparation.Failed -> {
                    if (generation != actionGeneration) return@launch
                    _state.value =
                        AccountUiState(
                            phase = AccountPhase.FAILED,
                            failure = preparation.reason,
                            retryable = true
                        )
                }
            }
        }
    }

    fun authorizationLaunchFailed() {
        if (_state.value.phase != AccountPhase.AWAITING_BROWSER) return
        actionGeneration += 1
        controller.cancelAuthorization()
        _state.value =
            AccountUiState(
                phase = AccountPhase.FAILED,
                failure = AccountFailure.DISCOVERY,
                retryable = true
            )
    }

    fun consumeAuthorizationResult(rawRedirectUri: String?) {
        when {
            rawRedirectUri == null && _state.value.phase == AccountPhase.AWAITING_BROWSER -> {
                actionGeneration += 1
                controller.cancelAuthorization()
                _state.value =
                    AccountUiState(
                        phase = AccountPhase.SIGNED_OUT,
                        notices = setOf(AccountNotice.AUTHORIZATION_CANCELLED)
                    )
            }

            rawRedirectUri != null && _state.value.phase in AUTHORIZATION_CALLBACK_PHASES ->
                runAction(AccountPhase.EXCHANGING, preserveSummary = false) {
                    controller.consumeCallback(rawRedirectUri)
                }
        }
    }

    fun retry() {
        if (_state.value.busy) return
        runAction(AccountPhase.RESTORING, preserveSummary = true) { controller.restore() }
    }

    fun refresh() {
        if (!_state.value.canUseSessionActions) return
        runAction(AccountPhase.REFRESHING, preserveSummary = true) { controller.refresh() }
    }

    fun logout() {
        if (!_state.value.canUseSessionActions) return
        runAction(AccountPhase.LOGGING_OUT, preserveSummary = false) { controller.logout() }
    }

    private fun runAction(activePhase: AccountPhase, preserveSummary: Boolean, action: suspend () -> AccountResult) {
        val generation = ++actionGeneration
        val previous = _state.value
        _state.value =
            AccountUiState(
                phase = activePhase,
                summary = previous.summary.takeIf { preserveSummary }
            )
        viewModelScope.launch {
            val result = action()
            if (generation == actionGeneration) {
                _state.value = result.toUiState(previous.summary.takeIf { preserveSummary })
            }
        }
    }
}

private val BUSY_PHASES = setOf(
    AccountPhase.RESTORING,
    AccountPhase.PREPARING,
    AccountPhase.AWAITING_BROWSER,
    AccountPhase.EXCHANGING,
    AccountPhase.REFRESHING,
    AccountPhase.LOGGING_OUT
)

private val AUTHORIZATION_CALLBACK_PHASES = setOf(
    AccountPhase.RESTORING,
    AccountPhase.AWAITING_BROWSER
)

private fun AccountResult.toUiState(previousSummary: AccountSummary?): AccountUiState = when (this) {
    is AccountResult.Authenticated ->
        AccountUiState(
            phase = AccountPhase.AUTHENTICATED,
            summary = summary,
            notices = notices
        )

    is AccountResult.SignedOut ->
        AccountUiState(
            phase = AccountPhase.SIGNED_OUT,
            notices = notices
        )

    AccountResult.Cancelled ->
        AccountUiState(
            phase = AccountPhase.SIGNED_OUT,
            notices = setOf(AccountNotice.AUTHORIZATION_CANCELLED)
        )

    is AccountResult.Failed -> {
        if (reason != AccountFailure.SECURE_STORAGE && sessionRetained && previousSummary != null) {
            AccountUiState(
                phase = AccountPhase.AUTHENTICATED,
                summary = previousSummary,
                failure = reason,
                retryable = retryable
            )
        } else {
            AccountUiState(
                phase = AccountPhase.FAILED,
                failure = reason,
                retryable = retryable
            )
        }
    }
}
