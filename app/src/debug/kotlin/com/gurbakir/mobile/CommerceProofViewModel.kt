package com.gurbakir.mobile

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class CommerceProofViewModel
@Inject
constructor(private val controller: CommerceProofController) : ViewModel() {
    private val _state = MutableStateFlow(CommerceProofUiState())
    val state: StateFlow<CommerceProofUiState> = _state.asStateFlow()

    init {
        runAction(CommerceProofPhase.RESTORING) { controller.restore() }
    }

    fun createCart() = runIfIdle(CommerceProofPhase.CREATING_CART, controller::createCart)

    fun addLine() = runIfActive(CommerceProofPhase.UPDATING_CART, controller::addLine)

    fun incrementLine() = runIfActive(CommerceProofPhase.UPDATING_CART, controller::incrementFirstLine)

    fun removeAllLines() = runIfActive(CommerceProofPhase.UPDATING_CART, controller::removeAllLines)

    fun preloadCheckout(activity: Activity) =
        runIfActive(CommerceProofPhase.PRELOADING_CHECKOUT) { controller.preloadCheckout(activity) }

    fun presentCheckout(activity: Activity) =
        runIfActive(CommerceProofPhase.PRESENTING_CHECKOUT) { controller.presentCheckout(activity) }

    private fun runIfIdle(phase: CommerceProofPhase, action: suspend () -> CommerceProofResult) {
        if (_state.value.busy) return
        runAction(phase, action)
    }

    private fun runIfActive(phase: CommerceProofPhase, action: suspend () -> CommerceProofResult) {
        if (_state.value.busy || _state.value.snapshot == null) return
        runAction(phase, action)
    }

    private fun runAction(phase: CommerceProofPhase, action: suspend () -> CommerceProofResult) {
        _state.value = _state.value.copy(phase = phase)
        viewModelScope.launch {
            val result = action()
            _state.value = result.toUiState(previous = _state.value)
            if (result is CommerceProofResult.CheckoutStarted) {
                result.events.collect(::consumeCheckoutEvent)
            }
        }
    }

    private fun consumeCheckoutEvent(event: CommerceProofEvent) {
        _state.value = when (event) {
            CommerceProofEvent.CHECKOUT_COMPLETED ->
                CommerceProofUiState(phase = CommerceProofPhase.CHECKOUT_COMPLETED)

            CommerceProofEvent.CHECKOUT_CANCELLED ->
                if (_state.value.phase == CommerceProofPhase.CHECKOUT_COMPLETED) {
                    _state.value
                } else {
                    _state.value.copy(phase = CommerceProofPhase.CHECKOUT_CANCELLED)
                }

            CommerceProofEvent.CHECKOUT_FAILED ->
                _state.value.copy(phase = CommerceProofPhase.CHECKOUT_FAILED)

            CommerceProofEvent.EXTERNAL_LINK_REQUIRES_POLICY ->
                _state.value.copy(phase = CommerceProofPhase.EXTERNAL_LINK_BLOCKED)
        }
    }
}

data class CommerceProofUiState(
    val phase: CommerceProofPhase = CommerceProofPhase.RESTORING,
    val variantAvailable: Boolean = false,
    val snapshot: CommerceProofSnapshot? = null,
    val failure: CommerceProofFailure? = null,
    val cartRetainedAfterFailure: Boolean = false
) {
    val busy: Boolean
        get() = phase in COMMERCE_BUSY_PHASES

    val canCreateCart: Boolean
        get() = !busy && variantAvailable && snapshot == null

    val canModifyCart: Boolean
        get() = !busy && snapshot != null

    val canCheckout: Boolean
        get() = canModifyCart && snapshot?.totalQuantity?.let { it > 0 } == true
}

enum class CommerceProofPhase {
    RESTORING,
    EMPTY,
    ACTIVE,
    CREATING_CART,
    UPDATING_CART,
    PRELOADING_CHECKOUT,
    CHECKOUT_PRELOADED,
    PRESENTING_CHECKOUT,
    CHECKOUT_PRESENTED,
    CHECKOUT_CANCELLED,
    CHECKOUT_COMPLETED,
    CHECKOUT_FAILED,
    EXTERNAL_LINK_BLOCKED,
    FAILED
}

private val COMMERCE_BUSY_PHASES = setOf(
    CommerceProofPhase.RESTORING,
    CommerceProofPhase.CREATING_CART,
    CommerceProofPhase.UPDATING_CART,
    CommerceProofPhase.PRELOADING_CHECKOUT,
    CommerceProofPhase.PRESENTING_CHECKOUT
)

private fun CommerceProofResult.toUiState(previous: CommerceProofUiState): CommerceProofUiState = when (this) {
    is CommerceProofResult.Active ->
        CommerceProofUiState(
            phase = CommerceProofPhase.ACTIVE,
            variantAvailable = true,
            snapshot = snapshot
        )

    is CommerceProofResult.Empty ->
        CommerceProofUiState(
            phase = CommerceProofPhase.EMPTY,
            variantAvailable = variantAvailable
        )

    is CommerceProofResult.CheckoutStarted ->
        previous.copy(
            phase =
                if (previous.phase == CommerceProofPhase.PRELOADING_CHECKOUT) {
                    CommerceProofPhase.CHECKOUT_PRELOADED
                } else {
                    CommerceProofPhase.CHECKOUT_PRESENTED
                },
            failure = null
        )

    is CommerceProofResult.Failed ->
        previous.copy(
            phase = CommerceProofPhase.FAILED,
            failure = failure,
            snapshot = previous.snapshot.takeIf { cartRetained },
            cartRetainedAfterFailure = cartRetained
        )
}
