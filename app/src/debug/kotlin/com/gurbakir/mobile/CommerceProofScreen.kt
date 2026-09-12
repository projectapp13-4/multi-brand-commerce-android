@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@file:Suppress("FunctionNaming")

package com.gurbakir.mobile

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gurbakir.foundation.ui.LocalBrandSpacing

internal data class CommerceProofActions(
    val createCart: () -> Unit,
    val addLine: () -> Unit,
    val incrementLine: () -> Unit,
    val removeLines: () -> Unit,
    val preloadCheckout: () -> Unit,
    val presentCheckout: () -> Unit,
    val back: () -> Unit
)

@Composable
internal fun CommerceProofRoute(viewModel: CommerceProofViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val activity = LocalContext.current.findActivity()
    CommerceProofScreen(
        state = state,
        actions =
            CommerceProofActions(
                createCart = viewModel::createCart,
                addLine = viewModel::addLine,
                incrementLine = viewModel::incrementLine,
                removeLines = viewModel::removeAllLines,
                preloadCheckout = { activity?.let(viewModel::preloadCheckout) },
                presentCheckout = { activity?.let(viewModel::presentCheckout) },
                back = onBack
            )
    )
}

@Composable
internal fun CommerceProofScreen(state: CommerceProofUiState, actions: CommerceProofActions) {
    val spacing = LocalBrandSpacing.current
    Scaffold(
        modifier = Modifier.testTag(CommerceProofTestTags.ROOT),
        topBar = { TopAppBar(title = { Text(stringResource(R.string.commerce_proof_title)) }) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(spacing.sectionDp.dp),
            verticalArrangement = Arrangement.spacedBy(spacing.generousDp.dp)
        ) {
            item { CommerceProofHeader(state) }
            item { CommerceProofActionsPanel(state, actions) }
        }
    }
}

@Composable
private fun CommerceProofHeader(state: CommerceProofUiState) {
    val spacing = LocalBrandSpacing.current
    Column(verticalArrangement = Arrangement.spacedBy(spacing.generousDp.dp)) {
        Text(
            text = stringResource(R.string.integration_checkout),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.semantics { heading() }
        )
        Text(stringResource(R.string.commerce_proof_explanation))
        Text(
            text = stringResource(COMMERCE_STATUS_RESOURCES.getValue(state.phase)),
            modifier = Modifier.testTag(CommerceProofTestTags.STATUS)
        )
        state.failure?.let { failure ->
            Text(
                text = stringResource(COMMERCE_FAILURE_RESOURCES.getValue(failure)),
                modifier = Modifier.testTag(CommerceProofTestTags.FAILURE)
            )
        }
        state.snapshot?.let { snapshot ->
            Text(
                text = stringResource(R.string.commerce_cart_summary, snapshot.totalQuantity, snapshot.lineCount),
                modifier = Modifier.testTag(CommerceProofTestTags.CART_SUMMARY)
            )
            if (snapshot.warningCount > 0) {
                Text(
                    text = stringResource(R.string.commerce_cart_warning_count, snapshot.warningCount),
                    modifier = Modifier.testTag(CommerceProofTestTags.CART_WARNINGS)
                )
            }
        }
    }
}

@Composable
private fun CommerceProofActionsPanel(state: CommerceProofUiState, actions: CommerceProofActions) {
    val spacing = LocalBrandSpacing.current
    Column(verticalArrangement = Arrangement.spacedBy(spacing.generousDp.dp)) {
        if (state.canCreateCart) {
            ProofButton(R.string.commerce_create_cart, CommerceProofTestTags.CREATE_CART, actions.createCart)
        }
        if (state.canModifyCart) {
            ProofButton(R.string.commerce_add_line, CommerceProofTestTags.ADD_LINE, actions.addLine)
            ProofButton(
                R.string.commerce_increment_line,
                CommerceProofTestTags.INCREMENT_LINE,
                actions.incrementLine,
                enabled = state.snapshot?.lineCount?.let { it > 0 } == true
            )
            ProofButton(
                R.string.commerce_remove_lines,
                CommerceProofTestTags.REMOVE_LINES,
                actions.removeLines,
                enabled = state.snapshot?.lineCount?.let { it > 0 } == true
            )
        }
        if (state.canCheckout) {
            ProofButton(R.string.commerce_preload_checkout, CommerceProofTestTags.PRELOAD, actions.preloadCheckout)
            ProofButton(R.string.commerce_present_checkout, CommerceProofTestTags.PRESENT, actions.presentCheckout)
        }
        ProofButton(R.string.back, CommerceProofTestTags.BACK, actions.back, enabled = !state.busy)
    }
}

