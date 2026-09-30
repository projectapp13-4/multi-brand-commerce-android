@file:Suppress("FunctionNaming")

package com.gurbakir.mobile.cart

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.gurbakir.foundation.ui.LocalBrandSpacing
import com.gurbakir.mobile.ui.LARGE_TEXT_FONT_SCALE

@Composable
internal fun CartTotalRow(label: String, amount: String, emphasized: Boolean = false) {
    val style = if (emphasized) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        if (LocalDensity.current.fontScale >= LARGE_TEXT_FONT_SCALE || maxWidth < 320.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(LocalBrandSpacing.current.compactDp.dp)) {
                Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(amount, style = style)
            }
        } else {
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(label, modifier = Modifier.weight(1f), style = style)
                Text(amount, modifier = Modifier.weight(1f), style = style, textAlign = TextAlign.End)
            }
        }
    }
}
