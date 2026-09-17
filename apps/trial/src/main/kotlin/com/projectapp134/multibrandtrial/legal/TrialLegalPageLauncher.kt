package com.projectapp134.multibrandtrial.legal

import android.content.ActivityNotFoundException
import android.content.Context
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.net.toUri
import java.net.URI

class TrialLegalPagePolicy(contract: TrialLegalContract) {
    private val allowedEntries = contract.entries.associateBy(TrialLegalEntry::role)

    fun isAllowed(entry: TrialLegalEntry): Boolean {
        val uri = runCatching { URI(entry.intendedUri) }.getOrNull()
        return entry.state == TrialLegalState.DEVELOPMENT_VERIFIED &&
            allowedEntries[entry.role] == entry &&
            uri != null &&
            uri.scheme == "https" &&
            uri.host == "multi-brand-trial-store.myshopify.com" &&
            uri.userInfo == null &&
            uri.port == -1 &&
            uri.query == null &&
            uri.fragment == null &&
            uri.path.startsWith("/pages/trial-") &&
            uri.normalize().path == uri.path
    }
}

enum class TrialLegalLaunchResult {
    OPENED,
    NO_BROWSER,
    REJECTED
}

class TrialLegalPageLauncher(private val policy: TrialLegalPagePolicy) {
    fun open(context: Context, entry: TrialLegalEntry): TrialLegalLaunchResult {
        if (!policy.isAllowed(entry)) return TrialLegalLaunchResult.REJECTED
        return try {
            CustomTabsIntent.Builder()
                .setShowTitle(true)
                .setShareState(CustomTabsIntent.SHARE_STATE_OFF)
                .build()
                .launchUrl(context, entry.intendedUri.toUri())
            TrialLegalLaunchResult.OPENED
        } catch (_: ActivityNotFoundException) {
            TrialLegalLaunchResult.NO_BROWSER
        } catch (_: SecurityException) {
            TrialLegalLaunchResult.NO_BROWSER
        } catch (_: IllegalArgumentException) {
            TrialLegalLaunchResult.REJECTED
        }
    }
}
