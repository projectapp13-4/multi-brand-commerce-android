@file:Suppress("FunctionNaming")

package com.gurbakir.mobile.cart

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.gurbakir.foundation.ui.LocalBrandSpacing
import com.gurbakir.mobile.checkout.CheckoutFailure
import com.gurbakir.mobile.checkout.CheckoutFailureCategory
import com.gurbakir.mobile.checkout.CheckoutState
import com.gurbakir.mobile.checkout.CheckoutStatus
import com.gurbakir.mobile.core.R
import com.gurbakir.mobile.ui.CommerceStatePanel

@Composable
internal fun CheckoutPanel(enabled: Boolean, onCheckout: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(LocalBrandSpacing.current.normalDp.dp)
    ) {
        Text(stringResource(R.string.checkout_external_context))
        Button(
            onClick = onCheckout,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth().testTag(CartTestTags.CHECKOUT)
        ) {
            Text(stringResource(R.string.checkout_start))
        }
    }
}

@Composable
internal fun CheckoutFeedback(
    state: CheckoutState,
    onContinue: () -> Unit,
    onRetryCheckout: () -> Unit,
    onRefresh: () -> Unit
) {
    val message = state.messageResource()
    if (message != null) {
        val action = state.feedbackAction(onContinue, onRetryCheckout, onRefresh)
        CommerceStatePanel(
            title = stringResource(state.titleResource()),
            body = stringResource(message),
            primaryActionLabel = action?.let { stringResource(it.labelResource) },
            onPrimaryAction = action?.onClick,
            primaryActionTestTag =
                action?.let { CartTestTags.CHECKOUT_FEEDBACK_ACTION },
            testTag = CartTestTags.CHECKOUT_FEEDBACK
        )
    }
}

internal fun CheckoutStatus.isCompleted(): Boolean =
    this == CheckoutStatus.COMPLETED || this == CheckoutStatus.COMPLETED_CURRENT_CART_PRESERVED

private fun CheckoutState.messageResource(): Int? = when (status) {
    CheckoutStatus.IDLE -> null

    CheckoutStatus.PREPARING -> R.string.checkout_preparing

    CheckoutStatus.PRESENTING,
    CheckoutStatus.IN_PROGRESS -> R.string.checkout_in_progress

    CheckoutStatus.CANCELLED -> R.string.checkout_cancelled

    CheckoutStatus.COMPLETED -> R.string.checkout_completed

    CheckoutStatus.COMPLETED_CURRENT_CART_PRESERVED -> R.string.checkout_completed_cart_preserved

    CheckoutStatus.CLEANUP_REQUIRED -> R.string.checkout_cleanup_required

    CheckoutStatus.EXTERNAL_LINK_BLOCKED -> R.string.checkout_external_link_blocked

    CheckoutStatus.FAILED -> failure?.messageResource() ?: R.string.checkout_error_fatal
}

private fun CheckoutFailure.messageResource(): Int = when (category) {
    CheckoutFailureCategory.CART_EMPTY -> R.string.checkout_error_empty
    CheckoutFailureCategory.CART_RESTRICTED -> R.string.checkout_error_restricted
    CheckoutFailureCategory.CART_UNAVAILABLE -> R.string.checkout_error_unavailable
    CheckoutFailureCategory.CONNECTION -> R.string.checkout_error_connection
    CheckoutFailureCategory.CONFIGURATION -> R.string.checkout_error_configuration
    CheckoutFailureCategory.SERVICE -> R.string.checkout_error_service
    CheckoutFailureCategory.SECURE_STORAGE -> R.string.checkout_error_storage
    CheckoutFailureCategory.INVALID_URL -> R.string.checkout_error_invalid_url
    CheckoutFailureCategory.SDK_UNAVAILABLE -> R.string.checkout_error_sdk
    CheckoutFailureCategory.NETWORK -> R.string.checkout_error_network
    CheckoutFailureCategory.EXPIRED -> R.string.checkout_error_expired
    CheckoutFailureCategory.RECOVERABLE -> R.string.checkout_error_recoverable
    CheckoutFailureCategory.FATAL -> R.string.checkout_error_fatal
}

private fun CheckoutState.titleResource(): Int = when (status) {
    CheckoutStatus.COMPLETED,
    CheckoutStatus.COMPLETED_CURRENT_CART_PRESERVED -> R.string.checkout_completed_title

    CheckoutStatus.CANCELLED -> R.string.checkout_cancelled_title

    CheckoutStatus.CLEANUP_REQUIRED,
    CheckoutStatus.EXTERNAL_LINK_BLOCKED -> R.string.checkout_attention_title

    CheckoutStatus.FAILED -> R.string.checkout_unavailable_title

    CheckoutStatus.IDLE,
    CheckoutStatus.PREPARING,
    CheckoutStatus.PRESENTING,
    CheckoutStatus.IN_PROGRESS -> R.string.checkout_secure_title
}

private fun CheckoutState.feedbackAction(
    onContinue: () -> Unit,
    onRetryCheckout: () -> Unit,
    onRefresh: () -> Unit
): CheckoutFeedbackAction? = when (status) {
    CheckoutStatus.COMPLETED,
    CheckoutStatus.COMPLETED_CURRENT_CART_PRESERVED ->
        CheckoutFeedbackAction(R.string.cart_continue_shopping, onContinue)

    CheckoutStatus.CANCELLED -> CheckoutFeedbackAction(R.string.checkout_retry, onRetryCheckout)

    CheckoutStatus.CLEANUP_REQUIRED -> CheckoutFeedbackAction(R.string.cart_refresh, onRefresh)

    CheckoutStatus.FAILED ->
        if (failure?.retryable == true) {
            CheckoutFeedbackAction(R.string.checkout_retry, onRetryCheckout)
        } else {
            CheckoutFeedbackAction(R.string.cart_refresh, onRefresh)
        }

    CheckoutStatus.IDLE,
    CheckoutStatus.PREPARING,
    CheckoutStatus.PRESENTING,
    CheckoutStatus.IN_PROGRESS,
    CheckoutStatus.EXTERNAL_LINK_BLOCKED -> null
}

private data class CheckoutFeedbackAction(val labelResource: Int, val onClick: () -> Unit)
