package com.gurbakir.foundation.ui

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import com.gurbakir.foundation.config.BrandTypographyTokens

/** Functional roles for explicitly selected product presentations; never editorial font bindings. */
@Immutable
data class BrandProductTypography(
    val title: TextStyle,
    val price: TextStyle,
    val body: TextStyle,
    val option: TextStyle,
    val action: TextStyle
)

val LocalBrandProductTypography = staticCompositionLocalOf {
    BrandProductTypography(
        TextStyle.Default,
        TextStyle.Default,
        TextStyle.Default,
        TextStyle.Default,
        TextStyle.Default
    )
}

internal fun BrandTypographyTokens.toProductTypography(fontFamilies: BrandFontFamilies): BrandProductTypography =
    BrandProductTypography(
        title = (productDetail?.title ?: titleLarge).toTextStyle(fontFamilies.functional),
        price = (productDetail?.price ?: titleMedium).toTextStyle(fontFamilies.functional),
        body = (productDetail?.body ?: bodyMedium).toTextStyle(fontFamilies.functional),
        option = (productDetail?.option ?: labelLarge).toTextStyle(fontFamilies.functional),
        action = (productDetail?.action ?: labelLarge).toTextStyle(fontFamilies.functional)
    )
