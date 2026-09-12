@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@file:Suppress("FunctionNaming")

package com.gurbakir.mobile.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.gurbakir.account.CustomerProfileField
import com.gurbakir.foundation.ui.LocalBrandSpacing
import com.gurbakir.mobile.core.R
import com.gurbakir.mobile.ui.DestinationLevel
import com.gurbakir.mobile.ui.DestinationScaffold
import com.gurbakir.mobile.ui.centeredDestinationContent
import com.gurbakir.mobile.ui.consumeDestinationInsets
import com.gurbakir.mobile.ui.withDestinationSpacing

data class ProfileActions(
    val onBack: () -> Unit,
    val onFirstNameChanged: (String) -> Unit,
    val onLastNameChanged: (String) -> Unit,
    val onSave: () -> Unit,
    val onReload: () -> Unit,
    val onFocusHandled: () -> Unit
)

@Composable
fun ProfileScreen(state: ProfileUiState, actions: ProfileActions) {
    val firstNameFocus = remember { FocusRequester() }
    val lastNameFocus = remember { FocusRequester() }
    LaunchedEffect(state.focusRequest) {
        when (state.focusRequest) {
            CustomerProfileField.FIRST_NAME -> firstNameFocus
            CustomerProfileField.LAST_NAME -> lastNameFocus
            CustomerProfileField.FORM, null -> null
        }?.let { requester ->
            withFrameNanos { }
            requester.requestFocus()
            actions.onFocusHandled()
        }
    }
    DestinationScaffold(
        title = stringResource(R.string.profile_title),
        level = DestinationLevel.SECONDARY,
        modifier = Modifier.fillMaxSize().testTag(ProfileTestTags.ROOT),
        onNavigateUp = actions.onBack,
        navigateUpTestTag = ProfileTestTags.BACK
    ) { padding ->
        ProfileScreenContent(state, actions, firstNameFocus, lastNameFocus, padding)
    }
}

@Composable
private fun ProfileScreenContent(
    state: ProfileUiState,
    actions: ProfileActions,
    firstNameFocus: FocusRequester,
    lastNameFocus: FocusRequester,
    padding: PaddingValues
) {
    val spacing = LocalBrandSpacing.current
    LazyColumn(
        modifier =
            Modifier.fillMaxSize()
                .centeredDestinationContent(600.dp)
                .consumeDestinationInsets(padding)
                .testTag(ProfileTestTags.CONTENT),
        contentPadding =
            padding.withDestinationSpacing(
                horizontal = spacing.sectionDp.dp,
                vertical = spacing.sectionDp.dp
            ),
        verticalArrangement = Arrangement.spacedBy(spacing.generousDp.dp)
    ) {
        item {
            Text(
                text = stringResource(R.string.profile_heading),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.semantics { heading() }
            )
        }
        item { Text(stringResource(R.string.profile_scope_explanation)) }
        if (state.busy) {
            item {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().testTag(ProfileTestTags.PROGRESS)
                )
            }
        }
        if (state.loaded) {
            item { ProfileForm(state, actions, firstNameFocus, lastNameFocus) }
        }
        if (state.notice != null || state.failure != null || CustomerProfileField.FORM in state.fieldErrors) {
            item { ProfileFeedback(state) }
        }
        if (state.canReload && (state.failure != null || !state.loaded)) {
            item { ProfileReloadAction(state.loaded, actions.onReload) }
        }
    }
}

@Composable
private fun ProfileReloadAction(loaded: Boolean, onReload: () -> Unit) {
    OutlinedButton(
        onClick = onReload,
        modifier = Modifier.fillMaxWidth().testTag(ProfileTestTags.RELOAD)
    ) {
        Text(stringResource(if (loaded) R.string.profile_reload_discard else R.string.retry))
    }
}

