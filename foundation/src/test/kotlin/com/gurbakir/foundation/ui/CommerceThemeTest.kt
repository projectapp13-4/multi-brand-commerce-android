package com.gurbakir.foundation.ui

import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import com.gurbakir.foundation.config.ArgbColor
import com.gurbakir.foundation.config.BrandColorSchemeTokens
import com.gurbakir.foundation.config.BrandFontWeight
import com.gurbakir.foundation.config.BrandTextStyleTokens
import com.gurbakir.foundation.config.BrandTypographyTokens
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CommerceThemeTest {
    @Test
    fun omittedExtendedRolesFollowCopiesOfLegacyColors() {
        val copied = colorScheme().copy(primary = ArgbColor(0xFF123456), surface = ArgbColor(0xFFABCDEF))
        val actual = copied.toMaterialColorScheme(darkTheme = false)
        assertEquals(copied.primary.value, actual.primaryFixedDim.toArgb().toLong() and 0xFFFFFFFF)
        assertEquals(copied.surface.value, actual.surfaceBright.toArgb().toLong() and 0xFFFFFFFF)
    }

    @Test
    fun legacyApplicationsRetainEveryOriginalMaterialColorRoleInBothModes() {
        val tokens = colorScheme()
        listOf(false, true).forEach { dark ->
            val actual = tokens.toMaterialColorScheme(dark)
            // Frozen from the pre-P2 theme, including its historical fixed/surface aliases.
            val expected = listOf(
                actual.primary to tokens.primary,
                actual.onPrimary to tokens.onPrimary,
                actual.primaryContainer to tokens.primaryContainer,
                actual.onPrimaryContainer to tokens.onPrimaryContainer,
                actual.inversePrimary to tokens.inversePrimary,
                actual.secondary to tokens.secondary,
                actual.onSecondary to tokens.onSecondary,
                actual.secondaryContainer to tokens.secondaryContainer,
                actual.onSecondaryContainer to tokens.onSecondaryContainer,
                actual.tertiary to tokens.tertiary,
                actual.onTertiary to tokens.onTertiary,
                actual.tertiaryContainer to tokens.tertiaryContainer,
                actual.onTertiaryContainer to tokens.onTertiaryContainer,
                actual.background to tokens.background,
                actual.onBackground to tokens.onBackground,
                actual.surface to tokens.surface,
                actual.onSurface to tokens.onSurface,
                actual.surfaceVariant to tokens.surfaceVariant,
                actual.onSurfaceVariant to tokens.onSurfaceVariant,
                actual.surfaceTint to tokens.primary,
                actual.inverseSurface to tokens.inverseSurface,
                actual.inverseOnSurface to tokens.inverseOnSurface,
                actual.error to tokens.error,
                actual.onError to tokens.onError,
                actual.errorContainer to tokens.errorContainer,
                actual.onErrorContainer to tokens.onErrorContainer,
                actual.outline to tokens.outline,
                actual.outlineVariant to tokens.outlineVariant,
                actual.scrim to tokens.scrim,
                actual.surfaceBright to tokens.surface,
                actual.surfaceDim to tokens.surfaceContainerHigh,
                actual.surfaceContainer to tokens.surfaceContainer,
                actual.surfaceContainerHigh to tokens.surfaceContainerHigh,
                actual.surfaceContainerHighest to tokens.surfaceContainerHigh,
                actual.surfaceContainerLow to tokens.surfaceContainerLow,
                actual.surfaceContainerLowest to tokens.surface,
                actual.primaryFixed to tokens.primaryContainer,
                actual.primaryFixedDim to tokens.primary,
                actual.onPrimaryFixed to tokens.onPrimaryContainer,
                actual.onPrimaryFixedVariant to tokens.onPrimary,
                actual.secondaryFixed to tokens.secondaryContainer,
                actual.secondaryFixedDim to tokens.secondary,
                actual.onSecondaryFixed to tokens.onSecondaryContainer,
                actual.onSecondaryFixedVariant to tokens.onSecondary,
                actual.tertiaryFixed to tokens.tertiaryContainer,
                actual.tertiaryFixedDim to tokens.tertiary,
                actual.onTertiaryFixed to tokens.onTertiaryContainer,
                actual.onTertiaryFixedVariant to tokens.onTertiary
            )
            expected.forEach { (actualColor, expectedColor) ->
                assertEquals(checkNotNull(expectedColor).value, actualColor.toArgb().toLong() and 0xFFFFFFFF)
            }
        }
    }

    @Test
    fun legacyTypographyRemainsSystemSansWithEveryOriginalSizeAndLineHeight() {
        val tokens = typography()
        val material = tokens.toTypography()
        val actual = listOf(
            material.displayLarge, material.displayMedium, material.displaySmall,
            material.headlineLarge, material.headlineMedium, material.headlineSmall,
            material.titleLarge, material.titleMedium, material.titleSmall,
            material.bodyLarge, material.bodyMedium, material.bodySmall,
            material.labelLarge, material.labelMedium, material.labelSmall
        )
        assertEquals(
            listOf(32, 28, 24, 32, 28, 24, 22, 16, 14, 16, 14, 12, 14, 12, 11).map {
                it.sp
            },
            actual.map { it.fontSize }
        )
        assertEquals(
            listOf(40, 36, 32, 40, 36, 32, 28, 24, 20, 24, 20, 16, 20, 16, 16).map {
                it.sp
            },
            actual.map { it.lineHeight }
        )
        assertTrue(actual.all { it.fontFamily == FontFamily.SansSerif })
        assertEquals(tokens.headlineLarge, tokens.editorialHero)
        assertEquals(tokens.titleLarge, tokens.editorialTitle)
    }

    @Test
    fun supplyingTwoFamiliesNeverMakesCommerceHeadlinesEditorial() {
        val fonts = BrandFontFamilies(functional = FontFamily.Monospace, editorial = FontFamily.Serif)
        val material = typography().toTypography(fonts)
        assertEquals(FontFamily.Monospace, material.headlineLarge.fontFamily)
        assertEquals(FontFamily.Monospace, material.titleLarge.fontFamily)
        assertEquals(FontFamily.Monospace, material.bodyLarge.fontFamily)
        assertEquals(FontFamily.Monospace, material.labelSmall.fontFamily)
        val allStyles = material.javaClass.methods.filter {
            it.parameterCount == 0 &&
                it.returnType == TextStyle::class.java
        }
            .map { it.invoke(material) as TextStyle }
        assertTrue(allStyles.all { it.fontFamily == FontFamily.Monospace })
        val editorial = typography().toEditorialTypography(fonts)
        assertEquals(FontFamily.Serif, editorial.hero.fontFamily)
        assertEquals(FontFamily.Serif, editorial.title.fontFamily)
        assertEquals(FontFamily.Monospace, BrandFontFamilies(FontFamily.Monospace).editorial)
    }

    @Test
    fun applicationsCanOverrideEveryPreviouslyAliasedRoleInBothModes() {
        val tokens = colorScheme().copy(
            surfaceBright = ArgbColor(0xFF112200),
            surfaceDim = ArgbColor(0xFF112201),
            surfaceContainerHighest = ArgbColor(0xFF112202),
            surfaceContainerLowest = ArgbColor(0xFF112203),
            primaryFixed = ArgbColor(0xFF112204),
            primaryFixedDim = ArgbColor(0xFF112205),
            onPrimaryFixed = ArgbColor(0xFF112206),
            onPrimaryFixedVariant = ArgbColor(0xFF112207),
            secondaryFixed = ArgbColor(0xFF112208),
            secondaryFixedDim = ArgbColor(0xFF112209),
            onSecondaryFixed = ArgbColor(0xFF11220A),
            onSecondaryFixedVariant = ArgbColor(0xFF11220B),
            tertiaryFixed = ArgbColor(0xFF11220C),
            tertiaryFixedDim = ArgbColor(0xFF11220D),
            onTertiaryFixed = ArgbColor(0xFF11220E),
            onTertiaryFixedVariant = ArgbColor(0xFF11220F)
        )
        listOf(false, true).forEach { dark ->
            val actual = tokens.toMaterialColorScheme(dark)
            val roles = listOf(
                actual.surfaceBright to tokens.surfaceBright,
                actual.surfaceDim to tokens.surfaceDim,
                actual.surfaceContainerHighest to tokens.surfaceContainerHighest,
                actual.surfaceContainerLowest to tokens.surfaceContainerLowest,
                actual.primaryFixed to tokens.primaryFixed,
                actual.primaryFixedDim to tokens.primaryFixedDim,
                actual.onPrimaryFixed to tokens.onPrimaryFixed,
                actual.onPrimaryFixedVariant to tokens.onPrimaryFixedVariant,
                actual.secondaryFixed to tokens.secondaryFixed,
                actual.secondaryFixedDim to tokens.secondaryFixedDim,
                actual.onSecondaryFixed to tokens.onSecondaryFixed,
                actual.onSecondaryFixedVariant to tokens.onSecondaryFixedVariant,
                actual.tertiaryFixed to tokens.tertiaryFixed,
                actual.tertiaryFixedDim to tokens.tertiaryFixedDim,
                actual.onTertiaryFixed to tokens.onTertiaryFixed,
                actual.onTertiaryFixedVariant to tokens.onTertiaryFixedVariant
            )
            roles.forEach { (actualColor, expectedColor) ->
                assertEquals(checkNotNull(expectedColor).value, actualColor.toArgb().toLong() and 0xFFFFFFFF)
            }
        }
    }

    private fun colorScheme(): BrandColorSchemeTokens = BrandColorSchemeTokens(
        primary = ArgbColor(0xFF000000),
        onPrimary = ArgbColor(0xFFFFFFFF),
        primaryContainer = ArgbColor(0xFF111111),
        onPrimaryContainer = ArgbColor(0xFFFFFFFF),
        secondary = ArgbColor(0xFF222222),
        onSecondary = ArgbColor(0xFFFFFFFF),
        secondaryContainer = ArgbColor(0xFF333333),
        onSecondaryContainer = ArgbColor(0xFFFFFFFF),
        tertiary = ArgbColor(0xFF444444),
        onTertiary = ArgbColor(0xFFFFFFFF),
        tertiaryContainer = ArgbColor(0xFF555555),
        onTertiaryContainer = ArgbColor(0xFFFFFFFF),
        background = ArgbColor(0xFFFFFFFF),
        onBackground = ArgbColor(0xFF000000),
        surface = ArgbColor(0xFFFFFFFF),
        onSurface = ArgbColor(0xFF000000),
        surfaceVariant = ArgbColor(0xFFEEEEEE),
        onSurfaceVariant = ArgbColor(0xFF222222),
        surfaceContainerLow = ArgbColor(0xFFF8F8F8),
        surfaceContainer = ArgbColor(0xFFF0F0F0),
        surfaceContainerHigh = ArgbColor(0xFFE8E8E8),
        outline = ArgbColor(0xFF555555),
        outlineVariant = ArgbColor(0xFFCCCCCC),
        error = ArgbColor(0xFFBA1A1A),
        onError = ArgbColor(0xFFFFFFFF),
        errorContainer = ArgbColor(0xFFFFDAD6),
        onErrorContainer = ArgbColor(0xFF410002),
        inverseSurface = ArgbColor(0xFF222222),
        inverseOnSurface = ArgbColor(0xFFFFFFFF),
        inversePrimary = ArgbColor(0xFFBBBBBB),
        scrim = ArgbColor(0xFF000000)
    )

    private fun typography(): BrandTypographyTokens = BrandTypographyTokens(
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
}
