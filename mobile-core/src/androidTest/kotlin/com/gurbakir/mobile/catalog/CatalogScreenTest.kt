@file:Suppress("FunctionNaming")

package com.gurbakir.mobile.catalog

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.gurbakir.mobile.CoreTestTheme
import com.gurbakir.mobile.core.R
import com.gurbakir.mobile.performDeterministicClick
import com.gurbakir.mobile.wishlist.WishlistMembershipUiState
import com.gurbakir.mobile.wishlist.WishlistTestTags
import com.gurbakir.storefront.CatalogDiscoveryCollection
import com.gurbakir.storefront.CatalogProductSummary
import com.gurbakir.storefront.CatalogProductTypeFilter
import com.gurbakir.storefront.CatalogProductTypeValue
import com.gurbakir.storefront.CollectionCatalogSort
import com.gurbakir.storefront.StorefrontMedia
import com.gurbakir.storefront.StorefrontMoney
import java.math.BigDecimal
import java.net.URI
import java.util.Locale
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CatalogScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun collectionCountUsesEnglishSingularAndPluralWhileRetainingTurkishCopy() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val expected = mapOf(
            "en-US" to listOf("0 products shown", "1 product shown", "3 products shown"),
            "tr" to listOf("0 ürün gösteriliyor", "1 ürün gösteriliyor", "3 ürün gösteriliyor")
        )
        expected.forEach { (languageTag, labels) ->
            val configuration = Configuration(context.resources.configuration).apply {
                setLocale(Locale.forLanguageTag(languageTag))
            }
            val resources = context.createConfigurationContext(configuration).resources
            listOf(0, 1, 3).zip(labels).forEach { (quantity, label) ->
                assertEquals(
                    label,
                    resources.getQuantityString(R.plurals.collection_loaded_count_compact, quantity, quantity)
                )
            }
        }
    }

    @Test
    fun wishlistOffCollectionCardHasNoHeartAndStillOpensProduct() {
        val product = product("123", available = true, ranged = false)
        var opened: String? = null
        setCatalogContent {
            CollectionScreen(
                CollectionUiState(handle = "test", title = "Test", products = listOf(product)),
                CollectionActions({}, {}, {}, {}, {}, onOpenProduct = { opened = it })
            )
        }
        composeRule.onNodeWithTag(CatalogTestTags.COLLECTION_GRID).performScrollToIndex(1)
        composeRule.onNodeWithTag(CatalogTestTags.product(product.handle)).assertIsDisplayed()
        composeRule.onNodeWithTag(WishlistTestTags.toggle(product.id), useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithTag(CatalogTestTags.product(product.handle)).performDeterministicClick()
        assertEquals(product.id, opened)
    }

    @Test
    fun categoriesExposeOnlyFunctionalCollectionActions() {
        var opened: String? = null
        val item =
            category(
                handle = "alpha",
                menuTitle = "Menu label",
                collectionTitle = "Collection title"
            )
        setCatalogContent {
            CategoriesScreen(
                state = CategoriesUiState.Content(listOf(item), partialFailure = null),
                onRetry = {},
                onOpenCollection = { opened = it }
            )
        }

        composeRule
            .onNodeWithTag(CatalogTestTags.category("alpha"))
            .assertIsDisplayed()
            .performDeterministicClick()
        composeRule.waitForIdle()
        assertEquals("alpha", opened)
        composeRule
            .onNodeWithTag(CatalogTestTags.categoryLabel("alpha"), useUnmergedTree = true)
            .assertTextEquals("Menu label")
    }

    @Test
    fun compactCategoriesUseScanningRowsAndPreserveMenuLabelsAndMediaDescriptions() {
        val items =
            listOf(
                category(
                    handle = "alpha",
                    menuTitle = "Menu Alpha",
                    collectionTitle = "Collection Alpha",
                    altText = null
                ),
                category(
                    handle = "beta",
                    menuTitle = "Menu Beta",
                    collectionTitle = "Collection Beta",
                    altText = "Explicit media description"
                )
            )
        setCatalogContent {
            CategoriesScreen(
                state = CategoriesUiState.Content(items, partialFailure = null),
                onRetry = {},
                onOpenCollection = {}
            )
        }

        val first = composeRule.onNodeWithTag(CatalogTestTags.category("alpha"))
        val second = composeRule.onNodeWithTag(CatalogTestTags.category("beta"))
        first.assertIsDisplayed()
        second.assertIsDisplayed()
        composeRule
            .onNodeWithTag(CatalogTestTags.categoryLabel("alpha"), useUnmergedTree = true)
            .assertTextEquals("Menu Alpha")
        composeRule
            .onNodeWithTag(CatalogTestTags.categoryLabel("beta"), useUnmergedTree = true)
            .assertTextEquals("Menu Beta")
        composeRule.onNodeWithContentDescription("Menu Alpha").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Explicit media description").assertIsDisplayed()

        val firstBounds = first.fetchSemanticsNode().boundsInRoot
        val secondBounds = second.fetchSemanticsNode().boundsInRoot
        assertTrue("Expected successive navigation rows", firstBounds.bottom <= secondBounds.top)
        assertTrue(
            "Navigation rows must use the same leading alignment",
            abs(firstBounds.left - secondBounds.left) < 1f
        )
    }

    @Test
    fun longMenuTitleRemainsReachableAtTwoHundredPercentText() {
        val longTitle = "A deliberately long merchant category title for accessible layout"
        val secondTitle = "A second lengthy category title"
        setCatalogContent(fontScale = 2f) {
            CategoriesScreen(
                state =
                    CategoriesUiState.Content(
                        listOf(
                            category("long-title", longTitle, "Different collection title"),
                            category("second", secondTitle, "Second collection")
                        ),
                        partialFailure = null
                    ),
                onRetry = {},
                onOpenCollection = {}
            )
        }

        val first = composeRule.onNodeWithTag(CatalogTestTags.category("long-title")).assertIsDisplayed()
        val grid = composeRule.onNodeWithTag(CatalogTestTags.CATEGORIES_GRID).fetchSemanticsNode().boundsInRoot
        assertTrue(
            "Large text must give each category a full-width row",
            first.fetchSemanticsNode().boundsInRoot.width > grid.width * 0.7f
        )
        composeRule
            .onNodeWithTag(CatalogTestTags.categoryLabel("long-title"), useUnmergedTree = true)
            .assertTextEquals(longTitle)
        composeRule.onNodeWithTag(CatalogTestTags.CATEGORIES_GRID).performScrollToIndex(1)
        composeRule.onNodeWithTag(CatalogTestTags.category("second")).assertIsDisplayed()
        composeRule
            .onNodeWithTag(CatalogTestTags.categoryLabel("second"), useUnmergedTree = true)
            .assertTextEquals(secondTitle)
    }

    @Test
    fun listingExposesServerFilterSupportedSortPaginationAndHonestAvailability() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        var selectedSort: CollectionCatalogSort? = null
        var selectedFilter: String? = null
        var loadMore = 0
        var openedProduct: String? = null
        var wishlistProduct: String? = null
        val product = product("bakir-fondu", available = false, ranged = true)
        setCatalogContent {
            CollectionScreen(
                state =
                    CollectionUiState(
                        handle = "ozel-urunlerimiz",
                        title = "Özel Ürünlerimiz",
                        products = listOf(product),
                        productTypeFilter =
                            CatalogProductTypeFilter(
                                label = "Ürün türü",
                                values =
                                    listOf(
                                        CatalogProductTypeValue("Fondü Tavası", "Fondü Tavası", 4),
                                        CatalogProductTypeValue("Şişe", "Şişe", 1)
                                    )
                            ),
                        hasNextPage = true
                    ),
                actions =
                    CollectionActions(
                        onBack = {},
                        onRetry = {},
                        onSortSelected = { selectedSort = it },
                        onProductTypeToggled = { selectedFilter = it },
                        onLoadMore = { loadMore += 1 },
                        onOpenProduct = { openedProduct = it },
                        onSetWishlist = { id, _ -> wishlistProduct = id }
                    ),
                wishlist = WishlistMembershipUiState()
            )
        }

        val productGrid = composeRule.onNodeWithTag(CatalogTestTags.COLLECTION_GRID)
        productGrid.performScrollToIndex(1)
        composeRule.waitForIdle()
        val productNode =
            composeRule.onNodeWithTag(CatalogTestTags.product(product.handle)).assertIsDisplayed()
        val mediaBounds =
            composeRule
                .onNodeWithTag(CatalogTestTags.productMedia(product.handle), useUnmergedTree = true)
                .fetchSemanticsNode()
                .boundsInRoot
        val titleBounds =
            composeRule
                .onNodeWithTag(CatalogTestTags.productTitle(product.handle), useUnmergedTree = true)
                .fetchSemanticsNode()
                .boundsInRoot
        val priceBounds =
            composeRule
                .onNodeWithTag(CatalogTestTags.productPrice(product.handle), useUnmergedTree = true)
                .fetchSemanticsNode()
                .boundsInRoot
        val availabilityBounds =
            composeRule
                .onNodeWithTag(CatalogTestTags.productAvailability(product.handle), useUnmergedTree = true)
                .fetchSemanticsNode()
                .boundsInRoot
        val wishlistBounds =
            composeRule
                .onNodeWithTag(WishlistTestTags.toggle(product.id), useUnmergedTree = true)
                .fetchSemanticsNode()
                .boundsInRoot
        val minimumTouchTarget = 48f * context.resources.displayMetrics.density
        assertTrue(
            "Catalog media must preserve imagery in a compact square frame",
            abs(mediaBounds.height / mediaBounds.width - 1f) < 0.03f
        )
        assertTrue("Product title must precede price", titleBounds.top < priceBounds.top)
        assertTrue("Price must precede availability", priceBounds.top < availabilityBounds.top)
        assertTrue("Wishlist hit area must be at least 48dp wide", wishlistBounds.width >= minimumTouchTarget - 1f)
        assertTrue("Wishlist hit area must be at least 48dp high", wishlistBounds.height >= minimumTouchTarget - 1f)

        productNode.performDeterministicClick()
        composeRule.waitForIdle()
        assertEquals(product.id, openedProduct)
        composeRule
            .onNodeWithTag(WishlistTestTags.toggle(product.id))
            .assertIsDisplayed()
            .performDeterministicClick()
        composeRule.waitForIdle()
        assertEquals(product.id, wishlistProduct)
        composeRule
            .onNodeWithTag(CatalogTestTags.productUnavailable(product.handle), useUnmergedTree = true)
            .assertTextEquals(context.getString(R.string.collection_unavailable))
        productGrid.performScrollToIndex(0)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(CatalogTestTags.filter("Fondü Tavası")).performDeterministicClick()
        composeRule.waitForIdle()
        assertEquals("Fondü Tavası", selectedFilter)
        composeRule.onNodeWithTag(CatalogTestTags.COLLECTION_SORT).performDeterministicClick()
        composeRule.waitForIdle()
        composeRule
            .onNodeWithText(context.getString(R.string.collection_sort_newest))
            .performDeterministicClick()
        composeRule.waitForIdle()
        assertEquals(CollectionCatalogSort.NEWEST, selectedSort)
        productGrid.performScrollToIndex(2)
        composeRule.waitForIdle()
        composeRule
            .onNodeWithTag(CatalogTestTags.COLLECTION_LOAD_MORE)
            .assertIsDisplayed()
            .performDeterministicClick()
        composeRule.waitForIdle()
        assertEquals(1, loadMore)
    }

    @Test
    fun normalAvailabilityIsSilentAndLongTileTitlesKeepTwoLinesWithFullAccessibleText() {
        val title =
            "A deliberately long product title that remains available to accessibility while scanning a compact grid"
        val available = product("available", available = true, ranged = false).copy(title = title)
        val soldOut = product("sold-out", available = false, ranged = true)
        setCatalogContent {
            CollectionScreen(
                state = CollectionUiState(handle = "test", title = "Collection", products = listOf(available, soldOut)),
                actions = CollectionActions({}, {}, {}, {}, {})
            )
        }
        composeRule.onNodeWithTag(CatalogTestTags.COLLECTION_GRID).performScrollToIndex(1)
        composeRule.onNodeWithTag(CatalogTestTags.productAvailability(available.handle), useUnmergedTree = true)
            .assertDoesNotExist()
        composeRule.onNodeWithTag(CatalogTestTags.productAvailability(soldOut.handle), useUnmergedTree = true)
            .assertIsDisplayed()
        val titleNode = composeRule.onNodeWithTag(
            CatalogTestTags.productTitle(available.handle),
            useUnmergedTree = true
        )
        titleNode.assertTextEquals(title)
        val layouts = mutableListOf<TextLayoutResult>()
        titleNode.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertEquals(2, layouts.single().lineCount)
    }

    @Test
    fun filteredEmptyStateRemainsReadableAtTwoHundredPercentText() {
        setCatalogContent(fontScale = 2f) {
            CollectionScreen(
                state =
                    CollectionUiState(
                        handle = "ozel-urunlerimiz",
                        title = "Özel Ürünlerimiz",
                        selectedProductTypes = setOf("Şişe")
                    ),
                actions =
                    CollectionActions(
                        onBack = {},
                        onRetry = {},
                        onSortSelected = {},
                        onProductTypeToggled = {},
                        onLoadMore = {}
                    )
            )
        }

        composeRule.onNodeWithTag(CatalogTestTags.COLLECTION_ROOT).assertIsDisplayed()
        composeRule.onNodeWithTag(CatalogTestTags.COLLECTION_EMPTY).assertIsDisplayed()
    }

    @Test
    fun populatedCollectionKeepsCountAndLongSortActionUsableAtTwoHundredPercentText() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        var selected: CollectionCatalogSort? = null
        val item = product("large-text", available = true, ranged = true)
        setCatalogContent(fontScale = 2f) {
            Box(Modifier.requiredWidth(320.dp)) {
                CollectionScreen(
                    CollectionUiState(
                        handle = "test",
                        title = "Collection",
                        products = listOf(item),
                        sort = CollectionCatalogSort.PRICE_HIGH_TO_LOW
                    ),
                    CollectionActions({}, {}, { selected = it }, {}, {})
                )
            }
        }
        val grid = composeRule.onNodeWithTag(CatalogTestTags.COLLECTION_GRID)
        val count = composeRule.onNodeWithTag(CatalogTestTags.COLLECTION_RESULT_COUNT)
            .assertIsDisplayed()
            .assertTextEquals(context.resources.getQuantityString(R.plurals.collection_loaded_count_compact, 1, 1))
            .fetchSemanticsNode().boundsInRoot
        val sort = composeRule.onNodeWithTag(CatalogTestTags.COLLECTION_SORT).assertIsDisplayed()
        val sortBounds = sort.fetchSemanticsNode().boundsInRoot
        val viewport = grid.fetchSemanticsNode().boundsInRoot
        assertTrue("Large-text controls must reflow without overlap", count.bottom <= sortBounds.top)
        assertTrue("The count must fit the viewport", count.left >= viewport.left && count.right <= viewport.right)
        assertTrue(
            "The long sort action must fit the viewport",
            sortBounds.left >= viewport.left && sortBounds.right <= viewport.right
        )
        sort.performDeterministicClick()
        composeRule.onNodeWithText(context.getString(R.string.collection_sort_newest)).performDeterministicClick()
        assertEquals(CollectionCatalogSort.NEWEST, selected)
        grid.performScrollToIndex(1)
        composeRule.onNodeWithTag(CatalogTestTags.product(item.handle)).assertIsDisplayed()
    }

    private fun setCatalogContent(fontScale: Float = 1f, content: @androidx.compose.runtime.Composable () -> Unit) {
        composeRule.setContent {
            CoreTestTheme(darkTheme = false) {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                    content()
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun product(handle: String, available: Boolean, ranged: Boolean): CatalogProductSummary =
        CatalogProductSummary(
            id = "gid://shopify/Product/$handle",
            handle = handle,
            title = "Bakır Fondü",
            availableForSale = available,
            media = media(),
            minimumPrice = StorefrontMoney(BigDecimal("100.00"), "TRY"),
            maximumPrice = StorefrontMoney(BigDecimal(if (ranged) "150.00" else "100.00"), "TRY")
        )

    private fun category(
        handle: String,
        menuTitle: String,
        collectionTitle: String,
        altText: String? = "Bakır ürün"
    ): CatalogCategoryItem = CatalogCategoryItem(
        stableId = "menu-item-$handle",
        title = menuTitle,
        collection =
            CatalogDiscoveryCollection(
                id = "gid://shopify/Collection/$handle",
                handle = handle,
                sourceTitle = collectionTitle,
                media = media(altText)
            )
    )

    private fun media(altText: String? = "Bakır ürün"): StorefrontMedia = StorefrontMedia(
        URI("file:///android_asset/nonexistent-catalog-image.png"),
        altText,
        300,
        400
    )
}
