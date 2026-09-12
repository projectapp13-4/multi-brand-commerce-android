@file:Suppress("FunctionNaming")

package com.gurbakir.mobile.update

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gurbakir.mobile.CoreTestTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UpdatePolicyBannerTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun optionalUpdateIsNonBlockingAndExposesRetryHelpAndDeferActions() {
        var retryCount = 0
        var helpCount = 0
        var deferCount = 0
        setBanner(
            state =
                UpdatePolicyUiState(
                    presentation =
                        UpdatePolicyPresentation(
                            source = UpdatePolicySource.REMOTE_CONFIG,
                            optionalUpdateVersionCode = 2
                        )
                ),
            actions =
                actions(
                    retry = { retryCount += 1 },
                    help = { helpCount += 1 },
                    defer = { deferCount += 1 }
                )
        )

        composeRule.onNodeWithTag(UpdatePolicyTestTags.UPDATE_NOTICE).assertIsDisplayed()
        composeRule.onNodeWithTag(UpdatePolicyTestTags.RETRY).performClick()
        composeRule.onNodeWithTag(UpdatePolicyTestTags.LEGAL_SUPPORT).performClick()
        composeRule.onNodeWithTag(UpdatePolicyTestTags.DISMISS).performClick()

        composeRule.runOnIdle {
            assertEquals(1, retryCount)
            assertEquals(1, helpCount)
            assertEquals(1, deferCount)
        }
    }

    @Test
    fun maintenanceTakesPriorityAndCanBeDismissedWithoutCreatingAHardGate() {
        var dismissCount = 0
        setBanner(
            state =
                UpdatePolicyUiState(
                    presentation =
                        UpdatePolicyPresentation(
                            source = UpdatePolicySource.UNEXPIRED_CACHE,
                            maintenanceMessageEnabled = true,
                            optionalUpdateVersionCode = 3,
                            policyRevision = 9L
                        )
                ),
            actions = actions(dismissMaintenance = { dismissCount += 1 })
        )

        composeRule.onNodeWithTag(UpdatePolicyTestTags.MAINTENANCE_NOTICE).assertIsDisplayed()
        composeRule.onAllNodesWithTag(UpdatePolicyTestTags.UPDATE_NOTICE).assertCountEquals(0)
        composeRule.onNodeWithTag(UpdatePolicyTestTags.DISMISS).performClick()

        composeRule.runOnIdle { assertEquals(1, dismissCount) }
    }

    @Test
    fun safeDefaultsRenderNoPolicySurface() {
        setBanner(UpdatePolicyUiState(), actions())

        composeRule.onAllNodesWithTag(UpdatePolicyTestTags.UPDATE_NOTICE).assertCountEquals(0)
        composeRule.onAllNodesWithTag(UpdatePolicyTestTags.MAINTENANCE_NOTICE).assertCountEquals(0)
    }

    @Test
    fun optionalUpdateActionsRemainVisibleAtTwoHundredPercentText() {
        setBanner(
            state =
                UpdatePolicyUiState(
                    presentation =
                        UpdatePolicyPresentation(
                            source = UpdatePolicySource.REMOTE_CONFIG,
                            optionalUpdateVersionCode = 2
                        )
                ),
            actions = actions(),
            fontScale = 2f
        )

        composeRule.onNodeWithTag(UpdatePolicyTestTags.UPDATE_NOTICE).assertIsDisplayed()
        composeRule.onNodeWithTag(UpdatePolicyTestTags.RETRY).assertIsDisplayed()
        composeRule.onNodeWithTag(UpdatePolicyTestTags.LEGAL_SUPPORT).assertIsDisplayed()
        composeRule.onNodeWithTag(UpdatePolicyTestTags.DISMISS).assertIsDisplayed()
    }

    private fun setBanner(state: UpdatePolicyUiState, actions: UpdatePolicyActions, fontScale: Float = 1f) {
        composeRule.setContent {
            CoreTestTheme(
                darkTheme = false
            ) {
                val density = LocalDensity.current
                CompositionLocalProvider(
                    LocalDensity provides Density(density.density, fontScale)
                ) {
                    UpdatePolicyBanner(state = state, actions = actions)
                }
            }
        }
    }

    private fun actions(
        retry: () -> Unit = {},
        help: () -> Unit = {},
        defer: () -> Unit = {},
        dismissMaintenance: () -> Unit = {}
    ): UpdatePolicyActions = UpdatePolicyActions(
        onRetry = retry,
        onLegalSupport = help,
        onDeferUpdate = defer,
        onDismissMaintenance = dismissMaintenance
    )
}
