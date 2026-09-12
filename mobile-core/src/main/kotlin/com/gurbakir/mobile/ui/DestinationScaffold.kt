@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@file:Suppress("FunctionNaming")

package com.gurbakir.mobile.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.gurbakir.foundation.ui.LocalBrandSpacing
import com.gurbakir.mobile.core.R

enum class DestinationLevel {
    PRIMARY,
    SECONDARY
}

enum class DestinationTitleAlignment {
    START,
    CENTER
}

@Composable
@Suppress("LongParameterList") // Scaffold slots and hierarchy metadata are one Compose DSL contract.
fun DestinationScaffold(
    title: String,
    level: DestinationLevel,
    modifier: Modifier = Modifier,
    titleAlignment: DestinationTitleAlignment = DestinationTitleAlignment.START,
    titleTestTag: String? = null,
    onNavigateUp: (() -> Unit)? = null,
    navigateUpTestTag: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit
) {
    check(level == DestinationLevel.PRIMARY || onNavigateUp != null) {
        "Secondary destinations require an Up action."
    }
    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            val navigationIcon: @Composable () -> Unit = {
                if (level == DestinationLevel.SECONDARY) {
                    AppNavigateUpButton(
                        onClick = checkNotNull(onNavigateUp),
                        testTag = navigateUpTestTag
                    )
                }
            }
            when (titleAlignment) {
                DestinationTitleAlignment.START ->
                    TopAppBar(
                        title = { Text(title, Modifier.optionalTestTag(titleTestTag)) },
                        navigationIcon = navigationIcon,
                        actions = actions
                    )

                DestinationTitleAlignment.CENTER ->
                    CenterAlignedTopAppBar(
                        title = { Text(title, Modifier.optionalTestTag(titleTestTag)) },
                        navigationIcon = navigationIcon,
                        actions = actions
                    )
            }
        },
        bottomBar = bottomBar,
        content = content
    )
}

@Composable
private fun AppNavigateUpButton(onClick: () -> Unit, testTag: String?) {
    IconButton(
        onClick = onClick,
        modifier =
            Modifier.size(MINIMUM_TOUCH_TARGET_SIZE).then(
                if (testTag == null) Modifier else Modifier.testTag(testTag)
            )
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_arrow_back),
            contentDescription = stringResource(R.string.back)
        )
    }
}

@Composable
fun PaddingValues.withDestinationSpacing(
    horizontal: Dp = LocalBrandSpacing.current.generousDp.dp,
    vertical: Dp = LocalBrandSpacing.current.generousDp.dp
): PaddingValues {
    val layoutDirection = LocalLayoutDirection.current
    return PaddingValues(
        start = calculateStartPadding(layoutDirection) + horizontal,
        top = calculateTopPadding() + vertical,
        end = calculateEndPadding(layoutDirection) + horizontal,
        bottom = calculateBottomPadding() + vertical
    )
}

fun Modifier.consumeDestinationInsets(paddingValues: PaddingValues): Modifier =
    consumeWindowInsets(paddingValues).imePadding()

fun Modifier.centeredDestinationContent(maxWidth: Dp): Modifier = fillMaxWidth()
    .wrapContentWidth(androidx.compose.ui.Alignment.CenterHorizontally)
    .widthIn(max = maxWidth)

private fun Modifier.optionalTestTag(testTag: String?): Modifier =
    then(if (testTag == null) Modifier else Modifier.testTag(testTag))

private val MINIMUM_TOUCH_TARGET_SIZE = 48.dp
