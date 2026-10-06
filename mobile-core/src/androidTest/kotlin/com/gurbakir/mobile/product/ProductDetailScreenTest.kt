@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
@file:Suppress("FunctionNaming")

package com.gurbakir.mobile.product

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.waitUntilExactlyOneExists
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.espresso.Espresso.pressBack
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.gurbakir.mobile.CoreTestTheme
import com.gurbakir.mobile.captureReviewScreenshot
import com.gurbakir.mobile.cart.CartActionAdjustment
import com.gurbakir.mobile.cart.CartActionKind
import com.gurbakir.mobile.core.R
import com.gurbakir.mobile.performDeterministicClick
import com.gurbakir.mobile.wishlist.WishlistMembershipUiState
import com.gurbakir.mobile.wishlist.WishlistTestTags
import com.gurbakir.storefront.StorefrontMedia
import com.gurbakir.storefront.StorefrontMoney
import com.gurbakir.storefront.StorefrontProductDetail
import com.gurbakir.storefront.StorefrontProductMedia
import com.gurbakir.storefront.StorefrontProductOption
import com.gurbakir.storefront.StorefrontProductOptionValue
import com.gurbakir.storefront.StorefrontProductVariant
import com.gurbakir.storefront.StorefrontSelectedOption
import java.math.BigDecimal
import java.net.URI
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProductDetailScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun correctedAddOffersCartReviewWithoutClaimingTheRequestedQuantityWasAdded() {
        var opened = 0
        setProductContent(fontScale = 2f) {
            ProductDetailScreen(
                ProductDetailUiState(
                    product = product(),
                    selectedOptions = mapOf("Size" to "Small", "Color" to "Red"),
                    cartFeedback = ProductCartFeedback.ADJUSTED,
                    cartAdjustment = CartActionAdjustment(CartActionKind.ADD, 1L, 3L, 2L)
                ),
                actions().copy(onOpenCart = { opened++ })
            )
        }

        val notice = InstrumentationRegistry.getInstrumentation().targetContext.getString(
            R.string.product_cart_adjusted
        )
        composeRule.onNodeWithTag(ProductDetailTestTags.CART_FEEDBACK).assertTextEquals(notice).assertIsDisplayed()
        composeRule.onNodeWithTag(ProductDetailTestTags.OPEN_CART).assertIsDisplayed().performDeterministicClick()
        composeRule.waitForIdle()
        assertEquals(1, opened)
    }

    @Test
    fun exactAddRetainsSuccessfulFeedbackAndCartReview() {
        var opened = 0
        setProductContent {
            ProductDetailScreen(
                ProductDetailUiState(
                    product = product(),
                    selectedOptions = mapOf("Size" to "Small", "Color" to "Red"),
                    cartFeedback = ProductCartFeedback.ADDED
                ),
                actions().copy(onOpenCart = { opened++ })
            )
        }

        val notice = InstrumentationRegistry.getInstrumentation().targetContext.getString(
            R.string.product_added_to_cart
        )
        composeRule.onNodeWithTag(ProductDetailTestTags.CART_FEEDBACK).assertTextEquals(notice).assertIsDisplayed()
        composeRule.onNodeWithTag(ProductDetailTestTags.OPEN_CART).assertIsDisplayed().performDeterministicClick()
        composeRule.waitForIdle()
        assertEquals(1, opened)
    }

    @Test
    fun incompletePurchaseShowsOneInstructionWithoutRepeatingThePriceRange() {
        setProductContent { ProductDetailScreen(ProductDetailUiState(product = product()), actions()) }
        composeRule.onNodeWithTag(ProductDetailTestTags.PURCHASE_PRICE).assertDoesNotExist()
        composeRule.onNodeWithTag(ProductDetailTestTags.PURCHASE_AVAILABILITY).assertIsDisplayed()
        composeRule.onNodeWithTag(ProductDetailTestTags.ADD_TO_CART).assertIsNotEnabled()
        composeRule.onNodeWithTag(ProductDetailTestTags.AVAILABILITY).assertDoesNotExist()
    }

    @Test
    fun soldOutProductExplainsUnavailabilityInsteadOfAskingForADisabledOption() {
        val original = product()
        val soldOut = original.copy(
            availableForSale = false,
            variants = original.variants.map { it.copy(availableForSale = false) }
        )
        setProductContent { ProductDetailScreen(ProductDetailUiState(product = soldOut), actions()) }
        val unavailable = InstrumentationRegistry.getInstrumentation().targetContext.getString(
            R.string.product_sold_out
        )
        composeRule.onNodeWithTag(ProductDetailTestTags.PURCHASE_AVAILABILITY).assertTextEquals(unavailable)
        composeRule.onNodeWithTag(ProductDetailTestTags.ADD_TO_CART).assertIsNotEnabled()
    }

    @Test
    fun clearingChoicesMakesAnotherSellableCombinationReachableWithoutEnablingSoldOutChoices() {
        var state by mutableStateOf(
            ProductDetailUiState(
                product = diagonalAvailabilityProduct(),
                selectedOptions = mapOf("Size" to "Small", "Color" to "Red")
            )
        )
        var cleared = 0
        setProductContent {
            ProductDetailScreen(
                state,
                actions(
                    selectOption = { name, value ->
                        if (VariantSelectionResolver.canSelect(
                                requireNotNull(state.product),
                                name,
                                value,
                                state.selectedOptions
                            )
                        ) {
                            state = state.copy(selectedOptions = state.selectedOptions + (name to value))
                        }
                    },
                    clearSelection = {
                        cleared += 1
                        state = state.copy(selectedOptions = emptyMap(), mediaIndex = 0)
                    }
                )
            )
        }
        val content = composeRule.onNodeWithTag(ProductDetailTestTags.CONTENT)
        content.performScrollToNode(hasTestTag(ProductDetailTestTags.option("Size", "Large")))
        composeRule.onNodeWithTag(ProductDetailTestTags.option("Size", "Large")).assertIsNotEnabled()
        content.performScrollToNode(hasTestTag(ProductDetailTestTags.option("Color", "Blue")))
        composeRule.onNodeWithTag(ProductDetailTestTags.option("Color", "Blue")).assertIsNotEnabled()

        content.performScrollToNode(hasTestTag(ProductDetailTestTags.CLEAR_SELECTION))
        composeRule.onNodeWithTag(ProductDetailTestTags.CLEAR_SELECTION).assertIsDisplayed()
            .assertIsEnabled().assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp)
            .performDeterministicClick()
        assertEquals(1, cleared)
        assertTrue(state.selectedOptions.isEmpty())
        composeRule.onNodeWithTag(ProductDetailTestTags.CLEAR_SELECTION).assertDoesNotExist()
        composeRule.onNodeWithTag(ProductDetailTestTags.ADD_TO_CART).assertIsNotEnabled()

        content.performScrollToNode(hasTestTag(ProductDetailTestTags.option("Size", "Large")))
        composeRule.onNodeWithTag(ProductDetailTestTags.option("Size", "Large")).assertIsEnabled()
            .performDeterministicClick()
        content.performScrollToNode(hasTestTag(ProductDetailTestTags.option("Color", "Blue")))
        composeRule.onNodeWithTag(ProductDetailTestTags.option("Color", "Blue")).assertIsEnabled()
            .performDeterministicClick()
        assertEquals("gid://shopify/ProductVariant/14", state.selectedVariant?.id)
        composeRule.onNodeWithTag(ProductDetailTestTags.ADD_TO_CART).assertIsEnabled()
    }

    @Test
    fun clearingChoicesIsHiddenForDefaultVariantsAndEntirelySoldOutProducts() {
        val original = product()
        val default = original.copy(
            options = listOf(option("default", "Title", "Default Title")),
            variants = listOf(
                original.variants.first().copy(
                    selectedOptions = listOf(StorefrontSelectedOption("Title", "Default Title"))
                )
            )
        )
        var state by mutableStateOf(
            ProductDetailUiState(
                product = default,
                selectedOptions = mapOf("Title" to "Default Title")
            )
        )
        setProductContent { ProductDetailScreen(state, actions()) }
        composeRule.onNodeWithTag(ProductDetailTestTags.CLEAR_SELECTION).assertDoesNotExist()

        composeRule.runOnIdle {
            state = ProductDetailUiState(
                product = original.copy(
                    availableForSale = false,
                    variants = original.variants.map { it.copy(availableForSale = false) }
                ),
                selectedOptions = mapOf("Size" to "Small", "Color" to "Red")
            )
        }
        composeRule.onNodeWithTag(ProductDetailTestTags.CLEAR_SELECTION).assertDoesNotExist()
        composeRule.onNodeWithTag(ProductDetailTestTags.ADD_TO_CART).assertIsNotEnabled()
    }

    @Test
    fun clearingChoicesRetainsItsTouchTargetButIsDisabledDuringCartMutationAtLargeText() {
        val state = ProductDetailUiState(
            product = diagonalAvailabilityProduct(),
            selectedOptions = mapOf("Size" to "Small", "Color" to "Red"),
            addingToCart = true
        )
        setProductContent(fontScale = 2f) { ProductDetailScreen(state, actions(clearSelection = {})) }
        composeRule.onNodeWithTag(ProductDetailTestTags.CONTENT)
            .performScrollToNode(hasTestTag(ProductDetailTestTags.CLEAR_SELECTION))
        composeRule.onNodeWithTag(ProductDetailTestTags.CLEAR_SELECTION).assertIsDisplayed()
            .assertIsNotEnabled().assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp)
    }

    @Test
    fun clearingChoicesIsHiddenWhenTheHostDoesNotBindTheOptionalAction() {
        val state = ProductDetailUiState(
            product = diagonalAvailabilityProduct(),
            selectedOptions = mapOf("Size" to "Small", "Color" to "Red")
        )
        setProductContent { ProductDetailScreen(state, actions()) }
        composeRule.onNodeWithTag(ProductDetailTestTags.CONTENT)
            .performScrollToNode(hasTestTag(ProductDetailTestTags.option("Size", "Small")))
        composeRule.onNodeWithTag(ProductDetailTestTags.CLEAR_SELECTION).assertDoesNotExist()
    }

    @Test
    fun optionsWrapAndUnavailableValuesKeepConciseDisabledAccessibleLabels() {
        val value = "An additional size for larger cookware"
        val original = product()
        val size = original.options.first().copy(
            values = original.options.first().values + StorefrontProductOptionValue("extra-size", value)
        )
        setProductContent {
            Box(Modifier.requiredWidth(320.dp)) {
                ProductDetailScreen(
                    ProductDetailUiState(product = original.copy(options = listOf(size) + original.options.drop(1))),
                    actions()
                )
            }
        }
        val list = composeRule.onNodeWithTag(ProductDetailTestTags.CONTENT)
        list.performScrollToNode(hasTestTag(ProductDetailTestTags.option("Size", value)))
        val first = composeRule.onNodeWithTag(ProductDetailTestTags.option("Size", "Small"))
            .assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val last = composeRule.onNodeWithTag(ProductDetailTestTags.option("Size", value))
            .assertIsDisplayed().assertIsNotEnabled()
            .assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
            .fetchSemanticsNode().boundsInRoot
        assertTrue("The additional option must wrap into a fully visible row", last.top >= first.bottom)
        list.performScrollToNode(hasTestTag(ProductDetailTestTags.option("Color", "Blue")))
        composeRule.onNodeWithTag(ProductDetailTestTags.option("Color", "Blue")).assertIsNotEnabled()
        val unavailable = InstrumentationRegistry.getInstrumentation().targetContext.getString(
            R.string.product_unavailable
        )
        composeRule.onNodeWithTag(ProductDetailTestTags.option("Color", "Blue"))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, unavailable))
        composeRule.onNodeWithText("Blue").assertIsDisplayed()
    }

    @Test
    fun swipingGalleryChangesTheIndexedMediaWithoutOpeningTheModal() {
        val original = product()
        val next = original.media.single().copy(
            id = "gid://shopify/MediaImage/22",
            image = media().copy(uri = URI("https://cdn.shopify.com/s/files/1/product-next.jpg"))
        )
        var state by mutableStateOf(ProductDetailUiState(product = original.copy(media = original.media + next)))
        setProductContent {
            ProductDetailScreen(state, actions(selectMedia = { state = state.copy(mediaIndex = it) }))
        }
        composeRule.onNodeWithTag(ProductDetailTestTags.MEDIA_OPEN).performTouchInput { swipeLeft() }
        composeRule.waitForIdle()
        assertEquals(1, state.mediaIndex)
        composeRule.onNodeWithTag(ProductDetailTestTags.MEDIA_VIEWER).assertDoesNotExist()
    }

    @Test
    fun accessibleMediaRequestInterruptsAControlledFlingAndDisplaysTheRequestedImage() {
        val original = product()
        val galleryMedia = (0..2).map { index ->
            original.media.single().copy(
                id = "gid://shopify/MediaImage/${21 + index}",
                image = media().copy(
                    uri = URI("https://cdn.shopify.com/s/files/1/product-$index.jpg"),
                    altText = "Product view $index"
                )
            )
        }
        var state by mutableStateOf(ProductDetailUiState(product = original.copy(media = galleryMedia), mediaIndex = 1))
        val mediaRequests = mutableListOf<Int>()
        var arrowRequest: Int? = null
        setProductContent {
            ProductDetailScreen(
                state,
                actions(selectMedia = {
                    mediaRequests += it
                    state = state.copy(mediaIndex = it)
                })
            )
        }
        composeRule.onNodeWithContentDescription("Product view 1", useUnmergedTree = true).assertIsDisplayed()
        composeRule.mainClock.autoAdvance = false
        try {
            composeRule.onNodeWithTag(ProductDetailTestTags.MEDIA_OPEN)
                .performTouchInput {
                    swipeLeft(startX = width * 0.75f, endX = width * 0.5f, durationMillis = 100)
                }
            composeRule.mainClock.advanceTimeByFrame()
            assertEquals("The controlled partial fling must retain its starting selection", 1, state.mediaIndex)
            val viewport = composeRule.onNodeWithTag(ProductDetailTestTags.MEDIA_OPEN).fetchSemanticsNode().boundsInRoot
            val movingImage = composeRule.onNodeWithContentDescription("Product view 1", useUnmergedTree = true)
                .assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            assertTrue(
                "The image must still be displaced when the external request arrives",
                movingImage.right < viewport.right - 1f
            )
            composeRule.onNodeWithTag(ProductDetailTestTags.MEDIA_PREVIOUS)
                .performSemanticsAction(SemanticsActions.OnClick) { click ->
                    val before = mediaRequests.size
                    click()
                    assertEquals("Previous must synchronously issue one media request", before + 1, mediaRequests.size)
                    arrowRequest = mediaRequests.last()
                }
            assertEquals("Previous must request the page before the original selection", 0, arrowRequest)
            composeRule.mainClock.advanceTimeByFrame()
            composeRule.mainClock.advanceTimeBy(1_000)
        } finally {
            composeRule.mainClock.autoAdvance = true
        }
        composeRule.waitForIdle()
        assertEquals(0, state.mediaIndex)
        val visible = composeRule.onNodeWithContentDescription("Product view 0", useUnmergedTree = true)
            .assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val viewport = composeRule.onNodeWithTag(ProductDetailTestTags.MEDIA_OPEN).fetchSemanticsNode().boundsInRoot
        assertEquals(viewport.left, visible.left, 1f)
        assertEquals(viewport.right, visible.right, 1f)
    }

    @Test
    fun wishlistOffCompactProductHasNoHeart() {
        setProductContent { ProductDetailScreen(ProductDetailUiState(product = product()), actions()) }
        composeRule.onNodeWithTag(ProductDetailTestTags.MEDIA).assertIsDisplayed()
        composeRule.onNodeWithTag(ProductDetailTestTags.CONTENT)
            .performScrollToNode(hasTestTag(ProductDetailTestTags.TITLE))
        composeRule.onNodeWithTag(ProductDetailTestTags.TITLE).assertIsDisplayed()
        composeRule.onNodeWithTag(WishlistTestTags.toggle(product().id), useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithTag(ProductDetailTestTags.ADD_TO_CART).assertIsDisplayed()
    }

    @Test
    fun wishlistOffExpandedProductHasNoHeart() {
        composeRule.setContent {
            CoreTestTheme {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density / 3f, 1f)) {
                    ProductDetailScreen(ProductDetailUiState(product = product()), actions())
                }
            }
        }
        composeRule.onNodeWithTag(ProductDetailTestTags.TITLE).assertIsDisplayed()
        composeRule.onNodeWithTag(WishlistTestTags.toggle(product().id), useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithTag(ProductDetailTestTags.ADD_TO_CART).assertIsDisplayed()
    }

    @Test
    fun exactOptionSelectionUpdatesPriceAvailabilityAndEnablesCartAction() {
        var state by mutableStateOf(ProductDetailUiState(product = product()))
        setProductContent {
            ProductDetailScreen(
                state = state,
                actions =
                    actions(
                        selectOption = { name, value ->
                            state = state.copy(selectedOptions = state.selectedOptions + (name to value))
                        }
                    )
            )
        }

        val detailList = composeRule.onNodeWithTag(ProductDetailTestTags.CONTENT)
        detailList.performScrollToNode(hasTestTag(ProductDetailTestTags.option("Size", "Small")))
        composeRule.waitForIdle()
        composeRule
            .onNodeWithTag(ProductDetailTestTags.option("Size", "Small"))
            .performDeterministicClick()
        composeRule.waitForIdle()
        detailList.performScrollToNode(hasTestTag(ProductDetailTestTags.option("Color", "Red")))
        composeRule.waitForIdle()
        composeRule
            .onNodeWithTag(ProductDetailTestTags.option("Color", "Red"))
            .performDeterministicClick()
        composeRule.waitForIdle()

        detailList.performScrollToNode(hasTestTag(ProductDetailTestTags.PRICE))
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(ProductDetailTestTags.PRICE).assertIsDisplayed()
        composeRule.onNodeWithTag(ProductDetailTestTags.AVAILABILITY).assertDoesNotExist()
        composeRule.onNodeWithTag(ProductDetailTestTags.ADD_TO_CART).assertIsDisplayed().assertIsEnabled()
        composeRule.onNodeWithTag(ProductDetailTestTags.PURCHASE_PRICE).assertIsDisplayed()
        composeRule.onNodeWithTag(ProductDetailTestTags.PURCHASE_AVAILABILITY).assertDoesNotExist()
        assertEquals("gid://shopify/ProductVariant/11", state.selectedVariant?.id)
    }

    @Test
    fun approvedImageOpensEdgeToEdgeViewerAndSystemBackRestoresFocus() {
        var state by mutableStateOf(ProductDetailUiState(product = product()))
        setProductContent {
            ProductDetailScreen(
                state = state,
                actions =
                    actions(
                        selectMedia = { state = state.copy(mediaIndex = it) },
                        openMedia = { state = state.copy(mediaViewerOpen = true) },
                        closeMedia = { state = state.copy(mediaViewerOpen = false) }
                    )
            )
        }

        val detailList = composeRule.onNodeWithTag(ProductDetailTestTags.CONTENT)
        detailList.performScrollToNode(hasTestTag(ProductDetailTestTags.MEDIA_OPEN))
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(ProductDetailTestTags.MEDIA_OPEN).performDeterministicClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(ProductDetailTestTags.MEDIA_VIEWER).assertIsDisplayed()
        composeRule.onNodeWithTag(ProductDetailTestTags.MEDIA_CLOSE).assertIsDisplayed()
        pressBack()
        composeRule.waitForIdle()
        composeRule.onAllNodesWithTag(ProductDetailTestTags.MEDIA_VIEWER).assertCountEquals(0)
        composeRule.onNodeWithTag(ProductDetailTestTags.MEDIA_OPEN).assertIsFocused()
        assertEquals(0, state.mediaIndex)
    }

    @Test
    fun compactHierarchyKeepsPurchaseBarVisibleWhileProductContentScrolls() {
        setProductContent { ProductDetailScreen(ProductDetailUiState(product = product()), actions()) }

        composeRule.onNodeWithTag(ProductDetailTestTags.MEDIA).assertIsDisplayed()
        composeRule.onNodeWithTag(ProductDetailTestTags.PURCHASE_BAR).assertIsDisplayed()
        composeRule
            .onNodeWithTag(ProductDetailTestTags.CONTENT)
            .performScrollToNode(hasTestTag(ProductDetailTestTags.TITLE))
        composeRule.onNodeWithTag(ProductDetailTestTags.TITLE).assertIsDisplayed()
        composeRule
            .onNodeWithTag(ProductDetailTestTags.CONTENT)
            .performScrollToNode(hasTestTag(ProductDetailTestTags.PRICE))
        composeRule.onNodeWithTag(ProductDetailTestTags.PRICE).assertIsDisplayed()

        composeRule
            .onNodeWithTag(ProductDetailTestTags.CONTENT)
            .performScrollToNode(hasTestTag(ProductDetailTestTags.DESCRIPTION))
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(ProductDetailTestTags.PURCHASE_BAR).assertIsDisplayed()
        composeRule.onNodeWithTag(ProductDetailTestTags.ADD_TO_CART).assertIsDisplayed()
    }

    @Test
    fun compactScrollableViewportKeepsAFullOptionTouchTargetAboveThePurchaseDock() {
        assertCompactOptionRemainsAbovePurchaseDock(fontScale = 1f)
    }

    @Test
    fun largeTextCompactViewportKeepsAFullOptionTouchTargetAboveThePurchaseDock() {
        assertCompactOptionRemainsAbovePurchaseDock(fontScale = 2f)
    }

    @Test
    fun purchaseBarStacksInstructionAndActionAtTwoHundredPercentText() {
        setProductContent(fontScale = 2f) {
            ProductDetailScreen(ProductDetailUiState(product = product()), actions())
        }

        composeRule.onNodeWithTag(ProductDetailTestTags.PURCHASE_PRICE).assertDoesNotExist()
        val availabilityBounds =
            composeRule
                .onNodeWithTag(ProductDetailTestTags.PURCHASE_AVAILABILITY)
                .assertIsDisplayed()
                .fetchSemanticsNode()
                .boundsInRoot
        val actionBounds =
            composeRule
                .onNodeWithTag(ProductDetailTestTags.ADD_TO_CART)
                .assertIsDisplayed()
                .fetchSemanticsNode()
                .boundsInRoot
        assertTrue(
            "The action must stack below its disabled-state explanation: " +
                "instruction=$availabilityBounds action=$actionBounds",
            availabilityBounds.bottom <= actionBounds.top
        )
    }

    @Test
    fun productCanBeSavedLocallyWithoutSubmittingCartAction() {
        val product = product()
        var desiredSaved: Boolean? = null
        setProductContent {
            ProductDetailScreen(
                state = ProductDetailUiState(product = product),
                actions = actions(setWishlist = { _, saved -> desiredSaved = saved }),
                wishlist = WishlistMembershipUiState()
            )
        }

        composeRule
            .onNodeWithTag(WishlistTestTags.toggle(product.id))
            .assertIsDisplayed()
            .performDeterministicClick()
        composeRule.waitForIdle()

        assertEquals(true, desiredSaved)
        composeRule.onNodeWithTag(ProductDetailTestTags.ADD_TO_CART).assertIsDisplayed()
    }

    @Test
    fun notFoundRecoveryRemainsReadableAtTwoHundredPercentText() {
        setProductContent(fontScale = 2f) {
            ProductDetailScreen(ProductDetailUiState(notFound = true), actions())
        }

        composeRule.onNodeWithTag(ProductDetailTestTags.NOT_FOUND).assertIsDisplayed()
    }

    private fun setProductContent(fontScale: Float = 1f, content: @androidx.compose.runtime.Composable () -> Unit) {
        composeRule.setContent {
            CoreTestTheme(darkTheme = false) {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                    content()
                }
            }
        }
        composeRule.waitUntilExactlyOneExists(hasTestTag(ProductDetailTestTags.ROOT), timeoutMillis = 5_000)
    }

    private fun assertCompactOptionRemainsAbovePurchaseDock(fontScale: Float) {
        var selected: Pair<String, String>? = null
        setProductContent(fontScale = fontScale) {
            Box(Modifier.requiredWidth(360.dp)) {
                ProductDetailScreen(
                    ProductDetailUiState(product = product()),
                    actions(selectOption = { name, value -> selected = name to value })
                )
            }
        }
        val list = composeRule.onNodeWithTag(ProductDetailTestTags.CONTENT)
        val viewport = list.fetchSemanticsNode().boundsInRoot
        val dock = composeRule.onNodeWithTag(ProductDetailTestTags.PURCHASE_BAR).fetchSemanticsNode().boundsInRoot
        assertTrue("The scrollable viewport must end above the purchase dock", viewport.bottom <= dock.top + 1f)
        val optionTag = ProductDetailTestTags.option("Size", "Small")
        list.performScrollToNode(hasTestTag(optionTag))
        val option = composeRule.onNodeWithTag(optionTag).performScrollTo().assertIsDisplayed()
            .assertIsEnabled().assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp)
        val bounds = option.fetchSemanticsNode().boundsInRoot
        assertTrue("The complete option must be inside the visible scroll viewport", bounds.top >= viewport.top - 1f)
        assertTrue(
            "The complete option touch target must remain above the purchase dock",
            bounds.bottom <= dock.top + 1f
        )
        captureReviewScreenshot("product-option-above-dock-font-$fontScale", composeRule)
        option.performClick()
        assertEquals("Size" to "Small", selected)
    }

    @Suppress("LongParameterList")
    private fun actions(
        selectOption: (String, String) -> Unit = { _, _ -> },
        selectMedia: (Int) -> Unit = {},
        openMedia: () -> Unit = {},
        closeMedia: () -> Unit = {},
        setWishlist: (String, Boolean) -> Unit = { _, _ -> },
        clearSelection: (() -> Unit)? = null
    ) = ProductDetailActions(
        onBack = {},
        onBrowse = {},
        onRetry = {},
        onSelectOption = selectOption,
        onSelectMedia = selectMedia,
        onOpenMediaViewer = openMedia,
        onCloseMediaViewer = closeMedia,
        onClearSelection = clearSelection,
        onSetWishlist = setWishlist
    )

    private fun diagonalAvailabilityProduct(): StorefrontProductDetail {
        val original = product()
        val large = original.variants.last()
        return original.copy(
            variants = original.variants.map { variant ->
                if (variant.id == large.id) variant.copy(availableForSale = false) else variant
            } + large.copy(
                id = "gid://shopify/ProductVariant/14",
                title = "Large / Blue",
                selectedOptions = listOf(
                    StorefrontSelectedOption("Size", "Large"),
                    StorefrontSelectedOption("Color", "Blue")
                )
            )
        )
    }

    private fun product(): StorefrontProductDetail = StorefrontProductDetail(
        id = "gid://shopify/Product/1",
        handle = "copper-pan",
        title = "Copper pan",
        description = "Handmade copper pan.",
        availableForSale = true,
        options =
            listOf(
                option("1", "Size", "Small", "Large"),
                option("2", "Color", "Red", "Blue")
            ),
        variants =
            listOf(
                variant("11", "Small", "Red", true, "100.00"),
                variant("12", "Small", "Blue", false, "110.00"),
                variant("13", "Large", "Red", true, "120.00")
            ),
        media = listOf(StorefrontProductMedia("gid://shopify/MediaImage/21", media()))
    )

    private fun option(id: String, name: String, vararg values: String): StorefrontProductOption =
        StorefrontProductOption(
            id = "gid://shopify/ProductOption/$id",
            name = name,
            values =
                values.mapIndexed { index, value ->
                    StorefrontProductOptionValue("gid://shopify/ProductOptionValue/$id$index", value)
                }
        )

    private fun variant(
        id: String,
        size: String,
        color: String,
        available: Boolean,
        amount: String
    ): StorefrontProductVariant = StorefrontProductVariant(
        id = "gid://shopify/ProductVariant/$id",
        title = "$size / $color",
        availableForSale = available,
        currentlyNotInStock = false,
        price = StorefrontMoney(BigDecimal(amount), "TRY"),
        compareAtPrice = null,
        image = null,
        selectedOptions =
            listOf(
                StorefrontSelectedOption("Size", size),
                StorefrontSelectedOption("Color", color)
            )
    )

    private fun media(): StorefrontMedia = StorefrontMedia(
        URI("https://cdn.shopify.com/s/files/1/product.jpg"),
        "Copper pan",
        600,
        800
    )
}
