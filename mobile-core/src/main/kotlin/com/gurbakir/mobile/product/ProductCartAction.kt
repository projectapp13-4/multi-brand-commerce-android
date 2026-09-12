@file:Suppress("FunctionNaming")

package com.gurbakir.mobile.product

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.gurbakir.foundation.ui.LocalBrandSpacing
import com.gurbakir.mobile.cart.CartFailureCategory
import com.gurbakir.mobile.core.R
import com.gurbakir.mobile.ui.PriceBlock
import com.gurbakir.mobile.ui.PriceBlockEmphasis

@Composable
internal fun ProductPurchaseBar(
    state: ProductDetailUiState,
    actions: ProductDetailActions,
    includeNavigationBarInset: Boolean = true
) {
    val spacing = LocalBrandSpacing.current
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = PURCHASE_BAR_ELEVATION,
        modifier =
            Modifier.fillMaxWidth()
                .then(
                    if (includeNavigationBarInset) {
                        Modifier.windowInsetsPadding(
                            WindowInsets.navigationBars.only(WindowInsetsSides.Bottom)
                        )
                    } else {
                        Modifier
                    }
                )
                .testTag(ProductDetailTestTags.PURCHASE_BAR)
    ) {
        BoxWithConstraints(
            modifier =
                Modifier.fillMaxWidth()
                    .padding(horizontal = spacing.normalDp.dp, vertical = spacing.compactDp.dp)
        ) {
            val stackContent =
                maxWidth < PURCHASE_BAR_COMPACT_WIDTH ||
                    LocalDensity.current.fontScale >= PURCHASE_BAR_STACK_FONT_SCALE
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(spacing.compactDp.dp)
            ) {
                if (stackContent) {
                    PurchaseSummary(state, Modifier.fillMaxWidth())
                    AddToCartButton(state, actions, Modifier.fillMaxWidth())
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(spacing.normalDp.dp)
                    ) {
                        PurchaseSummary(state, Modifier.weight(1f))
                        AddToCartButton(
                            state,
                            actions,
                            Modifier.widthIn(min = PURCHASE_BUTTON_MIN_WIDTH)
                        )
                    }
                }
                ProductCartFeedback(state, actions)
            }
        }
    }
}

@Composable
private fun PurchaseSummary(state: ProductDetailUiState, modifier: Modifier) {
    PriceBlock(
        minimumPrice = state.minimumPrice,
        maximumPrice = state.maximumPrice,
        currentPrice = state.selectedVariant?.price,
        compareAtPrice = state.selectedVariant?.compareAtPrice,
        availabilityText = state.availabilityText(),
        unavailable = state.hasUnavailableSelection,
        modifier = modifier,
        emphasis = PriceBlockEmphasis.PURCHASE,
        priceTestTag = ProductDetailTestTags.PURCHASE_PRICE,
        availabilityTestTag = ProductDetailTestTags.PURCHASE_AVAILABILITY
    )
}

@Composable
private fun AddToCartButton(state: ProductDetailUiState, actions: ProductDetailActions, modifier: Modifier) {
    Button(
        onClick = actions.onAddToCart,
        enabled = state.purchaseIntent != null && !state.addingToCart,
        modifier = modifier.heightIn(min = MINIMUM_TOUCH_TARGET_SIZE).testTag(ProductDetailTestTags.ADD_TO_CART)
    ) {
        Text(
            if (state.addingToCart) {
                stringResource(R.string.product_adding_to_cart)
            } else {
                stringResource(R.string.product_add_to_cart)
            }
        )
    }
}

@Composable
private fun ProductCartFeedback(state: ProductDetailUiState, actions: ProductDetailActions) {
    val feedback = state.cartFeedbackResource()
    feedback?.let { message ->
        Column(
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            verticalArrangement = Arrangement.spacedBy(LocalBrandSpacing.current.compactDp.dp)
        ) {
            Text(
                stringResource(message),
                color =
                    if (state.cartFeedback == ProductCartFeedback.ADDED) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                modifier = Modifier.testTag(ProductDetailTestTags.CART_FEEDBACK)
            )
            if (state.cartFeedback == ProductCartFeedback.ADDED) {
                TextButton(
                    onClick = actions.onOpenCart,
                    modifier = Modifier.testTag(ProductDetailTestTags.OPEN_CART)
                ) {
                    Text(stringResource(R.string.cart_open))
                }
            }
        }
    }
}

private fun ProductDetailUiState.cartFeedbackResource(): Int? = when {
    cartFeedback == ProductCartFeedback.ADDED -> R.string.product_added_to_cart

    cartFeedback == ProductCartFeedback.RESTRICTED -> R.string.product_cart_restricted

    cartFailure?.category == CartFailureCategory.QUANTITY_OR_AVAILABILITY ->
        R.string.product_cart_quantity_error

    cartFailure != null -> R.string.product_cart_error

    else -> null
}

private val PURCHASE_BAR_ELEVATION = 3.dp
private val PURCHASE_BAR_COMPACT_WIDTH = 340.dp
private val PURCHASE_BUTTON_MIN_WIDTH = 144.dp
private val MINIMUM_TOUCH_TARGET_SIZE = 48.dp
private const val PURCHASE_BAR_STACK_FONT_SCALE = 1.5f
