package com.gurbakir.mobile.legal

import androidx.annotation.StringRes
import java.time.LocalDate

private const val LEGAL_BASELINE_ADOPTION_YEAR = 2026
private const val LEGAL_BASELINE_ADOPTION_MONTH = 8
private const val LEGAL_BASELINE_ADOPTION_DAY = 11

const val LEGAL_BASELINE_VERSION = "gurbakir-legal-baseline-1"
val LEGAL_BASELINE_ADOPTION_DATE: LocalDate =
    LocalDate.of(
        LEGAL_BASELINE_ADOPTION_YEAR,
        LEGAL_BASELINE_ADOPTION_MONTH,
        LEGAL_BASELINE_ADOPTION_DAY
    )

enum class LegalPageId {
    SUPPORT,
    ACCOUNT_DELETION_REQUEST,
    PRIVACY,
    TERMS,
    SHIPPING,
    RETURNS,
    LEGAL_NOTICE
}

enum class LegalPageSource {
    SHOPIFY_POLICY,
    MERCHANT_PAGE
}

data class LegalPageMetadata(
    val id: LegalPageId,
    @StringRes val titleResourceId: Int,
    @StringRes val summaryResourceId: Int,
    val canonicalUrl: String,
    val baselineVersion: String,
    val adoptedAt: LocalDate,
    val sourceEffectiveDate: LocalDate?,
    val source: LegalPageSource
)

data class LegalSupportUiState(
    val pages: List<LegalPageMetadata> = emptyList(),
    val feedback: LegalPageFeedback? = null,
    val pendingReturnPageId: LegalPageId? = null,
    val focusRequestPageId: LegalPageId? = null
)

data class LegalPageFeedback(val pageId: LegalPageId, val type: LegalPageFeedbackType)

enum class LegalPageFeedbackType {
    OPENING,
    RETURNED,
    NO_BROWSER,
    REJECTED
}

enum class OwnedPageLaunchResult {
    OPENED,
    NO_BROWSER,
    REJECTED
}
