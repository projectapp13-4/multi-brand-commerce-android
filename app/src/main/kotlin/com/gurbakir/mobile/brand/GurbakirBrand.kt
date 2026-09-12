@file:Suppress("MagicNumber")

package com.gurbakir.mobile.brand

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

object GurbakirBrand {
    val configuration: BrandConfiguration =
        BrandConfiguration(
            key = "gurbakir",
            displayName = "Gürbakır",
            designTokens =
                BrandDesignTokens(
                    colors =
                        BrandColorTokens(
                            light =
                                BrandColorSchemeTokens(
                                    primary = ArgbColor(0xFF173F35),
                                    onPrimary = ArgbColor(0xFFFFFFFF),
                                    primaryContainer = ArgbColor(0xFFD7E7DF),
                                    onPrimaryContainer = ArgbColor(0xFF09251E),
                                    secondary = ArgbColor(0xFF8B5E34),
                                    onSecondary = ArgbColor(0xFFFFFFFF),
                                    secondaryContainer = ArgbColor(0xFFFFDCC0),
                                    onSecondaryContainer = ArgbColor(0xFF321300),
                                    tertiary = ArgbColor(0xFF6B5E12),
                                    onTertiary = ArgbColor(0xFFFFFFFF),
                                    tertiaryContainer = ArgbColor(0xFFF5E48B),
                                    onTertiaryContainer = ArgbColor(0xFF211B00),
                                    background = ArgbColor(0xFFFFFBF5),
                                    onBackground = ArgbColor(0xFF201A17),
                                    surface = ArgbColor(0xFFFFFBF5),
                                    onSurface = ArgbColor(0xFF201A17),
                                    surfaceVariant = ArgbColor(0xFFE5E1DC),
                                    onSurfaceVariant = ArgbColor(0xFF494642),
                                    surfaceContainerLow = ArgbColor(0xFFFAF5EF),
                                    surfaceContainer = ArgbColor(0xFFF4EFE9),
                                    surfaceContainerHigh = ArgbColor(0xFFEEE9E3),
                                    outline = ArgbColor(0xFF7A7772),
                                    outlineVariant = ArgbColor(0xFFCAC6C0),
                                    error = ArgbColor(0xFFBA1A1A),
                                    onError = ArgbColor(0xFFFFFFFF),
                                    errorContainer = ArgbColor(0xFFFFDAD6),
                                    onErrorContainer = ArgbColor(0xFF410002),
                                    inverseSurface = ArgbColor(0xFF352F2C),
                                    inverseOnSurface = ArgbColor(0xFFFBEFE9),
                                    inversePrimary = ArgbColor(0xFFB6CCBF),
                                    scrim = ArgbColor(0xFF000000)
                                ),
                            dark =
                                BrandColorSchemeTokens(
                                    primary = ArgbColor(0xFFB6CCBF),
                                    onPrimary = ArgbColor(0xFF21372F),
                                    primaryContainer = ArgbColor(0xFF344E44),
                                    onPrimaryContainer = ArgbColor(0xFFD7E7DF),
                                    secondary = ArgbColor(0xFFFFB77B),
                                    onSecondary = ArgbColor(0xFF4C2706),
                                    secondaryContainer = ArgbColor(0xFF6A401B),
                                    onSecondaryContainer = ArgbColor(0xFFFFDCC0),
                                    tertiary = ArgbColor(0xFFD7C867),
                                    onTertiary = ArgbColor(0xFF383000),
                                    tertiaryContainer = ArgbColor(0xFF504800),
                                    onTertiaryContainer = ArgbColor(0xFFF5E48B),
                                    background = ArgbColor(0xFF18120F),
                                    onBackground = ArgbColor(0xFFEDE0DA),
                                    surface = ArgbColor(0xFF18120F),
                                    onSurface = ArgbColor(0xFFEDE0DA),
                                    surfaceVariant = ArgbColor(0xFF4F453F),
                                    onSurfaceVariant = ArgbColor(0xFFD3C4BC),
                                    surfaceContainerLow = ArgbColor(0xFF211A16),
                                    surfaceContainer = ArgbColor(0xFF261E1A),
                                    surfaceContainerHigh = ArgbColor(0xFF312824),
                                    outline = ArgbColor(0xFF9C8F88),
                                    outlineVariant = ArgbColor(0xFF4F453F),
                                    error = ArgbColor(0xFFFFB4AB),
                                    onError = ArgbColor(0xFF690005),
                                    errorContainer = ArgbColor(0xFF93000A),
                                    onErrorContainer = ArgbColor(0xFFFFDAD6),
                                    inverseSurface = ArgbColor(0xFFEDE0DA),
                                    inverseOnSurface = ArgbColor(0xFF352F2C),
                                    inversePrimary = ArgbColor(0xFF173F35),
                                    scrim = ArgbColor(0xFF000000)
                                )
                        ),
                    typography = mobileTypographyTokens(),
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
                    appIconResourceName = "ic_launcher_foreground",
                    logoResourceName = null,
                    heroImageResourceName = null
                ),
            legalLinks =
                BrandLegalLinks(
                    privacyPolicyUrl = "https://gurbakir.com/policies/privacy-policy",
                    termsUrl = "https://gurbakir.com/policies/terms-of-service",
                    supportUrl = "https://gurbakir.com/pages/contact"
                ),
            analyticsEventNamespace = "gurbakir"
        )
}

private fun mobileTypographyTokens(): BrandTypographyTokens = BrandTypographyTokens(
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
