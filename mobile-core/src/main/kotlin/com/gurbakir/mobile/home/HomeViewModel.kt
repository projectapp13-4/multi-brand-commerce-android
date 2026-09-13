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
    private val loadingClock: HomeLoadingClock,
    private val editorialClock: HomeEditorialClock
) : ViewModel() {
    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    private var requestJob: Job? = null
    private var expiryJob: Job? = null

    init {
        start(HomeLoadTrigger.INITIAL)
    }

    fun refreshContent() {
        if (_state.value.requestActive) return
        start(HomeLoadTrigger.MANUAL_REFRESH)
    }

    fun onHomeResumed() {
        val deadline = _state.value.presentation?.editorialExpiresAtMillis ?: return
        if (editorialClock.nowMillis() >= deadline && !_state.value.requestActive) {
            start(HomeLoadTrigger.EXPIRY)
        }
    }

    private fun start(trigger: HomeLoadTrigger) {
        if (trigger == HomeLoadTrigger.EXPIRY) expiryJob?.cancel()
        _state.update { current ->
            val retained = if (trigger == HomeLoadTrigger.EXPIRY) null else current.presentation
            current.copy(
                presentation = retained?.copy(refreshing = true),
                loading = retained == null,
                slowLoading = false,
                requestActive = true,
                failure = null,
                expired = trigger == HomeLoadTrigger.EXPIRY
            )
        }
        requestJob = viewModelScope.launch {
            val slowJob = launch {
                loadingClock.awaitSlowLoading()
                _state.update { state -> if (state.requestActive) state.copy(slowLoading = true) else state }
            }
            val result = repository.load(trigger)
            slowJob.cancel()
            when (result) {
                is HomeLoadResult.Accepted -> accept(result.presentation)

                is HomeLoadResult.Failed -> fail(result.failure, trigger)

                HomeLoadResult.Superseded ->
                    _state.update { state ->
                        state.copy(
                            presentation = state.presentation?.copy(refreshing = false),
                            loading = false,
                            slowLoading = false,
                            requestActive = false
                        )
                    }
            }
        }
    }

    private fun accept(presentation: HomePresentation) {
        val settled = presentation.copy(refreshing = false)
        _state.value = HomeUiState(
            presentation = settled,
            loading = false,
            slowLoading = false,
            requestActive = false,
            failure = null,
            expired = false
        )
        installExpiry(settled.editorialExpiresAtMillis)
    }

    private fun fail(failure: HomeLoadFailure, trigger: HomeLoadTrigger) {
        val retained = if (trigger == HomeLoadTrigger.MANUAL_REFRESH) {
            _state.value.presentation?.copy(refreshing = false)
        } else {
            null
        }
        _state.value = HomeUiState(
            presentation = retained,
            loading = false,
            slowLoading = false,
            requestActive = false,
            failure = failure,
            expired = trigger == HomeLoadTrigger.EXPIRY
        )
        if (retained != null) installExpiry(retained.editorialExpiresAtMillis)
    }

    private fun installExpiry(deadlineMillis: Long?) {
        expiryJob?.cancel()
        if (deadlineMillis == null) return
        expiryJob = viewModelScope.launch {
            editorialClock.awaitUntil(deadlineMillis)
            if (editorialClock.nowMillis() >= deadlineMillis && !_state.value.requestActive) {
                start(HomeLoadTrigger.EXPIRY)
            }
        }
    }
}

data class HomeUiState(
    val presentation: HomePresentation? = null,
    val loading: Boolean = true,
    val slowLoading: Boolean = false,
    val requestActive: Boolean = true,
    val failure: HomeLoadFailure? = null,
    val expired: Boolean = false
)
