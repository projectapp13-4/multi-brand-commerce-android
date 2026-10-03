@file:Suppress("FunctionNaming")

package com.gurbakir.mobile.product

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.gurbakir.mobile.core.R

internal val ProductDetailUiState.hasUnavailableSelection: Boolean
    get() =
        selectedVariant?.availableForSale == false ||
            (selectedVariant == null && displayOptions.isEmpty())

@Composable
internal fun ProductDetailUiState.availabilityText(): String? = when {
    selectedVariant?.availableForSale == true && selectedVariant?.currentlyNotInStock == true ->
        stringResource(R.string.product_backorder_available)

    else -> null
}
