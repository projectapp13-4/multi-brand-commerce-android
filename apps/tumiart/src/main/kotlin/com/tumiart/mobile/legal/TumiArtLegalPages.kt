package com.tumiart.mobile.legal

import android.content.ActivityNotFoundException
import android.content.Context
import androidx.annotation.StringRes
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.net.toUri
import com.gurbakir.mobile.accountdeletion.DeletionPageDescriptor
import com.gurbakir.mobile.accountdeletion.DeletionPageId
import com.gurbakir.mobile.accountdeletion.DeletionPageLaunchResult
import com.gurbakir.mobile.accountdeletion.DeletionPageSource
import com.tumiart.mobile.BuildConfig
import com.tumiart.mobile.R

data class TumiArtLegalEntry(@param:StringRes val titleResource: Int, val uri: String?)

class TumiArtLegalPages : DeletionPageSource {
    val entries: List<TumiArtLegalEntry> = listOf(
        TumiArtLegalEntry(R.string.tumiart_legal_support, boundPage(BuildConfig.LEGAL_SUPPORT_PATH_SUPPORT)),
        TumiArtLegalEntry(R.string.tumiart_legal_privacy, boundPage(BuildConfig.LEGAL_SUPPORT_PATH_PRIVACY)),
        TumiArtLegalEntry(R.string.tumiart_legal_terms, boundPage(BuildConfig.LEGAL_SUPPORT_PATH_TERMS)),
        TumiArtLegalEntry(R.string.tumiart_legal_shipping, null),
        TumiArtLegalEntry(R.string.tumiart_legal_returns, null),
        TumiArtLegalEntry(R.string.tumiart_legal_notice, null)
    )

    override fun pages(): List<DeletionPageDescriptor> =
        listOf(DeletionPageDescriptor(DeletionPageId.PRIVACY, R.string.tumiart_legal_privacy))

    fun isAllowed(entry: TumiArtLegalEntry): Boolean = entry in entries && entry.uri != null && entry.uri in allowedUris

    fun open(context: Context, entry: TumiArtLegalEntry): Boolean {
        if (!isAllowed(entry)) return false
        return try {
            CustomTabsIntent.Builder().setShowTitle(true).setShareState(CustomTabsIntent.SHARE_STATE_OFF)
                .build().launchUrl(context, checkNotNull(entry.uri).toUri())
            true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (_: SecurityException) {
            false
        } catch (_: IllegalArgumentException) {
            false
        }
    }

    fun openDeletionPage(context: Context, id: DeletionPageId): DeletionPageLaunchResult {
        if (id != DeletionPageId.PRIVACY) return DeletionPageLaunchResult.REJECTED
        val privacy = entries.single { it.titleResource == R.string.tumiart_legal_privacy }
        return if (open(context, privacy)) DeletionPageLaunchResult.OPENED else DeletionPageLaunchResult.NO_BROWSER
    }

    private fun boundPage(path: String): String? =
        (BuildConfig.LEGAL_SUPPORT_ORIGIN + path).takeIf { it in allowedUris }
}

// Read back from the merchant's public navigation on 2026-10-15. Availability
// is not production legal acceptance or an account-deletion request process.
private val allowedUris = setOf(
    "https://tumiart.com/pages/iletisim",
    "https://tumiart.com/pages/kvkk-aydinlatma-metni",
    "https://tumiart.com/pages/uyelik-sozlesmesi"
)
