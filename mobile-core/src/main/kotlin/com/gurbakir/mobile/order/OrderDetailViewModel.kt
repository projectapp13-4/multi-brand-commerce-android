package com.gurbakir.mobile.order

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gurbakir.account.CustomerOrderDetail
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

enum class OrderDetailPhase {
    LOADING,
    READY,
    UNAVAILABLE,
    FAILED
}

enum class TrackingFeedback {
    OPENED,
    NO_BROWSER,
    REJECTED
}

data class OrderDetailUiState(
    val phase: OrderDetailPhase = OrderDetailPhase.LOADING,
    val order: CustomerOrderDetail? = null,
    val failure: OrderFailure? = null,
    val trackingFeedback: TrackingFeedback? = null
) {
    override fun toString(): String = "OrderDetailUiState(phase=$phase, hasOrder=${order != null}, failure=$failure, " +
        "trackingFeedback=$trackingFeedback)"
}

sealed interface OrderDetailEffect {
    data object ReturnToAccount : OrderDetailEffect
}

@HiltViewModel
class OrderDetailViewModel @Inject constructor(private val controller: OrderController) : ViewModel() {
    private val _state = MutableStateFlow(OrderDetailUiState())
    val state: StateFlow<OrderDetailUiState> = _state.asStateFlow()

    private val effectsChannel = Channel<OrderDetailEffect>(Channel.BUFFERED)
    val effects: Flow<OrderDetailEffect> = effectsChannel.receiveAsFlow()

    private var orderId: String? = null
    private var reloadWhenResumed = false
    private var requestGeneration = 0

    fun start(orderId: String) {
        if (this.orderId == orderId && _state.value.order != null) return
        this.orderId = orderId
        load()
    }

    fun retry() {
        if (_state.value.phase != OrderDetailPhase.LOADING) load()
    }

    fun onTrackingLaunchResult(result: TrackingLaunchResult) {
        _state.value =
            _state.value.copy(
                trackingFeedback =
                    when (result) {
                        TrackingLaunchResult.OPENED -> TrackingFeedback.OPENED
                        TrackingLaunchResult.NO_BROWSER -> TrackingFeedback.NO_BROWSER
                        TrackingLaunchResult.REJECTED -> TrackingFeedback.REJECTED
                    }
            )
    }

    fun clearPrivateContent() {
        if (orderId != null) {
            requestGeneration += 1
            reloadWhenResumed = true
            _state.value = OrderDetailUiState()
        }
    }

    fun onResumed() {
        if (!reloadWhenResumed || _state.value.phase != OrderDetailPhase.LOADING) return
        reloadWhenResumed = false
        load()
    }

    private fun load() {
        val currentOrderId = orderId ?: return
        requestGeneration += 1
        val generation = requestGeneration
        _state.value = OrderDetailUiState()
        viewModelScope.launch {
            val result = controller.loadDetail(currentOrderId)
            if (generation != requestGeneration) return@launch
            when (result) {
                is OrderDetailResult.Content ->
                    _state.value = OrderDetailUiState(OrderDetailPhase.READY, order = result.order)

                OrderDetailResult.Unavailable ->
                    _state.value = OrderDetailUiState(OrderDetailPhase.UNAVAILABLE)

                OrderDetailResult.SignedOut -> {
                    _state.value = OrderDetailUiState(OrderDetailPhase.FAILED)
                    effectsChannel.send(OrderDetailEffect.ReturnToAccount)
                }

                is OrderDetailResult.Failed ->
                    _state.value = OrderDetailUiState(OrderDetailPhase.FAILED, failure = result.reason)
            }
        }
    }
}
