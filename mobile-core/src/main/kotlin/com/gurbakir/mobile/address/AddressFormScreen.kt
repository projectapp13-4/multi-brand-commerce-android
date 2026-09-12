@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@file:Suppress("FunctionNaming")

package com.gurbakir.mobile.address

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.gurbakir.account.CustomerAddressField
import com.gurbakir.foundation.ui.LocalBrandSpacing
import com.gurbakir.mobile.core.R
import com.gurbakir.mobile.ui.DestinationLevel
import com.gurbakir.mobile.ui.DestinationScaffold
import com.gurbakir.mobile.ui.centeredDestinationContent
import com.gurbakir.mobile.ui.consumeDestinationInsets
import com.gurbakir.mobile.ui.withDestinationSpacing

data class AddressFormActions(
    val onBack: () -> Unit,
    val onFieldChanged: (CustomerAddressField, String) -> Unit,
    val onMakeDefaultChanged: (Boolean) -> Unit,
    val onSave: () -> Unit,
    val onReload: () -> Unit,
    val onFocusHandled: () -> Unit
)

@Composable
fun AddressFormScreen(state: AddressFormUiState, actions: AddressFormActions) {
    val focusRequesters = remember { ADDRESS_FORM_FIELDS.associateWith { FocusRequester() } }
    LaunchedEffect(state.focusRequest) {
        state.focusRequest?.let { field ->
            withFrameNanos { }
            focusRequesters[field]?.requestFocus()
            actions.onFocusHandled()
        }
    }
    DestinationScaffold(
        title =
            stringResource(
                if (state.isCreate) R.string.address_create_title else R.string.address_edit_title
            ),
        level = DestinationLevel.SECONDARY,
        modifier = Modifier.fillMaxSize().testTag(AddressFormTestTags.ROOT),
        onNavigateUp = actions.onBack,
        navigateUpTestTag = AddressFormTestTags.BACK
    ) { padding ->
        AddressFormContent(state, actions, focusRequesters, padding)
    }
}

@Composable
private fun AddressFormContent(
    state: AddressFormUiState,
    actions: AddressFormActions,
    focusRequesters: Map<CustomerAddressField, FocusRequester>,
    padding: PaddingValues
) {
    val spacing = LocalBrandSpacing.current
    val listState = rememberLazyListState()
    val fieldStartIndex =
        ADDRESS_FORM_HEADER_ITEM_COUNT +
            (if (state.busy) 1 else 0) +
            (if (state.failure != null || CustomerAddressField.FORM in state.fieldErrors) 1 else 0) +
            (if (state.canReload && (state.failure != null || !state.loaded)) 1 else 0)
    LazyColumn(
        state = listState,
        modifier =
            Modifier.fillMaxSize()
                .centeredDestinationContent(600.dp)
                .consumeDestinationInsets(padding)
                .testTag(AddressFormTestTags.CONTENT),
        contentPadding =
            padding.withDestinationSpacing(
                horizontal = spacing.sectionDp.dp,
                vertical = spacing.sectionDp.dp
            ),
        verticalArrangement = Arrangement.spacedBy(spacing.generousDp.dp)
    ) {
        item {
            Text(
                text =
                    stringResource(
                        if (state.isCreate) R.string.address_create_heading else R.string.address_edit_heading
                    ),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.semantics { heading() }
            )
        }
        item { Text(stringResource(R.string.address_form_explanation)) }
        item {
            Text(
                text = stringResource(R.string.address_country_value),
                modifier = Modifier.testTag(AddressFormTestTags.COUNTRY)
            )
        }
        if (state.busy) {
            item { LinearProgressIndicator(Modifier.fillMaxWidth().testTag(AddressFormTestTags.PROGRESS)) }
        }
        addressFormRecoveryItems(state, actions)
        addressFormBodyItems(state, actions, focusRequesters, listState, fieldStartIndex)
    }
}

private fun LazyListScope.addressFormRecoveryItems(state: AddressFormUiState, actions: AddressFormActions) {
    if (state.failure != null || CustomerAddressField.FORM in state.fieldErrors) {
        item { AddressFormFeedback(state) }
    }
    if (state.canReload && (state.failure != null || !state.loaded)) {
        item {
            OutlinedButton(
                onClick = actions.onReload,
                modifier = Modifier.fillMaxWidth().testTag(AddressFormTestTags.RELOAD)
            ) {
                Text(stringResource(if (state.loaded) R.string.address_reload_discard else R.string.retry))
            }
        }
    }
}

