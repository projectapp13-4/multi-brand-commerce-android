@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
@file:Suppress("FunctionNaming")

package com.gurbakir.mobile.search

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.waitUntilExactlyOneExists
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import com.gurbakir.mobile.CoreTestTheme
import com.gurbakir.mobile.catalog.CatalogTestTags
import com.gurbakir.mobile.performDeterministicClick
import com.gurbakir.mobile.wishlist.WishlistMembershipUiState
import com.gurbakir.mobile.wishlist.WishlistTestTags
import com.gurbakir.storefront.CatalogProductSummary
import com.gurbakir.storefront.StorefrontMoney
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SearchScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun wishlistOffSearchResultHasNoHeartAndStillOpensProduct() {
        val product = product("123")
        var opened: String? = null
        setSearchContent {
            SearchScreen(
                SearchUiState(query = "copper", hasSearched = true, historyLoading = false, products = listOf(product)),
                actions(openProduct = { opened = it })
            )
        }
        composeRule.onNodeWithTag(SearchTestTags.GRID).performScrollToIndex(2)
        composeRule.onNodeWithTag(CatalogTestTags.product(product.handle)).assertIsDisplayed()
        composeRule.onNodeWithTag(WishlistTestTags.toggle(product.id), useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithTag(CatalogTestTags.product(product.handle)).performDeterministicClick()
        assertEquals(product.id, opened)
    }

    @Test
    fun localHistoryExposesSelectRemoveClearAndDisableActions() {
        val entry = StoredSearchQuery("bakır tava", "Bakır Tava", 1L)
        var selected: String? = null
        var removed: String? = null
        var cleared = 0
        var enabled: Boolean? = null
        setSearchContent {
            SearchScreen(
                state = SearchUiState(history = listOf(entry), historyLoading = false),
                actions =
                    actions(
                        selectHistory = { selected = it },
                        removeHistory = { removed = it },
                        clearHistory = { cleared += 1 },
                        historyEnabledChanged = { enabled = it }
                    )
            )
        }

        composeRule
            .onNodeWithTag(SearchTestTags.historyItem(entry.normalized))
            .performDeterministicClick()
        composeRule.waitForIdle()
        composeRule
            .onNodeWithTag(SearchTestTags.historyRemove(entry.normalized))
            .performDeterministicClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(SearchTestTags.HISTORY_CLEAR).performDeterministicClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(SearchTestTags.HISTORY_SETTINGS).performDeterministicClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(SearchTestTags.HISTORY_SETTINGS_PANEL).assertIsDisplayed()
        composeRule.onNodeWithTag(SearchTestTags.HISTORY_TOGGLE).performDeterministicClick()
        composeRule.waitForIdle()

        assertEquals("Bakır Tava", selected)
        assertEquals("bakır tava", removed)
        assertEquals(1, cleared)
        assertEquals(false, enabled)
    }

    @Test
    fun resultsOpenProductDetailAndCanLoadNextPage() {
        var loaded = 0
        var openedProduct: String? = null
        var wishlistProduct: String? = null
        val product = product("bakir-tava")
        setSearchContent {
            SearchScreen(
                state =
                    SearchUiState(
                        query = "bakır",
                        products = listOf(product),
                        totalCount = 2,
                        hasNextPage = true,
                        hasSearched = true,
                        historyLoading = false
                    ),
                actions =
                    actions(
                        loadMore = { loaded += 1 },
                        openProduct = { openedProduct = it },
                        setWishlist = { id, _ -> wishlistProduct = id }
                    ),
                wishlist = WishlistMembershipUiState()
            )
        }

        val resultGrid = composeRule.onNodeWithTag(SearchTestTags.GRID)
        resultGrid.performScrollToIndex(2)
        composeRule.waitForIdle()
        composeRule
            .onNodeWithTag(CatalogTestTags.product(product.handle))
            .assertIsDisplayed()
            .performDeterministicClick()
        composeRule.waitForIdle()
        composeRule
            .onNodeWithTag(WishlistTestTags.toggle(product.id))
            .assertIsDisplayed()
            .performDeterministicClick()
        composeRule.waitForIdle()
        resultGrid.performScrollToIndex(1)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(SearchTestTags.RESULT_COUNT).assertIsDisplayed()
        resultGrid.performScrollToIndex(3)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(SearchTestTags.LOAD_MORE).performDeterministicClick()
        composeRule.waitForIdle()

        assertEquals(1, loaded)
        assertEquals(product.id, openedProduct)
        assertEquals(product.id, wishlistProduct)
    }

    @Test
    @SdkSuppress(minSdkVersion = 24)
    fun imeSearchIsTheOnlyExplicitSubmitAndClearsInputFocus() {
        var submitted = 0
        setSearchContent {
            SearchScreen(
                state = SearchUiState(query = "bakır", historyLoading = false),
                actions = actions(submit = { submitted += 1 })
            )
        }

        composeRule.onAllNodesWithTag(SearchTestTags.SUBMIT).assertCountEquals(0)
        composeRule
            .onNodeWithTag(SearchTestTags.INPUT)
            .performClick()
            .assertIsFocused()
            .performImeAction()
        composeRule.waitForIdle()

        assertEquals(1, submitted)
        composeRule
            .onNodeWithTag(SearchTestTags.INPUT)
            .assertIsDisplayed()
            .assertIsNotFocused()
    }

    @Test
    fun emptyResultRemainsReadableAtTwoHundredPercentText() {
        setSearchContent(fontScale = 2f) {
            SearchScreen(
                state = SearchUiState(query = "olmayan", hasSearched = true, historyLoading = false),
                actions = actions()
            )
        }

        composeRule.onNodeWithTag(SearchTestTags.ROOT).assertIsDisplayed()
        composeRule.onNodeWithTag(SearchTestTags.EMPTY).assertIsDisplayed()
    }

    private fun setSearchContent(fontScale: Float = 1f, content: @androidx.compose.runtime.Composable () -> Unit) {
        composeRule.setContent {
            CoreTestTheme(darkTheme = false) {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                    content()
                }
            }
        }
        composeRule.waitUntilExactlyOneExists(hasTestTag(SearchTestTags.ROOT), timeoutMillis = 5_000)
    }

    @Suppress("LongParameterList")
    private fun actions(
        submit: () -> Unit = {},
        loadMore: () -> Unit = {},
        selectHistory: (String) -> Unit = {},
        removeHistory: (String) -> Unit = {},
        clearHistory: () -> Unit = {},
        historyEnabledChanged: (Boolean) -> Unit = {},
        openProduct: (String) -> Unit = {},
        setWishlist: (String, Boolean) -> Unit = { _, _ -> }
    ) = SearchActions(
        onQueryChanged = {},
        onSubmit = submit,
        onRetry = {},
        onLoadMore = loadMore,
        onSelectHistory = selectHistory,
        onRemoveHistory = removeHistory,
        onClearHistory = clearHistory,
        onHistoryEnabledChanged = historyEnabledChanged,
        onOpenProduct = openProduct,
        onSetWishlist = setWishlist
    )

    private fun product(handle: String) = CatalogProductSummary(
        id = "gid://shopify/Product/$handle",
        handle = handle,
        title = "Bakır Tava",
        availableForSale = true,
        media = null,
        minimumPrice = StorefrontMoney(BigDecimal("120.00"), "TRY"),
        maximumPrice = StorefrontMoney(BigDecimal("120.00"), "TRY")
    )
}
