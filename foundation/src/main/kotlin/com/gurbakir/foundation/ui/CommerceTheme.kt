@file:Suppress("FunctionNaming")

package com.gurbakir.foundation.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gurbakir.foundation.config.ArgbColor
import com.gurbakir.foundation.config.BrandColorSchemeTokens
import com.gurbakir.foundation.config.BrandDesignTokens
import com.gurbakir.foundation.config.BrandFontWeight
import com.gurbakir.foundation.config.BrandMotionTokens
import com.gurbakir.foundation.config.BrandSpacingTokens
import com.gurbakir.foundation.config.BrandTextStyleTokens
import com.gurbakir.foundation.config.BrandTypographyTokens

private val DefaultSpacing = BrandSpacingTokens(compactDp = 4, normalDp = 8, generousDp = 16, sectionDp = 24)
private val DefaultMotion =
    BrandMotionTokens(microFeedbackMs = 150, contentReplacementMs = 220, containerTransitionMs = 300)

val LocalBrandSpacing = staticCompositionLocalOf { DefaultSpacing }
val LocalBrandMotion = staticCompositionLocalOf { DefaultMotion }

@Composable
fun CommerceTheme(designTokens: BrandDesignTokens, darkTheme: Boolean, content: @Composable () -> Unit) {
    val colors = if (darkTheme) designTokens.colors.dark else designTokens.colors.light
    val shapes = designTokens.shapes
    CompositionLocalProvider(
        LocalBrandSpacing provides designTokens.spacing,
        LocalBrandMotion provides designTokens.motion
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) colors.toDarkScheme() else colors.toLightScheme(),
            typography = designTokens.typography.toTypography(),
            shapes =
                Shapes(
                    small = RoundedCornerShape(shapes.smallCornerDp.dp),
                    medium = RoundedCornerShape(shapes.mediumCornerDp.dp),
                    large = RoundedCornerShape(shapes.largeCornerDp.dp)
                ),
            content = content
        )
    }
}

private fun BrandColorSchemeTokens.toLightScheme() = lightColorScheme(
    primary = primary.toColor(),
    onPrimary = onPrimary.toColor(),
    primaryContainer = primaryContainer.toColor(),
    onPrimaryContainer = onPrimaryContainer.toColor(),
    inversePrimary = inversePrimary.toColor(),
    secondary = secondary.toColor(),
    onSecondary = onSecondary.toColor(),
    secondaryContainer = secondaryContainer.toColor(),
    onSecondaryContainer = onSecondaryContainer.toColor(),
    tertiary = tertiary.toColor(),
    onTertiary = onTertiary.toColor(),
    tertiaryContainer = tertiaryContainer.toColor(),
    onTertiaryContainer = onTertiaryContainer.toColor(),
    background = background.toColor(),
    onBackground = onBackground.toColor(),
    surface = surface.toColor(),
    onSurface = onSurface.toColor(),
    surfaceVariant = surfaceVariant.toColor(),
    onSurfaceVariant = onSurfaceVariant.toColor(),
    surfaceTint = primary.toColor(),
    inverseSurface = inverseSurface.toColor(),
    inverseOnSurface = inverseOnSurface.toColor(),
    error = error.toColor(),
    onError = onError.toColor(),
    errorContainer = errorContainer.toColor(),
    onErrorContainer = onErrorContainer.toColor(),
    outline = outline.toColor(),
    outlineVariant = outlineVariant.toColor(),
    scrim = scrim.toColor(),
    surfaceBright = surface.toColor(),
    surfaceDim = surfaceContainerHigh.toColor(),
    surfaceContainer = surfaceContainer.toColor(),
    surfaceContainerHigh = surfaceContainerHigh.toColor(),
    surfaceContainerHighest = surfaceContainerHigh.toColor(),
    surfaceContainerLow = surfaceContainerLow.toColor(),
    surfaceContainerLowest = surface.toColor(),
    primaryFixed = primaryContainer.toColor(),
    primaryFixedDim = primary.toColor(),
    onPrimaryFixed = onPrimaryContainer.toColor(),
    onPrimaryFixedVariant = onPrimary.toColor(),
    secondaryFixed = secondaryContainer.toColor(),
    secondaryFixedDim = secondary.toColor(),
    onSecondaryFixed = onSecondaryContainer.toColor(),
    onSecondaryFixedVariant = onSecondary.toColor(),
    tertiaryFixed = tertiaryContainer.toColor(),
    tertiaryFixedDim = tertiary.toColor(),
    onTertiaryFixed = onTertiaryContainer.toColor(),
    onTertiaryFixedVariant = onTertiary.toColor()
)

