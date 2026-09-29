package com.gurbakir.mobile.home

import com.gurbakir.storefront.StorefrontVideoSource

/**
 * Keeps the v2 video budget independent of orientation. A portrait rendition may use the
 * same bounded pixel area as a landscape rendition, without admitting larger decodes.
 */
internal object HomeVideoSourcePolicy {
    private const val MAX_DIMENSION = 1280
    private const val MAX_PIXEL_AREA = 1280L * 720L

    fun accepts(source: StorefrontVideoSource): Boolean =
        source.mimeType.equals("video/mp4", ignoreCase = true) &&
            source.format.equals("mp4", ignoreCase = true) &&
            source.width in 1..MAX_DIMENSION &&
            source.height in 1..MAX_DIMENSION &&
            source.width.toLong() * source.height <= MAX_PIXEL_AREA
}
