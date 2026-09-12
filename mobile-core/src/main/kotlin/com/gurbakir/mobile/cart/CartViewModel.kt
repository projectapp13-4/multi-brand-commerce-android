package com.gurbakir.mobile.cart

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gurbakir.storefront.SensitiveCartLineId
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class CartViewModel @Inject constructor(private val repository: CartRepository) : ViewModel() {
    val state: StateFlow<CartState> = repository.state
    private var actionJob: Job? = null

    init {
        refresh()
    }

    fun refresh() = launchAction { repository.refresh() }

    fun increase(line: CartLine) {
        line.nextQuantity?.let { quantity -> launchAction { repository.update(line.id, quantity) } }
    }

    fun decrease(line: CartLine) {
        line.previousQuantity?.let { quantity -> launchAction { repository.update(line.id, quantity) } }
    }

    fun remove(lineId: SensitiveCartLineId) = launchAction { repository.remove(lineId) }

    fun discard() = launchAction { repository.discard() }

    private fun launchAction(action: suspend () -> Unit) {
        if (actionJob?.isActive == true) return
        actionJob = viewModelScope.launch { action() }
    }
}