private fun LazyListScope.addressFormBodyItems(
    state: AddressFormUiState,
    actions: AddressFormActions,
    focusRequesters: Map<CustomerAddressField, FocusRequester>,
    listState: LazyListState,
    fieldStartIndex: Int
) {
    if (state.loaded) {
        ADDRESS_FORM_FIELDS.forEachIndexed { index, field ->
            item(key = field.name) {
                AddressField(
                    field = field,
                    last = index == ADDRESS_FORM_FIELDS.lastIndex,
                    state = state,
                    actions = actions,
                    focusRequester = checkNotNull(focusRequesters[field]),
                    listState = listState,
                    itemIndex = fieldStartIndex + index
                )
            }
        }
        if (state.isCreate) {
            item { MakeDefaultControl(state, actions.onMakeDefaultChanged) }
        }
        item { Text(stringResource(R.string.address_unsaved_explanation)) }
        item {
            Button(
                onClick = actions.onSave,
                enabled = state.canSave,
                modifier = Modifier.fillMaxWidth().testTag(AddressFormTestTags.SAVE)
            ) {
                Text(stringResource(R.string.address_save))
            }
        }
    }
}

@Composable
@Suppress("LongParameterList") // Field state and lazy-list focus metadata form one UI contract.
private fun AddressField(
    field: CustomerAddressField,
    last: Boolean,
    state: AddressFormUiState,
    actions: AddressFormActions,
    focusRequester: FocusRequester,
    listState: LazyListState,
    itemIndex: Int
) {
    val focusManager = LocalFocusManager.current
    AddressTextField(
        spec = field.spec(state, last),
        onValueChanged = { value -> actions.onFieldChanged(field, value) },
        focusRequester = focusRequester,
        onFocused = { listState.scrollToItem(itemIndex) },
        onKeyboardAction = {
            if (last) focusManager.clearFocus() else focusManager.moveFocus(FocusDirection.Down)
        }
    )
}

@Composable
private fun MakeDefaultControl(state: AddressFormUiState, onChanged: (Boolean) -> Unit) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier.fillMaxWidth().testTag(AddressFormTestTags.MAKE_DEFAULT),
        horizontalArrangement = Arrangement.spacedBy(LocalBrandSpacing.current.compactDp.dp)
    ) {
        Checkbox(
            checked = state.makeDefault,
            onCheckedChange = onChanged,
            enabled = state.phase == AddressFormPhase.READY
        )
        Text(stringResource(R.string.address_make_default))
    }
}

private data class AddressFieldSpec(
    val value: String,
    val label: Int,
    val error: AddressFieldError?,
    val enabled: Boolean,
    val imeAction: ImeAction,
    val keyboardType: KeyboardType,
    val testTag: String
) {
    override fun toString(): String = "AddressFieldSpec(<redacted>)"
}

@Composable
private fun AddressTextField(
    spec: AddressFieldSpec,
    onValueChanged: (String) -> Unit,
    focusRequester: FocusRequester,
    onFocused: suspend () -> Unit,
    onKeyboardAction: () -> Unit
) {
    val errorText = spec.error?.let { stringResource(it.messageResource()) }
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(focused) {
        if (focused) {
            withFrameNanos { }
            onFocused()
        }
    }
    OutlinedTextField(
        value = spec.value,
        onValueChange = onValueChanged,
        label = { Text(stringResource(spec.label)) },
        supportingText = errorText?.let { message -> ({ Text(message) }) },
        isError = errorText != null,
        enabled = spec.enabled,
        singleLine = true,
        keyboardOptions =
            KeyboardOptions(
                capitalization = KeyboardCapitalization.Words,
                keyboardType = spec.keyboardType,
                imeAction = spec.imeAction
            ),
        keyboardActions =
            if (spec.imeAction == ImeAction.Next) {
                KeyboardActions(onNext = { onKeyboardAction() })
            } else {
                KeyboardActions(onDone = { onKeyboardAction() })
            },
        modifier =
            Modifier.fillMaxWidth()
                .focusRequester(focusRequester)
                .onFocusChanged { focused = it.isFocused }
                .then(if (errorText == null) Modifier else Modifier.semantics { error(errorText) })
                .testTag(spec.testTag)
    )
}

