@file:Suppress("MagicNumber", "LongMethod")

package com.tumiart.mobile.catalog

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gurbakir.mobile.catalog.CatalogCategoryItem
import com.gurbakir.mobile.catalog.CatalogLoadFailure
import com.gurbakir.mobile.catalog.CatalogLoadFailureCategory
import com.gurbakir.mobile.catalog.CatalogTestTags
import com.gurbakir.mobile.catalog.CategoriesScreen
import com.gurbakir.mobile.catalog.CategoriesUiState
import com.gurbakir.mobile.catalog.CollectionActions
import com.gurbakir.mobile.catalog.CollectionScreen
import com.gurbakir.mobile.catalog.CollectionUiState
import com.gurbakir.mobile.wishlist.WishlistMembershipUiState
import com.gurbakir.mobile.wishlist.WishlistTestTags
import com.gurbakir.storefront.CatalogDiscoveryCollection
import com.gurbakir.storefront.CatalogDiscoveryMediaSource
import com.gurbakir.storefront.CatalogProductSummary
import com.gurbakir.storefront.CatalogProductTypeFilter
import com.gurbakir.storefront.CatalogProductTypeValue
import com.gurbakir.storefront.CollectionCatalogSort
import com.gurbakir.storefront.StorefrontMedia
import com.gurbakir.storefront.StorefrontMoney
import com.tumiart.mobile.config.TumiArtConfiguration
import com.tumiart.mobile.design.TumiArtThemeProbe
import com.tumiart.mobile.design.TumiArtThemeProbeActivity
import java.math.BigDecimal
import java.net.URI
import kotlin.math.abs
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestName
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TumiArtCatalogTest {
    @get:Rule val rule = createAndroidComposeRule<TumiArtThemeProbeActivity>()

    @get:Rule val testName = TestName()
    private val policy get() = TumiArtConfiguration.catalog.presentation

    @After
    fun captureRequestedFixtureEvidence() {
        val instrumentation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
        if (androidx.test.platform.app.InstrumentationRegistry.getArguments().getString("captureCatalog") !=
            "true"
        ) {
            return
        }
        rule.waitForIdle()
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        val directory = java.io.File(instrumentation.targetContext.getExternalFilesDir(null), "p3-catalog").apply {
            mkdirs()
        }
        java.io.File(directory, "api${android.os.Build.VERSION.SDK_INT}-${testName.methodName}.png")
            .outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    @Test
    fun galleryDiscoveryKeepsEmptyErrorAndRefreshRecoveryTruthful() {
        var retries = 0
        val state = mutableStateOf<CategoriesUiState>(CategoriesUiState.Loading)
        content(dark = true) { CategoriesScreen(state.value, { retries++ }, {}, presentation = policy) }
        rule.onNodeWithTag(CatalogTestTags.CATEGORIES_GRID).assertIsDisplayed()
        rule.runOnIdle { state.value = CategoriesUiState.Empty }
        rule.onNodeWithTag(CatalogTestTags.CATEGORIES_EMPTY).assertIsDisplayed()
        val failure = CatalogLoadFailure(CatalogLoadFailureCategory.CONNECTION, true)
        rule.runOnIdle { state.value = CategoriesUiState.Error(failure) }
        rule.onNodeWithTag(CatalogTestTags.CATEGORIES_ERROR).assertIsDisplayed()
        rule.onNodeWithText(rule.activity.getString(com.gurbakir.mobile.core.R.string.retry)).performClick()
        assertEquals(1, retries)
        rule.runOnIdle {
            state.value =
                CategoriesUiState.Content(listOf(category("remaining", "Merchant")), failure, true)
        }
        rule.onNodeWithTag(CatalogTestTags.category("remaining")).assertIsDisplayed()
        rule.onNodeWithTag(CatalogTestTags.CATEGORIES_GRID)
            .performScrollToNode(hasTestTag(CatalogTestTags.CATEGORIES_PARTIAL_ERROR))
        rule.onNodeWithTag(CatalogTestTags.CATEGORIES_PARTIAL_ERROR).assertIsDisplayed()
        rule.onNodeWithText(rule.activity.getString(com.gurbakir.mobile.core.R.string.retry)).performClick()
        assertEquals(2, retries)
    }

    @Test
    fun galleryListingKeepsLoadingNotFoundEmptyAndErrorRecovery() {
        var retries = 0
        val state = mutableStateOf(CollectionUiState(handle = "test", loadingInitial = true))
        content {
            CollectionScreen(state.value, CollectionActions({}, { retries++ }, {}, {}, {}), presentation = policy)
        }
        rule.onNodeWithTag(CatalogTestTags.COLLECTION_LOADING).assertIsDisplayed()
        rule.runOnIdle { state.value = CollectionUiState(handle = "test", notFound = true) }
        rule.onNodeWithTag(CatalogTestTags.COLLECTION_NOT_FOUND).assertIsDisplayed()
        rule.runOnIdle { state.value = CollectionUiState(handle = "test") }
        rule.onNodeWithTag(CatalogTestTags.COLLECTION_EMPTY).assertIsDisplayed()
        rule.runOnIdle {
            state.value = CollectionUiState(
                handle = "test",
                initialFailure = CatalogLoadFailure(CatalogLoadFailureCategory.CONNECTION, true)
            )
        }
        rule.onNodeWithTag(CatalogTestTags.COLLECTION_ERROR).assertIsDisplayed()
        rule.onNodeWithText(rule.activity.getString(com.gurbakir.mobile.core.R.string.retry)).performClick()
        assertEquals(1, retries)
    }

    @Test
    fun largeTextDiscoveryStacksRootCoversWithoutClippingLabels() {
        content(scale = 2f, dark = true) {
            CategoriesScreen(CategoriesUiState.Content(categories(), null), {}, {}, presentation = policy)
        }
        val grid = rule.onNodeWithTag(CatalogTestTags.CATEGORIES_GRID)
        grid.performScrollToNode(hasTestTag(CatalogTestTags.category("originals")))
        val cover = rule.onNodeWithTag(CatalogTestTags.category("originals"))
        cover.assertIsDisplayed()
        rule.onNodeWithTag(CatalogTestTags.categoryLabel("originals"), useUnmergedTree = true)
            .assertTextEquals("Merchant originals")
        val media = rule.onNodeWithTag(CatalogTestTags.categoryMedia("originals"), useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        assertTrue(abs(media.width / media.height - .8f) < .03f)
        assertTrue(media.width > grid.fetchSemanticsNode().boundsInRoot.width * .8f)
        grid.performScrollToNode(hasTestTag(CatalogTestTags.category("modern")))
        rule.onNodeWithTag(CatalogTestTags.category("modern")).assertIsDisplayed()
    }

    @Test
    fun galleryKeepsRootCoversAndEveryNestedAndAllProductsAction() {
        val items = categories()
        val opened = mutableListOf<String>()
        content {
            CategoriesScreen(CategoriesUiState.Content(items, null), {}, { opened += it }, presentation = policy)
        }
        rule.onNodeWithTag(CatalogTestTags.GALLERY_FEATURED).assertIsDisplayed()
        rule.onNodeWithTag(CatalogTestTags.categoryLabel("ustalar"), useUnmergedTree = true)
            .assertTextEquals("Merchant featured label")
        items.forEach { item ->
            rule.onNodeWithTag(CatalogTestTags.CATEGORIES_GRID)
                .performScrollToNode(hasTestTag(CatalogTestTags.category(item.collection.handle)))
            rule.onNodeWithTag(CatalogTestTags.category(item.collection.handle)).performClick()
        }
        assertEquals(
            listOf("ustalar", "artist-one", "originals", "modern", "zen", "children", "extra", "tum-urunler"),
            opened
        )
    }

    @Test fun normalGalleryUsesTwoFourByFiveCoversWithoutInsetMats() = assertCoverLayout(1f)

    @Test fun moderateTextKeepsTwoGalleryCoversAtNarrowWidth() = assertCoverLayout(1.3f)

    @Test
    fun galleryIntroKeepsCompactEditorialHierarchyAndSystemTextScaling() {
        content {
            CategoriesScreen(CategoriesUiState.Content(categories(), null), {}, {}, presentation = policy)
        }
        val title = rule.onNodeWithTag(CatalogTestTags.GALLERY_INTRO_TITLE, useUnmergedTree = true)
        val body = rule.onNodeWithTag(CatalogTestTags.GALLERY_INTRO_BODY, useUnmergedTree = true)
        val titleLayouts = mutableListOf<TextLayoutResult>()
        val bodyLayouts = mutableListOf<TextLayoutResult>()
        title.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(titleLayouts) }
        body.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(bodyLayouts) }
        assertEquals(24f, titleLayouts.single().layoutInput.style.fontSize.value)
        assertEquals(13f, bodyLayouts.single().layoutInput.style.fontSize.value)
        assertCompleteTextLayout(titleLayouts.single())
        assertCompleteTextLayout(bodyLayouts.single())
        content(scale = 2f, dark = true) {
            CategoriesScreen(CategoriesUiState.Content(categories(), null), {}, {}, presentation = policy)
        }
        val largeTitleLayouts = mutableListOf<TextLayoutResult>()
        rule.onNodeWithTag(CatalogTestTags.GALLERY_INTRO_TITLE, useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(largeTitleLayouts) }
        assertTrue(largeTitleLayouts.single().lineCount >= titleLayouts.single().lineCount)
        assertCompleteTextLayout(largeTitleLayouts.single())
    }

    @Test
    fun missingFeaturedAndAllProductsKeepOtherMerchantActionsWithoutInventingLinks() {
        content(dark = true) {
            CategoriesScreen(
                CategoriesUiState.Content(listOf(category("remaining", "Remaining merchant label")), null),
                {},
                {},
                presentation = policy
            )
        }
        rule.onNodeWithTag(CatalogTestTags.GALLERY_FEATURED).assertDoesNotExist()
        rule.onNodeWithTag(CatalogTestTags.category("tum-urunler")).assertDoesNotExist()
        rule.onNodeWithTag(CatalogTestTags.category("remaining")).assertIsDisplayed().performClick()
    }

    @Test
    fun galleryListingPreservesSortFilterPagingPriceAndIndependentWishlist() {
        var opened = 0
        var saved: String? = null
        var sort: CollectionCatalogSort? = null
        var filter: String? = null
        var loads = 0
        val product = product()
        content(dark = true) {
            CollectionScreen(
                CollectionUiState(
                    handle = "test",
                    title = "Merchant collection",
                    products = listOf(product),
                    hasNextPage = true,
                    productTypeFilter = CatalogProductTypeFilter(
                        "Type",
                        listOf(
                            CatalogProductTypeValue("print", "Print", 2)
                        )
                    )
                ),
                CollectionActions(
                    {},
                    {},
                    { sort = it },
                    { filter = it },
                    { loads++ },
                    onOpenProduct = { opened++ },
                    onSetWishlist = { id, _ -> saved = id }
                ),
                WishlistMembershipUiState(),
                presentation = policy
            )
        }
        rule.onNodeWithTag(CatalogTestTags.filter("print")).performClick()
        assertEquals("print", filter)
        rule.onNodeWithTag(CatalogTestTags.COLLECTION_SORT).assertHeightIsAtLeast(48.dp).performClick()
        rule.onNodeWithText(
            rule.activity.getString(com.gurbakir.mobile.core.R.string.collection_sort_newest)
        ).performClick()
        assertEquals(CollectionCatalogSort.NEWEST, sort)
        val grid = rule.onNodeWithTag(CatalogTestTags.COLLECTION_GRID)
        grid.performScrollToIndex(1)
        val media = rule.onNodeWithTag(CatalogTestTags.productMedia(product.handle), useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val heart = rule.onNodeWithTag(WishlistTestTags.toggle(product.id)).assertHeightIsAtLeast(48.dp)
        assertTrue(abs(media.width / media.height - .8f) < .03f)
        assertTrue(heart.fetchSemanticsNode().boundsInRoot.top >= media.bottom)
        heart.performClick()
        assertEquals(product.id, saved)
        assertEquals(0, opened)
        rule.onNodeWithTag(CatalogTestTags.product(product.handle)).performClick()
        assertEquals(1, opened)
        rule.onNodeWithTag(CatalogTestTags.productPrice(product.handle), useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithTag(
            CatalogTestTags.productAvailability(product.handle),
            useUnmergedTree = true
        ).assertIsDisplayed()
        grid.performScrollToIndex(2)
        rule.onNodeWithTag(CatalogTestTags.COLLECTION_LOAD_MORE).performClick()
        assertEquals(1, loads)
    }

    @Test
    fun largeTextReflowsProductsAndKeepsLongTurkishTitleComplete() {
        val product = product().copy(title = "Çiçek Açan Dallar ve Yaşam Alanları İçin Uzun Türkçe Eser Başlığı")
        content(scale = 2f) {
            CollectionScreen(
                CollectionUiState(handle = "test", title = "Eserler", products = listOf(product)),
                CollectionActions({}, {}, {}, {}, {}),
                presentation = policy
            )
        }
        val grid = rule.onNodeWithTag(CatalogTestTags.COLLECTION_GRID)
        grid.performScrollToIndex(1)
        val tile = rule.onNodeWithTag(CatalogTestTags.product(product.handle)).fetchSemanticsNode().boundsInRoot
        assertTrue(tile.width > grid.fetchSemanticsNode().boundsInRoot.width * .8f)
        val title = rule.onNodeWithTag(CatalogTestTags.productTitle(product.handle), useUnmergedTree = true)
        title.assertTextEquals(product.title)
        val layouts = mutableListOf<TextLayoutResult>()
        title.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertTrue(layouts.single().lineCount > 2)
        assertTrue(!layouts.single().hasVisualOverflow)
    }

    @Test
    fun galleryPartialPageErrorKeepsProductsAndRetriesThePage() {
        var retried = 0
        content {
            CollectionScreen(
                CollectionUiState(
                    handle = "test",
                    title = "Collection",
                    products = listOf(product()),
                    nextPageFailure = CatalogLoadFailure(CatalogLoadFailureCategory.CONNECTION, true)
                ),
                CollectionActions({}, {}, {}, {}, { retried++ }),
                presentation = policy
            )
        }
        val grid = rule.onNodeWithTag(CatalogTestTags.COLLECTION_GRID)
        grid.performScrollToIndex(1)
        rule.onNodeWithTag(CatalogTestTags.product("test-product")).assertIsDisplayed()
        grid.performScrollToIndex(2)
        rule.onNodeWithTag(CatalogTestTags.COLLECTION_NEXT_ERROR).assertIsDisplayed()
        rule.onNodeWithText(rule.activity.getString(com.gurbakir.mobile.core.R.string.retry)).performClick()
        assertEquals(1, retried)
    }

    @Test
    fun galleryPreservesPortraitAndLandscapeArtworkEdges() {
        // ATD CI disables drawing; run this pixel proof explicitly on a rendering-enabled target.
        org.junit.Assume.assumeTrue(
            androidx.test.platform.app.InstrumentationRegistry.getArguments().getString("captureCatalog") == "true"
        )
        val context = rule.activity
        val files = listOf(300 to 600, 600 to 300).mapIndexed { index, (width, height) ->
            java.io.File(context.cacheDir, "p3-fit-$index.png").also { file ->
                val bitmap = android.graphics.Bitmap.createBitmap(
                    width,
                    height,
                    android.graphics.Bitmap.Config.ARGB_8888
                )
                val canvas = android.graphics.Canvas(bitmap)
                canvas.drawColor(android.graphics.Color.GREEN)
                canvas.drawRect(
                    24f,
                    24f,
                    width - 24f,
                    height - 24f,
                    android.graphics.Paint().apply { color = android.graphics.Color.RED }
                )
                file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
            }
        }
        try {
            val products = files.mapIndexed { index, file ->
                product().copy(
                    id = "gid://shopify/Product/fit-$index",
                    handle = "fit-$index",
                    media = StorefrontMedia(file.toURI(), "Owned edge preservation test pattern", null, null)
                )
            }
            content {
                CollectionScreen(
                    CollectionUiState(handle = "test", title = "Fit", products = products),
                    CollectionActions({}, {}, {}, {}, {}),
                    presentation = policy
                )
            }
            val frames = products.map {
                rule.onNodeWithTag(CatalogTestTags.productMedia(it.handle), useUnmergedTree = true)
                    .fetchSemanticsNode().boundsInRoot
            }
            // Fit retains both short-edge borders. Crop removes them for each opposite aspect ratio.
            rule.waitUntil(10_000) {
                val bitmap = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
                    .uiAutomation.takeScreenshot() ?: return@waitUntil false
                val points = listOf(
                    frames[0].center.x to (frames[0].top + frames[0].height * .03f),
                    frames[0].center.x to (frames[0].bottom - frames[0].height * .03f),
                    (frames[1].left + frames[1].width * .03f) to frames[1].center.y,
                    (frames[1].right - frames[1].width * .03f) to frames[1].center.y
                )
                val allEdgesVisible = points.all { (x, y) ->
                    val color = bitmap.getPixel(x.toInt(), y.toInt())
                    android.graphics.Color.green(color) > 200 && android.graphics.Color.red(color) < 40 &&
                        android.graphics.Color.blue(color) < 40
                }
                bitmap.recycle()
                allEdgesVisible
            }
        } finally {
            files.forEach { it.delete() }
        }
    }

    private fun assertCompleteTextLayout(layout: TextLayoutResult) {
        assertTrue(layout.lineCount > 0)
        assertTrue((0 until layout.lineCount).none(layout::isLineEllipsized))
        assertEquals(layout.layoutInput.text.length, layout.getLineEnd(layout.lineCount - 1, visibleEnd = true))
    }

    private fun assertCoverLayout(scale: Float) {
        content(scale = scale) {
            CategoriesScreen(CategoriesUiState.Content(categories(), null), {}, {}, presentation = policy)
        }
        rule.onNodeWithTag(CatalogTestTags.CATEGORIES_GRID)
            .performScrollToNode(hasTestTag(CatalogTestTags.category("originals")))
        val first = rule.onNodeWithTag(CatalogTestTags.categoryMedia("originals"), useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val second = rule.onNodeWithTag(CatalogTestTags.categoryMedia("modern"), useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        assertTrue(abs(first.width / first.height - .8f) < .03f)
        val density = rule.activity.resources.displayMetrics.density
        assertTrue(first.width / density <= 136.5f)
        assertTrue(abs(first.top - second.top) < 1f && second.left > first.left)
    }

    private fun content(scale: Float = 1f, dark: Boolean = false, content: @Composable () -> Unit) {
        rule.runOnUiThread {
            rule.activity.setContent {
                TumiArtThemeProbe(darkTheme = dark) {
                    val density = LocalDensity.current
                    CompositionLocalProvider(LocalDensity provides Density(density.density, scale)) {
                        Box(Modifier.requiredWidth(320.dp)) { content() }
                    }
                }
            }
        }
        rule.waitForIdle()
    }

    private fun categories() = listOf(
        category("ustalar", "Merchant featured label"),
        category("artist-one", "Nested merchant choice").copy(depth = 1, parentStableId = "ustalar"),
        category("originals", "Merchant originals"),
        category("modern", "Merchant modern"),
        category("zen", "Merchant botanical"),
        category("children", "Merchant children"),
        category("extra", "Extra merchant collection"),
        category("tum-urunler", "All merchant products")
    )

    private fun category(handle: String, title: String) = CatalogCategoryItem(
        handle,
        title,
        CatalogDiscoveryCollection(
            handle,
            handle,
            "Different resource title",
            StorefrontMedia(
                URI("file:///android_asset/missing-test-image.png"),
                null,
                300,
                400
            ),
            CatalogDiscoveryMediaSource.COLLECTION,
            "Actual merchant description"
        )
    )

    private fun product() = CatalogProductSummary(
        "gid://shopify/Product/test",
        "test-product",
        "Test artwork",
        false,
        null,
        StorefrontMoney(BigDecimal("840.00"), "TRY"),
        StorefrontMoney(BigDecimal("1490.00"), "TRY")
    )
}
