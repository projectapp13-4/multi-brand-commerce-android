@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
@file:Suppress("FunctionNaming")

package com.gurbakir.mobile.product

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.waitUntilExactlyOneExists
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.gurbakir.mobile.CoreTestTheme
import com.gurbakir.mobile.cart.CartActionAdjustment
import com.gurbakir.mobile.cart.CartActionKind
import com.gurbakir.mobile.cart.CartLifecycleAndroidFixture
import com.gurbakir.mobile.cart.CartMutationPlan
import com.gurbakir.mobile.cart.LIFECYCLE_VARIANT_A
import com.gurbakir.mobile.cart.LIFECYCLE_VARIANT_B
import com.gurbakir.mobile.core.R
import com.gurbakir.mobile.performDeterministicClick
import com.gurbakir.storefront.CartLineInput
import java.math.BigDecimal
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProductCartAttributionScreenTest {
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
    fun completedAWhileBIsSelectedDoesNotConfirmBOrOfferConfirmationOpenCart() {
        showProduct()
        submitA()
        selectLarge()
        settleA(3)

        assertNoAttribution()
        assertSelectedB()
        composeRule.runOnIdle {
            assertEquals(3, fixture.cart.state.value.cart?.totalQuantity)
            assertNull(fixture.cart.state.value.adjustment)
        }
    }

    @Test
    fun adjustedAWhileBIsSelectedKeepsCartTruthWithoutAttributingAdjustmentToB() {
        showProduct()
        submitA()
        selectLarge()
        settleA(4)

        assertNoAttribution()
        assertSelectedB()
        composeRule.runOnIdle {
            assertEquals(4, fixture.cart.state.value.cart?.totalQuantity)
            assertEquals(adjustment(), fixture.cart.state.value.adjustment)
        }
    }

    @Test
    fun returningToAWhileItsOldSubmissionIsPendingDoesNotReviveAttribution() {
        showProduct()
        submitA()
        selectLarge()
        selectSize("Small")
        settleA(3)

        assertNoAttribution()
        composeRule.onNodeWithTag(ProductDetailTestTags.option("Size", "Small")).assertIsSelected()
        composeRule.runOnIdle {
            assertEquals(LIFECYCLE_VARIANT_A, fixture.product.state.value.selectedVariant?.id)
            assertEquals(3, fixture.cart.state.value.cart?.totalQuantity)
        }
    }

    @Test
    fun sameSelectionCompletedActionShowsLocalizedConfirmationAndOpenCart() {
        showProduct()
        submitA()
        settleA(3)

        assertFeedback(R.string.product_added_to_cart)
        composeRule.runOnIdle {
            assertEquals(ProductCartFeedback.ADDED, fixture.product.state.value.cartFeedback)
            assertNull(fixture.product.state.value.cartAdjustment)
            assertEquals(3, fixture.cart.state.value.cart?.totalQuantity)
        }
    }

    @Test
    fun sameSelectionAdjustedActionShowsExactAdjustmentAndOpenCart() {
        showProduct()
        submitA()
        settleA(4)

        assertFeedback(R.string.product_cart_adjusted)
        composeRule.runOnIdle {
            assertEquals(ProductCartFeedback.ADJUSTED, fixture.product.state.value.cartFeedback)
            assertEquals(adjustment(), fixture.product.state.value.cartAdjustment)
            assertEquals(adjustment(), fixture.cart.state.value.adjustment)
            assertEquals(4, fixture.cart.state.value.cart?.totalQuantity)
        }
    }

    private fun showProduct() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync { fixture = CartLifecycleAndroidFixture() }
        composeRule.setContent {
            CoreTestTheme(darkTheme = false) {
                val state by fixture.product.state.collectAsState()
                ProductDetailScreen(state, fixture.productActions())
            }
        }
        composeRule.waitUntilExactlyOneExists(hasTestTag(ProductDetailTestTags.ROOT), timeoutMillis = 5_000)
        composeRule.waitUntil(5_000) { fixture.product.state.value.purchaseIntent != null }
    }

    private fun submitA() {
        composeRule.onNodeWithTag(ProductDetailTestTags.ADD_TO_CART).assertIsDisplayed().assertIsEnabled()
            .performDeterministicClick()
        composeRule.waitUntil(5_000) { fixture.operations.started.isCompleted }
        composeRule.runOnIdle {
            assertEquals(
                listOf(CartMutationPlan.Add(listOf(CartLineInput(LIFECYCLE_VARIANT_A, 1)))),
                fixture.operations.submitted
            )
        }
    }

    private fun selectLarge() {
        selectSize("Large")
        assertSelectedB()
    }

    private fun selectSize(size: String) {
        val tag = ProductDetailTestTags.option("Size", size)
        composeRule.onNodeWithTag(ProductDetailTestTags.CONTENT).performScrollToNode(hasTestTag(tag))
        composeRule.onNodeWithTag(tag).performScrollTo().assertIsDisplayed().assertIsEnabled()
            .performDeterministicClick()
        composeRule.waitForIdle()
    }

    private fun assertSelectedB() {
        composeRule.onNodeWithTag(ProductDetailTestTags.option("Size", "Large")).assertIsSelected()
        composeRule.runOnIdle {
            val selected = fixture.product.state.value.selectedVariant
            assertEquals(LIFECYCLE_VARIANT_B, selected?.id)
            assertEquals(BigDecimal("200.00"), selected?.price?.amount)
        }
    }

    private fun settleA(quantity: Int) {
        composeRule.runOnIdle { fixture.operations.completeMutation(quantity) }
        composeRule.waitUntil(5_000) { !fixture.product.state.value.addingToCart }
        composeRule.onNodeWithTag(ProductDetailTestTags.ADD_TO_CART).assertIsEnabled()
        composeRule.runOnIdle {
            assertEquals(1, fixture.operations.submitted.size)
            assertEquals(1, fixture.operations.restoreCount)
        }
    }

    private fun assertNoAttribution() {
        composeRule.onAllNodesWithTag(ProductDetailTestTags.CART_FEEDBACK).assertCountEquals(0)
        composeRule.onAllNodesWithTag(ProductDetailTestTags.OPEN_CART).assertCountEquals(0)
        composeRule.runOnIdle {
            assertNull(fixture.product.state.value.cartFeedback)
            assertNull(fixture.product.state.value.cartFailure)
            assertNull(fixture.product.state.value.cartAdjustment)
        }
    }

    private fun assertFeedback(resource: Int) {
        val expected = InstrumentationRegistry.getInstrumentation().targetContext.getString(resource)
        composeRule.onNodeWithTag(ProductDetailTestTags.CART_FEEDBACK).assertIsDisplayed().assertTextEquals(expected)
        composeRule.onNodeWithTag(ProductDetailTestTags.OPEN_CART).assertIsDisplayed().assertIsEnabled()
    }

    private fun adjustment() = CartActionAdjustment(CartActionKind.ADD, 2L, 3L, 4L)
}
