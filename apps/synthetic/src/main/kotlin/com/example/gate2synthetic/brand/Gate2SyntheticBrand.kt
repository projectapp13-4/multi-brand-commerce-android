@file:Suppress("MagicNumber")

package com.example.gate2synthetic.brand

import com.gurbakir.foundation.config.ArgbColor
import com.gurbakir.foundation.config.BrandAssetReferences
import com.gurbakir.foundation.config.BrandColorSchemeTokens
import com.gurbakir.foundation.config.BrandColorTokens
import com.gurbakir.foundation.config.BrandConfiguration
import com.gurbakir.foundation.config.BrandDesignTokens
import com.gurbakir.foundation.config.BrandFontWeight
import com.gurbakir.foundation.config.BrandLegalLinks
import com.gurbakir.foundation.config.BrandMotionTokens
import com.gurbakir.foundation.config.BrandShapeTokens
import com.gurbakir.foundation.config.BrandSpacingTokens
import com.gurbakir.foundation.config.BrandTextStyleTokens
import com.gurbakir.foundation.config.BrandTypographyTokens

object Gate2SyntheticBrand {
    val configuration: BrandConfiguration =
        BrandConfiguration(
            key = "gate2-synthetic",
            displayName = "Gate 2 Synthetic",
            designTokens =
                BrandDesignTokens(
                    colors = BrandColorTokens(light = lightColors(), dark = darkColors()),
                    typography = typography(),
                    shapes = BrandShapeTokens(smallCornerDp = 4, mediumCornerDp = 16, largeCornerDp = 28),
                    spacing = BrandSpacingTokens(compactDp = 6, normalDp = 12, generousDp = 20, sectionDp = 32),
                    motion =
                        BrandMotionTokens(
                            microFeedbackMs = 100,
                            contentReplacementMs = 240,
                            containerTransitionMs = 420
                        )
                ),
            assets =
                BrandAssetReferences(
                    appIconResourceName = "ic_gate2_synthetic_launcher",
                    logoResourceName = null,
                    heroImageResourceName = null
                ),
            legalLinks =
                BrandLegalLinks(
                    privacyPolicyUrl = "https://legal.gate2.invalid/privacy",
                    termsUrl = "https://legal.gate2.invalid/terms",
                    supportUrl = "https://legal.gate2.invalid/support"
                ),
            analyticsEventNamespace = "gate2_synthetic"
        )
}

private fun lightColors(): BrandColorSchemeTokens = BrandColorSchemeTokens(
    primary = ArgbColor(0xFF4F378B),
    onPrimary = ArgbColor(0xFFFFFFFF),
    primaryContainer = ArgbColor(0xFFEADDFF),
    onPrimaryContainer = ArgbColor(0xFF21005D),
    secondary = ArgbColor(0xFF625B71),
    onSecondary = ArgbColor(0xFFFFFFFF),
    secondaryContainer = ArgbColor(0xFFE8DEF8),
    onSecondaryContainer = ArgbColor(0xFF1D192B),
    tertiary = ArgbColor(0xFF7D5260),
    onTertiary = ArgbColor(0xFFFFFFFF),
    tertiaryContainer = ArgbColor(0xFFFFD8E4),
    onTertiaryContainer = ArgbColor(0xFF31111D),
    background = ArgbColor(0xFFFFF7FF),
    onBackground = ArgbColor(0xFF1D1B20),
    surface = ArgbColor(0xFFFFF7FF),
    onSurface = ArgbColor(0xFF1D1B20),
    surfaceVariant = ArgbColor(0xFFE7E0EC),
    onSurfaceVariant = ArgbColor(0xFF49454F),
    surfaceContainerLow = ArgbColor(0xFFF7F2FA),
    surfaceContainer = ArgbColor(0xFFF3EDF7),
    surfaceContainerHigh = ArgbColor(0xFFECE6F0),
    outline = ArgbColor(0xFF79747E),
    outlineVariant = ArgbColor(0xFFCAC4D0),
    error = ArgbColor(0xFFB3261E),
    onError = ArgbColor(0xFFFFFFFF),
    errorContainer = ArgbColor(0xFFF9DEDC),
    onErrorContainer = ArgbColor(0xFF410E0B),
    inverseSurface = ArgbColor(0xFF322F35),
    inverseOnSurface = ArgbColor(0xFFF5EFF7),
    inversePrimary = ArgbColor(0xFFD0BCFF),
    scrim = ArgbColor(0xFF000000)
)

