@file:Suppress("FunctionNaming")

package com.gurbakir.mobile.cart

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.gurbakir.foundation.ui.LocalBrandSpacing
import com.gurbakir.mobile.checkout.CheckoutFailureCategory
import com.gurbakir.mobile.checkout.CheckoutState
import com.gurbakir.mobile.checkout.CheckoutStatus
import com.gurbakir.mobile.core.R
import com.gurbakir.mobile.home.localizedText
import com.gurbakir.mobile.ui.CommerceStatePanel
import com.gurbakir.storefront.CartOwnership

internal fun LazyListScope.cartMessages(state: CartState, checkoutState: CheckoutState, actions: CartActions) {
    if (state.status == CartStatus.LOADING || state.mutation != null || checkoutState.busy) {
        item { LinearProgressIndicator(Modifier.fillMaxWidth().testTag(CartTestTags.LOADING)) }
    }
    state.failure?.takeIf {
        state.status != CartStatus.ERROR && state.status != CartStatus.RESTRICTED
    }?.let { failure ->
        item { CartInlineFailureBanner(failure, actions.onRetry) }
    }
    if (state.cart?.hasWarnings == true || state.adjustment != null) {
        item {
            Text(
                stringResource(R.string.cart_server_adjustment),
                color = MaterialTheme.colorScheme.error
            )
        }
    }
    val sharedStorageRecovery = checkoutState.status == CheckoutStatus.FAILED &&
        state.status == CartStatus.RESTRICTED &&
        state.failure?.category == CartFailureCategory.SECURE_STORAGE &&
        checkoutState.failure?.category == CheckoutFailureCategory.SECURE_STORAGE
    if (checkoutState.status != CheckoutStatus.IDLE && !sharedStorageRecovery) {
        item {
            CheckoutFeedback(
                state = checkoutState,
                onContinue = actions.onBrowse,
                onRetryCheckout = actions.onCheckout,
                onRefresh = actions.onRetry,
                onCleanup = actions.onRetryCheckoutCleanup
            )
        }
    }
}

internal fun LazyListScope.cartStatusContent(
    state: CartState,
    checkoutState: CheckoutState,
    actions: CartActions,
    onRequestDiscard: () -> Unit
) {
    when (state.status) {
        CartStatus.INITIAL,
        CartStatus.LOADING -> item { CartLoading() }

        CartStatus.ERROR -> item { CartErrorPanel(state.failure ?: serviceFailure(), actions) }

        CartStatus.EMPTY ->
            if (!checkoutState.status.isCompleted()) {
                item { EmptyCart(actions.onBrowse) }
            }

        CartStatus.EXPIRED -> item { ExpiredCart(actions.onBrowse) }

        CartStatus.RESTRICTED ->
            item { RestrictedCart(state.ownership, state.failure, actions.onBrowse, actions.onRetry, onRequestDiscard) }

        CartStatus.ACTIVE -> activeCartContent(state, checkoutState, actions, onRequestDiscard)
    }
}

private fun LazyListScope.activeCartContent(
    state: CartState,
    checkoutState: CheckoutState,
    actions: CartActions,
    onRequestDiscard: () -> Unit
) {
    val cart = state.cart
    when {
        cart == null -> item { CartInlineFailureBanner(serviceFailure(), actions.onRetry) }

        cart.lines.isEmpty() -> item { EmptyCart(actions.onBrowse) }

        else -> {
            if (state.ownership == CartOwnership.CUSTOMER_ASSOCIATED) {
                item { CustomerAssociatedCartNotice() }
            }
            items(cart.lines) { line ->
                CartLineCard(line, state.mutation != null, actions)
            }
            item { CartTotals(cart) }
            item {
                CheckoutPanel(
                    enabled = state.mutation == null && !checkoutState.busy &&
                        checkoutState.status != CheckoutStatus.CLEANUP_REQUIRED,
                    onCheckout = actions.onCheckout
                )
            }
            item {
                TextButton(
                    onClick = onRequestDiscard,
                    enabled = state.mutation == null,
                    modifier = Modifier.testTag(CartTestTags.DISCARD)
                ) {
                    Text(stringResource(R.string.cart_discard))
                }
            }
        }
    }
}

