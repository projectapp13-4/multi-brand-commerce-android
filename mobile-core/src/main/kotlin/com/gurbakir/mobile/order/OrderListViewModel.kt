package com.gurbakir.mobile.order

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
import kotlinx.coroutines.launch

enum class OrderListPhase {
    LOADING,
    READY,
    LOADING_MORE,
    FAILED
}

data class OrderListUiState(
    val phase: OrderListPhase = OrderListPhase.LOADING,
    val orders: List<OrderSummaryContent> = emptyList(),
    val nextCursor: String? = null,
    val loaded: Boolean = false,
    val failure: OrderFailure? = null,
    val addedOrderCount: Int = 0
) {
    val busy: Boolean
        get() = phase == OrderListPhase.LOADING || phase == OrderListPhase.LOADING_MORE

    val canLoadMore: Boolean
        get() = phase == OrderListPhase.READY && nextCursor != null

    override fun toString(): String =
        "OrderListUiState(phase=$phase, count=${orders.size}, hasNext=${nextCursor != null}, " +
            "loaded=$loaded, failure=$failure, addedOrderCount=$addedOrderCount)"
}

sealed interface OrderListEffect {
    data object ReturnToAccount : OrderListEffect
}

@HiltViewModel
class OrderListViewModel @Inject constructor(private val controller: OrderController) : ViewModel() {
    private val _state = MutableStateFlow(OrderListUiState())
    val state: StateFlow<OrderListUiState> = _state.asStateFlow()

    private val effectsChannel = Channel<OrderListEffect>(Channel.BUFFERED)
    val effects: Flow<OrderListEffect> = effectsChannel.receiveAsFlow()

    private var reloadWhenResumed = false
    private var requestGeneration = 0

    init {
        loadInitial()
    }

    fun refresh() {
        if (_state.value.busy) return
        loadInitial()
    }

    fun retry() {
        if (_state.value.busy) return
        if (_state.value.orders.isEmpty()) loadInitial() else requestNextPage()
    }

    fun loadNextPage() {
        val current = _state.value
        if (!current.canLoadMore) return
        requestNextPage()
    }

    private fun requestNextPage() {
        val current = _state.value
        val cursor = current.nextCursor ?: return
        if (current.busy) return
        _state.value = current.copy(phase = OrderListPhase.LOADING_MORE, failure = null, addedOrderCount = 0)
        val generation = requestGeneration
        viewModelScope.launch {
            handlePage(controller.loadPage(cursor), cursor, append = true, generation = generation)
        }
    }

    fun clearPrivateContent() {
        requestGeneration += 1
        reloadWhenResumed = true
        _state.value = OrderListUiState()
    }

    fun onResumed() {
        if (!reloadWhenResumed) return
        reloadWhenResumed = false
        loadInitial()
    }

    private fun loadInitial() {
        requestGeneration += 1
        val generation = requestGeneration
        _state.value = OrderListUiState()
        viewModelScope.launch {
            handlePage(
                controller.loadPage(),
                requestedCursor = null,
                append = false,
                generation = generation
            )
        }
    }

    private suspend fun handlePage(
        result: OrderPageResult,
        requestedCursor: String?,
        append: Boolean,
        generation: Int
    ) {
        if (generation != requestGeneration) return
        when (result) {
            is OrderPageResult.Content -> {
                val current = _state.value
                if (append && result.page.nextCursor == requestedCursor) {
                    _state.value = current.copy(phase = OrderListPhase.FAILED, failure = OrderFailure.SERVICE)
                    return
                }
                val existing = if (append) current.orders else emptyList()
                val knownIds = existing.mapTo(mutableSetOf()) { it.routeId }
                val additions = result.page.orders.filter { knownIds.add(it.routeId) }
                _state.value =
                    OrderListUiState(
                        phase = OrderListPhase.READY,
                        orders = existing + additions,
                        nextCursor = result.page.nextCursor,
                        loaded = true,
                        addedOrderCount = additions.size.takeIf { append } ?: 0
                    )
            }

            OrderPageResult.SignedOut -> returnToAccount()

            is OrderPageResult.Failed -> {
                val current = _state.value
                _state.value =
                    if (append && current.orders.isNotEmpty()) {
                        current.copy(phase = OrderListPhase.FAILED, failure = result.reason, addedOrderCount = 0)
                    } else {
                        OrderListUiState(phase = OrderListPhase.FAILED, failure = result.reason)
                    }
            }
        }
    }

    private suspend fun returnToAccount() {
        _state.value = OrderListUiState(phase = OrderListPhase.FAILED)
        effectsChannel.send(OrderListEffect.ReturnToAccount)
    }
}
