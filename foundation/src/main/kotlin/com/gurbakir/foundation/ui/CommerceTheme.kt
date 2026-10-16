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
fun CommerceTheme(
    designTokens: BrandDesignTokens,
    darkTheme: Boolean,
    fontFamilies: BrandFontFamilies = BrandFontFamilies(),
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) designTokens.colors.dark else designTokens.colors.light
    val shapes = designTokens.shapes
    CompositionLocalProvider(
        LocalBrandSpacing provides designTokens.spacing,
        LocalBrandMotion provides designTokens.motion,
        LocalBrandEditorialTypography provides designTokens.typography.toEditorialTypography(fontFamilies)
    ) {
        MaterialTheme(
            colorScheme = colors.toMaterialColorScheme(darkTheme),
            typography = designTokens.typography.toTypography(fontFamilies),
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

internal fun BrandColorSchemeTokens.toMaterialColorScheme(darkTheme: Boolean) =
    if (darkTheme) toDarkScheme() else toLightScheme()

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
    surfaceBright = surfaceBright.toColorOr(surface),
    surfaceDim = surfaceDim.toColorOr(surfaceContainerHigh),
    surfaceContainer = surfaceContainer.toColor(),
    surfaceContainerHigh = surfaceContainerHigh.toColor(),
    surfaceContainerHighest = surfaceContainerHighest.toColorOr(surfaceContainerHigh),
    surfaceContainerLow = surfaceContainerLow.toColor(),
    surfaceContainerLowest = surfaceContainerLowest.toColorOr(surface),
    primaryFixed = primaryFixed.toColorOr(primaryContainer),
    primaryFixedDim = primaryFixedDim.toColorOr(primary),
    onPrimaryFixed = onPrimaryFixed.toColorOr(onPrimaryContainer),
    onPrimaryFixedVariant = onPrimaryFixedVariant.toColorOr(onPrimary),
    secondaryFixed = secondaryFixed.toColorOr(secondaryContainer),
    secondaryFixedDim = secondaryFixedDim.toColorOr(secondary),
    onSecondaryFixed = onSecondaryFixed.toColorOr(onSecondaryContainer),
    onSecondaryFixedVariant = onSecondaryFixedVariant.toColorOr(onSecondary),
    tertiaryFixed = tertiaryFixed.toColorOr(tertiaryContainer),
    tertiaryFixedDim = tertiaryFixedDim.toColorOr(tertiary),
    onTertiaryFixed = onTertiaryFixed.toColorOr(onTertiaryContainer),
    onTertiaryFixedVariant = onTertiaryFixedVariant.toColorOr(onTertiary)
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
    surfaceBright = surfaceBright.toColorOr(surface),
    surfaceDim = surfaceDim.toColorOr(surfaceContainerHigh),
    surfaceContainer = surfaceContainer.toColor(),
    surfaceContainerHigh = surfaceContainerHigh.toColor(),
    surfaceContainerHighest = surfaceContainerHighest.toColorOr(surfaceContainerHigh),
    surfaceContainerLow = surfaceContainerLow.toColor(),
    surfaceContainerLowest = surfaceContainerLowest.toColorOr(surface),
    primaryFixed = primaryFixed.toColorOr(primaryContainer),
    primaryFixedDim = primaryFixedDim.toColorOr(primary),
    onPrimaryFixed = onPrimaryFixed.toColorOr(onPrimaryContainer),
    onPrimaryFixedVariant = onPrimaryFixedVariant.toColorOr(onPrimary),
    secondaryFixed = secondaryFixed.toColorOr(secondaryContainer),
    secondaryFixedDim = secondaryFixedDim.toColorOr(secondary),
    onSecondaryFixed = onSecondaryFixed.toColorOr(onSecondaryContainer),
    onSecondaryFixedVariant = onSecondaryFixedVariant.toColorOr(onSecondary),
    tertiaryFixed = tertiaryFixed.toColorOr(tertiaryContainer),
    tertiaryFixedDim = tertiaryFixedDim.toColorOr(tertiary),
    onTertiaryFixed = onTertiaryFixed.toColorOr(onTertiaryContainer),
    onTertiaryFixedVariant = onTertiaryFixedVariant.toColorOr(onTertiary)
)

internal fun BrandTypographyTokens.toTypography(fontFamilies: BrandFontFamilies = BrandFontFamilies()): Typography =
    Typography(
        displayLarge = headlineLarge.toTextStyle(fontFamilies.functional),
        displayMedium = headlineMedium.toTextStyle(fontFamilies.functional),
        displaySmall = headlineSmall.toTextStyle(fontFamilies.functional),
        headlineLarge = headlineLarge.toTextStyle(fontFamilies.functional),
        headlineMedium = headlineMedium.toTextStyle(fontFamilies.functional),
        headlineSmall = headlineSmall.toTextStyle(fontFamilies.functional),
        titleLarge = titleLarge.toTextStyle(fontFamilies.functional),
        titleMedium = titleMedium.toTextStyle(fontFamilies.functional),
        titleSmall = titleSmall.toTextStyle(fontFamilies.functional),
        bodyLarge = bodyLarge.toTextStyle(fontFamilies.functional),
        bodyMedium = bodyMedium.toTextStyle(fontFamilies.functional),
        bodySmall = bodySmall.toTextStyle(fontFamilies.functional),
        labelLarge = labelLarge.toTextStyle(fontFamilies.functional),
        labelMedium = labelMedium.toTextStyle(fontFamilies.functional),
        labelSmall = labelSmall.toTextStyle(fontFamilies.functional)
    )

internal fun BrandTypographyTokens.toEditorialTypography(fontFamilies: BrandFontFamilies): BrandEditorialTypography =
    BrandEditorialTypography(
        hero = editorialHero.toTextStyle(fontFamilies.editorial),
        title = editorialTitle.toTextStyle(fontFamilies.editorial)
    )

private fun BrandTextStyleTokens.toTextStyle(fontFamily: FontFamily): TextStyle = TextStyle(
    fontFamily = fontFamily,
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

private fun ArgbColor?.toColorOr(fallback: ArgbColor): Color = (this ?: fallback).toColor()
