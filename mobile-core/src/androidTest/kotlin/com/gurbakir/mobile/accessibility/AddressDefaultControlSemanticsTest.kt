@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package com.gurbakir.mobile.accessibility

import com.gurbakir.mobile.address.AddressFormPhase
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class AddressDefaultControlSemanticsTest(case: ControlSemanticCase, private val phase: AddressFormPhase) {
    private val harness = ControlSemanticsHarness(case)

    @get:Rule
    val composeRule = harness.rule

    @Test
    fun localizedDefaultControlHasOneLabelledNativeOwnerAndHonestInteraction() {
        harness.showAddress(phase)
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
            listOf(AddressFormPhase.READY, AddressFormPhase.SAVING, AddressFormPhase.FAILED).map { phase ->
                arrayOf<Any>(case, phase)
            }
        }
    }
}
