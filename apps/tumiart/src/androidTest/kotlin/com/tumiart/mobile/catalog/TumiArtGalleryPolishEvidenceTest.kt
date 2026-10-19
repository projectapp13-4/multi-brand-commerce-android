@file:Suppress("MagicNumber", "LongMethod")

package com.tumiart.mobile.catalog

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.gurbakir.mobile.catalog.CatalogCategoryItem
import com.gurbakir.mobile.catalog.CatalogTestTags
import com.gurbakir.mobile.catalog.CategoriesScreen
import com.gurbakir.mobile.catalog.CategoriesUiState
import com.gurbakir.mobile.catalog.CollectionActions
import com.gurbakir.mobile.catalog.CollectionScreen
import com.gurbakir.mobile.catalog.CollectionUiState
import com.gurbakir.mobile.product.ProductDetailScreen
import com.gurbakir.mobile.wishlist.WishlistActions
import com.gurbakir.mobile.wishlist.WishlistMembershipUiState
import com.gurbakir.mobile.wishlist.WishlistResolvedEntry
import com.gurbakir.mobile.wishlist.WishlistScreen
import com.gurbakir.mobile.wishlist.WishlistTestTags
import com.gurbakir.mobile.wishlist.WishlistUiState
import com.gurbakir.storefront.CatalogDiscoveryCollection
import com.gurbakir.storefront.CatalogProductSummary
import com.gurbakir.storefront.StorefrontMedia
import com.gurbakir.storefront.StorefrontMoney
import com.gurbakir.storefront.StorefrontProductMedia
import com.gurbakir.storefront.StorefrontProductOptionValue
import com.gurbakir.storefront.StorefrontSelectedOption
import com.tumiart.mobile.config.TumiArtConfiguration
import com.tumiart.mobile.design.TumiArtThemeProbe
import com.tumiart.mobile.design.TumiArtThemeProbeActivity
import com.tumiart.mobile.product.artworkActions
import com.tumiart.mobile.product.artworkState
import java.io.File
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Opt-in owned fixtures, actual system width/font scale; no provider, account or commerce mutations. */
@RunWith(AndroidJUnit4::class)
class TumiArtGalleryPolishEvidenceTest {
    @get:Rule val rule = createAndroidComposeRule<TumiArtThemeProbeActivity>()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val arguments get() = InstrumentationRegistry.getArguments()
    private val policy get() = TumiArtConfiguration.catalog.presentation

    @Test
    fun captureChangedSurfacesAtActualSystemTextScale() {
        assumeTrue(arguments.getString("captureGalleryPolish") == "true")
        val media = ownedMedia()
        val detail = artworkState().product!!.let { product ->
            product.copy(
                media = listOf(StorefrontProductMedia("owned", media)),
                description = "İlk paragraf.İkinci paragraf.Baskı HakkındaÖlçü: 30 × 40 cm." +
                    "GönderimKaynak açıklaması. https://tumiart.com",
                descriptionHtml = "<p>İlk paragraf.</p><p>İkinci paragraf.</p><h3>Baskı Hakkında</h3>" +
                    "<p>Ölçü: 30 × 40 cm.</p><h3>Gönderim</h3>" +
                    "<p>Kaynak açıklaması. https://tumiart.com</p>",
                options = product.options.map {
                    it.copy(values = it.values + StorefrontProductOptionValue("value-large", "50 × 70 cm"))
                },
                variants = product.variants + product.variants.single().copy(
                    id = "gid://shopify/ProductVariant/9002",
                    price = StorefrontMoney(BigDecimal("6690.00"), "TRY"),
                    selectedOptions = listOf(StorefrontSelectedOption(product.options.single().name, "50 × 70 cm"))
                )
            )
        }
        val products = (1..4).map { index ->
            CatalogProductSummary(
                "gid://shopify/Product/$index",
                "owned-$index",
                if (index % 2 == 0) "Çiçekler" else "Çiçek Açan Dallar ve Doğal Biçimlerin Sessiz Diyaloğu",
                true,
                media,
                StorefrontMoney(BigDecimal("1090.00"), "TRY"),
                StorefrontMoney(BigDecimal("6690.00"), "TRY")
            )
        }
        var opened = 0
        var saved = 0
        show {
            CollectionScreen(
                CollectionUiState(handle = "owned", title = "Eserler", products = products),
                CollectionActions({
                }, {}, {}, {}, {}, onOpenProduct = { opened++ }, onSetWishlist = { _, _ -> saved++ }),
                WishlistMembershipUiState(),
                presentation = policy
            )
        }
        val grid = rule.onNodeWithTag(CatalogTestTags.COLLECTION_GRID)
        grid.performScrollToNode(hasTestTag(CatalogTestTags.product(products.first().handle)))
        capture("collection-label")
        rule.onNodeWithTag(WishlistTestTags.toggle(products.first().id)).assertHeightIsAtLeast(48.dp).performClick()
        assertEquals(1, saved)
        assertEquals(0, opened)
        // Price belongs to the tile's merged product action; scroll by the real final lazy item,
        // then inspect the unmerged text bounds rather than asking the merged tree to find it.
        grid.performScrollToIndex(products.size + 1)
        capture("collection-bottom")
        assertComplete(CatalogTestTags.productPrice(products.last().handle))
        val price = rule.onNodeWithTag(CatalogTestTags.productPrice(products.last().handle), true)
            .fetchSemanticsNode().boundsInRoot
        val bounds = grid.fetchSemanticsNode().boundsInRoot
        val navigationInset = ViewCompat.getRootWindowInsets(rule.activity.window.decorView)
            ?.getInsets(WindowInsetsCompat.Type.navigationBars())?.bottom ?: 0
        assertTrue(
            "Last complete price can scroll above the system navigation area",
            price.bottom <= bounds.bottom - navigationInset + 1f
        )

        var removed = 0
        show {
            WishlistScreen(
                WishlistUiState(entries = listOf(WishlistResolvedEntry(detail.id, 1L, detail))),
                WishlistActions({}, {}, {}, { removed++ }, {}),
                presentation = policy
            )
        }
        rule.onNodeWithTag(WishlistTestTags.CONTENT)
            .performScrollToNode(hasTestTag(CatalogTestTags.product(detail.handle)))
        capture("wishlist")
        assertComplete(CatalogTestTags.productTitle(detail.handle))
        assertComplete(CatalogTestTags.productPrice(detail.handle))
        rule.onNodeWithTag(WishlistTestTags.toggle(detail.id)).assertHeightIsAtLeast(48.dp).performClick()
        assertEquals(1, removed)

        val categories = (0..6).map { index ->
            val handle = if (index == 0) "ustalar" else "artist-$index"
            CatalogCategoryItem(
                handle,
                if (index == 0) "Ustalar" else "Sanatçı seçkisi $index",
                CatalogDiscoveryCollection(handle, handle, "Owned collection", media),
                depth = if (index == 0) 0 else 1,
                parentStableId = if (index == 0) null else "ustalar"
            )
        }
        show { CategoriesScreen(CategoriesUiState.Content(categories, null), {}, {}, presentation = policy) }
        rule.onNodeWithTag(CatalogTestTags.CATEGORIES_GRID)
            .performScrollToNode(hasTestTag(CatalogTestTags.category("artist-6")))
        rule.onNodeWithTag(CatalogTestTags.category("artist-6")).assertIsDisplayed().assertHeightIsAtLeast(48.dp)
        capture("discovery-continuation")

        show {
            ProductDetailScreen(
                artworkState().copy(product = detail),
                artworkActions(),
                presentation = TumiArtConfiguration.productPresentation
            )
        }
        rule.onNodeWithTag("product-detail-content").performScrollToNode(hasTestTag("product-detail-description"))
        assertComplete("product-detail-description")
        rule.onNodeWithTag("product-detail-add-to-cart").assertIsDisplayed().assertHeightIsAtLeast(48.dp)
        capture("artwork-description")
    }

