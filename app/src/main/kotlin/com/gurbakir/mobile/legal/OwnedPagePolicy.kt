package com.gurbakir.mobile.legal

import java.net.URI

private const val HTTPS_PORT = 443
private const val UNSPECIFIED_PORT = -1

class OwnedPagePolicy(pages: List<LegalPageMetadata>) {
    private val canonicalById: Map<LegalPageId, String> = pages.associate { it.id to it.canonicalUrl }

    init {
        require(canonicalById.size == pages.size) { "Owned page identifiers must be unique." }
        require(pages.all { isPlainOwnedCanonicalUrl(it.canonicalUrl) }) {
            "Owned page metadata must contain exact Gürbakır HTTPS routes."
        }
        require(canonicalById.values.toSet().size == pages.size) { "Owned page routes must be unique." }
    }

    fun isAllowed(page: LegalPageMetadata): Boolean =
        canonicalById[page.id] == page.canonicalUrl && isPlainOwnedCanonicalUrl(page.canonicalUrl)

    fun isAllowed(rawUrl: String): Boolean = rawUrl in canonicalById.values && isPlainOwnedCanonicalUrl(rawUrl)

    private fun isPlainOwnedCanonicalUrl(rawUrl: String): Boolean = runCatching {
        val uri = URI(rawUrl)
        uri.scheme == "https" &&
            uri.host == "gurbakir.com" &&
            uri.userInfo == null &&
            uri.port in setOf(UNSPECIFIED_PORT, HTTPS_PORT) &&
            uri.rawQuery == null &&
            uri.rawFragment == null &&
            uri.rawPath?.startsWith("/") == true &&
            uri.normalize() == uri &&
            uri.toASCIIString() == rawUrl
    }.getOrDefault(false)
}
