@file:Suppress("FunctionNaming")

package com.gurbakir.mobile.product

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.gurbakir.foundation.ui.LocalBrandSpacing
import com.gurbakir.mobile.core.R

@Composable
internal fun VariantSelector(name: String, values: List<ProductOptionValueState>, onSelect: (String) -> Unit) {
    val spacing = LocalBrandSpacing.current
    val unavailable = stringResource(R.string.product_unavailable)
    Column(verticalArrangement = Arrangement.spacedBy(spacing.compactDp.dp)) {
        Text(name, style = MaterialTheme.typography.labelLarge)
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(spacing.normalDp.dp)
        ) {
            values.forEach { value ->
                FilterChip(
                    selected = value.selected,
                    onClick = { onSelect(value.name) },
                    enabled = value.existsForCurrentSelection && value.hasAvailableMatch,
                    label = { Text(value.name) },
                    modifier = Modifier.heightIn(min = VARIANT_TOUCH_TARGET)
                        .testTag(ProductDetailTestTags.option(name, value.name))
                        .semantics { if (!value.hasAvailableMatch) stateDescription = unavailable }
                )
            }
        }
    }
}

private val VARIANT_TOUCH_TARGET = 48.dp
