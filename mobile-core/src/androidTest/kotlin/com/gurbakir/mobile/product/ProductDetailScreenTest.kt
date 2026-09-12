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
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
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
    fun wishlistOffCompactProductHasNoHeart() {
        setProductContent { ProductDetailScreen(ProductDetailUiState(product = product()), actions()) }
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
        composeRule.onNodeWithTag(ProductDetailTestTags.AVAILABILITY).assertIsDisplayed()
        composeRule.onNodeWithTag(ProductDetailTestTags.ADD_TO_CART).assertIsDisplayed()
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

        val titleBounds =
            composeRule.onNodeWithTag(ProductDetailTestTags.TITLE).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val priceBounds =
            composeRule.onNodeWithTag(ProductDetailTestTags.PRICE).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val mediaBounds =
            composeRule.onNodeWithTag(ProductDetailTestTags.MEDIA).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertTrue("Product title must precede price", titleBounds.top < priceBounds.top)
        assertTrue("Price must precede product media", priceBounds.top < mediaBounds.top)

        composeRule
            .onNodeWithTag(ProductDetailTestTags.CONTENT)
            .performScrollToNode(hasTestTag(ProductDetailTestTags.DESCRIPTION))
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(ProductDetailTestTags.PURCHASE_BAR).assertIsDisplayed()
        composeRule.onNodeWithTag(ProductDetailTestTags.ADD_TO_CART).assertIsDisplayed()
    }

    @Test
    fun purchaseBarStacksPriceAvailabilityAndActionAtTwoHundredPercentText() {
        setProductContent(fontScale = 2f) {
            ProductDetailScreen(ProductDetailUiState(product = product()), actions())
        }

        val priceBounds =
            composeRule
                .onNodeWithTag(ProductDetailTestTags.PURCHASE_PRICE)
                .assertIsDisplayed()
                .fetchSemanticsNode()
                .boundsInRoot
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
        assertTrue("Availability must follow price", priceBounds.top < availabilityBounds.top)
        assertTrue(
            "The action must stack below its disabled-state explanation: " +
                "price=$priceBounds availability=$availabilityBounds action=$actionBounds",
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
            .performScrollTo()
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
