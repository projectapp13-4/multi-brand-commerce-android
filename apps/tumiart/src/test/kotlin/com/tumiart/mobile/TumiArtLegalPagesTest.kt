package com.tumiart.mobile

import com.gurbakir.mobile.accountdeletion.DeletionPageId
import com.gurbakir.mobile.accountdeletion.DeletionPageLaunchResult
import com.tumiart.mobile.legal.TumiArtLegalEntry
import com.tumiart.mobile.legal.TumiArtLegalPages
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TumiArtLegalPagesTest {
    @Test
    fun `unprovisioned request is absent and rejected without opening a browser`() {
        val pages = TumiArtLegalPages("https://tumiart.com", "")
        assertEquals(listOf(DeletionPageId.PRIVACY), pages.pages().map { it.id })
        assertEquals(
            DeletionPageLaunchResult.REJECTED,
            pages.launch(DeletionPageId.ACCOUNT_DELETION_REQUEST) { error("Unprovisioned route launched") }
        )
    }

    @Test
    fun `exact accepted binding maps the typed request and distinguishes missing browser`() {
        val pages = TumiArtLegalPages("https://tumiart.com", "/pages/uygulama-hesap-silme-talebi")
        val descriptor = pages.pages().single { it.id == DeletionPageId.ACCOUNT_DELETION_REQUEST }
        assertEquals(R.string.tumiart_legal_deletion_request, descriptor.titleResourceId)
        var launches = 0
        assertEquals(
            DeletionPageLaunchResult.OPENED,
            pages.launch(DeletionPageId.ACCOUNT_DELETION_REQUEST) {
                launches++
                assertEquals("https://tumiart.com/pages/uygulama-hesap-silme-talebi", it.uri)
                assertTrue(pages.isAllowed(it))
                true
            }
        )
        assertEquals(1, launches)
        assertEquals(
            DeletionPageLaunchResult.NO_BROWSER,
            pages.launch(DeletionPageId.ACCOUNT_DELETION_REQUEST) {
                false
            }
        )
        assertFalse(
            pages.isAllowed(pages.entries.first().copy(uri = "https://tumiart.com/pages/uygulama-hesap-silme-talebi"))
        )
    }

    @Test
    fun `unsafe origins and request path variants never expose or launch the request`() {
        val canonical = "/pages/uygulama-hesap-silme-talebi"
        val origins = listOf(
            "http://tumiart.com",
            "https://tumiart.com:443",
            "https://user@tumiart.com",
            "https://tumiart.com.attacker.invalid",
            "https://www.tumiart.com",
            "https://gurbakir.com",
            "https://multi-brand-trial-store.myshopify.com"
        )
        val paths = listOf(
            "",
            "$canonical/",
            "$canonical?redirect=foreign",
            "$canonical#fragment",
            "/pages/../pages/uygulama-hesap-silme-talebi",
            "/pages/%75ygulama-hesap-silme-talebi",
            "/pages/iletisim",
            "https://tumiart.com$canonical"
        )
        (origins.map { it to canonical } + paths.map { "https://tumiart.com" to it }).forEach { (origin, path) ->
            val pages = TumiArtLegalPages(origin, path)
            assertFalse(pages.pages().any { it.id == DeletionPageId.ACCOUNT_DELETION_REQUEST })
            assertEquals(
                DeletionPageLaunchResult.REJECTED,
                pages.launch(DeletionPageId.ACCOUNT_DELETION_REQUEST) { error("Unsafe binding launched") }
            )
        }
    }

    @Test
    fun `published merchant resources include verified shipping and returns`() {
        val pages = TumiArtLegalPages()
        assertEquals(
            setOf(
                "https://tumiart.com/pages/iletisim",
                "https://tumiart.com/pages/kvkk-aydinlatma-metni",
                "https://tumiart.com/pages/uyelik-sozlesmesi",
                "https://tumiart.com/pages/kargo-ve-teslimat",
                "https://tumiart.com/pages/iade-ve-iptal-politikasi"
            ),
            pages.entries.mapNotNull { it.uri }.toSet()
        )
        assertTrue(pages.entries.any { it.titleResource == R.string.tumiart_legal_notice && it.uri == null })
        pages.entries.filter { it.uri != null }.forEach { assertTrue(pages.isAllowed(it)) }
    }

    @Test
    fun `forged roles foreign origins and URI lookalikes never launch`() {
        val pages = TumiArtLegalPages()
        val support = pages.entries.first()
        listOf(
            "https://tumiart.com.attacker.invalid/pages/iletisim",
            "https://tumiart.com/pages/iletisim?redirect=foreign",
            "https://tumiart.com/pages/iletisim#fragment",
            "https://multi-brand-trial-store.myshopify.com/pages/trial-destek",
            "https://tumiart.com/setup-required/shipping"
        ).forEach { assertFalse(pages.isAllowed(support.copy(uri = it))) }
        assertFalse(pages.isAllowed(TumiArtLegalEntry(R.string.tumiart_legal_shipping, support.uri)))
    }
}
