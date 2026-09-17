package com.projectapp134.multibrandtrial

import com.gurbakir.mobile.accountdeletion.DeletionPageId
import com.gurbakir.mobile.accountdeletion.DeletionPageLaunchResult
import com.projectapp134.multibrandtrial.accountdeletion.TrialDeletionPages
import com.projectapp134.multibrandtrial.config.TrialConfiguration
import com.projectapp134.multibrandtrial.legal.TrialLegalEntry
import com.projectapp134.multibrandtrial.legal.TrialLegalPagePolicy
import com.projectapp134.multibrandtrial.legal.TrialLegalRole
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TrialLegalPagePolicyTest {
    private val contract = TrialConfiguration.legal
    private val policy = TrialLegalPagePolicy(contract)

    @Test
    fun `only exact provider verified Trial pages are openable`() {
        contract.entries.forEach { assertTrue(policy.isAllowed(it)) }

        val support = contract.entries.single { it.role == TrialLegalRole.SUPPORT }
        listOf(
            support.copy(intendedUri = support.intendedUri.replace("https://", "http://")),
            support.copy(intendedUri = support.intendedUri.replace("multi-brand-trial-store", "gurbakir")),
            support.copy(intendedUri = support.intendedUri + "?redirect=https://example.com"),
            support.copy(intendedUri = support.intendedUri + "#fragment"),
            support.copy(role = TrialLegalRole.PRIVACY)
        ).forEach { assertFalse(policy.isAllowed(it)) }
    }

    @Test
    fun `deletion adapter maps only privacy and support and preserves launch result`() {
        val pages = TrialDeletionPages(contract)
        assertEquals(setOf(DeletionPageId.PRIVACY, DeletionPageId.SUPPORT), pages.pages().map { it.id }.toSet())
        assertEquals(
            DeletionPageLaunchResult.OPENED,
            pages.launch(DeletionPageId.PRIVACY) { entry ->
                assertEquals(TrialLegalRole.PRIVACY, entry.role)
                DeletionPageLaunchResult.OPENED
            }
        )
        assertEquals(
            DeletionPageLaunchResult.NO_BROWSER,
            pages.launch(DeletionPageId.SUPPORT) { DeletionPageLaunchResult.NO_BROWSER }
        )
    }

    @Test
    fun `unregistered entry cannot inherit trust from the verified origin`() {
        val extra =
            TrialLegalEntry(
                role = TrialLegalRole.SUPPORT,
                state = contract.entries.first().state,
                intendedUri = "https://multi-brand-trial-store.myshopify.com/pages/unregistered"
            )
        assertFalse(policy.isAllowed(extra))
    }
}
