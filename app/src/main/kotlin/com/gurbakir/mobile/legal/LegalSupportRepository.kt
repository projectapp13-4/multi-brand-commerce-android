package com.gurbakir.mobile.legal

import com.gurbakir.mobile.R
import java.time.LocalDate
import javax.inject.Inject

interface LegalSupportRepository {
    fun pages(): List<LegalPageMetadata>
}

class PackagedLegalSupportRepository @Inject constructor() : LegalSupportRepository {
    override fun pages(): List<LegalPageMetadata> = PAGES

    private companion object {
        val MERCHANT_POLICY_EFFECTIVE_DATE: LocalDate = LocalDate.of(2026, 4, 26)

        val PAGES: List<LegalPageMetadata> =
            listOf(
                page(
                    id = LegalPageId.SUPPORT,
                    titleResourceId = R.string.legal_support_page_support,
                    summaryResourceId = R.string.legal_support_page_support_summary,
                    path = "/pages/contact",
                    source = LegalPageSource.MERCHANT_PAGE
                ),
                page(
                    id = LegalPageId.PRIVACY,
                    titleResourceId = R.string.legal_support_page_privacy,
                    summaryResourceId = R.string.legal_support_page_privacy_summary,
                    path = "/policies/privacy-policy"
                ),
                page(
                    id = LegalPageId.TERMS,
                    titleResourceId = R.string.legal_support_page_terms,
                    summaryResourceId = R.string.legal_support_page_terms_summary,
                    path = "/policies/terms-of-service"
                ),
                page(
                    id = LegalPageId.SHIPPING,
                    titleResourceId = R.string.legal_support_page_shipping,
                    summaryResourceId = R.string.legal_support_page_shipping_summary,
                    path = "/policies/shipping-policy"
                ),
                page(
                    id = LegalPageId.RETURNS,
                    titleResourceId = R.string.legal_support_page_returns,
                    summaryResourceId = R.string.legal_support_page_returns_summary,
                    path = "/policies/refund-policy"
                ),
                page(
                    id = LegalPageId.LEGAL_NOTICE,
                    titleResourceId = R.string.legal_support_page_notice,
                    summaryResourceId = R.string.legal_support_page_notice_summary,
                    path = "/policies/legal-notice"
                )
            )

        fun page(
            id: LegalPageId,
            titleResourceId: Int,
            summaryResourceId: Int,
            path: String,
            source: LegalPageSource = LegalPageSource.SHOPIFY_POLICY
        ): LegalPageMetadata = LegalPageMetadata(
            id = id,
            titleResourceId = titleResourceId,
            summaryResourceId = summaryResourceId,
            canonicalUrl = "https://gurbakir.com$path",
            baselineVersion = LEGAL_BASELINE_VERSION,
            adoptedAt = LEGAL_BASELINE_ADOPTION_DATE,
            sourceEffectiveDate =
                MERCHANT_POLICY_EFFECTIVE_DATE.takeIf { source == LegalPageSource.SHOPIFY_POLICY },
            source = source
        )
    }
}