@Composable
private fun CartTotals(cart: CartSummary) {
    val spacing = LocalBrandSpacing.current
    Column(
        modifier = Modifier.fillMaxWidth().testTag(CartTestTags.TOTAL),
        verticalArrangement = Arrangement.spacedBy(spacing.normalDp.dp)
    ) {
        HorizontalDivider()
        CartTotalRow(stringResource(R.string.cart_subtotal), cart.subtotal.localizedText())
        CartTotalRow(
            stringResource(R.string.cart_estimated_total),
            cart.total.localizedText(),
            emphasized = true
        )
        Text(stringResource(R.string.cart_totals_estimate_notice))
    }
}

@Composable
private fun EmptyCart(onBrowse: () -> Unit) {
    CommerceStatePanel(
        title = stringResource(R.string.cart_empty),
        body = stringResource(R.string.cart_empty_body),
        primaryActionLabel = stringResource(R.string.cart_continue_shopping),
        onPrimaryAction = onBrowse,
        primaryActionTestTag = CartTestTags.BROWSE,
        testTag = CartTestTags.EMPTY
    )
}

@Composable
private fun ExpiredCart(onBrowse: () -> Unit) {
    CommerceStatePanel(
        title = stringResource(R.string.cart_expired_title),
        body = stringResource(R.string.cart_expired),
        primaryActionLabel = stringResource(R.string.cart_continue_shopping),
        onPrimaryAction = onBrowse,
        primaryActionTestTag = CartTestTags.BROWSE,
        testTag = CartTestTags.EXPIRED
    )
}

@Composable
private fun RestrictedCart(
    ownership: CartOwnership?,
    failure: CartFailure?,
    onBrowse: () -> Unit,
    onRetry: () -> Unit,
    onRequestDiscard: () -> Unit
) {
    val verificationPending = ownership == CartOwnership.VERIFY_PENDING
    CommerceStatePanel(
        title =
            stringResource(
                if (failure?.category == CartFailureCategory.SECURE_STORAGE) {
                    R.string.cart_error_title
                } else if (ownership == CartOwnership.DETACH_PENDING) {
                    R.string.cart_detaching_title
                } else {
                    R.string.cart_restricted_title
                }
            ),
        body =
            stringResource(
                if (failure?.category == CartFailureCategory.SECURE_STORAGE) {
                    R.string.account_failure_secure_storage
                } else if (ownership == CartOwnership.DETACH_PENDING) {
                    R.string.cart_detaching
                } else {
                    R.string.cart_restricted
                }
            ),
        primaryActionLabel = stringResource(
            if (verificationPending) R.string.retry else R.string.cart_continue_shopping
        ),
        onPrimaryAction = if (verificationPending) onRetry else onBrowse,
        primaryActionTestTag = if (verificationPending) CartTestTags.RETRY else CartTestTags.BROWSE,
        secondaryActionLabel = stringResource(R.string.cart_discard),
        onSecondaryAction = onRequestDiscard,
        secondaryActionTestTag = CartTestTags.DISCARD,
        testTag = CartTestTags.RESTRICTED
    )
}

@Composable
private fun CartInlineFailureBanner(failure: CartFailure, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().testTag(CartTestTags.ERROR),
        verticalArrangement = Arrangement.spacedBy(LocalBrandSpacing.current.compactDp.dp)
    ) {
        Text(stringResource(failure.messageResource()), color = MaterialTheme.colorScheme.error)
        if (failure.retryable) {
            TextButton(onClick = onRetry, modifier = Modifier.testTag(CartTestTags.RETRY)) {
                Text(stringResource(R.string.retry))
            }
        }
    }
}

@Composable
private fun CartErrorPanel(failure: CartFailure, actions: CartActions) {
    CommerceStatePanel(
        title = stringResource(R.string.cart_error_title),
        body = stringResource(failure.messageResource()),
        primaryActionLabel =
            stringResource(if (failure.retryable) R.string.retry else R.string.cart_continue_shopping),
        onPrimaryAction = if (failure.retryable) actions.onRetry else actions.onBrowse,
        primaryActionTestTag = if (failure.retryable) CartTestTags.RETRY else CartTestTags.BROWSE,
        secondaryActionLabel =
            if (failure.retryable) stringResource(R.string.cart_continue_shopping) else null,
        onSecondaryAction = if (failure.retryable) actions.onBrowse else null,
        secondaryActionTestTag = if (failure.retryable) CartTestTags.BROWSE else null,
        testTag = CartTestTags.ERROR
    )
}

@Composable
private fun CartLoading() {
    CommerceStatePanel(
        title = stringResource(R.string.cart_loading_title),
        body = stringResource(R.string.cart_loading_body),
        testTag = CartTestTags.LOADING_STATE
    )
}
