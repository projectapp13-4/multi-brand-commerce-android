@file:Suppress("MagicNumber")

package com.gurbakir.mobile.brand

import com.gurbakir.foundation.config.ArgbColor
import com.gurbakir.foundation.config.BrandColorSchemeTokens
import com.gurbakir.foundation.config.BrandFontWeight
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GurbakirDesignTokensTest {
    @Test
    fun `semantic color roles are fully pinned for light and dark themes`() {
        val colors = GurbakirBrand.configuration.designTokens.colors

        assertEquals(
            expectedSemanticColors(
                secondaryContainer = 0xFFFFDCC0,
                onSecondaryContainer = 0xFF321300,
                tertiary = 0xFF6B5E12,
                onTertiary = 0xFFFFFFFF,
                tertiaryContainer = 0xFFF5E48B,
                onTertiaryContainer = 0xFF211B00,
                surfaceVariant = 0xFFE5E1DC,
                onSurfaceVariant = 0xFF494642,
                surfaceContainerLow = 0xFFFAF5EF,
                surfaceContainer = 0xFFF4EFE9,
                surfaceContainerHigh = 0xFFEEE9E3,
                outline = 0xFF7A7772,
                outlineVariant = 0xFFCAC6C0,
                errorContainer = 0xFFFFDAD6,
                onErrorContainer = 0xFF410002,
                inverseSurface = 0xFF352F2C,
                inverseOnSurface = 0xFFFBEFE9,
                inversePrimary = 0xFFB6CCBF,
                scrim = 0xFF000000
            ),
            colors.light.semanticColors()
        )
        assertEquals(
            expectedSemanticColors(
                secondaryContainer = 0xFF6A401B,
                onSecondaryContainer = 0xFFFFDCC0,
                tertiary = 0xFFD7C867,
                onTertiary = 0xFF383000,
                tertiaryContainer = 0xFF504800,
                onTertiaryContainer = 0xFFF5E48B,
                surfaceVariant = 0xFF4F453F,
                onSurfaceVariant = 0xFFD3C4BC,
                surfaceContainerLow = 0xFF211A16,
                surfaceContainer = 0xFF261E1A,
                surfaceContainerHigh = 0xFF312824,
                outline = 0xFF9C8F88,
                outlineVariant = 0xFF4F453F,
                errorContainer = 0xFF93000A,
                onErrorContainer = 0xFFFFDAD6,
                inverseSurface = 0xFFEDE0DA,
                inverseOnSurface = 0xFF352F2C,
                inversePrimary = 0xFF173F35,
                scrim = 0xFF000000
            ),
            colors.dark.semanticColors()
        )
    }

    @Test
    fun `documented foreground background pairs meet deterministic contrast thresholds`() {
        listOf(
            "light" to GurbakirBrand.configuration.designTokens.colors.light,
            "dark" to GurbakirBrand.configuration.designTokens.colors.dark
        )
            .forEach { (theme, scheme) ->
                normalTextPairs(scheme).forEach { (name, foreground, background) ->
                    val ratio = contrastRatio(foreground, background)
                    assertTrue(ratio >= NORMAL_TEXT_CONTRAST) {
                        "$theme $name contrast was $ratio"
                    }
                }
                val outlineRatio = contrastRatio(scheme.outline, scheme.surface)
                assertTrue(outlineRatio >= NON_TEXT_CONTRAST) {
                    "$theme outline contrast was $outlineRatio"
                }
            }
    }

    @Test
    fun `typography spacing shape and motion match the CP 02 contract`() {
        val tokens = GurbakirBrand.configuration.designTokens
        val type = tokens.typography

        assertEquals(
            listOf(32 to 40, 28 to 36, 24 to 32),
            listOf(
                type.headlineLarge.fontSizeSp to type.headlineLarge.lineHeightSp,
                type.headlineMedium.fontSizeSp to type.headlineMedium.lineHeightSp,
                type.headlineSmall.fontSizeSp to type.headlineSmall.lineHeightSp
            )
        )
        assertEquals(
            listOf(22 to 28, 16 to 24, 14 to 20),
            listOf(
                type.titleLarge.fontSizeSp to type.titleLarge.lineHeightSp,
                type.titleMedium.fontSizeSp to type.titleMedium.lineHeightSp,
                type.titleSmall.fontSizeSp to type.titleSmall.lineHeightSp
            )
        )
        assertEquals(
            listOf(16 to 24, 14 to 20, 12 to 16),
            listOf(
                type.bodyLarge.fontSizeSp to type.bodyLarge.lineHeightSp,
                type.bodyMedium.fontSizeSp to type.bodyMedium.lineHeightSp,
                type.bodySmall.fontSizeSp to type.bodySmall.lineHeightSp
            )
        )
        assertEquals(
            listOf(14 to 20, 12 to 16, 11 to 16),
            listOf(
                type.labelLarge.fontSizeSp to type.labelLarge.lineHeightSp,
                type.labelMedium.fontSizeSp to type.labelMedium.lineHeightSp,
                type.labelSmall.fontSizeSp to type.labelSmall.lineHeightSp
            )
        )
        assertTrue(
            listOf(type.bodyLarge, type.bodyMedium, type.bodySmall).all {
                it.fontWeight == BrandFontWeight.REGULAR
            }
        )
        assertTrue(
            listOf(type.titleLarge, type.titleMedium, type.titleSmall).all {
                it.fontWeight == BrandFontWeight.SEMIBOLD
            }
        )
        assertTrue(
            listOf(type.labelLarge, type.labelMedium, type.labelSmall).all {
                it.fontWeight == BrandFontWeight.MEDIUM
            }
        )
        assertEquals(listOf(4, 8, 16, 24), tokens.spacing.run { listOf(compactDp, normalDp, generousDp, sectionDp) })
        assertEquals(listOf(8, 12, 20), tokens.shapes.run { listOf(smallCornerDp, mediumCornerDp, largeCornerDp) })
        assertEquals(
            listOf(150, 220, 300),
            tokens.motion.run { listOf(microFeedbackMs, contentReplacementMs, containerTransitionMs) }
        )
    }

    private fun normalTextPairs(scheme: BrandColorSchemeTokens): List<Triple<String, ArgbColor, ArgbColor>> = listOf(
        Triple("secondary container", scheme.onSecondaryContainer, scheme.secondaryContainer),
        Triple("tertiary", scheme.onTertiary, scheme.tertiary),
        Triple("tertiary container", scheme.onTertiaryContainer, scheme.tertiaryContainer),
        Triple("surface variant", scheme.onSurfaceVariant, scheme.surfaceVariant),
        Triple("surface container low", scheme.onSurface, scheme.surfaceContainerLow),
        Triple("surface container", scheme.onSurface, scheme.surfaceContainer),
        Triple("surface container high", scheme.onSurface, scheme.surfaceContainerHigh),
        Triple("error container", scheme.onErrorContainer, scheme.errorContainer),
        Triple("inverse surface", scheme.inverseOnSurface, scheme.inverseSurface),
        Triple("inverse primary", scheme.inversePrimary, scheme.inverseSurface)
    )

    private fun contrastRatio(foreground: ArgbColor, background: ArgbColor): Double {
        val foregroundLuminance = relativeLuminance(foreground)
        val backgroundLuminance = relativeLuminance(background)
        return (max(foregroundLuminance, backgroundLuminance) + LUMINANCE_OFFSET) /
            (min(foregroundLuminance, backgroundLuminance) + LUMINANCE_OFFSET)
    }

    private fun relativeLuminance(color: ArgbColor): Double {
        val red = ((color.value shr RED_SHIFT) and CHANNEL_MASK).toDouble() / CHANNEL_MAX
        val green = ((color.value shr GREEN_SHIFT) and CHANNEL_MASK).toDouble() / CHANNEL_MAX
        val blue = (color.value and CHANNEL_MASK).toDouble() / CHANNEL_MAX
        return RED_LUMINANCE * red.linearized() +
            GREEN_LUMINANCE * green.linearized() +
            BLUE_LUMINANCE * blue.linearized()
    }

    private fun Double.linearized(): Double = if (this <= LINEAR_THRESHOLD) {
        this / LINEAR_DIVISOR
    } else {
        ((this + LINEAR_OFFSET) / LINEAR_SCALE).pow(LINEAR_EXPONENT)
    }

    private fun BrandColorSchemeTokens.semanticColors(): Map<String, Long> = mapOf(
        "secondaryContainer" to secondaryContainer.value,
        "onSecondaryContainer" to onSecondaryContainer.value,
        "tertiary" to tertiary.value,
        "onTertiary" to onTertiary.value,
        "tertiaryContainer" to tertiaryContainer.value,
        "onTertiaryContainer" to onTertiaryContainer.value,
        "surfaceVariant" to surfaceVariant.value,
        "onSurfaceVariant" to onSurfaceVariant.value,
        "surfaceContainerLow" to surfaceContainerLow.value,
        "surfaceContainer" to surfaceContainer.value,
        "surfaceContainerHigh" to surfaceContainerHigh.value,
        "outline" to outline.value,
        "outlineVariant" to outlineVariant.value,
        "errorContainer" to errorContainer.value,
        "onErrorContainer" to onErrorContainer.value,
        "inverseSurface" to inverseSurface.value,
        "inverseOnSurface" to inverseOnSurface.value,
        "inversePrimary" to inversePrimary.value,
        "scrim" to scrim.value
    )

    @Suppress("LongParameterList") // Named arguments keep the exact CP-02 palette fixture auditable.
    private fun expectedSemanticColors(
        secondaryContainer: Long,
        onSecondaryContainer: Long,
        tertiary: Long,
        onTertiary: Long,
        tertiaryContainer: Long,
        onTertiaryContainer: Long,
        surfaceVariant: Long,
        onSurfaceVariant: Long,
        surfaceContainerLow: Long,
        surfaceContainer: Long,
        surfaceContainerHigh: Long,
        outline: Long,
        outlineVariant: Long,
        errorContainer: Long,
        onErrorContainer: Long,
        inverseSurface: Long,
        inverseOnSurface: Long,
        inversePrimary: Long,
        scrim: Long
    ): Map<String, Long> = mapOf(
        "secondaryContainer" to secondaryContainer,
        "onSecondaryContainer" to onSecondaryContainer,
        "tertiary" to tertiary,
        "onTertiary" to onTertiary,
        "tertiaryContainer" to tertiaryContainer,
        "onTertiaryContainer" to onTertiaryContainer,
        "surfaceVariant" to surfaceVariant,
        "onSurfaceVariant" to onSurfaceVariant,
        "surfaceContainerLow" to surfaceContainerLow,
        "surfaceContainer" to surfaceContainer,
        "surfaceContainerHigh" to surfaceContainerHigh,
        "outline" to outline,
        "outlineVariant" to outlineVariant,
        "errorContainer" to errorContainer,
        "onErrorContainer" to onErrorContainer,
        "inverseSurface" to inverseSurface,
        "inverseOnSurface" to inverseOnSurface,
        "inversePrimary" to inversePrimary,
        "scrim" to scrim
    )

    private companion object {
        const val NORMAL_TEXT_CONTRAST = 4.5
        const val NON_TEXT_CONTRAST = 3.0
        const val CHANNEL_MASK = 0xFFL
        const val CHANNEL_MAX = 255.0
        const val RED_SHIFT = 16
        const val GREEN_SHIFT = 8
        const val RED_LUMINANCE = 0.2126
        const val GREEN_LUMINANCE = 0.7152
        const val BLUE_LUMINANCE = 0.0722
        const val LUMINANCE_OFFSET = 0.05
        const val LINEAR_THRESHOLD = 0.04045
        const val LINEAR_DIVISOR = 12.92
        const val LINEAR_OFFSET = 0.055
        const val LINEAR_SCALE = 1.055
        const val LINEAR_EXPONENT = 2.4
    }
}
