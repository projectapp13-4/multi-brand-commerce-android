@file:Suppress("FunctionNaming")

package com.gurbakir.mobile.profile

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gurbakir.account.CustomerProfileField
import com.gurbakir.mobile.CoreTestTheme
import com.gurbakir.mobile.performDeterministicClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProfileScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun readyProfileExposesOnlyTwoApprovedEditableFieldsAndConfirmedSave() {
        val state = mutableStateOf(readyState().copy(firstName = "Draft"))
        var firstName = "Ada"
        var save = 0
        setProfileContent(
            state = { state.value },
            actions =
                actions(
                    onFirstNameChanged = {
                        firstName = it
                        state.value = state.value.copy(firstName = it)
                    },
                    onSave = { save += 1 }
                )
        )

        composeRule.onAllNodesWithTag(ProfileTestTags.FIRST_NAME).assertCountEquals(1)
        composeRule.onAllNodesWithTag(ProfileTestTags.LAST_NAME).assertCountEquals(1)
        composeRule
            .onNodeWithTag(ProfileTestTags.FIRST_NAME)
            .assertTextContains("Draft")
            .performTextReplacement("Grace")
        composeRule.onNodeWithTag(ProfileTestTags.SAVE).assertIsEnabled().performDeterministicClick()

        assertEquals("Grace", firstName)
        assertEquals(1, save)
    }

    @Test
    fun serverRejectedFieldIsAnnouncedAndFocused() {
        var focusHandled = 0
        setProfileContent(
            readyState().copy(
                fieldErrors =
                    mapOf(CustomerProfileField.FIRST_NAME to ProfileFieldError.SERVER_REJECTED),
                focusRequest = CustomerProfileField.FIRST_NAME
            ),
            actions = actions(onFocusHandled = { focusHandled += 1 })
        )

        composeRule.onNodeWithTag(ProfileTestTags.FIRST_NAME).assertIsFocused()
        assertEquals(1, focusHandled)
    }

    @Test
    fun profileImeNextMovesFocusToLastName() {
        setProfileContent(readyState())

        composeRule
            .onNodeWithTag(ProfileTestTags.FIRST_NAME)
            .performClick()
            .performImeAction()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(ProfileTestTags.LAST_NAME).assertIsFocused()
    }

    @Test
    fun unconfirmedSaveKeepsFormAndOffersExplicitServerReload() {
        var reload = 0
        setProfileContent(
            readyState().copy(
                phase = ProfilePhase.FAILED,
                firstName = "Draft",
                failure = ProfileFailure.SAVE_UNCONFIRMED
            ),
            actions = actions(onReload = { reload += 1 })
        )

        composeRule.onNodeWithTag(ProfileTestTags.FIRST_NAME).assertTextContains("Draft")
        composeRule.onNodeWithTag(ProfileTestTags.FEEDBACK).assertIsDisplayed()
        composeRule.onNodeWithTag(ProfileTestTags.RELOAD).assertHasClickAction().performDeterministicClick()

        assertEquals(1, reload)
    }

    @Test
    fun profileReflowsAtTwoHundredPercentFontScaleAndBackRemainsReachable() {
        var back = 0
        setProfileContent(
            state = readyState(),
            actions = actions(onBack = { back += 1 }),
            fontScale = 2f
        )

        composeRule.onNodeWithTag(ProfileTestTags.ROOT).assertIsDisplayed()
        composeRule.onNodeWithTag(ProfileTestTags.FIRST_NAME).assertExists()
        composeRule.onNodeWithTag(ProfileTestTags.BACK).performDeterministicClick()

        assertEquals(1, back)
    }

    private fun setProfileContent(state: ProfileUiState, actions: ProfileActions = actions(), fontScale: Float = 1f) {
        setProfileContent(state = { state }, actions = actions, fontScale = fontScale)
    }

    private fun setProfileContent(
        state: () -> ProfileUiState,
        actions: ProfileActions = actions(),
        fontScale: Float = 1f
    ) {
        composeRule.setContent {
            CoreTestTheme(
                darkTheme = false
            ) {
                val density = LocalDensity.current
                CompositionLocalProvider(
                    LocalDensity provides Density(density.density, fontScale)
                ) {
                    ProfileScreen(state = state(), actions = actions)
                }
            }
        }
    }

    private fun actions(
        onBack: () -> Unit = {},
        onFirstNameChanged: (String) -> Unit = {},
        onSave: () -> Unit = {},
        onReload: () -> Unit = {},
        onFocusHandled: () -> Unit = {}
    ): ProfileActions = ProfileActions(
        onBack = onBack,
        onFirstNameChanged = onFirstNameChanged,
        onLastNameChanged = {},
        onSave = onSave,
        onReload = onReload,
        onFocusHandled = onFocusHandled
    )

    private fun readyState(): ProfileUiState = ProfileUiState(
        phase = ProfilePhase.READY,
        firstName = "Ada",
        lastName = "Lovelace",
        originalFirstName = "Ada",
        originalLastName = "Lovelace",
        loaded = true
    )
}
