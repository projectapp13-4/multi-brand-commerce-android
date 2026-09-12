package com.gurbakir.mobile.address

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gurbakir.account.CustomerAddressIssue
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

enum class AddressListPhase {
    LOADING,
    READY,
    MUTATING,
    FAILED
}

enum class AddressListFailure {
    CONNECTION,
    SERVICE,
    CONFLICT,
    SAVE_UNCONFIRMED,
    DEFAULT_ADDRESS_PROTECTED,
    SERVER_REJECTED
}

enum class AddressListNotice {
    DEFAULT_UPDATED,
    DELETED
}

enum class AddressConfirmationType {
    SET_DEFAULT,
    DELETE
}

data class AddressConfirmation(val addressId: String, val type: AddressConfirmationType) {
    override fun toString(): String = "AddressConfirmation(addressId=<redacted>, type=$type)"
}

data class AddressListUiState(
    val phase: AddressListPhase = AddressListPhase.LOADING,
    val addresses: List<AddressContent> = emptyList(),
    val loaded: Boolean = false,
    val confirmation: AddressConfirmation? = null,
    val failure: AddressListFailure? = null,
    val notice: AddressListNotice? = null
) {
    val busy: Boolean
        get() = phase == AddressListPhase.LOADING || phase == AddressListPhase.MUTATING

    val canReload: Boolean
        get() = !busy

    override fun toString(): String = "AddressListUiState(phase=$phase, count=${addresses.size}, loaded=$loaded, " +
        "confirmation=${confirmation?.type}, failure=$failure, notice=$notice)"
}

sealed interface AddressListEffect {
    data object ReturnToAccount : AddressListEffect
}

@HiltViewModel
class AddressListViewModel
@Inject
constructor(private val controller: AddressController) : ViewModel() {
    private val _state = MutableStateFlow(AddressListUiState())
    val state: StateFlow<AddressListUiState> = _state.asStateFlow()

    private val effectsChannel = Channel<AddressListEffect>(Channel.BUFFERED)
    val effects: Flow<AddressListEffect> = effectsChannel.receiveAsFlow()

    private var refreshWhenResumed = false

    init {
        load()
    }

    fun reload() {
        if (_state.value.busy) return
        load()
    }

    fun refreshAfterForm() {
        refreshWhenResumed = true
    }

    fun onResumed() {
        if (!refreshWhenResumed || _state.value.busy) return
        refreshWhenResumed = false
        load()
    }

    fun requestConfirmation(addressId: String, type: AddressConfirmationType) {
        _state.value.requestConfirmation(addressId, type)?.let {
            _state.value = it
        }
    }

    fun dismissConfirmation() {
        if (_state.value.phase != AddressListPhase.MUTATING) {
            _state.value = _state.value.copy(confirmation = null)
        }
    }

    fun confirm() {
        val current = _state.value
        val confirmation = current.confirmation
        val address = confirmation?.let { candidate ->
            current.addresses.firstOrNull { it.id == candidate.addressId }
        }
        if (!current.busy && confirmation != null && address != null) {
            _state.value =
                current.copy(
                    phase = AddressListPhase.MUTATING,
                    confirmation = null,
                    failure = null,
                    notice = null
                )
            viewModelScope.launch {
                val result = when (confirmation.type) {
                    AddressConfirmationType.SET_DEFAULT -> controller.setDefault(address.id)

                    AddressConfirmationType.DELETE ->
                        controller.deleteAddress(address.id, address.toInput())
                }
                handleActionResult(result, confirmation.type)
            }
        }
    }

    private fun load(notice: AddressListNotice? = null) {
        _state.value = _state.value.copy(phase = AddressListPhase.LOADING, confirmation = null, failure = null)
        viewModelScope.launch { handleLoadResult(controller.loadAddresses(), notice) }
    }

    private suspend fun handleLoadResult(result: AddressLoadResult, notice: AddressListNotice?) {
        when (result) {
            is AddressLoadResult.Content ->
                _state.value =
                    AddressListUiState(
                        phase = AddressListPhase.READY,
                        addresses = result.addresses,
                        loaded = true,
                        notice = notice
                    )

            AddressLoadResult.SignedOut -> returnToAccount()

            is AddressLoadResult.Failed ->
                _state.value =
                    _state.value.copy(
                        phase = AddressListPhase.FAILED,
                        failure = result.reason.toListFailure(),
                        notice = notice
                    )
        }
    }

    private suspend fun handleActionResult(result: AddressActionResult, type: AddressConfirmationType) {
        when (result) {
            is AddressActionResult.Confirmed ->
                load(
                    if (type == AddressConfirmationType.SET_DEFAULT) {
                        AddressListNotice.DEFAULT_UPDATED
                    } else {
                        AddressListNotice.DELETED
                    }
                )

            is AddressActionResult.Rejected ->
                _state.value =
                    _state.value.failed(
                        if (CustomerAddressIssue.DEFAULT_ADDRESS_PROTECTED in result.issues) {
                            AddressListFailure.DEFAULT_ADDRESS_PROTECTED
                        } else {
                            AddressListFailure.SERVER_REJECTED
                        }
                    )

            AddressActionResult.Conflict ->
                _state.value = _state.value.failed(AddressListFailure.CONFLICT)

            AddressActionResult.SignedOut -> returnToAccount()

            is AddressActionResult.Failed ->
                _state.value = _state.value.failed(result.reason.toListFailure())
        }
    }

    private suspend fun returnToAccount() {
        _state.value = AddressListUiState(AddressListPhase.FAILED)
        effectsChannel.send(AddressListEffect.ReturnToAccount)
    }
}

private fun AddressListUiState.requestConfirmation(
    addressId: String,
    type: AddressConfirmationType
): AddressListUiState? = if (phase == AddressListPhase.READY && addresses.any { it.id == addressId }) {
    copy(
        confirmation = AddressConfirmation(addressId, type),
        failure = null,
        notice = null
    )
} else {
    null
}

private fun AddressListUiState.failed(failure: AddressListFailure): AddressListUiState = copy(
    phase = AddressListPhase.FAILED,
    confirmation = null,
    failure = failure,
    notice = null
)

private fun AddressContent.toInput(): AddressInput = AddressInput(
    firstName = firstName,
    lastName = lastName,
    company = company,
    address1 = address1,
    address2 = address2,
    city = city,
    zip = zip,
    phoneNumber = phoneNumber
).normalized()

private fun AddressFailure.toListFailure(): AddressListFailure = when (this) {
    AddressFailure.CONNECTION -> AddressListFailure.CONNECTION

    AddressFailure.SAVE_UNCONFIRMED -> AddressListFailure.SAVE_UNCONFIRMED

    AddressFailure.SERVICE,
    AddressFailure.NOT_FOUND,
    AddressFailure.UNSUPPORTED_COUNTRY -> AddressListFailure.SERVICE
}
