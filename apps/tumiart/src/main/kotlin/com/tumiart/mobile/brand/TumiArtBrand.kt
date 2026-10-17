@file:Suppress("MagicNumber")

package com.tumiart.mobile.brand

import com.gurbakir.foundation.config.ArgbColor
import com.gurbakir.foundation.config.BrandAssetReferences
import com.gurbakir.foundation.config.BrandColorSchemeTokens
import com.gurbakir.foundation.config.BrandColorTokens
import com.gurbakir.foundation.config.BrandConfiguration
import com.gurbakir.foundation.config.BrandDesignTokens
import com.gurbakir.foundation.config.BrandFontWeight
import com.gurbakir.foundation.config.BrandLegalLinks
import com.gurbakir.foundation.config.BrandMotionTokens
import com.gurbakir.foundation.config.BrandProductTypographyTokens
import com.gurbakir.foundation.config.BrandShapeTokens
import com.gurbakir.foundation.config.BrandSpacingTokens
import com.gurbakir.foundation.config.BrandTextStyleTokens
import com.gurbakir.foundation.config.BrandTypographyTokens
import com.tumiart.mobile.BuildConfig

internal object TumiArtBrand {
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
                                    primary = ArgbColor(0xFF536A5D),
                                    onPrimary = ArgbColor(0xFFFFFDFA),
                                    primaryContainer = ArgbColor(0xFFE4EBE1),
                                    onPrimaryContainer = ArgbColor(0xFF263B2F),
                                    secondary = ArgbColor(0xFF65584C),
                                    onSecondary = ArgbColor(0xFFFFFDFA),
                                    secondaryContainer = ArgbColor(0xFFE8DDD0),
                                    onSecondaryContainer = ArgbColor(0xFF302A26),
                                    tertiary = ArgbColor(0xFF586F87),
                                    onTertiary = ArgbColor(0xFFFFFDFA),
                                    tertiaryContainer = ArgbColor(0xFFE5EBEF),
                                    onTertiaryContainer = ArgbColor(0xFF243747),
                                    background = ArgbColor(0xFFFAF7F2),
                                    onBackground = ArgbColor(0xFF302A26),
                                    surface = ArgbColor(0xFFFFFDFA),
                                    onSurface = ArgbColor(0xFF302A26),
                                    surfaceVariant = ArgbColor(0xFFE8DDD0),
                                    onSurfaceVariant = ArgbColor(0xFF62584F),
                                    surfaceContainerLow = ArgbColor(0xFFF7F2EA),
                                    surfaceContainer = ArgbColor(0xFFF3ECE3),
                                    surfaceContainerHigh = ArgbColor(0xFFEEE5DB),
                                    outline = ArgbColor(0xFF7C6D5F),
                                    outlineVariant = ArgbColor(0xFFCFC1B1),
                                    error = ArgbColor(0xFFBA1A1A),
                                    onError = ArgbColor(0xFFFFFFFF),
                                    errorContainer = ArgbColor(0xFFFFDAD6),
                                    onErrorContainer = ArgbColor(0xFF410002),
                                    inverseSurface = ArgbColor(0xFF302A26),
                                    inverseOnSurface = ArgbColor(0xFFF1EBE2),
                                    inversePrimary = ArgbColor(0xFFB3CEB9),
                                    scrim = ArgbColor(0xFF000000),
                                    surfaceBright = ArgbColor(0xFFFFFDFA),
                                    surfaceDim = ArgbColor(0xFFDED3C6),
                                    surfaceContainerHighest = ArgbColor(0xFFE8DDD0),
                                    surfaceContainerLowest = ArgbColor(0xFFFFFFFF),
                                    primaryFixed = ArgbColor(0xFFE4EBE1),
                                    primaryFixedDim = ArgbColor(0xFFB3CEB9),
                                    onPrimaryFixed = ArgbColor(0xFF26382D),
                                    onPrimaryFixedVariant = ArgbColor(0xFF394D40),
                                    secondaryFixed = ArgbColor(0xFFE8DDD0),
                                    secondaryFixedDim = ArgbColor(0xFFD2BDA7),
                                    onSecondaryFixed = ArgbColor(0xFF302A26),
                                    onSecondaryFixedVariant = ArgbColor(0xFF514334),
                                    tertiaryFixed = ArgbColor(0xFFE5EBEF),
                                    tertiaryFixedDim = ArgbColor(0xFFB2CADF),
                                    onTertiaryFixed = ArgbColor(0xFF223747),
                                    onTertiaryFixedVariant = ArgbColor(0xFF3B5267)
                                ),
                            dark =
                                BrandColorSchemeTokens(
                                    primary = ArgbColor(0xFFB3CEB9),
                                    onPrimary = ArgbColor(0xFF223A2B),
                                    primaryContainer = ArgbColor(0xFF394F40),
                                    onPrimaryContainer = ArgbColor(0xFFE4EBE1),
                                    secondary = ArgbColor(0xFFD2BDA7),
                                    onSecondary = ArgbColor(0xFF3B2E23),
                                    secondaryContainer = ArgbColor(0xFF514334),
                                    onSecondaryContainer = ArgbColor(0xFFF5DDC4),
                                    tertiary = ArgbColor(0xFFB2CADF),
                                    onTertiary = ArgbColor(0xFF243B4F),
                                    tertiaryContainer = ArgbColor(0xFF3B5267),
                                    onTertiaryContainer = ArgbColor(0xFFD9E9F7),
                                    background = ArgbColor(0xFF201F1E),
                                    onBackground = ArgbColor(0xFFF1EBE2),
                                    surface = ArgbColor(0xFF292725),
                                    onSurface = ArgbColor(0xFFF1EBE2),
                                    surfaceVariant = ArgbColor(0xFF46413C),
                                    onSurfaceVariant = ArgbColor(0xFFD2C4B5),
                                    surfaceContainerLow = ArgbColor(0xFF262422),
                                    surfaceContainer = ArgbColor(0xFF302D2A),
                                    surfaceContainerHigh = ArgbColor(0xFF3B3733),
                                    outline = ArgbColor(0xFFA89989),
                                    outlineVariant = ArgbColor(0xFF5A5148),
                                    error = ArgbColor(0xFFFFB4AB),
                                    onError = ArgbColor(0xFF690005),
                                    errorContainer = ArgbColor(0xFF93000A),
                                    onErrorContainer = ArgbColor(0xFFFFDAD6),
                                    inverseSurface = ArgbColor(0xFFF1EBE2),
                                    inverseOnSurface = ArgbColor(0xFF302A26),
                                    inversePrimary = ArgbColor(0xFF536A5D),
                                    scrim = ArgbColor(0xFF000000),
                                    surfaceBright = ArgbColor(0xFF49443F),
                                    surfaceDim = ArgbColor(0xFF191817),
                                    surfaceContainerHighest = ArgbColor(0xFF46413C),
                                    surfaceContainerLowest = ArgbColor(0xFF171615),
                                    primaryFixed = ArgbColor(0xFFE4EBE1),
                                    primaryFixedDim = ArgbColor(0xFFB3CEB9),
                                    onPrimaryFixed = ArgbColor(0xFF26382D),
                                    onPrimaryFixedVariant = ArgbColor(0xFF394D40),
                                    secondaryFixed = ArgbColor(0xFFE8DDD0),
                                    secondaryFixedDim = ArgbColor(0xFFD2BDA7),
                                    onSecondaryFixed = ArgbColor(0xFF302A26),
                                    onSecondaryFixedVariant = ArgbColor(0xFF514334),
                                    tertiaryFixed = ArgbColor(0xFFE5EBEF),
                                    tertiaryFixedDim = ArgbColor(0xFFB2CADF),
                                    onTertiaryFixed = ArgbColor(0xFF223747),
                                    onTertiaryFixedVariant = ArgbColor(0xFF3B5267)
                                )
                        ),
                    typography = tumiartTypographyTokens(),
                    shapes = BrandShapeTokens(smallCornerDp = 8, mediumCornerDp = 10, largeCornerDp = 12),
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
                    appIconResourceName = "ic_tumiart_launcher",
                    logoResourceName = null,
                    heroImageResourceName = null
                ),
            legalLinks =
                BrandLegalLinks(
                    privacyPolicyUrl = BuildConfig.LEGAL_SUPPORT_ORIGIN + BuildConfig.LEGAL_SUPPORT_PATH_PRIVACY,
                    termsUrl = BuildConfig.LEGAL_SUPPORT_ORIGIN + BuildConfig.LEGAL_SUPPORT_PATH_TERMS,
                    supportUrl = BuildConfig.LEGAL_SUPPORT_ORIGIN + BuildConfig.LEGAL_SUPPORT_PATH_SUPPORT
                ),
            analyticsEventNamespace = BuildConfig.ANALYTICS_NAMESPACE
        )
}