    private fun show(content: @Composable () -> Unit) {
        rule.activity.setContent {
            TumiArtThemeProbe(darkTheme = arguments.getString("dark") == "true") { content() }
        }
        rule.waitForIdle()
    }

    private fun assertComplete(tag: String) {
        val layouts = mutableListOf<TextLayoutResult>()
        rule.onNodeWithTag(tag, true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val layout = layouts.single()
        // A shrink-wrapped Text can report paragraph-width overflow although every glyph fits.
        // Check actual line extents (one physical pixel for rounding), all characters and ellipsis.
        val lines = 0 until layout.lineCount
        val glyphRight = lines.maxOf(layout::getLineRight)
        println(
            "NATIVE_TEXT_BOUNDS $tag size=${layout.size} paragraphWidth=${layout.multiParagraph.width} glyphRight=$glyphRight"
        )
        assertFalse("$tag must retain every line vertically", layout.didOverflowHeight)
        assertTrue(
            "$tag glyphs must stay within its text box",
            lines.all {
                layout.getLineLeft(it) >= -1f && layout.getLineRight(it) <= layout.size.width + 1f
            }
        )
        assertTrue("$tag must not ellipsize", lines.none(layout::isLineEllipsized))
        assertEquals(layout.layoutInput.text.length, layout.getLineEnd(layout.lineCount - 1, true))
    }

    private fun capture(label: String) {
        rule.waitForIdle()
        instrumentation.waitForIdleSync()
        android.os.SystemClock.sleep(300)
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        val config = rule.activity.resources.configuration
        val name = "api${android.os.Build.VERSION.SDK_INT}-${config.screenWidthDp}dp-${config.fontScale}-" +
            "${config.locales.toLanguageTags()}-${arguments.getString("dark") ?: "false"}-$label.png"
        val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "gallery-polish").apply {
            mkdirs()
        }
        File(directory, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    private fun ownedMedia(): StorefrontMedia {
        val file = File(rule.activity.cacheDir, "owned-polish-art.png")
        val bitmap = Bitmap.createBitmap(300, 400, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.rgb(232, 221, 208))
        canvas.drawOval(30f, 40f, 250f, 280f, Paint().apply { color = Color.rgb(83, 106, 93) })
        canvas.drawRect(120f, 210f, 280f, 350f, Paint().apply { color = Color.rgb(88, 111, 135) })
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        return StorefrontMedia(file.toURI(), "Project-owned geometric fixture", 300, 400)
    }
}
