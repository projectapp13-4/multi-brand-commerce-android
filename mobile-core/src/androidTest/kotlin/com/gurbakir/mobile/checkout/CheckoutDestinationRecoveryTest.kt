@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package com.gurbakir.mobile.checkout

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.waitUntilExactlyOneExists
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.gurbakir.checkout.CheckoutEvent
import com.gurbakir.mobile.CartDestination
import com.gurbakir.mobile.CoreTestTheme
import com.gurbakir.mobile.cart.CartStatus
import com.gurbakir.mobile.cart.CartTestTags
import com.gurbakir.mobile.performDeterministicClick
import kotlinx.coroutines.CompletableDeferred
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CheckoutDestinationRecoveryTest {
    @get:Rule
    val composeRule = createComposeRule()
    private lateinit var fixture: CheckoutProtocolAndroidFixture

    @After
    fun clearOwnedViewModels() {
        if (::fixture.isInitialized) {
            InstrumentationRegistry.getInstrumentation().runOnMainSync { fixture.close() }
        }
    }

    @Test
    fun actualDestinationCleanupRetryClearsTheAcceptedCartWithoutRelaunch() {
        showDestination()
        launchCheckout()
        acceptCompletionWithRefusedClear()
        composeRule.runOnIdle { fixture.cartStore.clearFailure = null }
        clickCleanupFeedback()

        composeRule.runOnIdle {
            assertEquals(CheckoutStatus.COMPLETED, fixture.checkout.state.value.status)
            assertEquals(listOf(fixture.cartA, fixture.cartA), fixture.completionAttempts)
            assertNull(fixture.cartStore.cart)
            assertEquals(1, fixture.sdk.presentations)
            assertEquals(0, fixture.gateway.merchandiseMutations)
        }
    }

    @Test
    fun actualDestinationCleanupRetryPreservesReplacementCartAndUsesAcceptedId() {
        showDestination()
        launchCheckout()
        acceptCompletionWithRefusedClear()
        composeRule.runOnIdle {
            fixture.cartStore.clearFailure = null
            fixture.installReplacement()
        }
        val replacement = fixture.cartStore.cart
        clickCleanupFeedback()

        composeRule.runOnIdle {
            assertEquals(CheckoutStatus.COMPLETED_CURRENT_CART_PRESERVED, fixture.checkout.state.value.status)
            assertEquals(listOf(fixture.cartA, fixture.cartA), fixture.completionAttempts)
            assertEquals(replacement, fixture.cartStore.cart)
            assertEquals(fixture.cartB, fixture.cartStore.cart?.id)
            assertEquals(1, fixture.cartStore.clears)
            assertEquals(1, fixture.sdk.presentations)
            assertEquals(0, fixture.gateway.merchandiseMutations)
        }
    }

    @Test
    fun cleanupRetryDoesNotWaitForTheOriginalTerminalFollowUpRead() {
        showDestination()
        launchCheckout()
        composeRule.runOnIdle {
            fixture.cartStore.clearFailure = IllegalStateException("synthetic refused clear")
            fixture.sessionStore.nextReadGate = CompletableDeferred()
            fixture.sdk.emit(CheckoutEvent.Completed)
        }
        composeRule.waitUntil(timeoutMillis = 5_000) { fixture.sessionStore.readEntered.isCompleted }
        composeRule.runOnIdle {
            assertEquals(CheckoutStatus.CLEANUP_REQUIRED, fixture.checkout.state.value.status)
            assertFalse(fixture.checkout.state.value.busy)
            fixture.cartStore.clearFailure = null
            fixture.installReplacement()
        }
        val replacement = fixture.cartStore.cart
        clickCleanupFeedback()

        composeRule.runOnIdle {
            assertEquals(CheckoutStatus.COMPLETED_CURRENT_CART_PRESERVED, fixture.checkout.state.value.status)
            assertEquals(listOf(fixture.cartA, fixture.cartA), fixture.completionAttempts)
            assertEquals(replacement, fixture.cartStore.cart)
            assertEquals(1, fixture.cartStore.clears)
            assertEquals(1, fixture.sdk.presentations)
        }
    }

    @Test
    fun ordinaryMatchingCompletionRemainsVisibleThroughActualDestination() {
        showDestination()
        launchCheckout()
        composeRule.runOnIdle { fixture.sdk.emit(CheckoutEvent.Completed) }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            fixture.checkout.state.value.status == CheckoutStatus.COMPLETED
        }
        composeRule.onNodeWithTag(CartTestTags.CHECKOUT_FEEDBACK).assertIsDisplayed()
        composeRule.runOnIdle {
            assertEquals(listOf(fixture.cartA), fixture.completionAttempts)
            assertNull(fixture.cartStore.cart)
            assertEquals(1, fixture.cartStore.clears)
            assertEquals(1, fixture.sdk.presentations)
            assertEquals(0, fixture.gateway.merchandiseMutations)
        }
    }

    @Test
    fun acceptedCleanupRemainsAccessibleAfterActualFollowUpSessionReadFault() =
        assertCleanupAfterSessionReadFault(replaceCart = false)

    @Test
    fun acceptedCleanupAfterSessionReadFaultPreservesReplacementCart() =
        assertCleanupAfterSessionReadFault(replaceCart = true)

    private fun showDestination() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync { fixture = CheckoutProtocolAndroidFixture() }
        composeRule.setContent {
            CoreTestTheme {
                CompositionLocalProvider(LocalViewModelStoreOwner provides fixture.owner) {
                    CartDestination(rememberNavController())
                }
            }
        }
        composeRule.waitUntilExactlyOneExists(hasTestTag(CartTestTags.ROOT), timeoutMillis = 5_000)
        composeRule.waitUntil(timeoutMillis = 5_000) { fixture.cart.state.value.status == CartStatus.ACTIVE }
        composeRule.runOnIdle { assertEquals(listOf(fixture.cartA), fixture.gateway.buyerRebinds) }
    }

    private fun launchCheckout() {
        composeRule.onNodeWithTag(CartTestTags.CHECKOUT).performScrollTo()
        composeRule.onNodeWithTag(CartTestTags.CHECKOUT).performDeterministicClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            fixture.checkout.state.value.status == CheckoutStatus.IN_PROGRESS
        }
        composeRule.runOnIdle {
            assertEquals(1, fixture.sdk.presentations)
            assertEquals(2, fixture.gateway.buyerRebinds.size)
        }
    }

    private fun acceptCompletionWithRefusedClear() {
        composeRule.runOnIdle {
            fixture.cartStore.clearFailure = IllegalStateException("synthetic refused clear")
            fixture.sdk.emit(CheckoutEvent.Completed)
        }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            fixture.checkout.state.value.status == CheckoutStatus.CLEANUP_REQUIRED
        }
        composeRule.runOnIdle { assertEquals(listOf(fixture.cartA), fixture.completionAttempts) }
        composeRule.onNodeWithTag(CartTestTags.CHECKOUT).performScrollTo().assertIsNotEnabled()
    }

    private fun clickCleanupFeedback() {
        composeRule.onNodeWithTag(CartTestTags.CHECKOUT_FEEDBACK_ACTION).performScrollTo()
        composeRule.onNodeWithTag(CartTestTags.CHECKOUT_FEEDBACK_ACTION).performDeterministicClick()
        composeRule.waitForIdle()
    }

    private fun assertCleanupAfterSessionReadFault(replaceCart: Boolean) {
        showDestination()
        launchCheckout()
        composeRule.runOnIdle {
            fixture.cartStore.clearFailure = IllegalStateException("synthetic refused clear")
            fixture.sessionStore.readFailure = IllegalStateException("synthetic customer read fault")
            fixture.sdk.emit(CheckoutEvent.Completed)
        }
        composeRule.waitUntil(timeoutMillis = 5_000) { fixture.cart.state.value.status == CartStatus.RESTRICTED }
        composeRule.runOnIdle {
            assertEquals(CheckoutStatus.CLEANUP_REQUIRED, fixture.checkout.state.value.status)
            assertFalse(fixture.checkout.state.value.busy)
            assertNull(fixture.cart.state.value.cart)
            assertEquals(listOf(fixture.cartA), fixture.completionAttempts)
            fixture.sessionStore.readFailure = null
            fixture.cartStore.clearFailure = null
            if (replaceCart) fixture.installReplacement()
        }
        val expectedCurrent = fixture.cartStore.cart.takeIf { replaceCart }
        composeRule.onNodeWithTag(CartTestTags.CHECKOUT_FEEDBACK_ACTION).performScrollTo().assertIsDisplayed()
        clickCleanupFeedback()
        composeRule.runOnIdle {
            val expectedStatus = if (replaceCart) {
                CheckoutStatus.COMPLETED_CURRENT_CART_PRESERVED
            } else {
                CheckoutStatus.COMPLETED
            }
            assertEquals(expectedStatus, fixture.checkout.state.value.status)
            assertEquals(expectedCurrent, fixture.cartStore.cart)
            assertEquals(listOf(fixture.cartA, fixture.cartA), fixture.completionAttempts)
            assertEquals(1, fixture.sdk.presentations)
            assertEquals(0, fixture.gateway.merchandiseMutations)
        }
    }
}
