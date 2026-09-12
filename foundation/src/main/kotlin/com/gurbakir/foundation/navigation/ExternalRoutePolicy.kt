package com.gurbakir.foundation.navigation

import java.net.URI

private const val UNSPECIFIED_PORT = -1
private const val HTTPS_PORT = 443

data class ExternalRoutePolicy(
    private val allowedHttpsHosts: Set<String>,
    private val allowedCustomSchemes: Set<String>
) {
    fun isAllowed(rawUri: String): Boolean = runCatching {
        val uri = URI(rawUri)
        when (uri.scheme?.lowercase()) {
            "https" ->
                uri.host?.lowercase() in allowedHttpsHosts &&
                    uri.userInfo == null &&
                    uri.port in setOf(UNSPECIFIED_PORT, HTTPS_PORT)

            in allowedCustomSchemes -> uri.userInfo == null

            else -> false
        }
    }.getOrDefault(false)
}
