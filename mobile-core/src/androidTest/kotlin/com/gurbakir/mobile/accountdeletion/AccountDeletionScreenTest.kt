@file:Suppress("FunctionNaming")

package com.gurbakir.mobile.accountdeletion

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gurbakir.mobile.CoreTestTheme
import com.gurbakir.mobile.performDeterministicClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AccountDeletionScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val pages =
        listOf(
            DeletionPageDescriptor(DeletionPageId.PRIVACY, com.gurbakir.mobile.core.R.string.account_deletion_title),
            DeletionPageDescriptor(
                DeletionPageId.ACCOUNT_DELETION_REQUEST,
                com.gurbakir.mobile.core.R.string.account_deletion_title
            )
        )
    private val readyState =
        AccountDeletionUiState(
            phase = AccountDeletionPhase.READY,
            privacyPage = pages.first { it.id == DeletionPageId.PRIVACY },
            requestPage = pages.first { it.id == DeletionPageId.ACCOUNT_DELETION_REQUEST }
        )

    @Test
    fun accountEnabledWithoutBrowsingStillExposesAllCleanupDefaults() {
        lateinit var controller: androidx.navigation.NavHostController
        composeRule.setContent {
            CoreTestTheme {
                val nav = androidx.navigation.compose.rememberNavController()
                androidx.compose.runtime.SideEffect { controller = nav }
                com.gurbakir.mobile.ProductionNavHost(
                    navController = nav,
                    applicationComposition = com.gurbakir.mobile.capabilityComposition(
                        search = false,
                        wishlist = false
                    ),
                    deepLinks = com.gurbakir.mobile.capabilityDeepLinks,
                    customerAccountBindings = com.gurbakir.mobile.capabilityAccountBindings,
                    content = com.gurbakir.mobile.ProductionDestinationContent(
                        home = {},
                        search = { error("Disabled Search invoked") },
                        wishlist = { error("Disabled Wishlist invoked") },
                        accountDeletion = { AccountDeletionScreen(readyState, actions()) }
                    )
                )
            }
        }
        composeRule.runOnIdle { controller.navigate(com.gurbakir.mobile.navigation.AccountDeletionRoute) }
        val content = composeRule.onNodeWithTag(AccountDeletionTestTags.CONTENT)
        content.performScrollToNode(hasTestTag(AccountDeletionTestTags.SEARCH))
        composeRule.onNodeWithTag(AccountDeletionTestTags.SEARCH).assertIsOn()
        content.performScrollToNode(hasTestTag(AccountDeletionTestTags.WISHLIST))
        composeRule.onNodeWithTag(AccountDeletionTestTags.WISHLIST).assertIsOff()
        content.performScrollToNode(hasTestTag(AccountDeletionTestTags.CART))
        composeRule.onNodeWithTag(AccountDeletionTestTags.CART).assertIsOn()
    }

    @Test
    fun requestResourcesAreExplicitAndDoNotProduceAnAutomaticSubmission() {
        var openedPage: DeletionPageId? = null
        setContent(
            readyState,
            actions = actions(onOpenPage = { openedPage = it.id })
        )

        composeRule.onNodeWithTag(AccountDeletionTestTags.REMOTE_REQUEST).assertIsDisplayed()
        composeRule
            .onNodeWithTag(AccountDeletionTestTags.REQUEST)
            .performScrollTo()
            .assertHasClickAction()
            .performDeterministicClick()

        assertEquals(DeletionPageId.ACCOUNT_DELETION_REQUEST, openedPage)
    }

    @Test
    fun deletionRequestRemainsReachableAtTwoHundredPercentFontScale() {
        setContent(readyState, fontScale = 2f)

        composeRule
            .onNodeWithTag(AccountDeletionTestTags.REQUEST)
            .performScrollTo()
            .assertHasClickAction()
    }

    @Test
    fun localDataDefaultsPreserveTheAccountIndependentWishlist() {
        setContent(readyState)

        val content = composeRule.onNodeWithTag(AccountDeletionTestTags.CONTENT)
        content.performScrollToNode(hasTestTag(AccountDeletionTestTags.SEARCH))
        composeRule.onNodeWithTag(AccountDeletionTestTags.SEARCH).assertIsOn()
        content.performScrollToNode(hasTestTag(AccountDeletionTestTags.WISHLIST))
        composeRule.onNodeWithTag(AccountDeletionTestTags.WISHLIST).assertIsOff()
        content.performScrollToNode(hasTestTag(AccountDeletionTestTags.CART))
        composeRule.onNodeWithTag(AccountDeletionTestTags.CART).assertIsOn()
    }

    @Test
    fun localCleanupRequiresASecondExplicitConfirmation() {
        var requestCount = 0
        var confirmCount = 0
        composeRule.setContent {
            CoreTestTheme(
                darkTheme = false
            ) {
                var confirmationVisible by remember { mutableStateOf(false) }
                AccountDeletionScreen(
                    state = readyState.copy(confirmationVisible = confirmationVisible),
                    actions =
                        actions(
                            onRequestLocalClear = {
                                requestCount += 1
                                confirmationVisible = true
                            },
                            onConfirmLocalClear = { confirmCount += 1 }
                        )
                )
            }
        }

        composeRule
            .onNodeWithTag(AccountDeletionTestTags.CONTENT)
            .performScrollToNode(hasTestTag(AccountDeletionTestTags.CLEAR))
        composeRule.onNodeWithTag(AccountDeletionTestTags.CLEAR).performDeterministicClick()

        assertEquals(1, requestCount)
        assertEquals(0, confirmCount)
        composeRule.onNodeWithTag(AccountDeletionTestTags.CONFIRMATION).assertIsDisplayed()
        composeRule.onNodeWithTag(AccountDeletionTestTags.CONFIRM).performDeterministicClick()
        assertEquals(1, confirmCount)
    }

    @Test
    fun completedResultsRemainUsableAtTwoHundredPercentFontScale() {
        setContent(
            AccountDeletionUiState(
                phase = AccountDeletionPhase.COMPLETED,
                result =
                    AccountDeletionLocalResult(
                        session = AccountDeletionClearOutcome.CLEARED,
                        searchHistory = AccountDeletionClearOutcome.CLEARED,
                        wishlist = AccountDeletionClearOutcome.NOT_SELECTED,
                        cart = AccountDeletionClearOutcome.FAILED,
                        remoteLogoutUnverified = true
                    )
            ),
            fontScale = 2f
        )

        composeRule.onNodeWithTag(AccountDeletionTestTags.RESULT).assertIsDisplayed()
        composeRule
            .onNodeWithTag(AccountDeletionTestTags.FINISH)
            .performScrollTo()
            .assertHasClickAction()
    }

    private fun setContent(
        state: AccountDeletionUiState,
        actions: AccountDeletionActions = actions(),
        fontScale: Float = 1f
    ) {
        composeRule.setContent {
            CoreTestTheme(
                darkTheme = false
            ) {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                    AccountDeletionScreen(state = state, actions = actions)
                }
            }
        }
    }

    private fun actions(
        onOpenPage: (DeletionPageDescriptor) -> Unit = {},
        onRequestLocalClear: () -> Unit = {},
        onConfirmLocalClear: () -> Unit = {}
    ) = AccountDeletionActions(
        onBack = {},
        onOpenPage = onOpenPage,
        onClearSearchHistoryChanged = {},
        onClearWishlistChanged = {},
        onDiscardCartChanged = {},
        onRequestLocalClear = onRequestLocalClear,
        onDismissLocalClear = {},
        onConfirmLocalClear = onConfirmLocalClear,
        onRetry = {},
        onFinish = {}
    )
}
