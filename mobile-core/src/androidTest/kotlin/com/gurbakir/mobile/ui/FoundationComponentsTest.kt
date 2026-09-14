@file:Suppress("DEPRECATION", "FunctionNaming")

package com.gurbakir.mobile.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onChildren
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.gurbakir.mobile.CoreTestTheme
import com.gurbakir.mobile.core.R
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FoundationComponentsTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun destinationScaffoldShowsUpOnlyForSecondaryDestinationsWithA48DpTarget() {
        val secondary = mutableStateOf(false)
        var navigateUpCount = 0
        composeRule.setContent {
            TestTheme {
                DestinationScaffold(
                    title = "Destination",
                    level =
                        if (secondary.value) {
                            DestinationLevel.SECONDARY
                        } else {
                            DestinationLevel.PRIMARY
                        },
                    onNavigateUp = { navigateUpCount += 1 },
                    navigateUpTestTag = UP_TAG
                ) { padding ->
                    Text("Content", Modifier.padding(padding))
                }
            }
        }

        composeRule.onNodeWithTag(UP_TAG).assertDoesNotExist()
        composeRule.runOnIdle { secondary.value = true }
        composeRule
            .onNodeWithTag(UP_TAG)
            .assertIsDisplayed()
            .assertHasClickAction()
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
            .performClick()
        composeRule.runOnIdle { assertEquals(1, navigateUpCount) }
    }

    @Test
    fun appNavigationItemKeepsItsLabelSelectionVectorAndTouchTargetInBarAndRail() {
        val selected = mutableStateOf(false)
        val layoutType = mutableStateOf(NavigationSuiteType.NavigationBar)
        val homeLabel =
            InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.primary_home)
        composeRule.setContent {
            TestTheme {
                NavigationSuiteScaffold(
                    navigationSuiteItems = {
                        AppNavigationItem(
                            selected = selected.value,
                            onClick = { selected.value = true },
                            labelResourceId = R.string.primary_home,
                            selectedIconResourceId = R.drawable.ic_nav_home_selected,
                            unselectedIconResourceId = R.drawable.ic_nav_home,
                            testTag = NAVIGATION_ITEM_TAG
                        )
                    },
                    layoutType = layoutType.value
                ) {
                    Text("Shell content")
                }
            }
        }

        composeRule
            .onNodeWithTag(NAVIGATION_ITEM_TAG)
            .assertIsDisplayed()
            .assertIsNotSelected()
            .assertHasClickAction()
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithContentDescription(homeLabel).assertIsDisplayed()
        composeRule
            .onNodeWithTag(navigationIconTestTag(NAVIGATION_ITEM_TAG, false), useUnmergedTree = true)
            .assertIsDisplayed()

        composeRule.onNodeWithTag(NAVIGATION_ITEM_TAG).performClick().assertIsSelected()
        composeRule
            .onNodeWithTag(navigationIconTestTag(NAVIGATION_ITEM_TAG, true), useUnmergedTree = true)
            .assertIsDisplayed()

        composeRule.runOnIdle { layoutType.value = NavigationSuiteType.NavigationRail }
        composeRule.onNodeWithTag(NAVIGATION_ITEM_TAG).assertIsDisplayed().assertIsSelected()
        composeRule.onNodeWithContentDescription(homeLabel).assertIsDisplayed()
    }

    @Test
    fun commerceStatePanelExposesStableRecoveryStructure() {
        var primaryCount = 0
        var secondaryCount = 0
        composeRule.setContent {
            TestTheme {
                Box(Modifier.safeDrawingPadding()) {
                    CommerceStatePanel(
                        title = "Unavailable",
                        body = "Try again or get help.",
                        primaryActionLabel = "Retry",
                        onPrimaryAction = { primaryCount += 1 },
                        secondaryActionLabel = "Help",
                        onSecondaryAction = { secondaryCount += 1 },
                        testTag = STATE_PANEL_TAG
                    )
                }
            }
        }

        composeRule.onNodeWithTag(STATE_PANEL_TAG).assertIsDisplayed()
        composeRule.onNodeWithText("Unavailable").assertIsDisplayed()
        composeRule.onNodeWithText("Try again or get help.").assertIsDisplayed()
        composeRule.onNodeWithText("Retry").performClick()
        composeRule.onNodeWithText("Help").performClick()
        composeRule.runOnIdle {
            assertEquals(1, primaryCount)
            assertEquals(1, secondaryCount)
        }
    }

    @Test
    @SdkSuppress(minSdkVersion = 26)
    fun commerceSkeletonUsesCallerGeometryWithoutMotionOrAccessibilityNoise() {
        var expectedColor = Color.Unspecified
        composeRule.setContent {
            TestTheme {
                val skeletonColor = MaterialTheme.colorScheme.surfaceVariant
                SideEffect { expectedColor = skeletonColor }
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier =
                            Modifier.size(width = 120.dp, height = 24.dp)
                                .testTag(SKELETON_TAG)
                    ) {
                        CommerceSkeleton(modifier = Modifier.fillMaxSize())
                    }
                }
            }
        }

        val skeletonHost = composeRule.onNodeWithTag(SKELETON_TAG)
        skeletonHost
            .assertIsDisplayed()
            .assertWidthIsAtLeast(120.dp)
            .assertHeightIsAtLeast(24.dp)
        val skeletonSemantics = skeletonHost.onChildren()
        skeletonSemantics.assertCountEquals(1)
        skeletonSemantics[0]
            .assert(
                SemanticsMatcher.keyNotDefined(SemanticsProperties.Text) and
                    SemanticsMatcher.keyNotDefined(SemanticsProperties.ContentDescription) and
                    SemanticsMatcher.keyNotDefined(SemanticsProperties.StateDescription) and
                    SemanticsMatcher.keyNotDefined(SemanticsActions.OnClick)
            )
            .assertHasNoClickAction()

        val image = skeletonHost.captureToImage()
        val centerPixel = image.toPixelMap()[image.width / 2, image.height / 2]
        assertEquals(expectedColor.toArgb(), centerPixel.toArgb())
    }

    @androidx.compose.runtime.Composable
    private fun TestTheme(content: @androidx.compose.runtime.Composable () -> Unit) {
        CoreTestTheme(
            darkTheme = false,
            content = content
        )
    }

    private companion object {
        const val UP_TAG = "foundation-up"
        const val NAVIGATION_ITEM_TAG = "foundation-navigation-item"
        const val STATE_PANEL_TAG = "foundation-state-panel"
        const val SKELETON_TAG = "foundation-skeleton"
    }
}
