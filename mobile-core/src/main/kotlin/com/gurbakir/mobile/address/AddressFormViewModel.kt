package com.gurbakir.mobile.address

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gurbakir.account.CustomerAddressField
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

private const val MAX_ADDRESS_FIELD_LENGTH = 255
private val E164_PHONE = Regex("^\\+[1-9][0-9]{7,14}$")

enum class AddressFormPhase {
    LOADING,
    READY,
    SAVING,
    FAILED
}

enum class AddressFieldError {
    REQUIRED,
    INVALID_CHARACTERS,
    TOO_LONG,
    INVALID_PHONE,
    INVALID_POSTAL_CODE,
    SERVER_REJECTED
}

enum class AddressFormFailure {
    CONNECTION,
    SERVICE,
    SECURE_STORAGE,
    NOT_FOUND,
    UNSUPPORTED_COUNTRY,
    CONFLICT,
    SAVE_UNCONFIRMED
}

data class AddressFocusRequest(val id: Long, val field: CustomerAddressField)

data class AddressFormUiState(
    val postalCodeInputMode: PostalCodeInputMode,
    val phase: AddressFormPhase = AddressFormPhase.LOADING,
    val addressId: String? = null,
    val input: AddressInput = EMPTY_ADDRESS_INPUT,
    val original: AddressInput? = null,
    val makeDefault: Boolean = false,
    val loaded: Boolean = false,
    val fieldErrors: Map<CustomerAddressField, AddressFieldError> = emptyMap(),
    val failure: AddressFormFailure? = null,
    val focusRequest: AddressFocusRequest? = null
) {
    val isCreate: Boolean
        get() = addressId == null

    val busy: Boolean
        get() = phase == AddressFormPhase.LOADING || phase == AddressFormPhase.SAVING

    val dirty: Boolean
        get() = if (isCreate) input != EMPTY_ADDRESS_INPUT || makeDefault else input != original

    val canSave: Boolean
        get() = loaded && phase == AddressFormPhase.READY && (isCreate || dirty)

    val canReload: Boolean
        get() = !busy

    override fun toString(): String = "AddressFormUiState(phase=$phase, mode=${if (isCreate) "CREATE" else "EDIT"}, " +
        "loaded=$loaded, dirty=$dirty, errors=${fieldErrors.keys}, failure=$failure)"
}

sealed interface AddressFormEffect {
    data object Saved : AddressFormEffect

    data object ReturnToAccount : AddressFormEffect
}

