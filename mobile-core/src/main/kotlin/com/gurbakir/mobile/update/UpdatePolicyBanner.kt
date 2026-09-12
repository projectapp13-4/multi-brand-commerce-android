@file:Suppress("FunctionNaming")

package com.gurbakir.mobile.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gurbakir.foundation.ui.LocalBrandSpacing
import com.gurbakir.mobile.core.R

internal data class UpdatePolicyActions(
    val onRetry: () -> Unit,
    val onLegalSupport: () -> Unit,
    val onDeferUpdate: () -> Unit,
    val onDismissMaintenance: () -> Unit
)

@Composable
internal fun UpdatePolicyDestination(onLegalSupport: () -> Unit, modifier: Modifier = Modifier) {
    val viewModel: UpdatePolicyViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    UpdatePolicyBanner(
        state = state,
        modifier = modifier,
        actions =
            UpdatePolicyActions(
                onRetry = viewModel::refresh,
                onLegalSupport = onLegalSupport,
                onDeferUpdate = viewModel::deferUpdate,
                onDismissMaintenance = viewModel::dismissMaintenance
            )
    )
}

@Composable
internal fun UpdatePolicyBanner(
    state: UpdatePolicyUiState,
    actions: UpdatePolicyActions,
    modifier: Modifier = Modifier
) {
    val notice = state.notice ?: return
    UpdatePolicyNoticeCard(
        notice = notice,
        refreshing = state.refreshing,
        actions = actions,
        modifier = modifier
    )
}

@Composable
private fun UpdatePolicyNoticeCard(
    notice: UpdatePolicyNotice,
    refreshing: Boolean,
    actions: UpdatePolicyActions,
    modifier: Modifier
) {
    val spacing = LocalBrandSpacing.current
    val isUpdate = notice is UpdatePolicyNotice.OptionalUpdate
    Card(
        modifier =
            modifier.fillMaxWidth()
                .padding(horizontal = spacing.sectionDp.dp, vertical = spacing.compactDp.dp)
                .semantics { liveRegion = LiveRegionMode.Polite }
                .testTag(
                    if (isUpdate) {
                        UpdatePolicyTestTags.UPDATE_NOTICE
                    } else {
                        UpdatePolicyTestTags.MAINTENANCE_NOTICE
                    }
                )
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(spacing.generousDp.dp),
            verticalArrangement = Arrangement.spacedBy(spacing.compactDp.dp)
        ) {
            Text(
                text = stringResource(notice.titleResourceId()),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() }
            )
            Text(stringResource(notice.messageResourceId()))
            if (refreshing) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().testTag(UpdatePolicyTestTags.PROGRESS)
                )
            }
            UpdatePolicyNoticeActions(notice, refreshing, actions)
        }
    }
}

@Composable
private fun UpdatePolicyNoticeActions(notice: UpdatePolicyNotice, refreshing: Boolean, actions: UpdatePolicyActions) {
    TextButton(
        onClick = actions.onRetry,
        enabled = !refreshing,
        modifier = Modifier.testTag(UpdatePolicyTestTags.RETRY)
    ) {
        Text(stringResource(R.string.update_policy_retry))
    }
    TextButton(
        onClick = actions.onLegalSupport,
        modifier = Modifier.testTag(UpdatePolicyTestTags.LEGAL_SUPPORT)
    ) {
        Text(stringResource(R.string.update_policy_help))
    }
    TextButton(
        onClick =
            if (notice is UpdatePolicyNotice.OptionalUpdate) {
                actions.onDeferUpdate
            } else {
                actions.onDismissMaintenance
            },
        modifier = Modifier.testTag(UpdatePolicyTestTags.DISMISS)
    ) {
        Text(stringResource(notice.dismissResourceId()))
    }
}

private fun UpdatePolicyNotice.titleResourceId(): Int = if (this is UpdatePolicyNotice.OptionalUpdate) {
    R.string.update_policy_update_title
} else {
    R.string.update_policy_maintenance_title
}

private fun UpdatePolicyNotice.messageResourceId(): Int = if (this is UpdatePolicyNotice.OptionalUpdate) {
    R.string.update_policy_update_message
} else {
    R.string.update_policy_maintenance_message
}

private fun UpdatePolicyNotice.dismissResourceId(): Int = if (this is UpdatePolicyNotice.OptionalUpdate) {
    R.string.update_policy_defer
} else {
    R.string.update_policy_dismiss
}

internal object UpdatePolicyTestTags {
    const val UPDATE_NOTICE = "update-policy-update-notice"
    const val MAINTENANCE_NOTICE = "update-policy-maintenance-notice"
    const val PROGRESS = "update-policy-progress"
    const val RETRY = "update-policy-retry"
    const val LEGAL_SUPPORT = "update-policy-legal-support"
    const val DISMISS = "update-policy-dismiss"
}
