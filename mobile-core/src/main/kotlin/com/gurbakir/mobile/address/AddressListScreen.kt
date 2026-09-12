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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.gurbakir.foundation.ui.LocalBrandSpacing
import com.gurbakir.mobile.core.R
import com.gurbakir.mobile.ui.DestinationLevel
import com.gurbakir.mobile.ui.DestinationScaffold
import com.gurbakir.mobile.ui.centeredDestinationContent
import com.gurbakir.mobile.ui.consumeDestinationInsets
import com.gurbakir.mobile.ui.withDestinationSpacing

data class AddressListActions(
    val onBack: () -> Unit,
    val onCreate: () -> Unit,
    val onEdit: (String) -> Unit,
    val onSetDefault: (String) -> Unit,
    val onDelete: (String) -> Unit,
    val onConfirm: () -> Unit,
    val onDismissConfirmation: () -> Unit,
    val onReload: () -> Unit
)

@Composable
fun AddressListScreen(state: AddressListUiState, actions: AddressListActions) {
    val createFocus = remember { FocusRequester() }
    LaunchedEffect(state.notice) {
        if (state.notice != null) {
            withFrameNanos { }
            createFocus.requestFocus()
        }
    }
    DestinationScaffold(
        title = stringResource(R.string.address_list_title),
        level = DestinationLevel.SECONDARY,
        modifier = Modifier.fillMaxSize().testTag(AddressListTestTags.ROOT),
        onNavigateUp = actions.onBack,
        navigateUpTestTag = AddressListTestTags.BACK
    ) { padding ->
        AddressListContent(state, actions, createFocus, padding)
    }
    state.confirmation?.let { confirmation ->
        AddressConfirmationDialog(confirmation.type, actions)
    }
}

@Suppress("LongMethod") // Shared scaffold inset consumption keeps this state renderer linear.
@Composable
private fun AddressListContent(
    state: AddressListUiState,
    actions: AddressListActions,
    createFocus: FocusRequester,
    padding: PaddingValues
) {
    val spacing = LocalBrandSpacing.current
    LazyColumn(
        modifier =
            Modifier.fillMaxSize()
                .centeredDestinationContent(720.dp)
                .consumeDestinationInsets(padding)
                .testTag(AddressListTestTags.CONTENT),
        contentPadding =
            padding.withDestinationSpacing(
                horizontal = spacing.sectionDp.dp,
                vertical = spacing.sectionDp.dp
            ),
        verticalArrangement = Arrangement.spacedBy(spacing.generousDp.dp)
    ) {
        item {
            Text(
                text = stringResource(R.string.address_list_heading),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.semantics { heading() }
            )
        }
        item { Text(stringResource(R.string.address_market_explanation)) }
        if (state.busy) {
            item { LinearProgressIndicator(Modifier.fillMaxWidth().testTag(AddressListTestTags.PROGRESS)) }
        }
        if (state.loaded) {
            item {
                Button(
                    onClick = actions.onCreate,
                    enabled = state.phase == AddressListPhase.READY,
                    modifier =
                        Modifier.fillMaxWidth()
                            .focusRequester(createFocus)
                            .testTag(AddressListTestTags.CREATE)
                ) {
                    Text(stringResource(R.string.address_create))
                }
            }
            if (state.addresses.isEmpty()) {
                item { AddressEmpty() }
            } else {
                items(state.addresses.size, key = { index -> state.addresses[index].id }) { index ->
                    AddressCard(
                        address = state.addresses[index],
                        enabled = state.phase == AddressListPhase.READY,
                        actions = actions
                    )
                }
            }
        }
        if (state.notice != null || state.failure != null) {
            item { AddressListFeedback(state) }
        }
        if (state.canReload && state.failure != null) {
            item {
                OutlinedButton(
                    onClick = actions.onReload,
                    modifier = Modifier.fillMaxWidth().testTag(AddressListTestTags.RELOAD)
                ) {
                    Text(stringResource(R.string.retry))
                }
            }
        }
    }
}

@Composable
private fun AddressEmpty() {
    Column(
        modifier = Modifier.fillMaxWidth().testTag(AddressListTestTags.EMPTY),
        verticalArrangement = Arrangement.spacedBy(LocalBrandSpacing.current.compactDp.dp)
    ) {
        Text(stringResource(R.string.address_empty_title), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.address_empty_message))
    }
}

