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

class TumiArtLegalPages internal constructor(private val origin: String, private val requestPath: String) :
    DeletionPageSource {
    constructor() : this(BuildConfig.LEGAL_SUPPORT_ORIGIN, BuildConfig.LEGAL_SUPPORT_PATH_ACCOUNT_DELETION_REQUEST)

    val entries: List<TumiArtLegalEntry> = listOf(
        TumiArtLegalEntry(
            R.string.tumiart_legal_support,
            boundPage(BuildConfig.LEGAL_SUPPORT_PATH_SUPPORT, "/pages/iletisim")
        ),
        TumiArtLegalEntry(
            R.string.tumiart_legal_privacy,
            boundPage(BuildConfig.LEGAL_SUPPORT_PATH_PRIVACY, "/pages/kvkk-aydinlatma-metni")
        ),
        TumiArtLegalEntry(
            R.string.tumiart_legal_terms,
            boundPage(BuildConfig.LEGAL_SUPPORT_PATH_TERMS, "/pages/uyelik-sozlesmesi")
        ),
        TumiArtLegalEntry(
            R.string.tumiart_legal_shipping,
            boundPage(BuildConfig.LEGAL_SUPPORT_PATH_SHIPPING, "/pages/kargo-ve-teslimat")
        ),
        TumiArtLegalEntry(
            R.string.tumiart_legal_returns,
            boundPage(BuildConfig.LEGAL_SUPPORT_PATH_RETURNS, "/pages/iade-ve-iptal-politikasi")
        ),
        TumiArtLegalEntry(R.string.tumiart_legal_notice, null),
        TumiArtLegalEntry(R.string.tumiart_legal_deletion_request, boundPage(requestPath, REQUEST_PAGE_PATH))
    )

    override fun pages(): List<DeletionPageDescriptor> = pageEntries.mapNotNull { (id, title) ->
        entry(title)?.let { DeletionPageDescriptor(id, title) }
    }

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

    fun openDeletionPage(context: Context, id: DeletionPageId): DeletionPageLaunchResult = launch(id) {
        open(context, it)
    }

    internal fun launch(id: DeletionPageId, opener: (TumiArtLegalEntry) -> Boolean): DeletionPageLaunchResult {
        val target = pageEntries[id]?.let(::entry) ?: return DeletionPageLaunchResult.REJECTED
        return if (opener(target)) DeletionPageLaunchResult.OPENED else DeletionPageLaunchResult.NO_BROWSER
    }

    private fun entry(@StringRes title: Int): TumiArtLegalEntry? =
        entries.singleOrNull { it.titleResource == title }?.takeIf(::isAllowed)

    private fun boundPage(path: String, expectedPath: String): String? =
        (origin + path).takeIf { origin == "https://tumiart.com" && path == expectedPath && it in allowedUris }
}

private const val REQUEST_PAGE_PATH = "/pages/uygulama-hesap-silme-talebi"
private val pageEntries = mapOf(
    DeletionPageId.PRIVACY to R.string.tumiart_legal_privacy,
    DeletionPageId.ACCOUNT_DELETION_REQUEST to R.string.tumiart_legal_deletion_request
)

// Published legal paths were read back on 2026-10-19. The optional request path
// stays absent in onboarding until public-resource and merchant-intake acceptance.
private val allowedUris = setOf(
    "https://tumiart.com/pages/iletisim",
    "https://tumiart.com/pages/kvkk-aydinlatma-metni",
    "https://tumiart.com/pages/uyelik-sozlesmesi",
    "https://tumiart.com/pages/kargo-ve-teslimat",
    "https://tumiart.com/pages/iade-ve-iptal-politikasi",
    "https://tumiart.com$REQUEST_PAGE_PATH"
)