private fun tumiartTypographyTokens(): BrandTypographyTokens = BrandTypographyTokens(
    fontFamilyResourceName = null,
    headlineLarge = BrandTextStyleTokens(28, 36, BrandFontWeight.REGULAR),
    headlineMedium = BrandTextStyleTokens(24, 32, BrandFontWeight.REGULAR),
    headlineSmall = BrandTextStyleTokens(20, 28, BrandFontWeight.REGULAR),
    titleLarge = BrandTextStyleTokens(20, 28, BrandFontWeight.SEMIBOLD),
    titleMedium = BrandTextStyleTokens(16, 24, BrandFontWeight.SEMIBOLD),
    titleSmall = BrandTextStyleTokens(14, 20, BrandFontWeight.SEMIBOLD),
    bodyLarge = BrandTextStyleTokens(16, 24, BrandFontWeight.REGULAR),
    bodyMedium = BrandTextStyleTokens(14, 20, BrandFontWeight.REGULAR),
    bodySmall = BrandTextStyleTokens(12, 16, BrandFontWeight.REGULAR),
    labelLarge = BrandTextStyleTokens(14, 20, BrandFontWeight.MEDIUM),
    labelMedium = BrandTextStyleTokens(13, 18, BrandFontWeight.MEDIUM),
    labelSmall = BrandTextStyleTokens(12, 16, BrandFontWeight.MEDIUM),
    editorialHero = BrandTextStyleTokens(28, 34, BrandFontWeight.REGULAR),
    editorialTitle = BrandTextStyleTokens(20, 28, BrandFontWeight.REGULAR),
    productDetail = BrandProductTypographyTokens(
        title = BrandTextStyleTokens(20, 27, BrandFontWeight.SEMIBOLD),
        price = BrandTextStyleTokens(17, 24, BrandFontWeight.SEMIBOLD),
        body = BrandTextStyleTokens(14, 21, BrandFontWeight.REGULAR),
        option = BrandTextStyleTokens(13, 19, BrandFontWeight.MEDIUM),
        action = BrandTextStyleTokens(14, 20, BrandFontWeight.SEMIBOLD)
    )
)
