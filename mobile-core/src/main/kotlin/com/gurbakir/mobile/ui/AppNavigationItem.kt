@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package com.gurbakir.mobile.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteItemColors
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScope
import androidx.compose.runtime.Composable
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
    alwaysShowLabel: Boolean = true,
    @DrawableRes selectedIconResourceId: Int,
    @DrawableRes unselectedIconResourceId: Int,
    testTag: String,
    colors: NavigationSuiteItemColors
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
                contentDescription =
                    if (alwaysShowLabel || selected) null else stringResource(accessibilityLabelResourceId),
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
        alwaysShowLabel = alwaysShowLabel,
        colors = colors,
        modifier =
            Modifier.sizeIn(
                minWidth = MINIMUM_TOUCH_TARGET_SIZE,
                minHeight = MINIMUM_TOUCH_TARGET_SIZE
            )
                .testTag(testTag)
    )
}

@Composable
internal fun commerceNavigationItemColors(): NavigationSuiteItemColors {
    val scheme = MaterialTheme.colorScheme
    val indicator = scheme.primary.copy(alpha = NAVIGATION_INDICATOR_ALPHA)
    return NavigationSuiteDefaults.itemColors(
        navigationBarItemColors = NavigationBarItemDefaults.colors(
            selectedIconColor = scheme.primary,
            selectedTextColor = scheme.primary,
            indicatorColor = indicator,
            unselectedIconColor = scheme.onSurfaceVariant,
            unselectedTextColor = scheme.onSurfaceVariant
        ),
        navigationRailItemColors = NavigationRailItemDefaults.colors(
            selectedIconColor = scheme.primary,
            selectedTextColor = scheme.primary,
            indicatorColor = indicator,
            unselectedIconColor = scheme.onSurfaceVariant,
            unselectedTextColor = scheme.onSurfaceVariant
        ),
        navigationDrawerItemColors = NavigationDrawerItemDefaults.colors(
            selectedContainerColor = indicator,
            selectedIconColor = scheme.primary,
            selectedTextColor = scheme.primary,
            unselectedIconColor = scheme.onSurfaceVariant,
            unselectedTextColor = scheme.onSurfaceVariant
        )
    )
}

internal fun navigationIconTestTag(itemTestTag: String, selected: Boolean): String =
    "$itemTestTag-icon-${if (selected) "selected" else "unselected"}"

private val NAVIGATION_ICON_SIZE = 24.dp
private val MINIMUM_TOUCH_TARGET_SIZE = 48.dp
private const val NAVIGATION_INDICATOR_ALPHA = 0.08f
