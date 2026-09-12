package com.gurbakir.mobile.checkout

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class CheckoutViewModel @Inject constructor(private val controller: CheckoutController) : ViewModel() {
    val state: StateFlow<CheckoutState> = controller.state
    private var launchJob: Job? = null

    fun start(activity: Activity) {
        if (launchJob?.isActive == true || state.value.busy) return
        launchJob = viewModelScope.launch { controller.start(activity) }
    }
}
