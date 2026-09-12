@file:Suppress("FunctionNaming")

package com.gurbakir.mobile.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.gurbakir.foundation.ui.LocalBrandSpacing

@Composable
@Suppress("LongParameterList") // Optional recovery content forms one cohesive state-panel API.
internal fun CommerceStatePanel(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    icon: Painter? = null,
    iconContentDescription: String? = null,
    primaryActionLabel: String? = null,
    onPrimaryAction: (() -> Unit)? = null,
    primaryActionTestTag: String? = null,
    secondaryActionLabel: String? = null,
    onSecondaryAction: (() -> Unit)? = null,
    secondaryActionTestTag: String? = null,
    testTag: String? = null
) {
    require((primaryActionLabel == null) == (onPrimaryAction == null))
    require((secondaryActionLabel == null) == (onSecondaryAction == null))
    require(primaryActionTestTag == null || primaryActionLabel != null)
    require(secondaryActionTestTag == null || secondaryActionLabel != null)
    val spacing = LocalBrandSpacing.current
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.large,
        modifier = modifier.fillMaxWidth().optionalTestTag(testTag)
    ) {
        Column(
            modifier =
                Modifier.fillMaxWidth()
                    .sizeIn(minHeight = MINIMUM_STATE_PANEL_HEIGHT)
                    .padding(spacing.sectionDp.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement =
                Arrangement.spacedBy(spacing.normalDp.dp, Alignment.CenterVertically)
        ) {
            icon?.let {
                Icon(
                    painter = it,
                    contentDescription = iconContentDescription,
                    modifier = Modifier.size(STATE_ICON_SIZE)
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() }
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
            CommerceStateAction(primaryActionLabel, onPrimaryAction, primaryActionTestTag, primary = true)
            CommerceStateAction(
                secondaryActionLabel,
                onSecondaryAction,
                secondaryActionTestTag,
                primary = false
            )
        }
    }
}

@Composable
private fun CommerceStateAction(label: String?, onAction: (() -> Unit)?, testTag: String?, primary: Boolean) {
    if (label == null || onAction == null) return
    if (primary) {
        Button(
            onClick = onAction,
            modifier =
                Modifier.sizeIn(minHeight = MINIMUM_TOUCH_TARGET_SIZE)
                    .optionalTestTag(testTag)
        ) {
            Text(label)
        }
    } else {
        TextButton(
            onClick = onAction,
            modifier =
                Modifier.sizeIn(minHeight = MINIMUM_TOUCH_TARGET_SIZE)
                    .optionalTestTag(testTag)
        ) {
            Text(label)
        }
    }
}

@Composable
internal fun CommerceSkeleton(modifier: Modifier, shape: CornerBasedShape = MaterialTheme.shapes.medium) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier.clip(shape).clearAndSetSemantics {}
    ) {}
}

private fun Modifier.optionalTestTag(testTag: String?): Modifier =
    then(if (testTag == null) Modifier else Modifier.testTag(testTag))

private val MINIMUM_TOUCH_TARGET_SIZE = 48.dp
private val STATE_ICON_SIZE = 40.dp
private val MINIMUM_STATE_PANEL_HEIGHT = 160.dp