@Composable
private fun AddressFormFeedback(state: AddressFormUiState) {
    Column(
        modifier =
            Modifier.fillMaxWidth()
                .semantics { liveRegion = LiveRegionMode.Polite }
                .testTag(AddressFormTestTags.FEEDBACK),
        verticalArrangement = Arrangement.spacedBy(LocalBrandSpacing.current.compactDp.dp)
    ) {
        state.failure?.let { Text(stringResource(it.messageResource())) }
        state.fieldErrors[CustomerAddressField.FORM]?.let {
            Text(stringResource(it.messageResource()))
        }
    }
}

private fun CustomerAddressField.spec(state: AddressFormUiState, last: Boolean): AddressFieldSpec {
    val values = state.input
    val value = when (this) {
        CustomerAddressField.FIRST_NAME -> values.firstName

        CustomerAddressField.LAST_NAME -> values.lastName

        CustomerAddressField.COMPANY -> values.company

        CustomerAddressField.ADDRESS1 -> values.address1

        CustomerAddressField.ADDRESS2 -> values.address2

        CustomerAddressField.CITY -> values.city

        CustomerAddressField.ZIP -> values.zip

        CustomerAddressField.PHONE -> values.phoneNumber

        CustomerAddressField.COUNTRY,
        CustomerAddressField.FORM -> ""
    }
    val (label, keyboardType, testTag) = metadata(state.postalCodeInputMode)
    return AddressFieldSpec(
        value = value,
        label = label,
        error = state.fieldErrors[this],
        enabled = state.phase == AddressFormPhase.READY,
        imeAction = if (last) ImeAction.Done else ImeAction.Next,
        keyboardType = keyboardType,
        testTag = "address-form-$testTag"
    )
}

private fun CustomerAddressField.metadata(postalCodeInputMode: PostalCodeInputMode): Triple<Int, KeyboardType, String> =
    when (this) {
        CustomerAddressField.FIRST_NAME -> Triple(R.string.address_first_name, KeyboardType.Text, "first-name")

        CustomerAddressField.LAST_NAME -> Triple(R.string.address_last_name, KeyboardType.Text, "last-name")

        CustomerAddressField.COMPANY -> Triple(R.string.address_company, KeyboardType.Text, "company")

        CustomerAddressField.ADDRESS1 -> Triple(R.string.address_line1, KeyboardType.Text, "address1")

        CustomerAddressField.ADDRESS2 -> Triple(R.string.address_line2, KeyboardType.Text, "address2")

        CustomerAddressField.CITY -> Triple(R.string.address_city, KeyboardType.Text, "city")

        CustomerAddressField.ZIP ->
            Triple(R.string.address_postal_code, postalCodeKeyboardType(postalCodeInputMode), "zip")

        CustomerAddressField.PHONE -> Triple(R.string.address_phone, KeyboardType.Phone, "phone")

        CustomerAddressField.COUNTRY,
        CustomerAddressField.FORM -> Triple(R.string.address_country, KeyboardType.Text, "form")
    }

private val ADDRESS_FORM_FIELDS =
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

private const val ADDRESS_FORM_HEADER_ITEM_COUNT = 3

object AddressFormTestTags {
    const val ROOT = "address-form-root"
    const val CONTENT = "address-form-content"
    const val PROGRESS = "address-form-progress"
    const val COUNTRY = "address-form-country"
    const val MAKE_DEFAULT = "address-form-make-default"
    const val SAVE = "address-form-save"
    const val FEEDBACK = "address-form-feedback"
    const val RELOAD = "address-form-reload"
    const val BACK = "address-form-back"

    val fields: Map<CustomerAddressField, String> =
        CustomerAddressField.entries.associateWith { field ->
            val suffix = when (field) {
                CustomerAddressField.FIRST_NAME -> "first-name"

                CustomerAddressField.LAST_NAME -> "last-name"

                CustomerAddressField.COMPANY -> "company"

                CustomerAddressField.ADDRESS1 -> "address1"

                CustomerAddressField.ADDRESS2 -> "address2"

                CustomerAddressField.CITY -> "city"

                CustomerAddressField.ZIP -> "zip"

                CustomerAddressField.PHONE -> "phone"

                CustomerAddressField.COUNTRY,
                CustomerAddressField.FORM -> "form"
            }
            "address-form-$suffix"
        }
}
