@file:Suppress("FunctionNaming")

package com.gurbakir.mobile.account

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gurbakir.mobile.CoreTestTheme
import com.gurbakir.mobile.performDeterministicClick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AccountScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun bothLocalBrowsingRowsAreAvailable() = localBrowsingRows(
        true,
        true,
        "Search history and My list are stored separately from your account on this device. Your cart is also managed separately."
    )

    @Test
    fun onlySearchLocalBrowsingRowIsAvailable() = localBrowsingRows(
        true,
        false,
        "Search history is stored separately from your account on this device. Your cart is also managed separately."
    )

    @Test
    fun onlyWishlistLocalBrowsingRowIsAvailable() = localBrowsingRows(
        false,
        true,
        "My list is stored separately from your account on this device. Your cart is also managed separately."
    )

    @Test
    fun neitherLocalBrowsingRowIsAvailable() = localBrowsingRows(
        false,
        false,
        "Your cart is managed separately from your account."
    )

    private fun localBrowsingRows(search: Boolean, wishlist: Boolean, explanation: String) {
        var searchCalls = 0
        var wishlistCalls = 0
        setAccountContent(
            AccountUiState(phase = AccountPhase.SIGNED_OUT),
            actions().copy(
                onSearchHistory = if (search) ({ searchCalls += 1 }) else null,
                onWishlist = if (wishlist) ({ wishlistCalls += 1 }) else null
            )
        )
        composeRule.onNodeWithTag(AccountTestTags.LOCAL_DATA_DISCLOSURE).performScrollTo().performDeterministicClick()
        if (search) {
            composeRule.onNodeWithTag(AccountTestTags.SEARCH_HISTORY).performScrollTo().performDeterministicClick()
        } else {
            composeRule.onNodeWithTag(AccountTestTags.SEARCH_HISTORY, useUnmergedTree = true).assertDoesNotExist()
        }
        if (wishlist) {
            composeRule.onNodeWithTag(AccountTestTags.WISHLIST).performScrollTo().performDeterministicClick()
        } else {
            composeRule.onNodeWithTag(AccountTestTags.WISHLIST, useUnmergedTree = true).assertDoesNotExist()
        }
        composeRule.onAllNodesWithText(explanation, substring = false).assertCountEquals(1)
        composeRule.onNodeWithTag(AccountTestTags.CART).performScrollTo().assertHasClickAction()
        assertEquals(if (search) 1 else 0, searchCalls)
        assertEquals(if (wishlist) 1 else 0, wishlistCalls)
    }

    @Test
    fun signedOutAccountPrioritizesHostedSignInThenPublicDestinations() {
        var signIn = 0
        var legal = 0
        setAccountContent(
            AccountUiState(phase = AccountPhase.SIGNED_OUT),
            actions = actions(onSignIn = { signIn += 1 }, onLegalSupport = { legal += 1 })
        )

        composeRule.onNodeWithTag(AccountTestTags.ROOT).assertIsDisplayed()
        composeRule.onAllNodesWithText("Account").assertCountEquals(1)
        composeRule.onNodeWithTag(AccountTestTags.STATUS).assertDoesNotExist()
        composeRule
            .onNodeWithTag(AccountTestTags.SIGN_IN)
            .assertHasClickAction()
            .performDeterministicClick()
        composeRule.onAllNodesWithTag(AccountTestTags.SIGN_IN_DETAILS_PANEL).assertCountEquals(0)
        composeRule.onNodeWithTag(AccountTestTags.SIGN_IN_DETAILS).performDeterministicClick()
        composeRule.onNodeWithTag(AccountTestTags.SIGN_IN_DETAILS_PANEL).assertIsDisplayed()
        composeRule
            .onNodeWithTag(AccountTestTags.CONTENT)
            .performScrollToNode(hasTestTag(AccountTestTags.LEGAL_SUPPORT))
        composeRule.onNodeWithTag(AccountTestTags.LEGAL_SUPPORT).performDeterministicClick()
        composeRule
            .onNodeWithTag(AccountTestTags.LOCAL_DATA_DISCLOSURE)
            .performScrollTo()
            .performDeterministicClick()
        composeRule.onNodeWithTag(AccountTestTags.LOCAL_DATA_PANEL).assertIsDisplayed()
        composeRule.onAllNodesWithTag(AccountTestTags.PROFILE).assertCountEquals(0)
        composeRule.onAllNodesWithTag(AccountTestTags.ADDRESSES).assertCountEquals(0)
        composeRule.onAllNodesWithTag(AccountTestTags.ORDERS).assertCountEquals(0)
        composeRule.onAllNodesWithTag(AccountTestTags.ACCOUNT_DELETION).assertCountEquals(0)
        composeRule.onAllNodesWithTag(AccountTestTags.LOGOUT).assertCountEquals(0)

        assertEquals(1, signIn)
        assertEquals(1, legal)
    }

    @Test
    fun authenticatedAccountShowsIdentityTasksPrivacyAndDirectSessionExit() {
        var logout = 0
        var profile = 0
        var addresses = 0
        var orders = 0
        var accountDeletion = 0
        setAccountContent(
            AccountUiState(
                phase = AccountPhase.AUTHENTICATED,
                summary = AccountSummary("Test Customer")
            ),
            actions =
                actions(
                    onLogout = { logout += 1 },
                    onProfile = { profile += 1 },
                    onAddresses = { addresses += 1 },
                    onOrders = { orders += 1 },
                    onAccountDeletion = { accountDeletion += 1 }
                )
        )

        composeRule.onNodeWithTag(AccountTestTags.STATUS).assertDoesNotExist()
        composeRule
            .onNodeWithTag(AccountTestTags.DISPLAY_NAME)
            .assertIsDisplayed()
            .assertTextContains("Test Customer")
        val orderTop = composeRule.onNodeWithTag(AccountTestTags.ORDERS).fetchSemanticsNode().boundsInRoot.top
        val profileTop = composeRule.onNodeWithTag(AccountTestTags.PROFILE).fetchSemanticsNode().boundsInRoot.top
        val addressTop = composeRule.onNodeWithTag(AccountTestTags.ADDRESSES).fetchSemanticsNode().boundsInRoot.top
        assertTrue(orderTop < profileTop && profileTop < addressTop)
        composeRule.onNodeWithTag(AccountTestTags.ORDERS).performDeterministicClick()
        composeRule.onNodeWithTag(AccountTestTags.PROFILE).performDeterministicClick()
        composeRule.onNodeWithTag(AccountTestTags.ADDRESSES).performDeterministicClick()
        composeRule.onAllNodesWithTag(AccountTestTags.SESSION_SETTINGS).assertCountEquals(0)
        composeRule.onAllNodesWithTag(AccountTestTags.SESSION_PANEL).assertCountEquals(0)
        composeRule.onAllNodesWithTag(AccountTestTags.REFRESH).assertCountEquals(0)
        composeRule
            .onNodeWithTag(AccountTestTags.ACCOUNT_DELETION)
            .performScrollTo()
            .performDeterministicClick()
        composeRule
            .onNodeWithTag(AccountTestTags.CONTENT)
            .performScrollToNode(hasTestTag(AccountTestTags.LOGOUT))
        composeRule
            .onNodeWithTag(AccountTestTags.LOGOUT)
            .performDeterministicClick()

        assertEquals(1, logout)
        assertEquals(1, profile)
        assertEquals(1, addresses)
        assertEquals(1, orders)
        assertEquals(1, accountDeletion)
    }

    @Test
    fun restoringShowsProgressWithoutSignedInOrSignedOutContent() {
        setAccountContent(AccountUiState(phase = AccountPhase.RESTORING))

        composeRule.onNodeWithTag(AccountTestTags.STATUS).assertIsDisplayed()
        composeRule.onNodeWithTag(AccountTestTags.PROGRESS).assertIsDisplayed()
        composeRule.onNodeWithTag(AccountTestTags.SIGN_IN).assertDoesNotExist()
        composeRule.onNodeWithTag(AccountTestTags.SUMMARY).assertDoesNotExist()
    }

    @Test
    fun browserHandoffKeepsContextAndDisablesDuplicateSignIn() {
        setAccountContent(AccountUiState(phase = AccountPhase.AWAITING_BROWSER))

        composeRule.onNodeWithTag(AccountTestTags.STATUS).assertIsDisplayed()
        composeRule.onNodeWithTag(AccountTestTags.PROGRESS).assertIsDisplayed()
        composeRule.onNodeWithTag(AccountTestTags.SIGN_IN).assertIsNotEnabled()
    }

    @Test
    fun loggingOutShowsProgressWithoutFalseSignedOutAction() {
        setAccountContent(AccountUiState(phase = AccountPhase.LOGGING_OUT))

        composeRule.onNodeWithTag(AccountTestTags.STATUS).assertIsDisplayed()
        composeRule.onNodeWithTag(AccountTestTags.PROGRESS).assertIsDisplayed()
        composeRule.onNodeWithTag(AccountTestTags.SIGN_IN).assertDoesNotExist()
    }

    @Test
    fun signedOutRetryableFailureOffersOneRecoveryAction() {
        var retry = 0
        setAccountContent(
            AccountUiState(
                phase = AccountPhase.FAILED,
                failure = AccountFailure.DISCOVERY,
                retryable = true
            ),
            actions = actions(onRetry = { retry += 1 })
        )

        composeRule.onNodeWithTag(AccountTestTags.FEEDBACK).assertIsDisplayed()
        composeRule.onNodeWithTag(AccountTestTags.SIGN_IN).assertDoesNotExist()
        composeRule.onNodeWithTag(AccountTestTags.RETRY).performDeterministicClick()
        assertEquals(1, retry)
    }

    @Test
    fun authenticatedRetryableFailureRetainsIdentityAndUsesSessionRefresh() {
        var refresh = 0
        setAccountContent(
            AccountUiState(
                phase = AccountPhase.AUTHENTICATED,
                summary = AccountSummary("Test Customer"),
                failure = AccountFailure.IDENTITY_TRANSPORT,
                retryable = true
            ),
            actions = actions(onRefresh = { refresh += 1 })
        )

        composeRule.onNodeWithTag(AccountTestTags.DISPLAY_NAME).assertIsDisplayed()
        composeRule.onNodeWithTag(AccountTestTags.REFRESH).performDeterministicClick()
        assertEquals(1, refresh)
    }

    @Test
    fun cancellationAndProtectedCartFeedbackRemainVisibleWithoutAPasswordForm() {
        setAccountContent(
            AccountUiState(
                phase = AccountPhase.SIGNED_OUT,
                notices =
                    setOf(
                        AccountNotice.AUTHORIZATION_CANCELLED,
                        AccountNotice.CART_PROTECTED
                    )
            )
        )

        composeRule.onNodeWithTag(AccountTestTags.FEEDBACK).assertIsDisplayed()
        composeRule.onNodeWithTag(AccountTestTags.SIGN_IN).assertHasClickAction().assertIsFocused()
    }

    @Test
    fun signedOutAccountReflowsAtTwoHundredPercentFontScale() {
        setAccountContent(
            state = AccountUiState(phase = AccountPhase.SIGNED_OUT),
            fontScale = 2f
        )

        composeRule.onNodeWithTag(AccountTestTags.ROOT).assertIsDisplayed()
        composeRule.onNodeWithTag(AccountTestTags.SIGN_IN).assertHasClickAction()
        composeRule
            .onNodeWithTag(AccountTestTags.CONTENT)
            .performScrollToNode(hasTestTag(AccountTestTags.LOCAL_DATA))
        composeRule.onNodeWithTag(AccountTestTags.LOCAL_DATA).assertIsDisplayed()
    }

    @Test
    fun authenticatedAccountReflowsAtTwoHundredPercentFontScale() {
        setAccountContent(
            state =
                AccountUiState(
                    phase = AccountPhase.AUTHENTICATED,
                    summary = AccountSummary("Test Customer")
                ),
            fontScale = 2f
        )

        composeRule.onNodeWithTag(AccountTestTags.DISPLAY_NAME).assertIsDisplayed()
        composeRule
            .onNodeWithTag(AccountTestTags.CONTENT)
            .performScrollToNode(hasTestTag(AccountTestTags.LOGOUT))
        composeRule.onNodeWithTag(AccountTestTags.LOGOUT).assertHasClickAction()
    }

    private fun setAccountContent(state: AccountUiState, actions: AccountActions = actions(), fontScale: Float = 1f) {
        composeRule.setContent {
            CoreTestTheme(
                darkTheme = false
            ) {
                val density = LocalDensity.current
                CompositionLocalProvider(
                    LocalDensity provides Density(density.density, fontScale)
                ) {
                    AccountScreen(state = state, actions = actions)
                }
            }
        }
    }

    @Suppress("LongParameterList")
    private fun actions(
        onSignIn: () -> Unit = {},
        onRetry: () -> Unit = {},
        onRefresh: () -> Unit = {},
        onLogout: () -> Unit = {},
        onProfile: () -> Unit = {},
        onAddresses: () -> Unit = {},
        onOrders: () -> Unit = {},
        onAccountDeletion: () -> Unit = {},
        onLegalSupport: () -> Unit = {}
    ): AccountActions = AccountActions(
        onSignIn = onSignIn,
        onRetry = onRetry,
        onRefresh = onRefresh,
        onLogout = onLogout,
        onProfile = onProfile,
        onAddresses = onAddresses,
        onOrders = onOrders,
        onAccountDeletion = onAccountDeletion,
        onLegalSupport = onLegalSupport,
        onSearchHistory = {},
        onWishlist = {},
        onCart = {}
    )
}
