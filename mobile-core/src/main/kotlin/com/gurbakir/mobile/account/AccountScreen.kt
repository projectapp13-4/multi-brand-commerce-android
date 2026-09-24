@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@file:Suppress("FunctionNaming", "TooManyFunctions")

package com.gurbakir.mobile.account

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.gurbakir.foundation.ui.LocalBrandSpacing
import com.gurbakir.mobile.core.R
import com.gurbakir.mobile.ui.DestinationLevel
import com.gurbakir.mobile.ui.DestinationScaffold
import com.gurbakir.mobile.ui.centeredDestinationContent
import com.gurbakir.mobile.ui.consumeDestinationInsets
import com.gurbakir.mobile.ui.withDestinationSpacing

data class AccountActions(
    val onSignIn: () -> Unit,
    val onRetry: () -> Unit,
    val onRefresh: () -> Unit,
    val onLogout: () -> Unit,
    val onProfile: () -> Unit,
    val onAddresses: () -> Unit,
    val onOrders: () -> Unit,
    val onAccountDeletion: () -> Unit,
    val onLegalSupport: () -> Unit,
    val onSearchHistory: (() -> Unit)?,
    val onWishlist: (() -> Unit)?,
    val onCart: () -> Unit
)

@Composable
@Suppress("LongMethod") // The root state branches are easier to audit together.
fun AccountScreen(state: AccountUiState, actions: AccountActions) {
    val spacing = LocalBrandSpacing.current
    val signInFocusRequester = remember { FocusRequester() }
    var signInDetailsExpanded by rememberSaveable { mutableStateOf(false) }
    var localDataExpanded by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(state.notices) {
        if (AccountNotice.AUTHORIZATION_CANCELLED in state.notices) {
            withFrameNanos { }
            signInFocusRequester.requestFocus()
        }
    }

    DestinationScaffold(
        title = stringResource(R.string.account_title),
        level = DestinationLevel.PRIMARY,
        modifier = Modifier.testTag(AccountTestTags.ROOT)
    ) { padding ->
        LazyColumn(
            modifier =
                Modifier.fillMaxSize()
                    .centeredDestinationContent(720.dp)
                    .consumeDestinationInsets(padding)
                    .testTag(AccountTestTags.CONTENT),
            contentPadding =
                padding.withDestinationSpacing(
                    horizontal = spacing.sectionDp.dp,
                    vertical = spacing.sectionDp.dp
                ),
            verticalArrangement = Arrangement.spacedBy(spacing.generousDp.dp)
        ) {
            if (state.busy) {
                item { AccountProgress(state.phase) }
            }

            if (state.notices.isNotEmpty() || state.failure != null) {
                item { AccountFeedback(state = state, actions = actions) }
            }

            when {
                state.summary != null -> {
                    item { AccountIdentityCard(state.summary) }
                    item {
                        AccountCustomerTasks(
                            actions = actions,
                            enabled = !state.busy
                        )
                    }
                    item {
                        AccountSupportAndPrivacy(
                            actions = actions,
                            authenticated = true,
                            sessionActionsEnabled = state.canUseSessionActions,
                            localDataExpanded = localDataExpanded,
                            onLocalDataExpandedChanged = { localDataExpanded = it }
                        )
                    }
                    item {
                        AccountSignOutAction(
                            enabled = state.canUseSessionActions,
                            onLogout = actions.onLogout
                        )
                    }
                }

                state.phase != AccountPhase.RESTORING && state.phase != AccountPhase.LOGGING_OUT -> {
                    item {
                        AccountSignInCard(
                            state = state,
                            actions = actions,
                            signInFocusRequester = signInFocusRequester,
                            detailsExpanded = signInDetailsExpanded,
                            onDetailsExpandedChanged = { signInDetailsExpanded = it }
                        )
                    }
                    item {
                        AccountSupportAndPrivacy(
                            actions = actions,
                            authenticated = false,
                            sessionActionsEnabled = false,
                            localDataExpanded = localDataExpanded,
                            onLocalDataExpandedChanged = { localDataExpanded = it }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AccountProgress(phase: AccountPhase) {
    val spacing = LocalBrandSpacing.current
    Column(
        modifier =
            Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                .testTag(AccountTestTags.STATUS),
        verticalArrangement = Arrangement.spacedBy(spacing.normalDp.dp)
    ) {
        Text(
            text = stringResource(phase.statusResourceId()),
            style = MaterialTheme.typography.titleMedium
        )
        LinearProgressIndicator(
            modifier = Modifier.fillMaxWidth().testTag(AccountTestTags.PROGRESS)
        )
    }
}

@Composable
private fun AccountIdentityCard(summary: AccountSummary) {
    val spacing = LocalBrandSpacing.current
    Card(
        modifier = Modifier.fillMaxWidth().testTag(AccountTestTags.SUMMARY),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(spacing.generousDp.dp),
            horizontalArrangement = Arrangement.spacedBy(spacing.generousDp.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                shape = CircleShape
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_nav_account),
                    contentDescription = null,
                    modifier = Modifier.padding(spacing.normalDp.dp).size(28.dp)
                )
            }
            Text(
                text = summary.displayName,
                style = MaterialTheme.typography.titleMedium,
                modifier =
                    Modifier.weight(1f)
                        .semantics { heading() }
                        .testTag(AccountTestTags.DISPLAY_NAME)
            )
        }
    }
}

@Composable
private fun AccountFeedback(state: AccountUiState, actions: AccountActions) {
    val spacing = LocalBrandSpacing.current
    val hasFailure = state.failure != null
    Surface(
        modifier =
            Modifier.fillMaxWidth()
                .semantics { liveRegion = LiveRegionMode.Polite }
                .testTag(AccountTestTags.FEEDBACK),
        color =
            if (hasFailure) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                MaterialTheme.colorScheme.secondaryContainer
            },
        contentColor =
            if (hasFailure) {
                MaterialTheme.colorScheme.onErrorContainer
            } else {
                MaterialTheme.colorScheme.onSecondaryContainer
            },
        shape = MaterialTheme.shapes.medium
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(spacing.generousDp.dp),
            verticalArrangement = Arrangement.spacedBy(spacing.normalDp.dp)
        ) {
            state.notices.sortedBy(AccountNotice::ordinal).forEach { notice ->
                Text(stringResource(notice.messageResourceId()))
            }
            state.failure?.let { failure ->
                Text(stringResource(failure.messageResourceId()))
            }
            if (state.retryable) {
                val retainedSession = state.summary != null
                OutlinedButton(
                    onClick = if (retainedSession) actions.onRefresh else actions.onRetry,
                    modifier =
                        Modifier.fillMaxWidth()
                            .testTag(
                                if (retainedSession) {
                                    AccountTestTags.REFRESH
                                } else {
                                    AccountTestTags.RETRY
                                }
                            )
                ) {
                    Text(stringResource(R.string.retry))
                }
            }
        }
    }
}

@Composable
private fun AccountSignInCard(
    state: AccountUiState,
    actions: AccountActions,
    signInFocusRequester: FocusRequester,
    detailsExpanded: Boolean,
    onDetailsExpandedChanged: (Boolean) -> Unit
) {
    val spacing = LocalBrandSpacing.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(spacing.generousDp.dp),
            verticalArrangement = Arrangement.spacedBy(spacing.normalDp.dp)
        ) {
            Text(
                text = stringResource(R.string.account_sign_in_heading),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() }
            )
            Text(
                text = stringResource(R.string.account_signed_out_benefit),
                style = MaterialTheme.typography.bodyMedium
            )
            if (!state.retryable) {
                Button(
                    onClick = actions.onSignIn,
                    enabled = state.canSignIn,
                    modifier =
                        Modifier.fillMaxWidth()
                            .focusRequester(signInFocusRequester)
                            .focusable()
                            .testTag(AccountTestTags.SIGN_IN)
                ) {
                    Text(stringResource(R.string.account_sign_in))
                }
            }
            DisclosureButton(
                expanded = detailsExpanded,
                showLabel = R.string.account_sign_in_details,
                hideLabel = R.string.account_sign_in_details_hide,
                testTag = AccountTestTags.SIGN_IN_DETAILS,
                onExpandedChanged = onDetailsExpandedChanged
            )
            if (detailsExpanded) {
                Text(
                    text = stringResource(R.string.account_hosted_sign_in_explanation),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.testTag(AccountTestTags.SIGN_IN_DETAILS_PANEL)
                )
            }
        }
    }
}

