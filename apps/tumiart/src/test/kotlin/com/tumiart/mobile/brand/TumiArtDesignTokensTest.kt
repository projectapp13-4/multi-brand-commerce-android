package com.tumiart.mobile.brand

import com.gurbakir.foundation.config.ArgbColor
import com.gurbakir.foundation.config.BrandColorSchemeTokens
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TumiArtDesignTokensTest {
    @Test
    fun selectedGalleryPaletteReplacesTheProvisionalScaffoldInBothThemes() {
        val colors = TumiArtBrand.configuration.designTokens.colors
        assertEquals(0xFFFAF7F2, colors.light.background.value)
        assertEquals(0xFFFFFDFA, colors.light.surface.value)
        assertEquals(0xFF302A26, colors.light.onSurface.value)
        assertEquals(0xFF536A5D, colors.light.primary.value)
        assertEquals(0xFFE4EBE1, colors.light.primaryContainer.value)
        assertEquals(0xFF201F1E, colors.dark.background.value)
        assertEquals(0xFFF1EBE2, colors.dark.onBackground.value)
        assertEquals(0xFFB3CEB9, colors.dark.primary.value)
    }

    @Test
    fun bothThemesHaveAccessibleTextControlsErrorsAndSelection() {
        val colors = TumiArtBrand.configuration.designTokens.colors
        listOf(colors.light, colors.dark).forEach { scheme ->
            textPairs(scheme).forEach { (foreground, background) ->
                assertTrue(contrast(foreground, background) >= 4.5, "Text contrast: $foreground / $background")
            }
            listOf(scheme.surface, scheme.background, scheme.surfaceContainer, scheme.surfaceVariant).forEach {
                assertTrue(contrast(scheme.outline, it) >= 3.0, "Control outline contrast")
            }
            // Material text selection uses primary at 40% over the surface.
            val selection = composite(scheme.primary, scheme.surface, 0.4)
            assertTrue(contrast(scheme.onSurface, selection) >= 4.5, "Selected text contrast")
        }
    }

    @Test
    fun fixedRolesAreStableAcrossThemesAndKeepBothOnColorsReadable() {
        val colors = TumiArtBrand.configuration.designTokens.colors
        fun fixed(s: BrandColorSchemeTokens) = listOf(
            listOf(s.primaryFixed, s.primaryFixedDim, s.onPrimaryFixed, s.onPrimaryFixedVariant),
            listOf(s.secondaryFixed, s.secondaryFixedDim, s.onSecondaryFixed, s.onSecondaryFixedVariant),
            listOf(s.tertiaryFixed, s.tertiaryFixedDim, s.onTertiaryFixed, s.onTertiaryFixedVariant)
        )
        assertEquals(fixed(colors.light), fixed(colors.dark))
        fixed(colors.light).forEach { group ->
            group.take(2).forEach { surface ->
                group.takeLast(2).forEach { assertTrue(contrast(checkNotNull(it), checkNotNull(surface)) >= 4.5) }
            }
        }
    }

    @Test
    fun readableNormalSizesAndRestrainedGeometryRetainExistingMotionAndSpacing() {
        val tokens = TumiArtBrand.configuration.designTokens
        assertEquals(28, tokens.typography.editorialHero.fontSizeSp)
        assertEquals(20, tokens.typography.editorialTitle.fontSizeSp)
        assertEquals(16, tokens.typography.bodyLarge.fontSizeSp)
        assertEquals(14, tokens.typography.titleSmall.fontSizeSp)
        assertEquals(12, tokens.typography.labelSmall.fontSizeSp)
        assertEquals(
            listOf(8, 10, 12),
            tokens.shapes.let { listOf(it.smallCornerDp, it.mediumCornerDp, it.largeCornerDp) }
        )
        assertEquals(
            listOf(4, 8, 16, 24),
            tokens.spacing.let { listOf(it.compactDp, it.normalDp, it.generousDp, it.sectionDp) }
        )
        assertEquals(
            listOf(150, 220, 300),
            tokens.motion.let { listOf(it.microFeedbackMs, it.contentReplacementMs, it.containerTransitionMs) }
        )
    }

    private fun textPairs(s: BrandColorSchemeTokens): List<Pair<ArgbColor, ArgbColor>> = listOf(
        s.onPrimary to s.primary, s.onPrimaryContainer to s.primaryContainer,
        s.onSecondary to s.secondary, s.onSecondaryContainer to s.secondaryContainer,
        s.onTertiary to s.tertiary, s.onTertiaryContainer to s.tertiaryContainer,
        s.onBackground to s.background, s.onSurface to s.surface,
        s.onSurfaceVariant to s.surfaceVariant, s.onError to s.error,
        s.onErrorContainer to s.errorContainer, s.inverseOnSurface to s.inverseSurface,
        s.inversePrimary to s.inverseSurface, s.primary to s.background, s.tertiary to s.background
    ) + listOf(
        s.surfaceContainerLow,
        s.surfaceContainer,
        s.surfaceContainerHigh,
        s.surfaceContainerHighest,
        s.surfaceContainerLowest,
        s.surfaceBright,
        s.surfaceDim
    ).map { s.onSurface to checkNotNull(it) }

    private fun contrast(a: ArgbColor, b: ArgbColor): Double {
        fun luminance(color: ArgbColor): Double {
            val rgb = listOf(16, 8, 0).map { shift ->
                val c = ((color.value shr shift) and 255) / 255.0
                if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
            }
            return rgb[0] * 0.2126 + rgb[1] * 0.7152 + rgb[2] * 0.0722
        }
        val x = luminance(a)
        val y = luminance(b)
        return (max(x, y) + 0.05) / (min(x, y) + 0.05)
    }

    private fun composite(foreground: ArgbColor, background: ArgbColor, alpha: Double): ArgbColor {
        val rgb = listOf(16, 8, 0).map { shift ->
            val front = ((foreground.value shr shift) and 255) * alpha
            val back = ((background.value shr shift) and 255) * (1 - alpha)
            (front + back).toLong() shl shift
        }
        return ArgbColor(0xFF000000 or rgb.reduce(Long::or))
    }
}
