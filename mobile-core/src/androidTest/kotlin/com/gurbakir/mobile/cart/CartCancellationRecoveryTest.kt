@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
@file:Suppress("FunctionNaming")

package com.gurbakir.mobile.cart

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.waitUntilExactlyOneExists
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.gurbakir.mobile.CoreTestTheme
import com.gurbakir.mobile.core.R
import com.gurbakir.mobile.performDeterministicClick
import com.gurbakir.storefront.CartLineInput
import com.gurbakir.storefront.CartOwnership
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CartCancellationRecoveryTest {
    @get:Rule
    val composeRule = createComposeRule()
    private lateinit var fixture: CartLifecycleAndroidFixture

    @After
    fun clearOwnedViewModels() {
        if (::fixture.isInitialized) {
            InstrumentationRegistry.getInstrumentation().runOnMainSync { fixture.close() }
        }
    }

    @Test
    fun clearingProductOwnerReleasesRetainedCartAndRenderedRetryReadsCurrentTruth() {
        showCart()
        assertUsableCart(2)
        startProductAdd()
        cancelOnlyProductOwner()

        assertNoProgress()
        composeRule.onNodeWithText(text(R.string.cart_error_ambiguous)).assertIsDisplayed()
        composeRule.runOnIdle {
            assertEquals(CartFailureCategory.AMBIGUOUS_MUTATION, fixture.cart.state.value.failure?.category)
            assertEquals(CartOwnership.ANONYMOUS, fixture.cart.state.value.ownership)
            assertEquals(1, fixture.operations.restoreCount)
            assertEquals(0, fixture.operations.clearCount)
            assertNull(fixture.product.state.value.cartFeedback)
            assertNull(fixture.product.state.value.cartAdjustment)
            fixture.operations.setRestoredQuantity(3)
        }
        assertUsableCart(2)
        clickOnlyRetry()

        assertNoProgress()
        assertUsableCart(3)
        composeRule.onAllNodesWithText(text(R.string.cart_error_ambiguous)).assertCountEquals(0)
        composeRule.runOnIdle {
            assertEquals(2, fixture.operations.restoreCount)
            assertNull(fixture.cart.state.value.failure)
            assertSubmittedAOnce()
        }
    }

    @Test
    fun interruptedAssociatedOwnerHidesPrivateLinesUntilVerificationRetrySucceeds() {
        showCart(CartOwnership.CUSTOMER_ASSOCIATED)
        assertUsableCart(2)
        startProductAdd()
        cancelOnlyProductOwner()

        assertNoProgress()
        assertPrivateCartHidden()
        composeRule.runOnIdle {
            assertEquals(CartOwnership.CUSTOMER_ASSOCIATED, fixture.cart.state.value.ownership)
            assertEquals(1, fixture.operations.restoreCount)
            fixture.operations.requireVerification()
        }
        clickOnlyRetry()
        composeRule.onNodeWithTag(CartTestTags.RESTRICTED).assertIsDisplayed()
        assertPrivateCartHidden()
        composeRule.runOnIdle {
            assertEquals(CartOwnership.VERIFY_PENDING, fixture.cart.state.value.ownership)
            fixture.operations.setRestoredQuantity(3)
        }
        clickOnlyRetry()

        assertNoProgress()
        assertUsableCart(3)
        composeRule.onNodeWithTag(CartTestTags.OWNERSHIP).performScrollTo().assertIsDisplayed()
        composeRule.runOnIdle {
            assertEquals(CartOwnership.CUSTOMER_ASSOCIATED, fixture.cart.state.value.ownership)
            assertEquals(3, fixture.operations.restoreCount)
            assertEquals(0, fixture.operations.clearCount)
            assertNull(fixture.cart.state.value.failure)
            assertSubmittedAOnce()
        }
    }

    @Test
    fun normalProductCompletionKeepsRetainedCartUsableWithoutRecoveryFailure() {
        showCart()
        startProductAdd()
        composeRule.runOnIdle { fixture.operations.completeMutation(3) }
        composeRule.waitUntil(5_000) { !fixture.product.state.value.addingToCart }

        assertNoProgress()
        assertUsableCart(3)
        composeRule.runOnIdle {
            assertNull(fixture.cart.state.value.failure)
            assertEquals(1, fixture.operations.restoreCount)
            assertEquals(0, fixture.operations.clearCount)
            assertSubmittedAOnce()
        }
    }

    private fun showCart(ownership: CartOwnership = CartOwnership.ANONYMOUS) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            fixture = CartLifecycleAndroidFixture(ownership)
        }
        composeRule.setContent {
            CoreTestTheme(darkTheme = false) {
                val state by fixture.cart.state.collectAsState()
                CartScreen(state, fixture.cartActions())
            }
        }
        composeRule.waitUntilExactlyOneExists(hasTestTag(CartTestTags.ROOT), timeoutMillis = 5_000)
        composeRule.waitUntil(5_000) { fixture.cart.state.value.status == CartStatus.ACTIVE }
    }

    private fun startProductAdd() {
        composeRule.runOnIdle { fixture.product.addToCart() }
        composeRule.waitUntil(5_000) { fixture.operations.started.isCompleted }
        composeRule.onNodeWithTag(CartTestTags.CONTENT).performScrollToIndex(0)
        composeRule.onNodeWithTag(CartTestTags.LOADING).assertIsDisplayed()
        composeRule.runOnIdle { assertSubmittedAOnce() }
    }

    private fun cancelOnlyProductOwner() {
        composeRule.runOnIdle { fixture.productStore.clear() }
        composeRule.waitUntil(5_000) { fixture.operations.cancellationObserved }
    }

    private fun assertNoProgress() {
        composeRule.onNodeWithTag(CartTestTags.CONTENT).performScrollToIndex(0)
        composeRule.onAllNodesWithTag(CartTestTags.LOADING).assertCountEquals(0)
        composeRule.onAllNodesWithTag(CartTestTags.LOADING_STATE).assertCountEquals(0)
        composeRule.runOnIdle { assertNull(fixture.cart.state.value.mutation) }
    }

    private fun assertUsableCart(quantity: Int) {
        composeRule.onNodeWithTag(CartTestTags.CONTENT)
            .performScrollToNode(hasTestTag(CartTestTags.line(LIFECYCLE_PRODUCT_ID)))
        composeRule.onNodeWithText(text(R.string.cart_quantity, quantity)).performScrollTo().assertIsDisplayed()
        listOf(
            CartTestTags.increase(LIFECYCLE_PRODUCT_ID),
            CartTestTags.decrease(LIFECYCLE_PRODUCT_ID),
            CartTestTags.remove(LIFECYCLE_PRODUCT_ID),
            CartTestTags.CHECKOUT,
            CartTestTags.DISCARD
        ).forEach { tag ->
            composeRule.onNodeWithTag(CartTestTags.CONTENT).performScrollToNode(hasTestTag(tag))
            composeRule.onNodeWithTag(tag).performScrollTo().assertIsDisplayed().assertIsEnabled()
        }
        composeRule.runOnIdle { assertEquals(quantity, fixture.cart.state.value.cart?.totalQuantity) }
    }

    private fun assertPrivateCartHidden() {
        composeRule.onAllNodesWithTag(CartTestTags.line(LIFECYCLE_PRODUCT_ID)).assertCountEquals(0)
        composeRule.onAllNodesWithTag(CartTestTags.CHECKOUT).assertCountEquals(0)
        composeRule.runOnIdle { assertNull(fixture.cart.state.value.cart) }
    }

    private fun clickOnlyRetry() {
        composeRule.onNodeWithTag(CartTestTags.CONTENT).performScrollToIndex(0)
        val retry = text(R.string.retry)
        composeRule.onAllNodesWithText(retry).assertCountEquals(1)
        composeRule.onNodeWithText(retry).performScrollTo().assertIsDisplayed().assertIsEnabled()
            .performDeterministicClick()
        composeRule.waitForIdle()
    }

    private fun assertSubmittedAOnce() {
        assertEquals(
            listOf(CartMutationPlan.Add(listOf(CartLineInput(LIFECYCLE_VARIANT_A, 1)))),
            fixture.operations.submitted
        )
        assertTrue(fixture.productStore !== fixture.cartStore)
    }

    private fun text(resource: Int, vararg arguments: Any): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(resource, *arguments)
}
