@file:Suppress("FunctionNaming", "MatchingDeclarationName")

package com.gurbakir.mobile.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.gurbakir.foundation.ui.LocalBrandSpacing
import com.gurbakir.mobile.core.R
import com.gurbakir.mobile.home.localizedText
import com.gurbakir.storefront.StorefrontMoney

internal enum class PriceBlockEmphasis {
    CARD,
    DETAIL,
    PURCHASE
}

@Composable
@Suppress("LongParameterList")
internal fun PriceBlock(
    minimumPrice: StorefrontMoney?,
    availabilityText: String,
    unavailable: Boolean,
    modifier: Modifier = Modifier,
    maximumPrice: StorefrontMoney? = minimumPrice,
    currentPrice: StorefrontMoney? = null,
    compareAtPrice: StorefrontMoney? = null,
    emphasis: PriceBlockEmphasis = PriceBlockEmphasis.DETAIL,
    priceTestTag: String? = null,
    availabilityTestTag: String? = null
) {
    val current = currentPrice ?: minimumPrice
    val displayedPrice =
        when {
            currentPrice != null -> currentPrice.localizedText()

            minimumPrice != null && maximumPrice != null && !minimumPrice.sameValue(maximumPrice) ->
                stringResource(
                    R.string.price_range,
                    minimumPrice.localizedText(),
                    maximumPrice.localizedText()
                )

            minimumPrice != null -> minimumPrice.localizedText()

            else -> null
        }
    val displayedCompareAt =
        compareAtPrice?.takeIf { compareAt ->
            current != null &&
                compareAt.currencyCode == current.currencyCode &&
                compareAt.amount > current.amount
        }
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(LocalBrandSpacing.current.compactDp.dp)
    ) {
        displayedPrice?.let { price ->
            Text(
                text = price,
                style = emphasis.priceStyle(),
                modifier = Modifier.optionalTestTag(priceTestTag)
            )
        }
        displayedCompareAt?.let { compareAt ->
            Text(
                text = stringResource(R.string.product_compare_at_price, compareAt.localizedText()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textDecoration = TextDecoration.LineThrough
            )
        }
        Text(
            text = availabilityText,
            style = emphasis.availabilityStyle(),
            color =
                if (unavailable) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            modifier = Modifier.optionalTestTag(availabilityTestTag)
        )
    }
}

@Composable
private fun PriceBlockEmphasis.priceStyle(): TextStyle = when (this) {
    PriceBlockEmphasis.CARD -> MaterialTheme.typography.titleSmall
    PriceBlockEmphasis.DETAIL -> MaterialTheme.typography.titleLarge
    PriceBlockEmphasis.PURCHASE -> MaterialTheme.typography.titleMedium
}

@Composable
private fun PriceBlockEmphasis.availabilityStyle(): TextStyle = when (this) {
    PriceBlockEmphasis.CARD,
    PriceBlockEmphasis.PURCHASE -> MaterialTheme.typography.labelMedium

    PriceBlockEmphasis.DETAIL -> MaterialTheme.typography.bodyMedium
}

private fun StorefrontMoney.sameValue(other: StorefrontMoney): Boolean =
    currencyCode == other.currencyCode && amount.compareTo(other.amount) == 0

private fun Modifier.optionalTestTag(tag: String?): Modifier =
    then(if (tag == null) Modifier else Modifier.testTag(tag))