@Composable
private fun ProofButton(resourceId: Int, tag: String, onClick: () -> Unit, enabled: Boolean = true) {
    Button(onClick = onClick, enabled = enabled, modifier = Modifier.testTag(tag)) {
        Text(stringResource(resourceId))
    }
}

private val COMMERCE_STATUS_RESOURCES = mapOf(
    CommerceProofPhase.RESTORING to R.string.commerce_status_restoring,
    CommerceProofPhase.EMPTY to R.string.commerce_status_empty,
    CommerceProofPhase.ACTIVE to R.string.commerce_status_active,
    CommerceProofPhase.CREATING_CART to R.string.commerce_status_creating,
    CommerceProofPhase.UPDATING_CART to R.string.commerce_status_updating,
    CommerceProofPhase.PRELOADING_CHECKOUT to R.string.commerce_status_preloading,
    CommerceProofPhase.CHECKOUT_PRELOADED to R.string.commerce_status_preloaded,
    CommerceProofPhase.PRESENTING_CHECKOUT to R.string.commerce_status_presenting,
    CommerceProofPhase.CHECKOUT_PRESENTED to R.string.commerce_status_presented,
    CommerceProofPhase.CHECKOUT_CANCELLED to R.string.commerce_status_cancelled,
    CommerceProofPhase.CHECKOUT_COMPLETED to R.string.commerce_status_completed,
    CommerceProofPhase.CHECKOUT_FAILED to R.string.commerce_status_checkout_failed,
    CommerceProofPhase.EXTERNAL_LINK_BLOCKED to R.string.commerce_status_external_link,
    CommerceProofPhase.FAILED to R.string.commerce_status_failed
)

private val COMMERCE_FAILURE_RESOURCES = mapOf(
    CommerceProofFailure.CONFIGURATION to R.string.commerce_failure_configuration,
    CommerceProofFailure.TRANSPORT to R.string.commerce_failure_transport,
    CommerceProofFailure.GRAPHQL to R.string.commerce_failure_graphql,
    CommerceProofFailure.USER_INPUT to R.string.commerce_failure_user_input,
    CommerceProofFailure.INVALID_CART to R.string.commerce_failure_invalid_cart,
    CommerceProofFailure.SECURE_PERSISTENCE to R.string.commerce_failure_secure_persistence,
    CommerceProofFailure.NO_AVAILABLE_VARIANT to R.string.commerce_failure_no_variant,
    CommerceProofFailure.ACCOUNT_SESSION to R.string.commerce_failure_account_session,
    CommerceProofFailure.CHECKOUT_INVALID_URL to R.string.commerce_failure_checkout_url,
    CommerceProofFailure.CHECKOUT_UNAVAILABLE to R.string.commerce_failure_checkout_unavailable,
    CommerceProofFailure.CHECKOUT_NETWORK to R.string.commerce_failure_checkout_network,
    CommerceProofFailure.CHECKOUT_EXPIRED to R.string.commerce_failure_checkout_expired,
    CommerceProofFailure.CHECKOUT_CONFIGURATION to R.string.commerce_failure_checkout_configuration,
    CommerceProofFailure.CHECKOUT_RECOVERABLE to R.string.commerce_failure_checkout_recoverable,
    CommerceProofFailure.CHECKOUT_FATAL to R.string.commerce_failure_checkout_fatal
)

internal object CommerceProofTestTags {
    const val ROOT = "commerce-proof-root"
    const val STATUS = "commerce-proof-status"
    const val FAILURE = "commerce-proof-failure"
    const val CART_SUMMARY = "commerce-proof-cart-summary"
    const val CART_WARNINGS = "commerce-proof-cart-warnings"
    const val CREATE_CART = "commerce-proof-create-cart"
    const val ADD_LINE = "commerce-proof-add-line"
    const val INCREMENT_LINE = "commerce-proof-increment-line"
    const val REMOVE_LINES = "commerce-proof-remove-lines"
    const val PRELOAD = "commerce-proof-preload"
    const val PRESENT = "commerce-proof-present"
    const val BACK = "commerce-proof-back"
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
