package com.gurbakir.mobile.legal

import java.time.LocalDate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LegalSupportRepositoryTest {
    private val pages = PackagedLegalSupportRepository().pages()
    private val policy = OwnedPagePolicy(pages)

    @Test
    fun `packaged baseline contains every approved owned route with immutable version metadata`() {
        assertEquals(LegalPageId.entries.toSet(), pages.map { it.id }.toSet())
        assertEquals(pages.size, pages.map { it.canonicalUrl }.toSet().size)
        assertTrue(
            pages.filter { it.id != LegalPageId.ACCOUNT_DELETION_REQUEST }
                .all { it.baselineVersion == LEGAL_BASELINE_VERSION && it.adoptedAt == LocalDate.of(2026, 8, 11) }
        )
        assertEquals(
            "gurbakir-deletion-request-1",
            pages.single { it.id == LegalPageId.ACCOUNT_DELETION_REQUEST }.baselineVersion
        )
        assertTrue(
            pages.filter { it.source == LegalPageSource.SHOPIFY_POLICY }
                .all { it.sourceEffectiveDate == LocalDate.of(2026, 4, 26) }
        )
        assertNull(pages.single { it.id == LegalPageId.SUPPORT }.sourceEffectiveDate)
        assertTrue(pages.all(policy::isAllowed))
    }

    @Test
    fun `exact route policy rejects query fragment credentials alternate ports and path drift`() {
        val privacy = pages.single { it.id == LegalPageId.PRIVACY }

        assertTrue(policy.isAllowed(privacy.canonicalUrl))
        assertFalse(policy.isAllowed("${privacy.canonicalUrl}?source=app"))
        assertFalse(policy.isAllowed("${privacy.canonicalUrl}#summary"))
        assertFalse(policy.isAllowed("https://user@gurbakir.com/policies/privacy-policy"))
        assertFalse(policy.isAllowed("https://gurbakir.com:444/policies/privacy-policy"))
        assertFalse(policy.isAllowed("https://gurbakir.com/policies/privacy-policy/extra"))
        assertFalse(policy.isAllowed("http://gurbakir.com/policies/privacy-policy"))
        assertFalse(policy.isAllowed(privacy.copy(canonicalUrl = "https://example.test/privacy")))
        val deletion = pages.single { it.id == LegalPageId.ACCOUNT_DELETION_REQUEST }
        assertFalse(policy.isAllowed(deletion.copy(canonicalUrl = "https://gurbakir.com/pages/%2e%2e/contact")))
    }
}
