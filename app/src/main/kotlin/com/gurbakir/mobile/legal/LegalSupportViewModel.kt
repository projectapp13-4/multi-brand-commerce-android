package com.gurbakir.mobile.legal

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

@HiltViewModel
class LegalSupportViewModel @Inject constructor(repository: LegalSupportRepository) : ViewModel() {
    private val mutableState = MutableStateFlow(LegalSupportUiState(pages = repository.pages()))
    val state: StateFlow<LegalSupportUiState> = mutableState.asStateFlow()

    fun onLaunchResult(pageId: LegalPageId, result: OwnedPageLaunchResult) {
        mutableState.update { current ->
            when (result) {
                OwnedPageLaunchResult.OPENED ->
                    current.copy(
                        feedback = LegalPageFeedback(pageId, LegalPageFeedbackType.OPENING),
                        pendingReturnPageId = pageId,
                        focusRequestPageId = null
                    )

                OwnedPageLaunchResult.NO_BROWSER ->
                    current.copy(
                        feedback = LegalPageFeedback(pageId, LegalPageFeedbackType.NO_BROWSER),
                        pendingReturnPageId = null,
                        focusRequestPageId = pageId
                    )

                OwnedPageLaunchResult.REJECTED ->
                    current.copy(
                        feedback = LegalPageFeedback(pageId, LegalPageFeedbackType.REJECTED),
                        pendingReturnPageId = null,
                        focusRequestPageId = pageId
                    )
            }
        }
    }

    fun onActivityResumed() {
        mutableState.update { current ->
            val returnedPageId = current.pendingReturnPageId ?: return@update current
            current.copy(
                feedback = LegalPageFeedback(returnedPageId, LegalPageFeedbackType.RETURNED),
                pendingReturnPageId = null,
                focusRequestPageId = returnedPageId
            )
        }
    }

    fun onFocusRequestHandled(pageId: LegalPageId) {
        mutableState.update { current ->
            if (current.focusRequestPageId == pageId) current.copy(focusRequestPageId = null) else current
        }
    }
}
