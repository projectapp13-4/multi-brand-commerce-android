@file:Suppress("FunctionNaming")

package com.gurbakir.mobile.cart

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.gurbakir.foundation.ui.LocalBrandSpacing
import com.gurbakir.mobile.core.R
import com.gurbakir.mobile.home.localizedText

@Composable
internal fun CartLineCard(line: CartLine, mutating: Boolean, actions: CartActions) {
    val spacing = LocalBrandSpacing.current
    Card(modifier = Modifier.fillMaxWidth().testTag(CartTestTags.line(line.productId))) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(spacing.generousDp.dp),
            verticalArrangement = Arrangement.spacedBy(spacing.normalDp.dp)
        ) {
            CartLineIdentity(line)
            CartQuantityControls(line, mutating, actions)
            CartLineActions(line, mutating, actions)
        }
    }
}

@Composable
private fun CartLineIdentity(line: CartLine) {
    val spacing = LocalBrandSpacing.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(spacing.normalDp.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.size(96.dp)) {
            if (line.image == null) {
                Box(contentAlignment = Alignment.Center) { Text(stringResource(R.string.cart_no_image)) }
            } else {
                AsyncImage(
                    model = line.image.uri.toASCIIString(),
                    contentDescription = line.image.altText ?: line.productTitle,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(spacing.compactDp.dp)
        ) {
            Text(line.productTitle, style = MaterialTheme.typography.titleMedium)
            if (line.variantTitle.isNotBlank() && line.variantTitle != "Default Title") {
                Text(line.variantTitle)
            }
            Text(stringResource(R.string.cart_unit_price, line.unitPrice.localizedText()))
            Text(line.totalPrice.localizedText(), style = MaterialTheme.typography.titleMedium)
            CartLineAvailability(line)
        }
    }
}

@Composable
private fun CartLineAvailability(line: CartLine) {
    if (!line.availableForSale) {
        Text(stringResource(R.string.cart_line_unavailable), color = MaterialTheme.colorScheme.error)
    } else if (line.currentlyNotInStock) {
        Text(stringResource(R.string.cart_line_backorder))
    }
}

@Composable
private fun CartQuantityControls(line: CartLine, mutating: Boolean, actions: CartActions) {
    val spacing = LocalBrandSpacing.current
    val stackControls = shouldStackCartQuantityControls(LocalDensity.current.fontScale)
    val quantity: @Composable () -> Unit = {
        Text(
            stringResource(R.string.cart_quantity, line.quantity),
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
        )
    }
    if (stackControls) {
        Column(verticalArrangement = Arrangement.spacedBy(spacing.compactDp.dp)) {
            quantity()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing.normalDp.dp)
            ) {
                CartQuantityButton(
                    labelResourceId = R.string.cart_decrease,
                    enabled = !mutating && line.previousQuantity != null,
                    testTag = CartTestTags.decrease(line.productId),
                    modifier = Modifier.weight(1f),
                    onClick = { actions.onDecrease(line) }
                )
                CartQuantityButton(
                    labelResourceId = R.string.cart_increase,
                    enabled = !mutating && line.nextQuantity != null,
                    testTag = CartTestTags.increase(line.productId),
                    modifier = Modifier.weight(1f),
                    onClick = { actions.onIncrease(line) }
                )
            }
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(spacing.compactDp.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CartQuantityButton(
                labelResourceId = R.string.cart_decrease,
                enabled = !mutating && line.previousQuantity != null,
                testTag = CartTestTags.decrease(line.productId),
                onClick = { actions.onDecrease(line) }
            )
            quantity()
            CartQuantityButton(
                labelResourceId = R.string.cart_increase,
                enabled = !mutating && line.nextQuantity != null,
                testTag = CartTestTags.increase(line.productId),
                onClick = { actions.onIncrease(line) }
            )
        }
    }
}

@Composable
private fun CartQuantityButton(
    @StringRes labelResourceId: Int,
    enabled: Boolean,
    testTag: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.testTag(testTag)
    ) {
        Text(stringResource(labelResourceId))
    }
}

@Composable
private fun CartLineActions(line: CartLine, mutating: Boolean, actions: CartActions) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        TextButton(onClick = { actions.onOpenProduct(line.productId) }) {
            Text(stringResource(R.string.cart_open_product))
        }
        TextButton(
            onClick = { actions.onRemove(line.id) },
            enabled = !mutating && line.canRemove,
            modifier = Modifier.testTag(CartTestTags.remove(line.productId))
        ) {
            Text(stringResource(R.string.cart_remove))
        }
    }
}

internal fun shouldStackCartQuantityControls(fontScale: Float): Boolean =
    fontScale >= STACK_QUANTITY_CONTROLS_FONT_SCALE

private const val STACK_QUANTITY_CONTROLS_FONT_SCALE = 1.5f
