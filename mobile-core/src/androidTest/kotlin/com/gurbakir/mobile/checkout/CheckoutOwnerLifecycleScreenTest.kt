package com.gurbakir.mobile.checkout

import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gurbakir.checkout.CheckoutEvent
import com.gurbakir.checkout.CheckoutFailure
import com.gurbakir.mobile.CoreTestTheme
import com.gurbakir.mobile.cart.CartActions
import com.gurbakir.mobile.cart.CartScreen
import com.gurbakir.mobile.cart.CartStatus
import com.gurbakir.mobile.cart.CartTestTags
import com.gurbakir.mobile.performDeterministicClick
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class CheckoutOwnerLifecycleScreenTest {
    @get:Rule val composeRule = createAndroidComposeRule<CheckoutOwnerProbeActivity>()
    private lateinit var fixture: CheckoutProtocolAndroidFixture
    private val checkoutStore = ViewModelStore()
    private lateinit var checkout: CheckoutViewModel

    @After
    fun clearRemainingViewModels() {
        composeRule.runOnIdle {
            checkoutStore.clear()
            if (::fixture.isInitialized) fixture.close()
        }
    }

    @Test
    fun realCheckoutStoreClearRetainsCartViewModelAndRendersUnconfirmedOutcomeRecovery() {
        showScreen()
        val original = fixture.cartStore.cart
        launchCheckout()
        composeRule.runOnIdle { checkoutStore.clear() }
        composeRule.waitUntil(timeoutMillis = 5_000) { checkout.state.value.status == CheckoutStatus.INTERRUPTED }
        composeRule.runOnIdle {
            assertFalse(checkout.state.value.busy)
            assertEquals(1, fixture.sdk.disposals)
            assertEquals(CartStatus.ACTIVE, fixture.cart.state.value.status)
            assertEquals(original, fixture.cartStore.cart)
            assertEquals(0, fixture.cartStore.clears)
            fixture.sdk.emit(CheckoutEvent.Completed)
            fixture.sdk.emit(CheckoutEvent.Cancelled)
            fixture.sdk.emit(CheckoutEvent.Failed(CheckoutFailure.FATAL))
            assertEquals(CheckoutStatus.INTERRUPTED, checkout.state.value.status)
        }
        composeRule.onNodeWithTag(CartTestTags.LOADING).assertDoesNotExist()
        composeRule.onNodeWithTag(CartTestTags.CONTENT)
            .performScrollToNode(hasTestTag(CartTestTags.CHECKOUT_FEEDBACK))
        composeRule.onNodeWithTag(CartTestTags.CHECKOUT_FEEDBACK).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag(CartTestTags.CONTENT)
            .performScrollToNode(hasTestTag(CartTestTags.CHECKOUT_FEEDBACK_ACTION))
        composeRule.onNodeWithTag(CartTestTags.CHECKOUT_FEEDBACK_ACTION).performDeterministicClick()
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            assertEquals(CartStatus.ACTIVE, fixture.cart.state.value.status)
            assertEquals(original, fixture.cartStore.cart)
            assertEquals(3, fixture.gateway.buyerRebinds.size)
            assertEquals(1, fixture.sdk.presentations)
            assertEquals(0, fixture.gateway.merchandiseMutations)
        }
    }

    @Test
    fun realCheckoutStoreClearAfterKnownCompletionDoesNotInventInterruption() {
        showScreen()
        launchCheckout()
        composeRule.runOnIdle { fixture.sdk.emit(CheckoutEvent.Completed) }
        composeRule.waitUntil(timeoutMillis = 5_000) { checkout.state.value.status == CheckoutStatus.COMPLETED }
        composeRule.runOnIdle {
            checkoutStore.clear()
            assertEquals(CheckoutStatus.COMPLETED, checkout.state.value.status)
            assertNull(fixture.cartStore.cart)
            assertEquals(1, fixture.cartStore.clears)
            assertEquals(1, fixture.sdk.disposals)
            assertEquals(1, fixture.sdk.presentations)
        }
        composeRule.onNodeWithTag(CartTestTags.CONTENT)
            .performScrollToNode(hasTestTag(CartTestTags.CHECKOUT_FEEDBACK))
        composeRule.onNodeWithTag(CartTestTags.CHECKOUT_FEEDBACK).assertIsDisplayed()
    }

    private fun showScreen() {
        composeRule.runOnIdle {
            fixture = CheckoutProtocolAndroidFixture()
            checkout = ViewModelProvider(
                checkoutStore,
                object : ViewModelProvider.Factory {
                    override fun <T : ViewModel> create(modelClass: Class<T>): T =
                        requireNotNull(modelClass.cast(CheckoutViewModel(fixture.controller)))
                }
            )[CheckoutViewModel::class.java]
        }
        composeRule.setContent {
            val cartState by fixture.cart.state.collectAsStateWithLifecycle()
            val checkoutState by checkout.state.collectAsStateWithLifecycle()
            CoreTestTheme {
                CartScreen(cartState, actions(), checkoutState)
            }
        }
        composeRule.waitUntil(timeoutMillis = 5_000) { fixture.cart.state.value.status == CartStatus.ACTIVE }
    }

    private fun launchCheckout() {
        composeRule.onNodeWithTag(CartTestTags.CONTENT).performScrollToNode(hasTestTag(CartTestTags.CHECKOUT))
        composeRule.onNodeWithTag(CartTestTags.CHECKOUT).performScrollTo().performDeterministicClick()
        composeRule.waitUntil(timeoutMillis = 5_000) { checkout.state.value.status == CheckoutStatus.IN_PROGRESS }
        composeRule.runOnIdle { assertEquals(1, fixture.sdk.presentations) }
    }

    private fun actions() = CartActions(
        onBack = {},
        onBrowse = {},
        onRetry = fixture.cart::refresh,
        onOpenProduct = {},
        onIncrease = fixture.cart::increase,
        onDecrease = fixture.cart::decrease,
        onRemove = fixture.cart::remove,
        onDiscard = fixture.cart::discard,
        onCheckout = { checkout.start(composeRule.activity) },
        onRetryCheckoutCleanup = checkout::retryCleanup
    )
}
