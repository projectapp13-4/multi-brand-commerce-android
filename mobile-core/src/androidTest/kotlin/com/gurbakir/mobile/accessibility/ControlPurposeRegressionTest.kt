@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package com.gurbakir.mobile.accessibility

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gurbakir.mobile.address.AddressFormPhase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The two original actionable-purpose regressions plus two old-API healthy native-action controls. */
@RunWith(AndroidJUnit4::class)
class ControlPurposeRegressionTest {
    private val harness = ControlSemanticsHarness(ControlSemanticCase("tr", 1f, initiallyChecked = false))

    @get:Rule
    val composeRule = harness.rule

    @Test
    fun addressMakeDefaultActionHasItsExactLocalizedPurpose() {
        harness.showAddress(AddressFormPhase.READY)
        harness.assertPurpose()
    }

    @Test
    fun searchHistoryActionHasItsExactLocalizedPurpose() {
        harness.showSearch(SearchHistoryAvailability.AVAILABLE)
        harness.assertPurpose()
    }

    @Test
    fun enabledAddressNativeActionInvokesOnlyItsCallbackOnce() {
        harness.showAddress(AddressFormPhase.READY)
        assertTrue(harness.performAccessibilityClick())
        composeRule.waitForIdle()
        assertEquals(listOf(true), harness.changes)
        assertTrue(harness.checked.value)
        assertTrue(harness.unrelatedActions.isEmpty())
    }

    @Test
    fun unavailableSearchNativeActionPreservesValueAndInvokesNoCallback() {
        harness.showSearch(SearchHistoryAvailability.UNAVAILABLE)
        assertFalse(harness.performAccessibilityClick())
        composeRule.waitForIdle()
        assertTrue(harness.changes.isEmpty())
        assertFalse(harness.checked.value)
        assertTrue(harness.unrelatedActions.isEmpty())
    }
}
