@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@file:Suppress("FunctionNaming", "TooManyFunctions")

package com.gurbakir.mobile.accountdeletion

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.gurbakir.foundation.ui.LocalBrandSpacing
import com.gurbakir.mobile.core.R
import com.gurbakir.mobile.ui.DestinationLevel
import com.gurbakir.mobile.ui.DestinationScaffold
import com.gurbakir.mobile.ui.DestinationTitleAlignment
import com.gurbakir.mobile.ui.centeredDestinationContent
import com.gurbakir.mobile.ui.consumeDestinationInsets
import com.gurbakir.mobile.ui.withDestinationSpacing

data class AccountDeletionActions(
    val onBack: () -> Unit,
    val onOpenPage: (DeletionPageDescriptor) -> Unit,
    val onClearSearchHistoryChanged: (Boolean) -> Unit,
    val onClearWishlistChanged: (Boolean) -> Unit,
    val onDiscardCartChanged: (Boolean) -> Unit,
    val onRequestLocalClear: () -> Unit,
    val onDismissLocalClear: () -> Unit,
    val onConfirmLocalClear: () -> Unit,
    val onRetry: () -> Unit,
    val onFinish: () -> Unit
)

@Composable
fun AccountDeletionScreen(state: AccountDeletionUiState, actions: AccountDeletionActions) {
    if (state.confirmationVisible) {
        LocalClearConfirmation(actions)
    }
    DestinationScaffold(
        title = stringResource(R.string.account_deletion_title),
        level = DestinationLevel.SECONDARY,
        modifier = Modifier.testTag(AccountDeletionTestTags.ROOT),
        titleAlignment = DestinationTitleAlignment.CENTER,
        onNavigateUp = actions.onBack
    ) { padding ->
        val spacing = LocalBrandSpacing.current
        LazyColumn(
            modifier =
                Modifier.fillMaxSize()
                    .centeredDestinationContent(720.dp)
                    .consumeDestinationInsets(padding)
                    .testTag(AccountDeletionTestTags.CONTENT),
            contentPadding = padding.withDestinationSpacing(),
            verticalArrangement = Arrangement.spacedBy(spacing.generousDp.dp)
        ) {
            item { AccountDeletionHeader(state) }
            when (state.phase) {
                AccountDeletionPhase.CHECKING,
                AccountDeletionPhase.CLEARING -> item { ProgressCard(state.phase) }

                AccountDeletionPhase.UNAVAILABLE -> item { UnavailableCard(actions.onRetry) }

                AccountDeletionPhase.READY -> {
                    item { RemoteRequestCard(state, actions.onOpenPage) }
                    item { LocalDataCard(state, actions) }
                }

                AccountDeletionPhase.COMPLETED -> item {
                    LocalResultCard(requireNotNull(state.result), actions.onFinish)
                }
            }
        }
    }
}

@Composable
private fun AccountDeletionHeader(state: AccountDeletionUiState) {
    val spacing = LocalBrandSpacing.current
    Column(verticalArrangement = Arrangement.spacedBy(spacing.normalDp.dp)) {
        Text(
            text = stringResource(R.string.account_deletion_heading),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.semantics { heading() }
        )
        Text(stringResource(R.string.account_deletion_boundary))
        state.externalFeedback?.let { ExternalFeedback(it, state) }
    }
}

