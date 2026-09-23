package com.gurbakir.mobile.order

import android.content.ActivityNotFoundException
import android.content.Context
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.net.toUri
import java.net.IDN
import java.net.URI
import java.util.Locale

private const val HTTPS_PORT = 443
private const val UNSPECIFIED_PORT = -1
private const val MAX_TRACKING_URL_LENGTH = 2_048

class TrackingUrlPolicy(private val allowedHosts: Set<String>) {
    init {
        require(allowedHosts.all { it == it.lowercase(Locale.ROOT) && !it.startsWith('.') })
    }

    companion object {
        fun denyAll(): TrackingUrlPolicy = TrackingUrlPolicy(emptySet())
    }

    fun isAllowed(rawUrl: String): Boolean = rawUrl.length <= MAX_TRACKING_URL_LENGTH && runCatching {
        val uri = URI(rawUrl)
        val host = IDN.toASCII(uri.host ?: return@runCatching false).lowercase(Locale.ROOT)
        uri.scheme == "https" &&
            uri.userInfo == null &&
            uri.port in setOf(UNSPECIFIED_PORT, HTTPS_PORT) &&
            uri.rawFragment == null &&
            uri.rawPath != null &&
            uri.normalize() == uri &&
            uri.toASCIIString() == rawUrl &&
            allowedHosts.any { host == it || host.endsWith(".$it") }
    }.getOrDefault(false)
}

enum class TrackingLaunchResult {
    OPENED,
    NO_BROWSER,
    REJECTED
}

class TrackingLauncher(private val policy: TrackingUrlPolicy) {
    fun open(context: Context, rawUrl: String): TrackingLaunchResult {
        if (!policy.isAllowed(rawUrl)) return TrackingLaunchResult.REJECTED
        return try {
            CustomTabsIntent.Builder()
                .setShowTitle(true)
                .setShareState(CustomTabsIntent.SHARE_STATE_OFF)
                .build()
                .launchUrl(context, rawUrl.toUri())
            TrackingLaunchResult.OPENED
        } catch (_: ActivityNotFoundException) {
            TrackingLaunchResult.NO_BROWSER
        } catch (_: SecurityException) {
            TrackingLaunchResult.NO_BROWSER
        } catch (_: IllegalArgumentException) {
            TrackingLaunchResult.REJECTED
        }
    }
}
