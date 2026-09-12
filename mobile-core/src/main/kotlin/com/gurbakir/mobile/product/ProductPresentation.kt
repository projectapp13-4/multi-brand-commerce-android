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
internal fun ProductDetailUiState.availabilityText(): String = when {
    selectedVariant == null && displayOptions.isNotEmpty() ->
        stringResource(R.string.product_select_options)

    selectedVariant == null -> stringResource(R.string.product_unavailable)

    selectedVariant?.availableForSale == false -> stringResource(R.string.product_unavailable)

    selectedVariant?.currentlyNotInStock == true ->
        stringResource(R.string.product_backorder_available)

    else -> stringResource(R.string.product_available)
}
