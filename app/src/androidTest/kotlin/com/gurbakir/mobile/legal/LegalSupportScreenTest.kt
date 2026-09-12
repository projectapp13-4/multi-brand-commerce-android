@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package com.gurbakir.mobile.legal

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.waitUntilExactlyOneExists
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gurbakir.foundation.ui.CommerceTheme
import com.gurbakir.mobile.brand.GurbakirBrand
import com.gurbakir.mobile.performDeterministicClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LegalSupportScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val pages = PackagedLegalSupportRepository().pages()

    @Test
    fun versionedOwnedPageCanBeOpenedFromTheLocalIndex() {
        var openedPage: LegalPageMetadata? = null
        setLegalContent(
            state = LegalSupportUiState(pages = pages),
            actions = LegalSupportActions(onBack = {}, onOpen = { openedPage = it })
        )

        composeRule.onNodeWithTag(LegalSupportTestTags.BASELINE).assertIsDisplayed()
        composeRule.onNodeWithTag(LegalSupportTestTags.CONTENT).performScrollToIndex(2)
        composeRule
            .onNodeWithTag(LegalSupportTestTags.open(LegalPageId.PRIVACY))
            .assertIsDisplayed()
            .performDeterministicClick()
        composeRule.waitForIdle()

        assertEquals(LegalPageId.PRIVACY, openedPage?.id)
    }

    @Test
    fun browserFailureIsAnnouncedAndFocusReturnsToTheSameAction() {
        setLegalContent(
            state =
                LegalSupportUiState(
                    pages = pages,
                    feedback = LegalPageFeedback(LegalPageId.PRIVACY, LegalPageFeedbackType.NO_BROWSER),
                    focusRequestPageId = LegalPageId.PRIVACY
                )
        )

        composeRule.onNodeWithTag(LegalSupportTestTags.FEEDBACK).assertIsDisplayed()
        composeRule.waitForIdle()
        composeRule
            .onNodeWithTag(LegalSupportTestTags.open(LegalPageId.PRIVACY))
            .assertIsDisplayed()
            .assertIsFocused()
    }

    @Test
    fun fullIndexRemainsScrollableAtTwoHundredPercentText() {
        setLegalContent(state = LegalSupportUiState(pages = pages), fontScale = 2f)

        composeRule.onNodeWithTag(LegalSupportTestTags.CONTENT).performScrollToIndex(6)
        composeRule.onNodeWithTag(LegalSupportTestTags.page(LegalPageId.LEGAL_NOTICE)).assertIsDisplayed()
    }

    private fun setLegalContent(
        state: LegalSupportUiState,
        actions: LegalSupportActions = LegalSupportActions(onBack = {}, onOpen = {}),
        fontScale: Float = 1f
    ) {
        composeRule.setContent {
            CommerceTheme(
                designTokens = GurbakirBrand.configuration.designTokens,
                darkTheme = false
            ) {
                val currentDensity = LocalDensity.current
                CompositionLocalProvider(
                    LocalDensity provides Density(currentDensity.density, fontScale)
                ) {
                    LegalSupportScreen(state = state, actions = actions)
                }
            }
        }
        composeRule.waitUntilExactlyOneExists(hasTestTag(LegalSupportTestTags.ROOT), timeoutMillis = 5_000)
    }
}
