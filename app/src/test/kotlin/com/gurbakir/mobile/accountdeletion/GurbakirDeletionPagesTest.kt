package com.gurbakir.mobile.accountdeletion

import com.gurbakir.mobile.R
import com.gurbakir.mobile.legal.LegalPageId
import com.gurbakir.mobile.legal.LegalPageMetadata
import com.gurbakir.mobile.legal.LegalSupportRepository
import com.gurbakir.mobile.legal.OwnedPageLaunchResult
import com.gurbakir.mobile.legal.PackagedLegalSupportRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class GurbakirDeletionPagesTest {
    @Test
    fun `adapter exposes exact privacy deletion titles and launches canonical metadata`() {
        val pages = PackagedLegalSupportRepository().pages()
        val adapter = GurbakirDeletionPages(repository(pages))
        assertEquals(
            listOf(
                DeletionPageDescriptor(
                    DeletionPageId.ACCOUNT_DELETION_REQUEST,
                    R.string.legal_support_page_account_deletion_request
                ),
                DeletionPageDescriptor(DeletionPageId.PRIVACY, R.string.legal_support_page_privacy)
            ),
            adapter.pages()
        )
        for ((id, legalId, url) in listOf(
            Triple(DeletionPageId.PRIVACY, LegalPageId.PRIVACY, "https://gurbakir.com/policies/privacy-policy"),
            Triple(
                DeletionPageId.ACCOUNT_DELETION_REQUEST,
                LegalPageId.ACCOUNT_DELETION_REQUEST,
                "https://gurbakir.com/pages/uygulama-hesap-silme-talebi"
            )
        )) {
            val result = adapter.launch(id) { page ->
                assertSame(pages.single { it.id == legalId }, page)
                assertEquals(url, page.canonicalUrl)
                OwnedPageLaunchResult.OPENED
            }
            assertEquals(DeletionPageLaunchResult.OPENED, result)
        }
    }

    @Test
    fun `missing pages and unrelated legal pages cannot be launched`() {
        val adapter = GurbakirDeletionPages(
            repository(
                PackagedLegalSupportRepository().pages().filter {
                    it.id ==
                        LegalPageId.TERMS
                }
            )
        )
        assertEquals(emptyList<DeletionPageDescriptor>(), adapter.pages())
        var launched = false
        for (id in DeletionPageId.entries) {
            assertEquals(
                DeletionPageLaunchResult.REJECTED,
                adapter.launch(id) {
                    launched = true
                    OwnedPageLaunchResult.OPENED
                }
            )
        }
        assertFalse(launched)
    }

    @Test
    fun `no browser and rejection outcomes retain their meanings`() {
        val adapter = GurbakirDeletionPages(PackagedLegalSupportRepository())
        assertEquals(
            DeletionPageLaunchResult.NO_BROWSER,
            adapter.launch(DeletionPageId.PRIVACY) {
                OwnedPageLaunchResult.NO_BROWSER
            }
        )
        assertEquals(
            DeletionPageLaunchResult.REJECTED,
            adapter.launch(DeletionPageId.ACCOUNT_DELETION_REQUEST) {
                OwnedPageLaunchResult.REJECTED
            }
        )
    }

    private fun repository(pages: List<LegalPageMetadata>) = object : LegalSupportRepository {
        override fun pages(): List<LegalPageMetadata> = pages
    }
}
