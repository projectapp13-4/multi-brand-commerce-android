@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package com.gurbakir.mobile.accessibility

import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class SearchHistoryControlSemanticsTest(
    case: ControlSemanticCase,
    private val availability: SearchHistoryAvailability
) {
    private val harness = ControlSemanticsHarness(case)

    @get:Rule
    val composeRule = harness.rule

    @Test
    fun localizedHistoryControlHasOneLabelledNativeOwnerAndHonestInteraction() {
        harness.showSearch(availability)
        harness.assertPurpose()
        harness.assertContract()
        harness.assertSingleOwner()
        harness.assertLayout()
        harness.assertActivations()
        harness.assertKeyboardTraversal()
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}-{1}")
        fun cases(): List<Array<Any>> = controlSemanticCases().flatMap { case ->
            SearchHistoryAvailability.entries.map { availability -> arrayOf<Any>(case, availability) }
        }
    }
}