@HiltViewModel
class AddressFormViewModel
@Inject
constructor(
    private val controller: AddressController,
    private val territoryPolicy: AddressTerritoryPolicy
) : ViewModel() {
    private val _state =
        MutableStateFlow(AddressFormUiState(postalCodeInputMode = territoryPolicy.postalCodeInputMode))
    val state: StateFlow<AddressFormUiState> = _state.asStateFlow()

    private val effectsChannel = Channel<AddressFormEffect>(Channel.BUFFERED)
    val effects: Flow<AddressFormEffect> = effectsChannel.receiveAsFlow()

    private var initialTarget: AddressFormTarget? = null
    private var storageRecoveryTarget: AddressFormTarget? = null
    private var lastFocusRequestId = 0L

    fun start(addressId: String?) {
        if (initialTarget != null) return
        val target = AddressFormTarget(addressId)
        initialTarget = target
        load(target)
    }

    fun reload() {
        val target = initialTarget ?: return
        if (_state.value.busy) return
        load(target)
    }

    fun update(field: CustomerAddressField, value: String) {
        val current = _state.value
        if (!current.loaded || current.phase != AddressFormPhase.READY) return
        val input = current.input.update(field, value)
        _state.value =
            current.copy(
                input = input,
                fieldErrors = current.fieldErrors - field - CustomerAddressField.FORM,
                failure = null,
                focusRequest = null
            )
    }

    fun setMakeDefault(value: Boolean) {
        val current = _state.value
        if (!current.isCreate || current.phase != AddressFormPhase.READY) return
        _state.value = current.copy(makeDefault = value, failure = null)
    }

    fun save() {
        val current = _state.value
        if (!current.canSave) return
        val normalized = current.input.normalized()
        val errors = normalized.validate(territoryPolicy)
        if (errors.isNotEmpty()) {
            _state.value =
                current.copy(
                    input = normalized,
                    fieldErrors = errors,
                    failure = null,
                    focusRequest =
                        errors.keys.firstAddressField()?.let { AddressFocusRequest(++lastFocusRequestId, it) }
                )
            return
        }
        _state.value =
            current.copy(
                phase = AddressFormPhase.SAVING,
                input = normalized,
                fieldErrors = emptyMap(),
                failure = null,
                focusRequest = null
            )
        viewModelScope.launch {
            handleActionResult(
                controller.saveAddress(
                    addressId = current.addressId,
                    input = normalized,
                    expected = current.original,
                    makeDefault = current.makeDefault
                )
            )
        }
    }

    fun consumeFocusRequest(id: Long) {
        val current = _state.value
        if (current.focusRequest?.id == id) {
            _state.value = current.copy(focusRequest = null)
        }
    }

    private fun load(target: AddressFormTarget) {
        val recoveryTarget = storageRecoveryTarget
        _state.value =
            AddressFormUiState(
                postalCodeInputMode = territoryPolicy.postalCodeInputMode,
                phase = AddressFormPhase.LOADING,
                addressId = target.addressId.takeIf { recoveryTarget == null }
            )
        viewModelScope.launch {
            val result = if (recoveryTarget == null) {
                controller.loadForm(target.addressId)
            } else {
                controller.recoverForm(recoveryTarget.addressId)
            }
            handleLoadResult(target.addressId, result)
        }
    }

    private suspend fun handleLoadResult(addressId: String?, result: AddressFormLoadResult) {
        when (result) {
            is AddressFormLoadResult.Ready -> {
                storageRecoveryTarget = null
                val input = result.address?.toInput() ?: EMPTY_ADDRESS_INPUT
                _state.value =
                    AddressFormUiState(
                        postalCodeInputMode = territoryPolicy.postalCodeInputMode,
                        phase = AddressFormPhase.READY,
                        addressId = addressId,
                        input = input,
                        original = result.address?.let { input },
                        loaded = true
                    )
            }

            AddressFormLoadResult.SignedOut -> returnToAccount()

            is AddressFormLoadResult.Failed -> {
                val formFailure = result.reason.toFormFailure()
                if (formFailure == AddressFormFailure.SECURE_STORAGE) {
                    storageRecoveryTarget = initialTarget
                }
                _state.value = _state.value.failed(formFailure)
            }
        }
    }

    private suspend fun handleActionResult(result: AddressActionResult) {
        when (result) {
            is AddressActionResult.Confirmed -> effectsChannel.send(AddressFormEffect.Saved)

            is AddressActionResult.Rejected -> {
                val errors = result.fields.associateWith { AddressFieldError.SERVER_REJECTED }
                _state.value =
                    _state.value.copy(
                        phase = AddressFormPhase.READY,
                        fieldErrors = errors,
                        failure = null,
                        focusRequest =
                            errors.keys.firstAddressField()?.let { AddressFocusRequest(++lastFocusRequestId, it) }
                    )
            }

            AddressActionResult.Conflict ->
                _state.value = _state.value.failed(AddressFormFailure.CONFLICT)

            AddressActionResult.SignedOut -> returnToAccount()

            is AddressActionResult.Failed -> {
                val formFailure = result.reason.toFormFailure()
                if (formFailure == AddressFormFailure.SECURE_STORAGE) {
                    storageRecoveryTarget = initialTarget
                }
                _state.value = _state.value.failed(formFailure)
            }
        }
    }

    private suspend fun returnToAccount() {
        storageRecoveryTarget = null
        _state.value =
            AddressFormUiState(
                postalCodeInputMode = territoryPolicy.postalCodeInputMode,
                phase = AddressFormPhase.FAILED
            )
        effectsChannel.send(AddressFormEffect.ReturnToAccount)
    }
}

/** Route intent stays private while storage recovery removes the visible address identifier. */
private class AddressFormTarget(val addressId: String?)

