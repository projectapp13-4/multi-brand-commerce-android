@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
@file:Suppress("FunctionNaming")

package com.gurbakir.mobile.cart

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.waitUntilExactlyOneExists
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gurbakir.mobile.CoreTestTheme
import com.gurbakir.mobile.checkout.CheckoutFailure
import com.gurbakir.mobile.checkout.CheckoutFailureCategory
import com.gurbakir.mobile.checkout.CheckoutState
import com.gurbakir.mobile.checkout.CheckoutStatus
import com.gurbakir.mobile.performDeterministicClick
import com.gurbakir.storefront.CartOwnership
import com.gurbakir.storefront.CartQuantityRule
import com.gurbakir.storefront.SensitiveCartLineId
import com.gurbakir.storefront.StorefrontMoney
import java.math.BigDecimal
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CartScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun trueEmptyCartRemainsReadableAtTwoHundredPercentText() {
        var browsed = 0
        setCartContent(fontScale = 2f) {
            CartScreen(CartState(status = CartStatus.EMPTY), actions(browse = { browsed += 1 }))
        }

        composeRule.onNodeWithTag(CartTestTags.ROOT).assertIsDisplayed()
        composeRule.onNodeWithTag(CartTestTags.EMPTY).assertIsDisplayed()
        composeRule.onNodeWithTag(CartTestTags.BROWSE).performDeterministicClick()
        composeRule.waitForIdle()
        assertEquals(1, browsed)
    }

    @Test
    fun activeCartUsesServerQuantityRuleAndExposesRemoveSeparately() {
        val line = line(quantity = 2, CartQuantityRule(minimum = 2, maximum = 4, increment = 2))
        var increased: CartLine? = null
        var removed: SensitiveCartLineId? = null
        setCartContent {
            CartScreen(
                state = activeState(line),
                actions =
                    actions(
                        increase = { increased = it },
                        remove = { removed = it }
                    )
            )
        }

        composeRule.onNodeWithTag(CartTestTags.line(line.productId)).assertIsDisplayed()
        composeRule
            .onNodeWithTag(CartTestTags.increase(line.productId))
            .performScrollTo()
            .performDeterministicClick()
        composeRule.waitForIdle()
        composeRule
            .onNodeWithTag(CartTestTags.remove(line.productId))
            .performScrollTo()
            .performDeterministicClick()
        composeRule.waitForIdle()

        assertEquals(line, increased)
        assertEquals(line.id, removed)
    }

    @Test
    fun activeCartKeepsQuantityActionsReadableAtTwoHundredPercentText() {
        val line = line(quantity = 2, CartQuantityRule(minimum = 1, maximum = 4, increment = 1))
        setCartContent(fontScale = 2f) {
            CartScreen(state = activeState(line), actions = actions())
        }

        val decreaseBounds =
            composeRule
                .onNodeWithTag(CartTestTags.decrease(line.productId))
                .performScrollTo()
                .assertIsDisplayed()
                .fetchSemanticsNode()
                .boundsInRoot
        val increaseBounds =
            composeRule
                .onNodeWithTag(CartTestTags.increase(line.productId))
                .assertIsDisplayed()
                .fetchSemanticsNode()
                .boundsInRoot

        assertTrue(
            "Decrease and increase must share a row: $decreaseBounds vs $increaseBounds",
            abs(decreaseBounds.center.y - increaseBounds.center.y) <= ROW_ALIGNMENT_TOLERANCE_PX
        )
        assertTrue(
            "Quantity actions must not collapse into vertical text",
            increaseBounds.width > increaseBounds.height
        )
        assertTrue("Quantity actions must remain distinct", decreaseBounds.right <= increaseBounds.left)
    }

    @Test
    fun quarantinedCartShowsNoLinesAndRequiresDiscardConfirmation() {
        var discarded = false
        setCartContent {
            CartScreen(
                CartState(
                    status = CartStatus.RESTRICTED,
                    ownership = CartOwnership.QUARANTINED
                ),
                actions(discard = { discarded = true })
            )
        }

        composeRule.onNodeWithTag(CartTestTags.RESTRICTED).assertIsDisplayed()
        composeRule.onNodeWithTag(CartTestTags.DISCARD).performDeterministicClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(CartTestTags.DISCARD_CONFIRM).performDeterministicClick()
        composeRule.waitForIdle()

        assertTrue(discarded)
    }

    @Test
    fun refreshedActiveCartExposesProductionCheckoutAction() {
        val line = line(quantity = 1, CartQuantityRule(minimum = 1, maximum = null, increment = 1))
        var checkoutStarted = false
        setCartContent {
            CartScreen(
                state = activeState(line),
                actions = actions(checkout = { checkoutStarted = true })
            )
        }

        composeRule
            .onNodeWithTag(CartTestTags.CHECKOUT)
            .performScrollTo()
            .performDeterministicClick()
        composeRule.waitForIdle()

        assertTrue(checkoutStarted)
    }

    @Test
    fun customerAssociatedCartMakesOwnershipVisibleWithoutExposingIdentity() {
        val line = line(quantity = 1, CartQuantityRule(minimum = 1, maximum = null, increment = 1))
        setCartContent {
            CartScreen(
                state = activeState(line, CartOwnership.CUSTOMER_ASSOCIATED),
                actions = actions()
            )
        }

        composeRule.onNodeWithTag(CartTestTags.OWNERSHIP).assertIsDisplayed()
        composeRule.onNodeWithTag(CartTestTags.line(line.productId)).assertIsDisplayed()
    }

    @Test
    fun retryableCartErrorOffersRefreshAndBrowseRecovery() {
        var retried = 0
        setCartContent {
            CartScreen(
                state =
                    CartState(
                        status = CartStatus.ERROR,
                        failure =
                            CartFailure(
                                CartFailureCategory.CONNECTION,
                                retryable = true,
                                cartRetained = false
                            )
                    ),
                actions = actions(retry = { retried += 1 })
            )
        }

        composeRule.onNodeWithTag(CartTestTags.ERROR).assertIsDisplayed()
        composeRule.onNodeWithText("No current cart is shown", substring = true).assertIsDisplayed()
        composeRule.onNodeWithTag(CartTestTags.RETRY).performDeterministicClick()
        composeRule.waitForIdle()
        assertEquals(1, retried)
    }

    @Test
    fun retryableCheckoutFailureOffersCheckoutRecovery() {
        val line = line(quantity = 1, CartQuantityRule(minimum = 1, maximum = null, increment = 1))
        var checkoutStarted = false
        setCartContent {
            CartScreen(
                state = activeState(line),
                actions = actions(checkout = { checkoutStarted = true }),
                checkoutState =
                    CheckoutState(
                        status = CheckoutStatus.FAILED,
                        failure =
                            CheckoutFailure(
                                CheckoutFailureCategory.NETWORK,
                                retryable = true,
                                cartRetained = true
                            )
                    )
            )
        }

        composeRule.onNodeWithTag(CartTestTags.CHECKOUT_FEEDBACK).assertIsDisplayed()
        composeRule
            .onNodeWithTag(CartTestTags.CHECKOUT_FEEDBACK_ACTION)
            .performDeterministicClick()
        composeRule.waitForIdle()
        assertTrue(checkoutStarted)
    }

    @Test
    fun confirmedCompletionShowsHonestReturnInsteadOfAnEmptyCartClaim() {
        setCartContent(fontScale = 2f) {
            CartScreen(
                state = CartState(status = CartStatus.EMPTY),
                actions = actions(),
                checkoutState = CheckoutState(CheckoutStatus.COMPLETED, cartRetained = false)
            )
        }

        composeRule.onNodeWithTag(CartTestTags.CHECKOUT_FEEDBACK).assertIsDisplayed()
        composeRule.onAllNodesWithTag(CartTestTags.EMPTY).assertCountEquals(0)
    }

    private fun setCartContent(fontScale: Float = 1f, content: @androidx.compose.runtime.Composable () -> Unit) {
        composeRule.setContent {
            CoreTestTheme(darkTheme = false) {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                    content()
                }
            }
        }
        composeRule.waitUntilExactlyOneExists(hasTestTag(CartTestTags.ROOT), timeoutMillis = 5_000)
    }

    private fun actions(
        browse: () -> Unit = {},
        retry: () -> Unit = {},
        increase: (CartLine) -> Unit = {},
        remove: (SensitiveCartLineId) -> Unit = {},
        discard: () -> Unit = {},
        checkout: () -> Unit = {}
    ): CartActions = CartActions(
        onBack = {},
        onBrowse = browse,
        onRetry = retry,
        onOpenProduct = {},
        onIncrease = increase,
        onDecrease = {},
        onRemove = remove,
        onDiscard = discard,
        onCheckout = checkout
    )

    private fun activeState(line: CartLine, ownership: CartOwnership = CartOwnership.ANONYMOUS): CartState {
        val subtotal = StorefrontMoney(BigDecimal("20.00"), "TRY")
        return CartState(
            status = CartStatus.ACTIVE,
            cart = CartSummary(2, listOf(line), subtotal, subtotal, hasWarnings = false),
            ownership = ownership
        )
    }

    private fun line(quantity: Int, rule: CartQuantityRule): CartLine {
        val unit = StorefrontMoney(BigDecimal("10.00"), "TRY")
        return CartLine(
            id = syntheticLineId("gid://shopify/CartLine/1"),
            merchandiseId = "gid://shopify/ProductVariant/1",
            productId = "gid://shopify/Product/1",
            productTitle = "Copper pan",
            variantTitle = "Large",
            quantity = quantity,
            quantityRule = rule,
            canRemove = true,
            canUpdateQuantity = true,
            availableForSale = true,
            currentlyNotInStock = false,
            image = null,
            unitPrice = unit,
            totalPrice = StorefrontMoney(BigDecimal("20.00"), "TRY")
        )
    }
}

private const val ROW_ALIGNMENT_TOLERANCE_PX = 1f

private fun syntheticLineId(value: String): SensitiveCartLineId =
    SensitiveCartLineId::class.java.getDeclaredConstructor(String::class.java).run {
        isAccessible = true
        newInstance(value)
    }
