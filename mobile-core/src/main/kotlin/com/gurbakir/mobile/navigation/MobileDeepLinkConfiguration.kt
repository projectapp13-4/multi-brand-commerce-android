package com.gurbakir.mobile.navigation

data class MobileDeepLinkConfiguration(val collectionBasePath: String, val productBasePath: String) {
    init {
        requireSecureDeepLinkBase(collectionBasePath)
        requireSecureDeepLinkBase(productBasePath)
    }
}

internal fun requireSecureDeepLinkBase(value: String) {
    val uri = runCatching { java.net.URI(value) }.getOrNull()
    require(
        uri != null && uri.isAbsolute && uri.scheme == "https" && !uri.host.isNullOrBlank() &&
            uri.userInfo == null && uri.query == null && uri.fragment == null
    ) {
        "Deep-link base must be an absolute HTTPS URL without user information, query or fragment."
    }
}