private fun AddressFormUiState.failed(failure: AddressFormFailure): AddressFormUiState =
    if (failure == AddressFormFailure.SECURE_STORAGE) {
        AddressFormUiState(
            postalCodeInputMode = postalCodeInputMode,
            phase = AddressFormPhase.FAILED,
            failure = failure
        )
    } else {
        copy(
            phase = AddressFormPhase.FAILED,
            fieldErrors = emptyMap(),
            failure = failure,
            focusRequest = null
        )
    }

private val EMPTY_ADDRESS_INPUT = AddressInput("", "", "", "", "", "", "", "")

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

private fun AddressInput.update(field: CustomerAddressField, value: String): AddressInput = when (field) {
    CustomerAddressField.FIRST_NAME -> copy(firstName = value)

    CustomerAddressField.LAST_NAME -> copy(lastName = value)

    CustomerAddressField.COMPANY -> copy(company = value)

    CustomerAddressField.ADDRESS1 -> copy(address1 = value)

    CustomerAddressField.ADDRESS2 -> copy(address2 = value)

    CustomerAddressField.CITY -> copy(city = value)

    CustomerAddressField.ZIP -> copy(zip = value)

    CustomerAddressField.PHONE -> copy(phoneNumber = value)

    CustomerAddressField.COUNTRY,
    CustomerAddressField.FORM -> this
}

private fun AddressInput.validate(
    territoryPolicy: AddressTerritoryPolicy
): Map<CustomerAddressField, AddressFieldError> = buildMap {
    validateRequired(CustomerAddressField.FIRST_NAME, firstName)
    validateRequired(CustomerAddressField.LAST_NAME, lastName)
    validateOptional(CustomerAddressField.COMPANY, company)
    validateRequired(CustomerAddressField.ADDRESS1, address1)
    validateOptional(CustomerAddressField.ADDRESS2, address2)
    validateRequired(CustomerAddressField.CITY, city)
    validateOptional(CustomerAddressField.ZIP, zip)
    validateOptional(CustomerAddressField.PHONE, phoneNumber)
    if (zip.isNotEmpty() && !territoryPolicy.acceptsPostalCode(zip)) {
        put(CustomerAddressField.ZIP, AddressFieldError.INVALID_POSTAL_CODE)
    }
    if (phoneNumber.isNotEmpty() && !E164_PHONE.matches(phoneNumber)) {
        put(CustomerAddressField.PHONE, AddressFieldError.INVALID_PHONE)
    }
}

private fun MutableMap<CustomerAddressField, AddressFieldError>.validateRequired(
    field: CustomerAddressField,
    value: String
) {
    if (value.isBlank()) {
        put(field, AddressFieldError.REQUIRED)
    } else {
        validateOptional(field, value)
    }
}

private fun MutableMap<CustomerAddressField, AddressFieldError>.validateOptional(
    field: CustomerAddressField,
    value: String
) {
    when {
        value.any { it.isISOControl() } || '<' in value || '>' in value ->
            put(field, AddressFieldError.INVALID_CHARACTERS)

        value.length > MAX_ADDRESS_FIELD_LENGTH -> put(field, AddressFieldError.TOO_LONG)
    }
}

private fun Set<CustomerAddressField>.firstAddressField(): CustomerAddressField? =
    ADDRESS_FIELD_ORDER.firstOrNull(::contains)

private val ADDRESS_FIELD_ORDER =
    listOf(
        CustomerAddressField.FIRST_NAME,
        CustomerAddressField.LAST_NAME,
        CustomerAddressField.COMPANY,
        CustomerAddressField.ADDRESS1,
        CustomerAddressField.ADDRESS2,
        CustomerAddressField.CITY,
        CustomerAddressField.ZIP,
        CustomerAddressField.PHONE
    )

private fun AddressFailure.toFormFailure(): AddressFormFailure = when (this) {
    AddressFailure.CONNECTION -> AddressFormFailure.CONNECTION
    AddressFailure.SERVICE -> AddressFormFailure.SERVICE
    AddressFailure.SECURE_STORAGE -> AddressFormFailure.SECURE_STORAGE
    AddressFailure.NOT_FOUND -> AddressFormFailure.NOT_FOUND
    AddressFailure.UNSUPPORTED_COUNTRY -> AddressFormFailure.UNSUPPORTED_COUNTRY
    AddressFailure.SAVE_UNCONFIRMED -> AddressFormFailure.SAVE_UNCONFIRMED
}
