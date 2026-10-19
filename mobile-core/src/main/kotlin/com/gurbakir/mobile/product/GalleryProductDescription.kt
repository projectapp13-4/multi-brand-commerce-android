package com.gurbakir.mobile.product

import androidx.core.text.HtmlCompat
import com.gurbakir.storefront.StorefrontProductDetail

/** Native text only: retain provider block boundaries without executing HTML, loading images or opening links. */
internal fun StorefrontProductDetail.galleryDescription(): String {
    val source = descriptionHtml?.takeIf { it.length <= MAX_DESCRIPTION_HTML_LENGTH } ?: return description
    val structured = HtmlCompat.fromHtml(source, HtmlCompat.FROM_HTML_MODE_LEGACY).toString().trim()
    // Use structure only when it describes exactly the same content as the authoritative plain field.
    // Unsupported markup, omitted content or an over-length plain field falls back without guessing.
    return structured.takeIf {
        it.length <= MAX_DESCRIPTION_TEXT_LENGTH && it.contentIdentity() == description.contentIdentity()
    } ?: description
}

private fun String.contentIdentity(): String = filterNot { it.isWhitespace() || it == '\u00a0' }

private const val MAX_DESCRIPTION_HTML_LENGTH = 48_000
private const val MAX_DESCRIPTION_TEXT_LENGTH = 12_000