@Composable
private fun ProfileForm(
    state: ProfileUiState,
    actions: ProfileActions,
    firstNameFocus: FocusRequester,
    lastNameFocus: FocusRequester
) {
    val spacing = LocalBrandSpacing.current
    val focusManager = LocalFocusManager.current
    Column(verticalArrangement = Arrangement.spacedBy(spacing.normalDp.dp)) {
        ProfileTextField(
            spec =
                ProfileFieldSpec(
                    value = state.firstName,
                    labelResource = R.string.profile_first_name,
                    error = state.fieldErrors[CustomerProfileField.FIRST_NAME],
                    imeAction = ImeAction.Next,
                    enabled = state.phase == ProfilePhase.READY,
                    testTag = ProfileTestTags.FIRST_NAME
                ),
            onValueChange = actions.onFirstNameChanged,
            focusRequester = firstNameFocus,
            keyboardAction = { focusManager.moveFocus(FocusDirection.Down) }
        )
        ProfileTextField(
            spec =
                ProfileFieldSpec(
                    value = state.lastName,
                    labelResource = R.string.profile_last_name,
                    error = state.fieldErrors[CustomerProfileField.LAST_NAME],
                    imeAction = ImeAction.Done,
                    enabled = state.phase == ProfilePhase.READY,
                    testTag = ProfileTestTags.LAST_NAME
                ),
            onValueChange = actions.onLastNameChanged,
            focusRequester = lastNameFocus,
            keyboardAction = focusManager::clearFocus
        )
        Text(stringResource(R.string.profile_unsaved_explanation))
        Button(
            onClick = actions.onSave,
            enabled = state.canSave,
            modifier = Modifier.fillMaxWidth().testTag(ProfileTestTags.SAVE)
        ) {
            Text(stringResource(R.string.profile_save))
        }
    }
}

private data class ProfileFieldSpec(
    val value: String,
    val labelResource: Int,
    val error: ProfileFieldError?,
    val imeAction: ImeAction,
    val enabled: Boolean,
    val testTag: String
) {
    override fun toString(): String = "ProfileFieldSpec(<redacted>)"
}

@Composable
private fun ProfileTextField(
    spec: ProfileFieldSpec,
    onValueChange: (String) -> Unit,
    focusRequester: FocusRequester,
    keyboardAction: () -> Unit
) {
    val errorText = spec.error?.let { stringResource(it.messageResourceId()) }
    OutlinedTextField(
        value = spec.value,
        onValueChange = onValueChange,
        label = { Text(stringResource(spec.labelResource)) },
        supportingText = errorText?.let { message -> ({ Text(message) }) },
        isError = errorText != null,
        enabled = spec.enabled,
        singleLine = true,
        keyboardOptions =
            KeyboardOptions(
                capitalization = KeyboardCapitalization.Words,
                imeAction = spec.imeAction
            ),
        keyboardActions =
            if (spec.imeAction == ImeAction.Next) {
                KeyboardActions(onNext = { keyboardAction() })
            } else {
                KeyboardActions(onDone = { keyboardAction() })
            },
        modifier =
            Modifier.fillMaxWidth()
                .focusRequester(focusRequester)
                .then(
                    if (errorText == null) {
                        Modifier
                    } else {
                        Modifier.semantics { error(errorText) }
                    }
                ).testTag(spec.testTag)
    )
}

@Composable
private fun ProfileFeedback(state: ProfileUiState) {
    Column(
        modifier =
            Modifier.fillMaxWidth()
                .semantics { liveRegion = LiveRegionMode.Polite }
                .testTag(ProfileTestTags.FEEDBACK),
        verticalArrangement = Arrangement.spacedBy(LocalBrandSpacing.current.compactDp.dp)
    ) {
        state.notice?.let { Text(stringResource(it.messageResourceId())) }
        state.failure?.let { Text(stringResource(it.messageResourceId())) }
        state.fieldErrors[CustomerProfileField.FORM]?.let {
            Text(stringResource(it.messageResourceId()))
        }
    }
}

private fun ProfileFieldError.messageResourceId(): Int = when (this) {
    ProfileFieldError.INVALID_CHARACTERS -> R.string.profile_error_invalid_characters
    ProfileFieldError.TOO_LONG -> R.string.profile_error_too_long
    ProfileFieldError.SERVER_REJECTED -> R.string.profile_error_server_rejected
}

private fun ProfileFailure.messageResourceId(): Int = when (this) {
    ProfileFailure.CONNECTION -> R.string.profile_failure_connection
    ProfileFailure.SERVICE -> R.string.profile_failure_service
    ProfileFailure.CONFLICT -> R.string.profile_failure_conflict
    ProfileFailure.SAVE_UNCONFIRMED -> R.string.profile_failure_save_unconfirmed
}

private fun ProfileNotice.messageResourceId(): Int = when (this) {
    ProfileNotice.SAVED -> R.string.profile_notice_saved
    ProfileNotice.NO_CHANGES -> R.string.profile_notice_no_changes
}

object ProfileTestTags {
    const val ROOT = "profile-root"
    const val CONTENT = "profile-content"
    const val PROGRESS = "profile-progress"
    const val FIRST_NAME = "profile-first-name"
    const val LAST_NAME = "profile-last-name"
    const val SAVE = "profile-save"
    const val RELOAD = "profile-reload"
    const val FEEDBACK = "profile-feedback"
    const val BACK = "profile-back"
}
