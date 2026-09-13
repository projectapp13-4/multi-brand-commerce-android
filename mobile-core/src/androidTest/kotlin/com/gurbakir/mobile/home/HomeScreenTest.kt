@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package com.gurbakir.mobile.home

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.waitUntilExactlyOneExists
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.gurbakir.mobile.CoreTestTheme
import com.gurbakir.mobile.core.R
import com.gurbakir.mobile.localization.effectiveForegroundLocale
import com.gurbakir.mobile.performDeterministicClick
import com.gurbakir.storefront.HomeCollectionSummary
import com.gurbakir.storefront.HomeProductSummary
import com.gurbakir.storefront.StorefrontMedia
import com.gurbakir.storefront.StorefrontMoney
import java.math.BigDecimal
import java.net.URI
import java.text.NumberFormat
import java.util.Currency
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeScreenTest {
    private val configuration = HomeConfiguration(
        HomeProductRangeConfiguration(
            "TEST_RANGE",
            R.string.home_title,
            1,
            listOf(HomeCollectionSource("TEST_COLLECTION", "fixture-collection", R.string.categories_title))
        ),
        HomeFeaturedProductConfiguration("TEST_FEATURED", R.string.home_title, "fixture-product")
    )

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun wishlistOffFeaturedCardHasNoHeartAndStillOpensProduct() {
        var opened: String? = null
        setHomeContent(
            HomeUiState(
                productRange = HomeSectionUiState.Empty,
                featuredProduct = HomeSectionUiState.Content(featuredItem(), null)
            ),
            HomeActions(retryProductRange = {}, retryFeaturedProduct = {}, openProduct = { opened = it })
        )
        composeRule.onNodeWithTag(HomeTestTags.FEATURED_CARD).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag(
            com.gurbakir.mobile.wishlist.WishlistTestTags.toggle(featuredItem().summary.id),
            useUnmergedTree = true
        ).assertDoesNotExist()
        composeRule.onNodeWithTag(HomeTestTags.FEATURED_CARD).performDeterministicClick()
        assertEquals(featuredItem().summary.id, opened)
    }

    @Test
    fun loadingSectionsExposeIndependentSkeletons() {
        setHomeContent(HomeUiState())

        composeRule.onNodeWithTag(HomeTestTags.ROOT).assertIsDisplayed()
        composeRule.onNodeWithTag(HomeTestTags.PRODUCT_RANGE_LOADING).assertIsDisplayed()
        composeRule.onNodeWithTag(HomeTestTags.FEATURED_LOADING).assertIsDisplayed()
    }

    @Test
    fun homeTopBarKeepsCartAsTheContextualCommerceAction() {
        var cart = 0
        setHomeContent(
            state = HomeUiState(),
            actions =
                HomeActions(
                    retryProductRange = {},
                    retryFeaturedProduct = {},
                    openCart = { cart += 1 }
                )
        )

        composeRule.onNodeWithTag(HomeTestTags.CART).performDeterministicClick()
        composeRule.waitForIdle()

        assertEquals(1, cart)
    }

    @Test
    fun homeOmitsRedundantHelpAndPolicyDestination() {
        setHomeContent(
            state =
                HomeUiState(
                    productRange = HomeSectionUiState.Empty,
                    featuredProduct = HomeSectionUiState.Empty
                )
        )

        composeRule.onNodeWithTag(HomeTestTags.LEGAL_SUPPORT).assertIsNotDisplayed()
    }

    @Test
    fun loadingHomeAppendsAvailableLegalSupportAndOpensIt() {
        var opened = 0
        setHomeContent(
            state = HomeUiState(),
            actions =
                HomeActions(
                    retryProductRange = {},
                    retryFeaturedProduct = {},
                    openLegalSupport = { opened += 1 }
                )
        )

        composeRule
            .onNodeWithTag(HomeTestTags.LEGAL_SUPPORT)
            .performScrollTo()
            .assertIsDisplayed()
            .performDeterministicClick()
        composeRule.waitForIdle()

        assertEquals(1, opened)
    }

    @Test
    fun emptyHomeAppendsAvailableLegalSupport() {
        setHomeContent(
            state =
                HomeUiState(
                    productRange = HomeSectionUiState.Empty,
                    featuredProduct = HomeSectionUiState.Empty
                ),
            actions =
                HomeActions(
                    retryProductRange = {},
                    retryFeaturedProduct = {},
                    openLegalSupport = {}
                )
        )

        composeRule.onNodeWithTag(HomeTestTags.EMPTY).assertIsDisplayed()
        composeRule.onNodeWithTag(HomeTestTags.LEGAL_SUPPORT).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun healthyHomeAppendsAvailableLegalSupport() {
        setHomeContent(
            state =
                HomeUiState(
                    productRange = HomeSectionUiState.Content(listOf(collectionItem("LEGAL_HEALTHY")), null),
                    featuredProduct = HomeSectionUiState.Content(featuredItem(), null)
                ),
            actions =
                HomeActions(
                    retryProductRange = {},
                    retryFeaturedProduct = {},
                    openLegalSupport = {}
                )
        )

        composeRule.onNodeWithTag(HomeTestTags.PRODUCT_RANGE).assertIsDisplayed()
        composeRule.onNodeWithTag(HomeTestTags.LEGAL_SUPPORT).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun retryableErrorHomeAppendsAvailableLegalSupport() {
        setHomeContent(
            state =
                HomeUiState(
                    productRange =
                        HomeSectionUiState.Error(
                            HomeLoadFailure(HomeLoadFailureCategory.CONNECTION, retryable = true)
                        ),
                    featuredProduct = HomeSectionUiState.Empty
                ),
            actions =
                HomeActions(
                    retryProductRange = {},
                    retryFeaturedProduct = {},
                    openLegalSupport = {}
                )
        )

        composeRule.onNodeWithTag(HomeTestTags.PRODUCT_RANGE_ERROR).assertIsDisplayed()
        composeRule.onNodeWithTag(HomeTestTags.LEGAL_SUPPORT).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun nonretryableErrorHomeAppendsAvailableLegalSupport() {
        setHomeContent(
            state =
                HomeUiState(
                    productRange =
                        HomeSectionUiState.Error(
                            HomeLoadFailure(HomeLoadFailureCategory.CONFIGURATION, retryable = false)
                        ),
                    featuredProduct = HomeSectionUiState.Empty
                ),
            actions =
                HomeActions(
                    retryProductRange = {},
                    retryFeaturedProduct = {},
                    openLegalSupport = {}
                )
        )

        composeRule.onNodeWithTag(HomeTestTags.PRODUCT_RANGE_ERROR).assertIsDisplayed()
        composeRule.onNodeWithTag(HomeTestTags.LEGAL_SUPPORT).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun collectionAndFeaturedProductCardsOpenFunctionalDestinations() {
        val locale =
            effectiveForegroundLocale(
                InstrumentationRegistry.getInstrumentation().targetContext.resources.configuration
            )
        val expectedPrice =
            NumberFormat.getCurrencyInstance(locale).apply {
                currency = Currency.getInstance("TRY")
            }.format(BigDecimal("0.00"))
        val collection = collectionItem("HOME_RANGE_TEST")
        var openedHandle: String? = null
        var openedProduct: String? = null
        setHomeContent(
            state =
                HomeUiState(
                    productRange = HomeSectionUiState.Content(listOf(collection), partialFailure = null),
                    featuredProduct = HomeSectionUiState.Content(featuredItem(), partialFailure = null)
                ),
            actions =
                HomeActions(
                    retryProductRange = {},
                    retryFeaturedProduct = {},
                    openCollection = { openedHandle = it },
                    openProduct = { openedProduct = it }
                )
        )

        composeRule
            .onNodeWithTag(HomeTestTags.collection(collection.source.stableId))
            .performScrollTo()
            .assertIsDisplayed()
            .performDeterministicClick()
        composeRule.waitForIdle()
        assertEquals(collection.summary.handle, openedHandle)
        composeRule
            .onNodeWithTag(HomeTestTags.FEATURED_CARD)
            .performScrollTo()
            .assertIsDisplayed()
            .performDeterministicClick()
        composeRule.waitForIdle()
        assertEquals(featuredItem().summary.id, openedProduct)
        composeRule
            .onNodeWithTag(HomeTestTags.FEATURED_PRICE, useUnmergedTree = true)
            .assertTextEquals(expectedPrice)
    }

    @Test
    fun partialFailureRetainsContentAndRetriesOnlyItsSection() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        var productRetries = 0
        val collection = collectionItem("HOME_RANGE_PARTIAL")
        setHomeContent(
            state =
                HomeUiState(
                    productRange =
                        HomeSectionUiState.Content(
                            listOf(collection),
                            partialFailure =
                                HomeLoadFailure(HomeLoadFailureCategory.CONNECTION, retryable = true)
                        ),
                    featuredProduct = HomeSectionUiState.Empty
                ),
            actions = HomeActions(retryProductRange = { productRetries += 1 }, retryFeaturedProduct = {})
        )

        composeRule.onNodeWithTag(HomeTestTags.collection(collection.source.stableId)).assertIsDisplayed()
        composeRule.onNodeWithTag(HomeTestTags.PRODUCT_RANGE_PARTIAL_ERROR).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.retry)).performDeterministicClick()
        composeRule.waitForIdle()
        assertEquals(1, productRetries)
    }

    @Test
    fun fullyEmptyHomeUsesOneHonestEmptyState() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        var openedCategories = 0
        setHomeContent(
            state =
                HomeUiState(
                    productRange = HomeSectionUiState.Empty,
                    featuredProduct = HomeSectionUiState.Empty
                ),
            actions =
                HomeActions(
                    retryProductRange = {},
                    retryFeaturedProduct = {},
                    openCategories = { openedCategories += 1 }
                )
        )

        composeRule.onNodeWithTag(HomeTestTags.EMPTY).assertIsDisplayed()
        composeRule.onNodeWithTag(HomeTestTags.PRODUCT_RANGE).assertIsNotDisplayed()
        composeRule.onNodeWithTag(HomeTestTags.FEATURED).assertIsNotDisplayed()
        composeRule
            .onNodeWithText(context.getString(R.string.home_browse_categories))
            .performDeterministicClick()
        composeRule.waitForIdle()
        assertEquals(1, openedCategories)
    }

    @Test
    fun slowLoadingReplacesIndefiniteSkeletonWithRecoveryAction() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        var productRetries = 0
        var featuredRetries = 0
        setHomeContent(
            state =
                HomeUiState(
                    productRange = HomeSectionUiState.SlowLoading,
                    featuredProduct = HomeSectionUiState.SlowLoading
                ),
            actions =
                HomeActions(
                    retryProductRange = { productRetries += 1 },
                    retryFeaturedProduct = { featuredRetries += 1 }
                )
        )

        composeRule.onNodeWithTag(HomeTestTags.SLOW_LOADING).assertIsDisplayed()
        composeRule.onNodeWithTag(HomeTestTags.PRODUCT_RANGE_LOADING).assertIsNotDisplayed()
        composeRule.onNodeWithTag(HomeTestTags.FEATURED_LOADING).assertIsNotDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.retry)).performDeterministicClick()
        composeRule.waitForIdle()
        assertEquals(1, productRetries)
        assertEquals(1, featuredRetries)
    }

    @Test
    fun offlineErrorExposesOnlyItsSectionRetry() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        var retries = 0
        setHomeContent(
            state =
                HomeUiState(
                    productRange =
                        HomeSectionUiState.Error(
                            HomeLoadFailure(HomeLoadFailureCategory.CONNECTION, retryable = true)
                        ),
                    featuredProduct = HomeSectionUiState.Empty
                ),
            actions = HomeActions(retryProductRange = { retries += 1 }, retryFeaturedProduct = {})
        )

        composeRule.onNodeWithTag(HomeTestTags.PRODUCT_RANGE_ERROR).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.retry)).performDeterministicClick()
        composeRule.waitForIdle()
        assertEquals(1, retries)
    }

    @Test
    fun emptyHomeRemainsReadableAtTwoHundredPercentText() {
        setHomeContent(
            state =
                HomeUiState(
                    productRange = HomeSectionUiState.Empty,
                    featuredProduct = HomeSectionUiState.Empty
                ),
            fontScale = 2f
        )

        composeRule.onNodeWithTag(HomeTestTags.EMPTY).performScrollTo().assertIsDisplayed()
    }

    private fun setHomeContent(
        state: HomeUiState,
        actions: HomeActions = HomeActions(retryProductRange = {}, retryFeaturedProduct = {}),
        fontScale: Float = 1f
    ) {
        composeRule.setContent {
            CoreTestTheme(
                darkTheme = false
            ) {
                val currentDensity = LocalDensity.current
                CompositionLocalProvider(
                    LocalDensity provides Density(currentDensity.density, fontScale)
                ) {
                    HomeScreen(
                        state = state,
                        brandDisplayName = "Test store",
                        actions = actions
                    )
                }
            }
        }
        composeRule.waitUntilExactlyOneExists(hasTestTag(HomeTestTags.ROOT), timeoutMillis = 5_000)
    }

    private fun collectionItem(stableId: String): HomeCollectionItem {
        val source = configuration.productRange.sources.first().copy(stableId = stableId)
        return HomeCollectionItem(
            source,
            HomeCollectionSummary("gid://shopify/Collection/test", source.handle, "Bardaklar", media())
        )
    }

    private fun featuredItem(): HomeFeaturedItem = HomeFeaturedItem(
        configuration.featuredProduct,
        HomeProductSummary(
            id = "gid://shopify/Product/featured",
            handle = configuration.featuredProduct.handle,
            title = "Bakır Tava",
            availableForSale = true,
            media = media(),
            price = StorefrontMoney(BigDecimal("0.00"), "TRY")
        )
    )

    private fun media(): StorefrontMedia =
        StorefrontMedia(URI("file:///android_asset/nonexistent-home-image.png"), "Bakır ürün", 300, 400)
}