@Composable
private fun ProgressCard(phase: AccountDeletionPhase) {
    val spacing = LocalBrandSpacing.current
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(spacing.generousDp.dp),
            verticalArrangement = Arrangement.spacedBy(spacing.normalDp.dp)
        ) {
            Text(
                stringResource(
                    if (phase == AccountDeletionPhase.CLEARING) {
                        R.string.account_deletion_clearing
                    } else {
                        R.string.account_deletion_checking
                    }
                ),
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
            )
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun UnavailableCard(onRetry: () -> Unit) {
    val spacing = LocalBrandSpacing.current
    Card(modifier = Modifier.fillMaxWidth().testTag(AccountDeletionTestTags.UNAVAILABLE)) {
        Column(
            modifier = Modifier.padding(spacing.generousDp.dp),
            verticalArrangement = Arrangement.spacedBy(spacing.normalDp.dp)
        ) {
            Text(stringResource(R.string.account_deletion_unavailable))
            Button(onClick = onRetry) { Text(stringResource(R.string.retry)) }
        }
    }
}

@Composable
private fun RemoteRequestCard(state: AccountDeletionUiState, onOpenPage: (DeletionPageDescriptor) -> Unit) {
    val spacing = LocalBrandSpacing.current
    Card(modifier = Modifier.fillMaxWidth().testTag(AccountDeletionTestTags.REMOTE_REQUEST)) {
        Column(
            modifier = Modifier.padding(spacing.generousDp.dp),
            verticalArrangement = Arrangement.spacedBy(spacing.normalDp.dp)
        ) {
            Text(
                text = stringResource(R.string.account_deletion_request_heading),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() }
            )
            Text(stringResource(R.string.account_deletion_request_explanation))
            Text(stringResource(R.string.account_deletion_identity_warning))
            state.privacyPage?.let { page ->
                TextButton(
                    onClick = { onOpenPage(page) },
                    modifier = Modifier.testTag(AccountDeletionTestTags.PRIVACY)
                ) {
                    Text(stringResource(R.string.account_deletion_open_privacy))
                }
            }
            state.requestPage?.let { page ->
                Button(
                    onClick = { onOpenPage(page) },
                    modifier = Modifier.testTag(AccountDeletionTestTags.REQUEST)
                ) {
                    Text(stringResource(R.string.account_deletion_open_request))
                }
            }
            Text(stringResource(R.string.account_deletion_request_not_verified))
        }
    }
}

@Composable
private fun LocalDataCard(state: AccountDeletionUiState, actions: AccountDeletionActions) {
    val spacing = LocalBrandSpacing.current
    Card(modifier = Modifier.fillMaxWidth().testTag(AccountDeletionTestTags.LOCAL_DATA)) {
        Column(
            modifier = Modifier.padding(spacing.generousDp.dp),
            verticalArrangement = Arrangement.spacedBy(spacing.normalDp.dp)
        ) {
            Text(
                text = stringResource(R.string.account_deletion_local_heading),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() }
            )
            Text(stringResource(R.string.account_deletion_local_explanation))
            LocalOption(
                checked = state.clearSearchHistory,
                label = stringResource(R.string.account_deletion_clear_search),
                tag = AccountDeletionTestTags.SEARCH,
                onChanged = actions.onClearSearchHistoryChanged
            )
            LocalOption(
                checked = state.clearWishlist,
                label = stringResource(R.string.account_deletion_clear_wishlist),
                tag = AccountDeletionTestTags.WISHLIST,
                onChanged = actions.onClearWishlistChanged
            )
            LocalOption(
                checked = state.discardCart,
                label = stringResource(R.string.account_deletion_discard_cart),
                tag = AccountDeletionTestTags.CART,
                onChanged = actions.onDiscardCartChanged
            )
            Text(stringResource(R.string.account_deletion_retained_data))
            Button(
                onClick = actions.onRequestLocalClear,
                modifier = Modifier.testTag(AccountDeletionTestTags.CLEAR)
            ) {
                Text(stringResource(R.string.account_deletion_clear_action))
            }
        }
    }
}

