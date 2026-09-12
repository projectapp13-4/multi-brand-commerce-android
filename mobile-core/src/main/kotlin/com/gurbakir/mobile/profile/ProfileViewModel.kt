package com.gurbakir.mobile.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gurbakir.account.CustomerProfileField
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

private const val MAX_PROFILE_NAME_LENGTH = 255

enum class ProfilePhase {
    LOADING,
    READY,
    SAVING,
    FAILED
}

enum class ProfileFieldError {
    INVALID_CHARACTERS,
    TOO_LONG,
    SERVER_REJECTED
}

enum class ProfileNotice {
    SAVED,
    NO_CHANGES
}

data class ProfileUiState(
    val phase: ProfilePhase = ProfilePhase.LOADING,
    val firstName: String = "",
    val lastName: String = "",
    val originalFirstName: String = "",
    val originalLastName: String = "",
    val loaded: Boolean = false,
    val fieldErrors: Map<CustomerProfileField, ProfileFieldError> = emptyMap(),
    val failure: ProfileFailure? = null,
    val notice: ProfileNotice? = null,
    val focusRequest: CustomerProfileField? = null
) {
    val busy: Boolean
        get() = phase == ProfilePhase.LOADING || phase == ProfilePhase.SAVING

    val dirty: Boolean
        get() = firstName != originalFirstName || lastName != originalLastName

    val canSave: Boolean
        get() = loaded && dirty && phase == ProfilePhase.READY

    val canReload: Boolean
        get() = !busy

    override fun toString(): String =
        "ProfileUiState(phase=$phase, loaded=$loaded, dirty=$dirty, errors=${fieldErrors.keys}, " +
            "failure=$failure, notice=$notice)"
}

sealed interface ProfileEffect {
    data object ReturnToAccount : ProfileEffect
}

@HiltViewModel
class ProfileViewModel
@Inject
constructor(private val controller: ProfileController) : ViewModel() {
    private val _state = MutableStateFlow(ProfileUiState())
    val state: StateFlow<ProfileUiState> = _state.asStateFlow()

    private val effectChannel = Channel<ProfileEffect>(Channel.BUFFERED)
    val effects: Flow<ProfileEffect> = effectChannel.receiveAsFlow()

    init {
        load()
    }

    fun onFirstNameChanged(value: String) = updateField(CustomerProfileField.FIRST_NAME, value)

    fun onLastNameChanged(value: String) = updateField(CustomerProfileField.LAST_NAME, value)

    fun reload() {
        if (_state.value.busy) return
        load()
    }

    fun save() {
        val current = _state.value
        when {
            !current.loaded || current.phase != ProfilePhase.READY -> Unit

            !current.dirty ->
                _state.value = current.copy(notice = ProfileNotice.NO_CHANGES, failure = null)

            else -> validateAndSave(current)
        }
    }

    private fun validateAndSave(current: ProfileUiState) {
        val validation = validate(current.firstName, current.lastName)
        if (validation.errors.isNotEmpty()) {
            _state.value =
                current.copy(
                    fieldErrors = validation.errors,
                    failure = null,
                    notice = null,
                    focusRequest = validation.errors.keys.firstEditableField()
                )
        } else {
            _state.value =
                current.copy(
                    phase = ProfilePhase.SAVING,
                    firstName = validation.firstName.orEmpty(),
                    lastName = validation.lastName.orEmpty(),
                    fieldErrors = emptyMap(),
                    failure = null,
                    notice = null,
                    focusRequest = null
                )
            viewModelScope.launch {
                handleResult(
                    controller.save(
                        firstName = validation.firstName,
                        lastName = validation.lastName,
                        expectedFirstName = current.originalFirstName.ifBlank { null },
                        expectedLastName = current.originalLastName.ifBlank { null }
                    ),
                    saving = true
                )
            }
        }
    }

    fun consumeFocusRequest() {
        if (_state.value.focusRequest != null) {
            _state.value = _state.value.copy(focusRequest = null)
        }
    }

    private fun load() {
        _state.value = ProfileUiState(ProfilePhase.LOADING)
        viewModelScope.launch { handleResult(controller.load(), saving = false) }
    }

    private fun updateField(field: CustomerProfileField, value: String) {
        val current = _state.value
        if (!current.loaded || current.phase != ProfilePhase.READY) return
        val retainedErrors = current.fieldErrors - field - CustomerProfileField.FORM
        _state.value =
            when (field) {
                CustomerProfileField.FIRST_NAME -> current.copy(firstName = value)
                CustomerProfileField.LAST_NAME -> current.copy(lastName = value)
                CustomerProfileField.FORM -> current
            }.copy(
                fieldErrors = retainedErrors,
                failure = null,
                notice = null,
                focusRequest = null
            )
    }

    private suspend fun handleResult(result: ProfileResult, saving: Boolean) {
        when (result) {
            is ProfileResult.Content -> {
                _state.value = result.profile.toReadyState(if (saving) ProfileNotice.SAVED else null)
            }

            is ProfileResult.Rejected -> {
                val current = _state.value
                val errors = result.fields.associateWith { ProfileFieldError.SERVER_REJECTED }
                _state.value =
                    current.copy(
                        phase = ProfilePhase.READY,
                        fieldErrors = errors,
                        failure = null,
                        notice = null,
                        focusRequest = errors.keys.firstEditableField()
                    )
            }

            ProfileResult.Conflict -> {
                _state.value =
                    _state.value.copy(
                        phase = ProfilePhase.FAILED,
                        failure = ProfileFailure.CONFLICT,
                        notice = null,
                        focusRequest = null
                    )
            }

            ProfileResult.SignedOut -> {
                _state.value = ProfileUiState(ProfilePhase.FAILED)
                effectChannel.send(ProfileEffect.ReturnToAccount)
            }

            is ProfileResult.Failed -> {
                _state.value =
                    _state.value.copy(
                        phase = ProfilePhase.FAILED,
                        failure = result.reason,
                        notice = null,
                        focusRequest = null
                    )
            }
        }
    }
}

private data class ValidatedProfile(
    val firstName: String?,
    val lastName: String?,
    val errors: Map<CustomerProfileField, ProfileFieldError>
)

private fun validate(firstName: String, lastName: String): ValidatedProfile {
    val errors = buildMap {
        firstName.validationError()?.let { put(CustomerProfileField.FIRST_NAME, it) }
        lastName.validationError()?.let { put(CustomerProfileField.LAST_NAME, it) }
    }
    return ValidatedProfile(
        firstName = firstName.trim().ifBlank { null },
        lastName = lastName.trim().ifBlank { null },
        errors = errors
    )
}

private fun String.validationError(): ProfileFieldError? = when {
    any { it.isISOControl() } -> ProfileFieldError.INVALID_CHARACTERS
    trim().length > MAX_PROFILE_NAME_LENGTH -> ProfileFieldError.TOO_LONG
    else -> null
}

private fun Set<CustomerProfileField>.firstEditableField(): CustomerProfileField? = when {
    CustomerProfileField.FIRST_NAME in this -> CustomerProfileField.FIRST_NAME
    CustomerProfileField.LAST_NAME in this -> CustomerProfileField.LAST_NAME
    else -> null
}

private fun ProfileContent.toReadyState(notice: ProfileNotice?): ProfileUiState = ProfileUiState(
    phase = ProfilePhase.READY,
    firstName = firstName,
    lastName = lastName,
    originalFirstName = firstName,
    originalLastName = lastName,
    loaded = true,
    notice = notice
)
