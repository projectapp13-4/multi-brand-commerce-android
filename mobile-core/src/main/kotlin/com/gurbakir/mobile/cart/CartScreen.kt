@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@file:Suppress("FunctionNaming")

package com.gurbakir.mobile.cart

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.gurbakir.foundation.ui.LocalBrandSpacing
import com.gurbakir.mobile.checkout.CheckoutState
import com.gurbakir.mobile.core.R
import com.gurbakir.mobile.ui.DestinationLevel
import com.gurbakir.mobile.ui.DestinationScaffold
import com.gurbakir.mobile.ui.DestinationTitleAlignment
import com.gurbakir.mobile.ui.centeredDestinationContent
import com.gurbakir.mobile.ui.consumeDestinationInsets
import com.gurbakir.mobile.ui.withDestinationSpacing

@Composable
fun CartScreen(state: CartState, actions: CartActions, checkoutState: CheckoutState = CheckoutState()) {
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }
    DestinationScaffold(
        title = stringResource(R.string.cart_title),
        level = DestinationLevel.SECONDARY,
        modifier = Modifier.fillMaxSize().testTag(CartTestTags.ROOT),
        titleAlignment = DestinationTitleAlignment.CENTER,
        onNavigateUp = actions.onBack
    ) { padding ->
        CartBody(state, checkoutState, actions, padding, onRequestDiscard = { confirmDiscard = true })
    }
    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text(stringResource(R.string.cart_discard_title)) },
            text = { Text(stringResource(R.string.cart_discard_message)) },
            confirmButton = {
                Button(
                    onClick = {
                        confirmDiscard = false
                        actions.onDiscard()
                    },
                    modifier = Modifier.testTag(CartTestTags.DISCARD_CONFIRM)
                ) {
                    Text(stringResource(R.string.cart_discard_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDiscard = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun CartBody(
    state: CartState,
    checkoutState: CheckoutState,
    actions: CartActions,
    padding: PaddingValues,
    onRequestDiscard: () -> Unit
) {
    val spacing = LocalBrandSpacing.current
    LazyColumn(
        modifier =
            Modifier.fillMaxSize()
                .centeredDestinationContent(720.dp)
                .consumeDestinationInsets(padding),
        contentPadding = padding.withDestinationSpacing(),
        verticalArrangement = Arrangement.spacedBy(spacing.generousDp.dp)
    ) {
        cartMessages(state, checkoutState, actions)
        cartStatusContent(state, checkoutState, actions, onRequestDiscard)
    }
}