@Composable
private fun LocalOption(checked: Boolean, label: String, tag: String, onChanged: (Boolean) -> Unit) {
    val spacing = LocalBrandSpacing.current
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .toggleable(value = checked, role = Role.Checkbox) { onChanged(it) }
                .padding(vertical = spacing.compactDp.dp)
                .testTag(tag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Text(text = label, modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun LocalClearConfirmation(actions: AccountDeletionActions) {
    AlertDialog(
        modifier = Modifier.testTag(AccountDeletionTestTags.CONFIRMATION),
        onDismissRequest = actions.onDismissLocalClear,
        title = { Text(stringResource(R.string.account_deletion_confirm_title)) },
        text = { Text(stringResource(R.string.account_deletion_confirm_message)) },
        confirmButton = {
            Button(
                onClick = actions.onConfirmLocalClear,
                modifier = Modifier.testTag(AccountDeletionTestTags.CONFIRM)
            ) {
                Text(stringResource(R.string.account_deletion_confirm_action))
            }
        },
        dismissButton = {
            TextButton(onClick = actions.onDismissLocalClear) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
private fun LocalResultCard(result: AccountDeletionLocalResult, onFinish: () -> Unit) {
    val spacing = LocalBrandSpacing.current
    Card(modifier = Modifier.fillMaxWidth().testTag(AccountDeletionTestTags.RESULT)) {
        Column(
            modifier =
                Modifier.padding(spacing.generousDp.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite },
            verticalArrangement = Arrangement.spacedBy(spacing.normalDp.dp)
        ) {
            Text(
                text = stringResource(R.string.account_deletion_result_heading),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() }
            )
            ResultLine(R.string.account_deletion_result_session, result.session)
            ResultLine(R.string.account_deletion_result_search, result.searchHistory)
            ResultLine(R.string.account_deletion_result_wishlist, result.wishlist)
            ResultLine(R.string.account_deletion_result_cart, result.cart)
            if (result.remoteLogoutUnverified) {
                Text(stringResource(R.string.account_deletion_remote_logout_unverified))
            }
            Text(stringResource(R.string.account_deletion_remote_unchanged))
            Button(onClick = onFinish, modifier = Modifier.testTag(AccountDeletionTestTags.FINISH)) {
                Text(stringResource(R.string.account_deletion_finish))
            }
        }
    }
}

@Composable
private fun ResultLine(labelResourceId: Int, outcome: AccountDeletionClearOutcome) {
    val outcomeResourceId = when (outcome) {
        AccountDeletionClearOutcome.NOT_SELECTED -> R.string.account_deletion_outcome_not_selected
        AccountDeletionClearOutcome.CLEARED -> R.string.account_deletion_outcome_cleared
        AccountDeletionClearOutcome.FAILED -> R.string.account_deletion_outcome_failed
    }
    Text(
        stringResource(
            R.string.account_deletion_result_line,
            stringResource(labelResourceId),
            stringResource(outcomeResourceId)
        )
    )
}

@Composable
private fun ExternalFeedback(feedback: DeletionPageFeedback, state: AccountDeletionUiState) {
    val page = listOfNotNull(state.privacyPage, state.requestPage).firstOrNull { it.id == feedback.pageId }
        ?: return
    val message = when (feedback.type) {
        DeletionPageFeedbackType.OPENING -> R.string.account_deletion_feedback_opening
        DeletionPageFeedbackType.RETURNED -> R.string.account_deletion_feedback_returned
        DeletionPageFeedbackType.NO_BROWSER -> R.string.account_deletion_feedback_no_browser
        DeletionPageFeedbackType.REJECTED -> R.string.account_deletion_feedback_rejected
    }
    Text(
        text = stringResource(message, stringResource(page.titleResourceId)),
        modifier =
            Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                .testTag(AccountDeletionTestTags.FEEDBACK)
    )
}

object AccountDeletionTestTags {
    const val ROOT = "account-deletion-root"
    const val CONTENT = "account-deletion-content"
    const val REMOTE_REQUEST = "account-deletion-remote-request"
    const val PRIVACY = "account-deletion-privacy"
    const val REQUEST = "account-deletion-request"
    const val FEEDBACK = "account-deletion-feedback"
    const val LOCAL_DATA = "account-deletion-local-data"
    const val SEARCH = "account-deletion-search"
    const val WISHLIST = "account-deletion-wishlist"
    const val CART = "account-deletion-cart"
    const val CLEAR = "account-deletion-clear"
    const val CONFIRMATION = "account-deletion-confirmation"
    const val CONFIRM = "account-deletion-confirm"
    const val UNAVAILABLE = "account-deletion-unavailable"
    const val RESULT = "account-deletion-result"
    const val FINISH = "account-deletion-finish"
}