@Composable
private fun AccountCustomerTasks(actions: AccountActions, enabled: Boolean) {
    val spacing = LocalBrandSpacing.current
    Card(
        modifier = Modifier.fillMaxWidth().testTag(AccountTestTags.CUSTOMER_TASKS),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(spacing.normalDp.dp)) {
            AccountSectionHeading(R.string.account_customer_tasks_heading)
            AccountMenuRow(
                title = stringResource(R.string.account_manage_orders),
                iconResource = R.drawable.ic_account_orders,
                enabled = enabled,
                testTag = AccountTestTags.ORDERS,
                onClick = actions.onOrders
            )
            AccountDivider()
            AccountMenuRow(
                title = stringResource(R.string.profile_title),
                iconResource = R.drawable.ic_nav_account,
                enabled = enabled,
                testTag = AccountTestTags.PROFILE,
                onClick = actions.onProfile
            )
            AccountDivider()
            AccountMenuRow(
                title = stringResource(R.string.account_manage_addresses),
                iconResource = R.drawable.ic_account_address,
                enabled = enabled,
                testTag = AccountTestTags.ADDRESSES,
                onClick = actions.onAddresses
            )
        }
    }
}

@Composable
@Suppress("LongParameterList")
private fun AccountSupportAndPrivacy(
    actions: AccountActions,
    authenticated: Boolean,
    sessionActionsEnabled: Boolean,
    localDataExpanded: Boolean,
    onLocalDataExpandedChanged: (Boolean) -> Unit
) {
    val spacing = LocalBrandSpacing.current
    val expandedDescription = stringResource(R.string.state_expanded)
    val collapsedDescription = stringResource(R.string.state_collapsed)
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(spacing.normalDp.dp)) {
            if (authenticated) {
                AccountSectionHeading(R.string.account_support_privacy_heading)
            }
            AccountMenuRow(
                title = stringResource(R.string.legal_support_title),
                iconResource = R.drawable.ic_account_help,
                testTag = AccountTestTags.LEGAL_SUPPORT,
                onClick = actions.onLegalSupport
            )
            AccountDivider()
            Column(modifier = Modifier.fillMaxWidth().testTag(AccountTestTags.LOCAL_DATA)) {
                AccountMenuRow(
                    title = stringResource(R.string.account_local_data_heading),
                    iconResource = R.drawable.ic_account_device,
                    trailingIconResource = R.drawable.ic_expand_more,
                    trailingIconRotationDegrees = if (localDataExpanded) 180f else 0f,
                    modifier =
                        Modifier.semantics {
                            stateDescription =
                                if (localDataExpanded) {
                                    expandedDescription
                                } else {
                                    collapsedDescription
                                }
                        },
                    testTag = AccountTestTags.LOCAL_DATA_DISCLOSURE,
                    onClick = { onLocalDataExpandedChanged(!localDataExpanded) }
                )
                if (localDataExpanded) {
                    LocalDataPanel(actions)
                }
            }
            if (authenticated) {
                AccountDivider()
                AccountDeletionButton(
                    enabled = sessionActionsEnabled,
                    onAccountDeletion = actions.onAccountDeletion
                )
            }
        }
    }
}