private fun BrandColorSchemeTokens.toDarkScheme() = darkColorScheme(
    primary = primary.toColor(),
    onPrimary = onPrimary.toColor(),
    primaryContainer = primaryContainer.toColor(),
    onPrimaryContainer = onPrimaryContainer.toColor(),
    inversePrimary = inversePrimary.toColor(),
    secondary = secondary.toColor(),
    onSecondary = onSecondary.toColor(),
    secondaryContainer = secondaryContainer.toColor(),
    onSecondaryContainer = onSecondaryContainer.toColor(),
    tertiary = tertiary.toColor(),
    onTertiary = onTertiary.toColor(),
    tertiaryContainer = tertiaryContainer.toColor(),
    onTertiaryContainer = onTertiaryContainer.toColor(),
    background = background.toColor(),
    onBackground = onBackground.toColor(),
    surface = surface.toColor(),
    onSurface = onSurface.toColor(),
    surfaceVariant = surfaceVariant.toColor(),
    onSurfaceVariant = onSurfaceVariant.toColor(),
    surfaceTint = primary.toColor(),
    inverseSurface = inverseSurface.toColor(),
    inverseOnSurface = inverseOnSurface.toColor(),
    error = error.toColor(),
    onError = onError.toColor(),
    errorContainer = errorContainer.toColor(),
    onErrorContainer = onErrorContainer.toColor(),
    outline = outline.toColor(),
    outlineVariant = outlineVariant.toColor(),
    scrim = scrim.toColor(),
    surfaceBright = surface.toColor(),
    surfaceDim = surfaceContainerHigh.toColor(),
    surfaceContainer = surfaceContainer.toColor(),
    surfaceContainerHigh = surfaceContainerHigh.toColor(),
    surfaceContainerHighest = surfaceContainerHigh.toColor(),
    surfaceContainerLow = surfaceContainerLow.toColor(),
    surfaceContainerLowest = surface.toColor(),
    primaryFixed = primaryContainer.toColor(),
    primaryFixedDim = primary.toColor(),
    onPrimaryFixed = onPrimaryContainer.toColor(),
    onPrimaryFixedVariant = onPrimary.toColor(),
    secondaryFixed = secondaryContainer.toColor(),
    secondaryFixedDim = secondary.toColor(),
    onSecondaryFixed = onSecondaryContainer.toColor(),
    onSecondaryFixedVariant = onSecondary.toColor(),
    tertiaryFixed = tertiaryContainer.toColor(),
    tertiaryFixedDim = tertiary.toColor(),
    onTertiaryFixed = onTertiaryContainer.toColor(),
    onTertiaryFixedVariant = onTertiary.toColor()
)

private fun BrandTypographyTokens.toTypography(): Typography = Typography(
    displayLarge = headlineLarge.toTextStyle(),
    displayMedium = headlineMedium.toTextStyle(),
    displaySmall = headlineSmall.toTextStyle(),
    headlineLarge = headlineLarge.toTextStyle(),
    headlineMedium = headlineMedium.toTextStyle(),
    headlineSmall = headlineSmall.toTextStyle(),
    titleLarge = titleLarge.toTextStyle(),
    titleMedium = titleMedium.toTextStyle(),
    titleSmall = titleSmall.toTextStyle(),
    bodyLarge = bodyLarge.toTextStyle(),
    bodyMedium = bodyMedium.toTextStyle(),
    bodySmall = bodySmall.toTextStyle(),
    labelLarge = labelLarge.toTextStyle(),
    labelMedium = labelMedium.toTextStyle(),
    labelSmall = labelSmall.toTextStyle()
)

private fun BrandTextStyleTokens.toTextStyle(): TextStyle = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = fontWeight.toComposeWeight(),
    fontSize = fontSizeSp.sp,
    lineHeight = lineHeightSp.sp
)

private fun BrandFontWeight.toComposeWeight(): FontWeight = when (this) {
    BrandFontWeight.REGULAR -> FontWeight.Normal
    BrandFontWeight.MEDIUM -> FontWeight.Medium
    BrandFontWeight.SEMIBOLD -> FontWeight.SemiBold
}

private fun ArgbColor.toColor(): Color = Color(value.toInt())
