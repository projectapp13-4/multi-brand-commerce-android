package com.gurbakir.mobile.accountdeletion

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AccountDeletionPhase {
    CHECKING,
    READY,
    CLEARING,
    COMPLETED,
    UNAVAILABLE
}

data class AccountDeletionUiState(
    val phase: AccountDeletionPhase = AccountDeletionPhase.CHECKING,
    val privacyPage: DeletionPageDescriptor? = null,
    val requestPage: DeletionPageDescriptor? = null,
    val externalFeedback: DeletionPageFeedback? = null,
    val pendingReturnPageId: DeletionPageId? = null,
    val clearSearchHistory: Boolean = true,
    val clearWishlist: Boolean = false,
    val discardCart: Boolean = true,
    val confirmationVisible: Boolean = false,
    val result: AccountDeletionLocalResult? = null
) {
    val busy: Boolean
        get() = phase == AccountDeletionPhase.CHECKING || phase == AccountDeletionPhase.CLEARING

    val ready: Boolean
        get() = phase == AccountDeletionPhase.READY
}

sealed interface AccountDeletionEffect {
    data object ReturnToAccount : AccountDeletionEffect
}

@HiltViewModel
@Suppress("TooManyFunctions") // Compose event handlers keep UI events explicit and independently testable.
class AccountDeletionViewModel
@Inject
constructor(
    private val controller: AccountDeletionController,
    pageSource: DeletionPageSource
) : ViewModel() {
    private val pages = pageSource.pages().associateBy(DeletionPageDescriptor::id)
    private val mutableState =
        MutableStateFlow(
            AccountDeletionUiState(
                privacyPage = pages[DeletionPageId.PRIVACY],
                requestPage = pages[DeletionPageId.ACCOUNT_DELETION_REQUEST]
            )
        )
    val state: StateFlow<AccountDeletionUiState> = mutableState.asStateFlow()

    private val effectChannel = Channel<AccountDeletionEffect>(Channel.BUFFERED)
    val effects: Flow<AccountDeletionEffect> = effectChannel.receiveAsFlow()

    init {
        checkSession()
    }

    fun retry() {
        if (mutableState.value.busy) return
        checkSession()
    }

    fun setClearSearchHistory(value: Boolean) = updateReady { copy(clearSearchHistory = value) }

    fun setClearWishlist(value: Boolean) = updateReady { copy(clearWishlist = value) }

    fun setDiscardCart(value: Boolean) = updateReady { copy(discardCart = value) }

    fun requestLocalClearConfirmation() = updateReady { copy(confirmationVisible = true) }

    fun dismissLocalClearConfirmation() {
        mutableState.update { current -> current.copy(confirmationVisible = false) }
    }

    fun confirmLocalClearAndSignOut() {
        val current = mutableState.value
        if (!current.ready || !current.confirmationVisible) return
        val plan =
            AccountDeletionClearPlan(
                clearSearchHistory = current.clearSearchHistory,
                clearWishlist = current.clearWishlist,
                discardCart = current.discardCart
            )
        mutableState.value = current.copy(phase = AccountDeletionPhase.CLEARING, confirmationVisible = false)
        viewModelScope.launch {
            val result = controller.clearLocalDataAndSignOut(plan)
            mutableState.update {
                it.copy(
                    phase = AccountDeletionPhase.COMPLETED,
                    externalFeedback = null,
                    pendingReturnPageId = null,
                    result = result
                )
            }
        }
    }

    fun onLaunchResult(pageId: DeletionPageId, result: DeletionPageLaunchResult) {
        if (!mutableState.value.ready || pageId !in DeletionPageId.entries) return
        mutableState.update { current ->
            when (result) {
                DeletionPageLaunchResult.OPENED ->
                    current.copy(
                        externalFeedback = DeletionPageFeedback(pageId, DeletionPageFeedbackType.OPENING),
                        pendingReturnPageId = pageId
                    )

                DeletionPageLaunchResult.NO_BROWSER ->
                    current.copy(
                        externalFeedback = DeletionPageFeedback(pageId, DeletionPageFeedbackType.NO_BROWSER),
                        pendingReturnPageId = null
                    )

                DeletionPageLaunchResult.REJECTED ->
                    current.copy(
                        externalFeedback = DeletionPageFeedback(pageId, DeletionPageFeedbackType.REJECTED),
                        pendingReturnPageId = null
                    )
            }
        }
    }

    fun onActivityResumed() {
        mutableState.update { current ->
            val pageId = current.pendingReturnPageId ?: return@update current
            current.copy(
                externalFeedback = DeletionPageFeedback(pageId, DeletionPageFeedbackType.RETURNED),
                pendingReturnPageId = null
            )
        }
    }

    fun finish() {
        if (mutableState.value.phase == AccountDeletionPhase.COMPLETED) {
            viewModelScope.launch { effectChannel.send(AccountDeletionEffect.ReturnToAccount) }
        }
    }

    private fun checkSession() {
        mutableState.update { it.copy(phase = AccountDeletionPhase.CHECKING, result = null) }
        viewModelScope.launch {
            when (controller.checkSession()) {
                AccountDeletionSessionCheck.READY ->
                    mutableState.update { it.copy(phase = AccountDeletionPhase.READY) }

                AccountDeletionSessionCheck.SIGNED_OUT ->
                    effectChannel.send(AccountDeletionEffect.ReturnToAccount)

                AccountDeletionSessionCheck.UNAVAILABLE ->
                    mutableState.update { it.copy(phase = AccountDeletionPhase.UNAVAILABLE) }
            }
        }
    }

    private fun updateReady(transform: AccountDeletionUiState.() -> AccountDeletionUiState) {
        mutableState.update { current -> if (current.ready) current.transform() else current }
    }
}
