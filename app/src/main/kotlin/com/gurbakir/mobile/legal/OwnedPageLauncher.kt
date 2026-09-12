package com.gurbakir.mobile.legal

import android.content.ActivityNotFoundException
import android.content.Context
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.net.toUri

class OwnedPageLauncher(private val policy: OwnedPagePolicy) {
    fun open(context: Context, page: LegalPageMetadata): OwnedPageLaunchResult {
        if (!policy.isAllowed(page)) return OwnedPageLaunchResult.REJECTED

        return try {
            CustomTabsIntent.Builder()
                .setShowTitle(true)
                .setShareState(CustomTabsIntent.SHARE_STATE_OFF)
                .build()
                .launchUrl(context, page.canonicalUrl.toUri())
            OwnedPageLaunchResult.OPENED
        } catch (_: ActivityNotFoundException) {
            OwnedPageLaunchResult.NO_BROWSER
        } catch (_: SecurityException) {
            OwnedPageLaunchResult.NO_BROWSER
        } catch (_: IllegalArgumentException) {
            OwnedPageLaunchResult.REJECTED
        }
    }
}
