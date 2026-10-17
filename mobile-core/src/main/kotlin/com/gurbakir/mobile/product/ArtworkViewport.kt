@file:Suppress("MagicNumber")

package com.gurbakir.mobile.product

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.min

/** Bounds the mat, never crops the fitted artwork. Missing dimensions reserve a square. */
internal fun artworkGalleryHeight(
    width: Dp,
    imageWidth: Int?,
    imageHeight: Int?,
    viewportHeight: Dp = Dp.Infinity
): Dp {
    val ratio = artworkRatio(imageWidth, imageHeight).coerceIn(0.8f, 1.5f)
    return (width / ratio).coerceAtMost(420.dp).coerceAtMost(viewportHeight * 0.46f)
}

internal fun isGalleryProductLayout(width: Dp, fontScale: Float): Boolean =
    width >= 600.dp && width.value / fontScale >= 560f

internal fun clampArtworkPan(pan: Offset, zoom: Float, viewport: Size, imageWidth: Int?, imageHeight: Int?): Offset {
    if (viewport.width <= 0f || viewport.height <= 0f || zoom <= 1f) return Offset.Zero
    val ratio = artworkRatio(imageWidth, imageHeight)
    val fittedWidth = min(viewport.width, viewport.height * ratio)
    val fittedHeight = min(viewport.height, viewport.width / ratio)
    val maxX = ((fittedWidth * zoom - viewport.width) / 2f).coerceAtLeast(0f)
    val maxY = ((fittedHeight * zoom - viewport.height) / 2f).coerceAtLeast(0f)
    return Offset(
        if (maxX == 0f) 0f else pan.x.coerceIn(-maxX, maxX),
        if (maxY == 0f) 0f else pan.y.coerceIn(-maxY, maxY)
    )
}

private fun artworkRatio(width: Int?, height: Int?): Float {
    val safeWidth = width ?: 0
    val safeHeight = height ?: 0
    return if (safeWidth > 0 && safeHeight > 0) safeWidth.toFloat() / safeHeight else 1f
}
