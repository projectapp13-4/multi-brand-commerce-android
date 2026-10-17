package com.gurbakir.mobile.product

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf

/** Finite presentation choice; commerce, provider and navigation authority remain shared. */
sealed interface ProductDetailPresentation {
    data object Standard : ProductDetailPresentation

    data class Gallery(@StringRes val titleResource: Int) : ProductDetailPresentation
}

internal val LocalProductDetailPresentation = staticCompositionLocalOf<ProductDetailPresentation> {
    ProductDetailPresentation.Standard
}

@Composable
internal fun isGalleryProductPresentation(): Boolean =
    LocalProductDetailPresentation.current is ProductDetailPresentation.Gallery
