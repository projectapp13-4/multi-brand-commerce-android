package com.tumiart.mobile

import com.tumiart.mobile.legal.TumiArtLegalEntry
import com.tumiart.mobile.legal.TumiArtLegalPages
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TumiArtLegalPagesTest {
    @Test
    fun `only the three observed merchant resources are available`() {
        val pages = TumiArtLegalPages()
        assertEquals(
            setOf(
                "https://tumiart.com/pages/iletisim",
                "https://tumiart.com/pages/kvkk-aydinlatma-metni",
                "https://tumiart.com/pages/uyelik-sozlesmesi"
            ),
            pages.entries.mapNotNull { it.uri }.toSet()
        )
        assertEquals(3, pages.entries.count { it.uri == null })
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
