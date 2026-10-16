package com.gurbakir.foundation.ui

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily

/** Application-owned font bindings. Resource names in configuration never resolve fonts implicitly. */
@Immutable
data class BrandFontFamilies(val functional: FontFamily = FontFamily.SansSerif, val editorial: FontFamily = functional)

/** Explicit opt-in styles for short editorial copy; commerce Material styles remain functional. */
@Immutable
data class BrandEditorialTypography(val hero: TextStyle, val title: TextStyle)

val LocalBrandEditorialTypography = staticCompositionLocalOf {
    BrandEditorialTypography(TextStyle(fontFamily = FontFamily.SansSerif), TextStyle(fontFamily = FontFamily.SansSerif))
}
