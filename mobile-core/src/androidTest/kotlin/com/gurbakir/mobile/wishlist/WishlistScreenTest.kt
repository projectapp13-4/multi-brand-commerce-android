@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
@file:Suppress("FunctionNaming")

package com.gurbakir.mobile.wishlist

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.waitUntilExactlyOneExists
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gurbakir.mobile.CoreTestTheme
import com.gurbakir.mobile.navigatePrimary
import com.gurbakir.mobile.navigateProduct
import com.gurbakir.mobile.performDeterministicClick
import com.gurbakir.storefront.StorefrontMoney
import com.gurbakir.storefront.StorefrontProductDetail
import com.gurbakir.storefront.StorefrontProductOption
import com.gurbakir.storefront.StorefrontProductOptionValue
import com.gurbakir.storefront.StorefrontProductVariant
import com.gurbakir.storefront.StorefrontSelectedOption
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WishlistScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun wishlistWithoutSearchBrowseOpensCategories() {
        wishlistGraph(WishlistUiState())
        composeRule.onNodeWithTag(WishlistTestTags.BROWSE).performDeterministicClick()
        composeRule.onNodeWithTag("wishlist-journey-categories").assertIsDisplayed()
    }

    @Test
    fun wishlistWithoutSearchItemOpensProduct() {
        val product = product()
        wishlistGraph(WishlistUiState(entries = listOf(WishlistResolvedEntry(product.id, 1L, product = product))))
        composeRule.onNodeWithTag(
            com.gurbakir.mobile.catalog.CatalogTestTags.product(product.handle)
        ).performScrollTo().performDeterministicClick()
        composeRule.onNodeWithTag("wishlist-journey-product").assertIsDisplayed()
    }

    private fun wishlistGraph(state: WishlistUiState) {
        val composition = com.gurbakir.mobile.capabilityComposition(search = false, wishlist = true, account = false)
        lateinit var controller: androidx.navigation.NavHostController
        composeRule.setContent {
            CoreTestTheme {
                val nav = androidx.navigation.compose.rememberNavController()
                androidx.compose.runtime.SideEffect { controller = nav }
                com.gurbakir.mobile.ProductionNavHost(
                    navController = nav,
                    deepLinks = com.gurbakir.mobile.capabilityDeepLinks,
                    applicationComposition = composition,
                    content = com.gurbakir.mobile.ProductionDestinationContent(
                        home = {},
                        search = { error("Disabled Search invoked") },
                        categories = {
                            androidx.compose.material3.Text(
                                "Categories",
                                androidx.compose.ui.Modifier.testTag("wishlist-journey-categories")
                            )
                        },
                        product = {
                            androidx.compose.material3.Text(
                                it.productId,
                                androidx.compose.ui.Modifier.testTag("wishlist-journey-product")
                            )
                        },
                        wishlist = {
                            WishlistScreen(
                                state,
                                actions().copy(
                                    onBrowse = {
                                        nav.navigatePrimary(
                                            com.gurbakir.foundation.config.PrimaryNavigationDestination.CATEGORIES,
                                            composition
                                        )
                                    },
                                    onOpenProduct = nav::navigateProduct
                                )
                            )
                        }
                    )
                )
            }
        }
        composeRule.runOnIdle {
            controller.navigatePrimary(
                com.gurbakir.foundation.config.PrimaryNavigationDestination.WISHLIST,
                composition
            )
        }
    }

    @Test
    fun emptyStateExplainsDeviceOnlyStorageAtTwoHundredPercentText() {
        var browsed = 0
        setWishlistContent(fontScale = 2f) {
            WishlistScreen(WishlistUiState(), actions(onBrowse = { browsed += 1 }))
        }

        composeRule.onNodeWithTag(WishlistTestTags.ROOT).assertIsDisplayed()
        composeRule.onNodeWithTag(WishlistTestTags.EMPTY).assertIsDisplayed()
        composeRule.onNodeWithTag(WishlistTestTags.BROWSE).performDeterministicClick()
        composeRule.waitForIdle()
        assertEquals(1, browsed)
    }

    @Test
    fun loadedAndFailedItemsCanBeRemovedRetriedAndCleared() {
        val product = product()
        val failedId = "gid://shopify/Product/2"
        var removed: String? = null
        var retried = 0
        var cleared = 0
        setWishlistContent {
            WishlistScreen(
                state =
                    WishlistUiState(
                        entries =
                            listOf(
                                WishlistResolvedEntry(product.id, 2L, product = product),
                                WishlistResolvedEntry(failedId, 1L, issue = WishlistItemIssue.CONNECTION)
                            )
                    ),
                actions =
                    actions(
                        onRetry = { retried += 1 },
                        onRemove = { removed = it },
                        onClear = { cleared += 1 }
                    )
            )
        }

        composeRule
            .onNodeWithTag(WishlistTestTags.toggle(product.id))
            .performScrollTo()
            .performDeterministicClick()
        composeRule.waitForIdle()
        assertEquals(product.id, removed)
        composeRule
            .onNodeWithTag(WishlistTestTags.RETRY)
            .performScrollTo()
            .performDeterministicClick()
        composeRule.waitForIdle()
        assertEquals(1, retried)
        composeRule
            .onNodeWithTag(WishlistTestTags.CONTENT)
            .performScrollToNode(hasTestTag(WishlistTestTags.CLEAR))
        composeRule.onNodeWithTag(WishlistTestTags.CLEAR).performDeterministicClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(WishlistTestTags.CLEAR_CONFIRM).performDeterministicClick()
        composeRule.waitForIdle()
        assertEquals(1, cleared)
    }

    @Test
    fun storageFailureDoesNotPretendTheWishlistIsEmpty() {
        var retried = 0
        setWishlistContent {
            WishlistScreen(
                WishlistUiState(storageAvailable = false),
                actions(onRetry = { retried += 1 })
            )
        }

        composeRule.onNodeWithTag(WishlistTestTags.STORAGE_ERROR).assertIsDisplayed()
        composeRule.onNodeWithTag(WishlistTestTags.RETRY).performDeterministicClick()
        composeRule.waitForIdle()
        assertEquals(1, retried)
    }

    @Test
    fun loadingStateUsesSharedTruthfulPanel() {
        setWishlistContent {
            WishlistScreen(WishlistUiState(loading = true), actions())
        }

        composeRule.onNodeWithTag(WishlistTestTags.LOADING).assertIsDisplayed()
    }

    private fun setWishlistContent(fontScale: Float = 1f, content: @androidx.compose.runtime.Composable () -> Unit) {
        composeRule.setContent {
            CoreTestTheme(darkTheme = false) {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                    content()
                }
            }
        }
        composeRule.waitUntilExactlyOneExists(hasTestTag(WishlistTestTags.ROOT), timeoutMillis = 5_000)
    }

    private fun actions(
        onBrowse: () -> Unit = {},
        onRetry: () -> Unit = {},
        onRemove: (String) -> Unit = {},
        onClear: () -> Unit = {}
    ) = WishlistActions(
        onBrowse = onBrowse,
        onRetry = onRetry,
        onOpenProduct = {},
        onRemove = onRemove,
        onClear = onClear
    )

    private fun product(): StorefrontProductDetail = StorefrontProductDetail(
        id = "gid://shopify/Product/1",
        handle = "copper-pan",
        title = "Copper pan",
        description = "Handmade product.",
        availableForSale = true,
        options =
            listOf(
                StorefrontProductOption(
                    "gid://shopify/ProductOption/1",
                    "Title",
                    listOf(StorefrontProductOptionValue("gid://shopify/ProductOptionValue/1", "Default Title"))
                )
            ),
        variants =
            listOf(
                StorefrontProductVariant(
                    id = "gid://shopify/ProductVariant/1",
                    title = "Default Title",
                    availableForSale = true,
                    currentlyNotInStock = false,
                    price = StorefrontMoney(BigDecimal("100.00"), "TRY"),
                    compareAtPrice = null,
                    image = null,
                    selectedOptions = listOf(StorefrontSelectedOption("Title", "Default Title"))
                )
            ),
        media = emptyList()
    )
}