@Composable
private fun LocalDataPanel(actions: AccountActions) {
    val spacing = LocalBrandSpacing.current
    Column(
        modifier =
            Modifier.fillMaxWidth()
                .padding(
                    start = spacing.generousDp.dp,
                    end = spacing.generousDp.dp,
                    bottom = spacing.normalDp.dp
                ).testTag(AccountTestTags.LOCAL_DATA_PANEL),
        verticalArrangement = Arrangement.spacedBy(spacing.compactDp.dp)
    ) {
        Text(
            text = stringResource(
                when {
                    actions.onSearchHistory != null && actions.onWishlist != null ->
                        R.string.account_local_data_explanation

                    actions.onSearchHistory != null -> R.string.account_local_data_search_only

                    actions.onWishlist != null -> R.string.account_local_data_wishlist_only

                    else -> R.string.account_local_data_cart_only
                }
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        actions.onSearchHistory?.let { onClick ->
            AccountMenuRow(
                title = stringResource(R.string.account_manage_search_history),
                iconResource = R.drawable.ic_nav_search,
                testTag = AccountTestTags.SEARCH_HISTORY,
                onClick = onClick
            )
        }
        actions.onWishlist?.let { onClick ->
            AccountMenuRow(
                title = stringResource(R.string.account_manage_wishlist),
                iconResource = R.drawable.ic_nav_wishlist,
                testTag = AccountTestTags.WISHLIST,
                onClick = onClick
            )
        }
        AccountMenuRow(
            title = stringResource(R.string.account_manage_cart),
            iconResource = R.drawable.ic_account_cart,
            testTag = AccountTestTags.CART,
            onClick = actions.onCart
        )
    }
}

@Composable
private fun AccountSignOutAction(enabled: Boolean, onLogout: () -> Unit) {
    TextButton(
        onClick = onLogout,
        enabled = enabled,
        modifier =
            Modifier.fillMaxWidth()
                .defaultMinSize(minHeight = 48.dp)
                .testTag(AccountTestTags.LOGOUT)
    ) {
        Text(stringResource(R.string.account_logout))
    }
}

@Composable
private fun AccountSectionHeading(titleResource: Int) {
    val spacing = LocalBrandSpacing.current
    Text(
        text = stringResource(titleResource),
        style = MaterialTheme.typography.titleMedium,
        modifier =
            Modifier.fillMaxWidth()
                .padding(horizontal = spacing.normalDp.dp, vertical = spacing.normalDp.dp)
                .semantics { heading() }
    )
}

@Composable
private fun AccountDivider() {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun DisclosureButton(
    expanded: Boolean,
    showLabel: Int,
    hideLabel: Int,
    testTag: String,
    onExpandedChanged: (Boolean) -> Unit
) {
    val expandedDescription = stringResource(R.string.state_expanded)
    val collapsedDescription = stringResource(R.string.state_collapsed)
    TextButton(
        onClick = { onExpandedChanged(!expanded) },
        modifier =
            Modifier.fillMaxWidth()
                .semantics {
                    stateDescription = if (expanded) expandedDescription else collapsedDescription
                }.testTag(testTag)
    ) {
        Text(stringResource(if (expanded) hideLabel else showLabel))
    }
}

private fun AccountPhase.statusResourceId(): Int = when (this) {
    AccountPhase.RESTORING -> R.string.account_status_restoring
    AccountPhase.SIGNED_OUT -> R.string.account_status_signed_out
    AccountPhase.PREPARING -> R.string.account_status_preparing
    AccountPhase.AWAITING_BROWSER -> R.string.account_status_awaiting_browser
    AccountPhase.EXCHANGING -> R.string.account_status_exchanging
    AccountPhase.AUTHENTICATED -> R.string.account_status_authenticated
    AccountPhase.REFRESHING -> R.string.account_status_refreshing
    AccountPhase.LOGGING_OUT -> R.string.account_status_logging_out
    AccountPhase.FAILED -> R.string.account_status_failed
}

private fun AccountNotice.messageResourceId(): Int = when (this) {
    AccountNotice.AUTHORIZATION_CANCELLED -> R.string.account_notice_cancelled
    AccountNotice.SESSION_EXPIRED -> R.string.account_notice_session_expired
    AccountNotice.REMOTE_LOGOUT_UNVERIFIED -> R.string.account_notice_remote_logout
    AccountNotice.CART_PROTECTED -> R.string.account_notice_cart_protected
    AccountNotice.CART_RECONCILIATION_FAILED -> R.string.account_notice_cart_reconciliation
}

private fun AccountFailure.messageResourceId(): Int = when (this) {
    AccountFailure.SECURE_STORAGE -> R.string.account_failure_secure_storage

    AccountFailure.DISCOVERY -> R.string.account_failure_discovery

    AccountFailure.CALLBACK -> R.string.account_failure_callback

    AccountFailure.TOKEN_TRANSIENT,
    AccountFailure.IDENTITY_TRANSPORT -> R.string.account_failure_connection

    AccountFailure.TOKEN_REJECTED,
    AccountFailure.TOKEN_INVALID,
    AccountFailure.TOKEN_MISSING_FIELDS,
    AccountFailure.TOKEN_TYPE,
    AccountFailure.TOKEN_SCOPE,
    AccountFailure.TOKEN_EXPIRED,
    AccountFailure.TOKEN_IDENTITY,
    AccountFailure.IDENTITY_AUTHENTICATION -> R.string.account_failure_session

    AccountFailure.IDENTITY_DISCOVERY,
    AccountFailure.IDENTITY_GRAPHQL -> R.string.account_failure_service
}

object AccountTestTags {
    const val ROOT = "account-root"
    const val CONTENT = "account-content"
    const val STATUS = "account-status"
    const val PROGRESS = "account-progress"
    const val SUMMARY = "account-summary"
    const val DISPLAY_NAME = "account-display-name"
    const val FEEDBACK = "account-feedback"
    const val SIGN_IN = "account-sign-in"
    const val SIGN_IN_DETAILS = "account-sign-in-details"
    const val SIGN_IN_DETAILS_PANEL = "account-sign-in-details-panel"
    const val REFRESH = "account-refresh"
    const val LOGOUT = "account-logout"
    const val PROFILE = "account-profile"
    const val ADDRESSES = "account-addresses"
    const val ORDERS = "account-orders"
    const val CUSTOMER_TASKS = "account-customer-tasks"
    const val SESSION_SETTINGS = "account-session-settings"
    const val SESSION_PANEL = "account-session-panel"
    const val ACCOUNT_DELETION = "account-deletion"
    const val RETRY = "account-retry"
    const val LOCAL_DATA = "account-local-data"
    const val LOCAL_DATA_DISCLOSURE = "account-local-data-disclosure"
    const val LOCAL_DATA_PANEL = "account-local-data-panel"
    const val SEARCH_HISTORY = "account-search-history"
    const val WISHLIST = "account-wishlist"
    const val CART = "account-cart"
    const val LEGAL_SUPPORT = "account-legal-support"
}
