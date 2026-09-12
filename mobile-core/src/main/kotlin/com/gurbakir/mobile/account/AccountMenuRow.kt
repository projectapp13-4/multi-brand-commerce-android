@file:Suppress("FunctionNaming")

package com.gurbakir.mobile.account

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.gurbakir.foundation.ui.LocalBrandSpacing
import com.gurbakir.mobile.core.R

@Suppress("LongParameterList")
@Composable
internal fun AccountMenuRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes iconResource: Int? = null,
    @DrawableRes trailingIconResource: Int = R.drawable.ic_arrow_back,
    trailingIconRotationDegrees: Float = TRAILING_ICON_ROTATION_DEGREES,
    enabled: Boolean = true,
    testTag: String? = null
) {
    val spacing = LocalBrandSpacing.current
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier =
            modifier.fillMaxWidth()
                .defaultMinSize(minHeight = 56.dp)
                .then(if (testTag == null) Modifier else Modifier.testTag(testTag)),
        color = Color.Transparent,
        shape = MaterialTheme.shapes.small
    ) {
        Row(
            modifier =
                Modifier.fillMaxWidth()
                    .padding(
                        horizontal = spacing.generousDp.dp,
                        vertical = spacing.normalDp.dp
                    ),
            horizontalArrangement = Arrangement.spacedBy(spacing.normalDp.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            iconResource?.let { resource ->
                Icon(
                    painter = painterResource(resource),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )
            Icon(
                painter = painterResource(trailingIconResource),
                contentDescription = null,
                modifier = Modifier.size(24.dp).rotate(trailingIconRotationDegrees)
            )
        }
    }
}

private const val TRAILING_ICON_ROTATION_DEGREES = 180f
