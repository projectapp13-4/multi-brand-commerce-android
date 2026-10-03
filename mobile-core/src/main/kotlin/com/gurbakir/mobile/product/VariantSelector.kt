@file:Suppress("FunctionNaming")

package com.gurbakir.mobile.product

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.gurbakir.foundation.ui.LocalBrandSpacing
import com.gurbakir.mobile.core.R

@Composable
internal fun VariantSelector(
    name: String,
    values: List<ProductOptionValueState>,
    onSelect: (String) -> Unit,
    onClearSelection: (() -> Unit)? = null,
    clearSelectionEnabled: Boolean = true
) {
    val spacing = LocalBrandSpacing.current
    val unavailable = stringResource(R.string.product_unavailable)
    Column(verticalArrangement = Arrangement.spacedBy(spacing.compactDp.dp)) {
        FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                name,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.align(Alignment.CenterVertically)
            )
            onClearSelection?.let { onClear ->
                TextButton(
                    onClick = onClear,
                    enabled = clearSelectionEnabled,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
                    modifier = Modifier.heightIn(min = VARIANT_TOUCH_TARGET)
                        .testTag(ProductDetailTestTags.CLEAR_SELECTION)
                ) {
                    Text(stringResource(R.string.product_clear_selection))
                }
            }
        }
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
