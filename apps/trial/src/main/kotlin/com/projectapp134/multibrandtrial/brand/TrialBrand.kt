@file:Suppress("MagicNumber")

package com.projectapp134.multibrandtrial.brand

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
import com.projectapp134.multibrandtrial.BuildConfig

internal object TrialBrand {
    val configuration: BrandConfiguration =
        BrandConfiguration(
            key = BuildConfig.BRAND_KEY,
            displayName = BuildConfig.BRAND_DISPLAY_NAME,
            designTokens =
                BrandDesignTokens(
                    colors =
                        BrandColorTokens(
                            light =
                                BrandColorSchemeTokens(
                                    primary = ArgbColor(0xFF315DA8),
                                    onPrimary = ArgbColor(0xFFFFFFFF),
                                    primaryContainer = ArgbColor(0xFFD9E2FF),
                                    onPrimaryContainer = ArgbColor(0xFF001A41),
                                    secondary = ArgbColor(0xFF575E71),
                                    onSecondary = ArgbColor(0xFFFFFFFF),
                                    secondaryContainer = ArgbColor(0xFFDBE2F9),
                                    onSecondaryContainer = ArgbColor(0xFF141B2C),
                                    tertiary = ArgbColor(0xFF715573),
                                    onTertiary = ArgbColor(0xFFFFFFFF),
                                    tertiaryContainer = ArgbColor(0xFFFBD7FC),
                                    onTertiaryContainer = ArgbColor(0xFF29132D),
                                    background = ArgbColor(0xFFFAF8FF),
                                    onBackground = ArgbColor(0xFF1A1B20),
                                    surface = ArgbColor(0xFFFAF8FF),
                                    onSurface = ArgbColor(0xFF1A1B20),
                                    surfaceVariant = ArgbColor(0xFFE2E2EC),
                                    onSurfaceVariant = ArgbColor(0xFF45464F),
                                    surfaceContainerLow = ArgbColor(0xFFF4F3FA),
                                    surfaceContainer = ArgbColor(0xFFEEEFF4),
                                    surfaceContainerHigh = ArgbColor(0xFFE8E8EF),
                                    outline = ArgbColor(0xFF757780),
                                    outlineVariant = ArgbColor(0xFFC5C6D0),
                                    error = ArgbColor(0xFFBA1A1A),
                                    onError = ArgbColor(0xFFFFFFFF),
                                    errorContainer = ArgbColor(0xFFFFDAD6),
                                    onErrorContainer = ArgbColor(0xFF410002),
                                    inverseSurface = ArgbColor(0xFF2F3036),
                                    inverseOnSurface = ArgbColor(0xFFF1F0F7),
                                    inversePrimary = ArgbColor(0xFFAFC6FF),
                                    scrim = ArgbColor(0xFF000000)
                                ),
                            dark =
                                BrandColorSchemeTokens(
                                    primary = ArgbColor(0xFFAFC6FF),
                                    onPrimary = ArgbColor(0xFF002E69),
                                    primaryContainer = ArgbColor(0xFF164580),
                                    onPrimaryContainer = ArgbColor(0xFFD9E2FF),
                                    secondary = ArgbColor(0xFFBFC6DC),
                                    onSecondary = ArgbColor(0xFF293042),
                                    secondaryContainer = ArgbColor(0xFF3F4759),
                                    onSecondaryContainer = ArgbColor(0xFFDBE2F9),
                                    tertiary = ArgbColor(0xFFDEBCDF),
                                    onTertiary = ArgbColor(0xFF402843),
                                    tertiaryContainer = ArgbColor(0xFF583E5B),
                                    onTertiaryContainer = ArgbColor(0xFFFBD7FC),
                                    background = ArgbColor(0xFF121318),
                                    onBackground = ArgbColor(0xFFE3E2E9),
                                    surface = ArgbColor(0xFF121318),
                                    onSurface = ArgbColor(0xFFE3E2E9),
                                    surfaceVariant = ArgbColor(0xFF45464F),
                                    onSurfaceVariant = ArgbColor(0xFFC5C6D0),
                                    surfaceContainerLow = ArgbColor(0xFF1A1B20),
                                    surfaceContainer = ArgbColor(0xFF1E2025),
                                    surfaceContainerHigh = ArgbColor(0xFF292A30),
                                    outline = ArgbColor(0xFF8F909A),
                                    outlineVariant = ArgbColor(0xFF45464F),
                                    error = ArgbColor(0xFFFFB4AB),
                                    onError = ArgbColor(0xFF690005),
                                    errorContainer = ArgbColor(0xFF93000A),
                                    onErrorContainer = ArgbColor(0xFFFFDAD6),
                                    inverseSurface = ArgbColor(0xFFE3E2E9),
                                    inverseOnSurface = ArgbColor(0xFF2F3036),
                                    inversePrimary = ArgbColor(0xFF315DA8),
                                    scrim = ArgbColor(0xFF000000)
                                )
                        ),
                    typography = trialTypographyTokens(),
                    shapes = BrandShapeTokens(smallCornerDp = 8, mediumCornerDp = 12, largeCornerDp = 20),
                    spacing = BrandSpacingTokens(compactDp = 4, normalDp = 8, generousDp = 16, sectionDp = 24),
                    motion =
                        BrandMotionTokens(
                            microFeedbackMs = 150,
                            contentReplacementMs = 220,
                            containerTransitionMs = 300
                        )
                ),
            assets =
                BrandAssetReferences(
                    appIconResourceName = "ic_trial_launcher",
                    logoResourceName = null,
                    heroImageResourceName = null
                ),
            legalLinks = BrandLegalLinks(privacyPolicyUrl = null, termsUrl = null, supportUrl = null),
            analyticsEventNamespace = BuildConfig.ANALYTICS_NAMESPACE
        )
}

private fun trialTypographyTokens(): BrandTypographyTokens = BrandTypographyTokens(
    fontFamilyResourceName = null,
    headlineLarge = BrandTextStyleTokens(32, 40, BrandFontWeight.REGULAR),
    headlineMedium = BrandTextStyleTokens(28, 36, BrandFontWeight.REGULAR),
    headlineSmall = BrandTextStyleTokens(24, 32, BrandFontWeight.REGULAR),
    titleLarge = BrandTextStyleTokens(22, 28, BrandFontWeight.SEMIBOLD),
    titleMedium = BrandTextStyleTokens(16, 24, BrandFontWeight.SEMIBOLD),
    titleSmall = BrandTextStyleTokens(14, 20, BrandFontWeight.SEMIBOLD),
    bodyLarge = BrandTextStyleTokens(16, 24, BrandFontWeight.REGULAR),
    bodyMedium = BrandTextStyleTokens(14, 20, BrandFontWeight.REGULAR),
    bodySmall = BrandTextStyleTokens(12, 16, BrandFontWeight.REGULAR),
    labelLarge = BrandTextStyleTokens(14, 20, BrandFontWeight.MEDIUM),
    labelMedium = BrandTextStyleTokens(12, 16, BrandFontWeight.MEDIUM),
    labelSmall = BrandTextStyleTokens(11, 16, BrandFontWeight.MEDIUM)
)