private fun darkColors(): BrandColorSchemeTokens = BrandColorSchemeTokens(
    primary = ArgbColor(0xFFD0BCFF),
    onPrimary = ArgbColor(0xFF381E72),
    primaryContainer = ArgbColor(0xFF4F378B),
    onPrimaryContainer = ArgbColor(0xFFEADDFF),
    secondary = ArgbColor(0xFFCCC2DC),
    onSecondary = ArgbColor(0xFF332D41),
    secondaryContainer = ArgbColor(0xFF4A4458),
    onSecondaryContainer = ArgbColor(0xFFE8DEF8),
    tertiary = ArgbColor(0xFFEFB8C8),
    onTertiary = ArgbColor(0xFF492532),
    tertiaryContainer = ArgbColor(0xFF633B48),
    onTertiaryContainer = ArgbColor(0xFFFFD8E4),
    background = ArgbColor(0xFF141218),
    onBackground = ArgbColor(0xFFE6E0E9),
    surface = ArgbColor(0xFF141218),
    onSurface = ArgbColor(0xFFE6E0E9),
    surfaceVariant = ArgbColor(0xFF49454F),
    onSurfaceVariant = ArgbColor(0xFFCAC4D0),
    surfaceContainerLow = ArgbColor(0xFF1D1B20),
    surfaceContainer = ArgbColor(0xFF211F26),
    surfaceContainerHigh = ArgbColor(0xFF2B2930),
    outline = ArgbColor(0xFF938F99),
    outlineVariant = ArgbColor(0xFF49454F),
    error = ArgbColor(0xFFF2B8B5),
    onError = ArgbColor(0xFF601410),
    errorContainer = ArgbColor(0xFF8C1D18),
    onErrorContainer = ArgbColor(0xFFF9DEDC),
    inverseSurface = ArgbColor(0xFFE6E0E9),
    inverseOnSurface = ArgbColor(0xFF322F35),
    inversePrimary = ArgbColor(0xFF6750A4),
    scrim = ArgbColor(0xFF000000)
)

private fun typography(): BrandTypographyTokens = BrandTypographyTokens(
    fontFamilyResourceName = null,
    headlineLarge = BrandTextStyleTokens(34, 42, BrandFontWeight.SEMIBOLD),
    headlineMedium = BrandTextStyleTokens(30, 38, BrandFontWeight.SEMIBOLD),
    headlineSmall = BrandTextStyleTokens(26, 34, BrandFontWeight.MEDIUM),
    titleLarge = BrandTextStyleTokens(20, 28, BrandFontWeight.SEMIBOLD),
    titleMedium = BrandTextStyleTokens(17, 24, BrandFontWeight.MEDIUM),
    titleSmall = BrandTextStyleTokens(15, 22, BrandFontWeight.MEDIUM),
    bodyLarge = BrandTextStyleTokens(18, 26, BrandFontWeight.REGULAR),
    bodyMedium = BrandTextStyleTokens(15, 22, BrandFontWeight.REGULAR),
    bodySmall = BrandTextStyleTokens(13, 18, BrandFontWeight.REGULAR),
    labelLarge = BrandTextStyleTokens(15, 20, BrandFontWeight.SEMIBOLD),
    labelMedium = BrandTextStyleTokens(13, 18, BrandFontWeight.MEDIUM),
    labelSmall = BrandTextStyleTokens(12, 16, BrandFontWeight.MEDIUM)
)