@Composable
private fun AddressCard(address: AddressContent, enabled: Boolean, actions: AddressListActions) {
    val spacing = LocalBrandSpacing.current
    Card(modifier = Modifier.fillMaxWidth().testTag(AddressListTestTags.card(address.id))) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(spacing.normalDp.dp),
            verticalArrangement = Arrangement.spacedBy(spacing.compactDp.dp)
        ) {
            if (address.isDefault) {
                Text(
                    text = stringResource(R.string.address_default_badge),
                    style = MaterialTheme.typography.labelLarge
                )
            }
            address.formatted.filter(String::isNotBlank).forEach { line -> Text(line) }
            if (!address.isSupported) {
                Text(stringResource(R.string.address_unsupported_country))
            }
            if (address.isSupported) {
                OutlinedButton(
                    onClick = { actions.onEdit(address.id) },
                    enabled = enabled,
                    modifier = Modifier.fillMaxWidth().testTag(AddressListTestTags.edit(address.id))
                ) {
                    Text(stringResource(R.string.address_edit))
                }
                if (!address.isDefault) {
                    OutlinedButton(
                        onClick = { actions.onSetDefault(address.id) },
                        enabled = enabled,
                        modifier =
                            Modifier.fillMaxWidth().testTag(AddressListTestTags.setDefault(address.id))
                    ) {
                        Text(stringResource(R.string.address_set_default))
                    }
                }
            }
            if (address.isDefault) {
                Text(stringResource(R.string.address_default_delete_explanation))
            } else {
                TextButton(
                    onClick = { actions.onDelete(address.id) },
                    enabled = enabled,
                    modifier = Modifier.fillMaxWidth().testTag(AddressListTestTags.delete(address.id))
                ) {
                    Text(stringResource(R.string.address_delete))
                }
            }
        }
    }
}

@Composable
private fun AddressListFeedback(state: AddressListUiState) {
    Column(
        modifier =
            Modifier.fillMaxWidth()
                .semantics { liveRegion = LiveRegionMode.Polite }
                .testTag(AddressListTestTags.FEEDBACK),
        verticalArrangement = Arrangement.spacedBy(LocalBrandSpacing.current.compactDp.dp)
    ) {
        state.notice?.let { Text(stringResource(it.messageResource())) }
        state.failure?.let { Text(stringResource(it.messageResource())) }
    }
}

@Composable
private fun AddressConfirmationDialog(type: AddressConfirmationType, actions: AddressListActions) {
    val setDefault = type == AddressConfirmationType.SET_DEFAULT
    AlertDialog(
        onDismissRequest = actions.onDismissConfirmation,
        title = {
            Text(
                stringResource(
                    if (setDefault) R.string.address_confirm_default_title else R.string.address_confirm_delete_title
                )
            )
        },
        text = {
            Text(
                stringResource(
                    if (setDefault) {
                        R.string.address_confirm_default_message
                    } else {
                        R.string.address_confirm_delete_message
                    }
                )
            )
        },
        confirmButton = {
            Button(onClick = actions.onConfirm, modifier = Modifier.testTag(AddressListTestTags.CONFIRM)) {
                Text(
                    stringResource(
                        if (setDefault) R.string.address_set_default else R.string.address_delete
                    )
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = actions.onDismissConfirmation,
                modifier = Modifier.testTag(AddressListTestTags.DISMISS)
            ) {
                Text(stringResource(R.string.cancel))
            }
        },
        modifier = Modifier.testTag(AddressListTestTags.DIALOG)
    )
}

private fun AddressListNotice.messageResource(): Int = when (this) {
    AddressListNotice.DEFAULT_UPDATED -> R.string.address_notice_default_updated
    AddressListNotice.DELETED -> R.string.address_notice_deleted
}

private fun AddressListFailure.messageResource(): Int = when (this) {
    AddressListFailure.CONNECTION -> R.string.address_failure_connection
    AddressListFailure.SERVICE -> R.string.address_failure_service
    AddressListFailure.CONFLICT -> R.string.address_failure_conflict
    AddressListFailure.SAVE_UNCONFIRMED -> R.string.address_failure_unconfirmed
    AddressListFailure.DEFAULT_ADDRESS_PROTECTED -> R.string.address_failure_default_protected
    AddressListFailure.SERVER_REJECTED -> R.string.address_failure_server_rejected
}

object AddressListTestTags {
    const val ROOT = "address-list-root"
    const val CONTENT = "address-list-content"
    const val PROGRESS = "address-list-progress"
    const val CREATE = "address-list-create"
    const val EMPTY = "address-list-empty"
    const val FEEDBACK = "address-list-feedback"
    const val RELOAD = "address-list-reload"
    const val BACK = "address-list-back"
    const val DIALOG = "address-list-dialog"
    const val CONFIRM = "address-list-confirm"
    const val DISMISS = "address-list-dismiss"

    fun card(id: String) = "address-card-${id.hashCode()}"

    fun edit(id: String) = "address-edit-${id.hashCode()}"

    fun setDefault(id: String) = "address-default-${id.hashCode()}"

    fun delete(id: String) = "address-delete-${id.hashCode()}"
}
