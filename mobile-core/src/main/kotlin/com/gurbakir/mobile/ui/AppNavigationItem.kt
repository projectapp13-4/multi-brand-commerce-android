@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package com.gurbakir.mobile.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Suppress("LongParameterList") // NavigationSuite item contract is explicit at the call site.
internal fun NavigationSuiteScope.AppNavigationItem(
    selected: Boolean,
    onClick: () -> Unit,
    @StringRes labelResourceId: Int,
    @StringRes accessibilityLabelResourceId: Int = labelResourceId,
    @DrawableRes selectedIconResourceId: Int,
    @DrawableRes unselectedIconResourceId: Int,
    testTag: String
) {
    item(
        selected = selected,
        onClick = onClick,
        icon = {
            Icon(
                painter =
                    painterResource(
                        if (selected) selectedIconResourceId else unselectedIconResourceId
                    ),
                contentDescription = null,
                modifier =
                    Modifier.size(NAVIGATION_ICON_SIZE)
                        .testTag(navigationIconTestTag(testTag, selected))
            )
        },
        label = {
            val accessibilityLabel = stringResource(accessibilityLabelResourceId)
            Text(
                text = stringResource(labelResourceId),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.clearAndSetSemantics { contentDescription = accessibilityLabel }
            )
        },
        alwaysShowLabel = true,
        modifier =
            Modifier.sizeIn(
                minWidth = MINIMUM_TOUCH_TARGET_SIZE,
                minHeight = MINIMUM_TOUCH_TARGET_SIZE
            )
                .testTag(testTag)
    )
}

internal fun navigationIconTestTag(itemTestTag: String, selected: Boolean): String =
    "$itemTestTag-icon-${if (selected) "selected" else "unselected"}"

private val NAVIGATION_ICON_SIZE = 24.dp
private val MINIMUM_TOUCH_TARGET_SIZE = 48.dp
