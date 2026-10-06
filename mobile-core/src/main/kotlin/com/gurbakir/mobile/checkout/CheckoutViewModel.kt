package com.gurbakir.mobile.checkout

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class CheckoutViewModel @Inject constructor(private val controller: CheckoutController) : ViewModel() {
    val state: StateFlow<CheckoutState> = controller.state
    private var launchJob: Job? = null

    fun start(activity: Activity) {
        if (state.value.busy || state.value.status == CheckoutStatus.CLEANUP_REQUIRED) return
        replaceTerminalFollowUp { controller.start(activity) }
    }

    fun retryCleanup() {
        if (state.value.status != CheckoutStatus.CLEANUP_REQUIRED) return
        launchJob = viewModelScope.launch { controller.retryCleanup() }
    }

    private fun replaceTerminalFollowUp(action: suspend () -> Unit) {
        val previous = launchJob
        launchJob = viewModelScope.launch {
            previous?.cancelAndJoin()
            action()
        }
    }
}
