@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
@file:Suppress("FunctionNaming")

package com.gurbakir.mobile.product

import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.waitUntilExactlyOneExists
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.espresso.Espresso.pressBack
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gurbakir.mobile.CoreTestTheme
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
    fun incompletePurchaseShowsOneInstructionWithoutRepeatingThePriceRange() {
        setProductContent { ProductDetailScreen(ProductDetailUiState(product = product()), actions()) }
        composeRule.onNodeWithTag(ProductDetailTestTags.PURCHASE_PRICE).assertDoesNotExist()
        composeRule.onNodeWithTag(ProductDetailTestTags.PURCHASE_AVAILABILITY).assertIsDisplayed()
        composeRule.onNodeWithTag(ProductDetailTestTags.ADD_TO_CART).assertIsNotEnabled()
        composeRule.onNodeWithTag(ProductDetailTestTags.AVAILABILITY).assertDoesNotExist()
    }

    @Test
    fun optionsWrapAndUnavailableValuesKeepConciseDisabledAccessibleLabels() {
        val value = "An additional size"
        val original = product()
        val size = original.options.first().copy(
            values = original.options.first().values + StorefrontProductOptionValue("extra-size", value)
        )
        setProductContent {
            ProductDetailScreen(
                ProductDetailUiState(product = original.copy(options = listOf(size) + original.options.drop(1))),
                actions()
            )
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

    @Suppress("LongParameterList")
    private fun actions(
        selectOption: (String, String) -> Unit = { _, _ -> },
        selectMedia: (Int) -> Unit = {},
        openMedia: () -> Unit = {},
        closeMedia: () -> Unit = {},
        setWishlist: (String, Boolean) -> Unit = { _, _ -> }
    ) = ProductDetailActions(
        onBack = {},
        onBrowse = {},
        onRetry = {},
        onSelectOption = selectOption,
        onSelectMedia = selectMedia,
        onOpenMediaViewer = openMedia,
        onCloseMediaViewer = closeMedia,
        onSetWishlist = setWishlist
    )

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
