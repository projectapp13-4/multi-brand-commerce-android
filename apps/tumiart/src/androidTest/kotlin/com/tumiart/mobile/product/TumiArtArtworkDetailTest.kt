@file:Suppress("MagicNumber", "LongMethod")

package com.tumiart.mobile.product

import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.espresso.Espresso.pressBack
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.gurbakir.mobile.product.ProductDetailActions
import com.gurbakir.mobile.product.ProductDetailScreen
import com.gurbakir.mobile.product.ProductDetailUiState
import com.gurbakir.storefront.StorefrontMedia
import com.gurbakir.storefront.StorefrontMoney
import com.gurbakir.storefront.StorefrontProductDetail
import com.gurbakir.storefront.StorefrontProductMedia
import com.gurbakir.storefront.StorefrontProductOption
import com.gurbakir.storefront.StorefrontProductOptionValue
import com.gurbakir.storefront.StorefrontProductVariant
import com.gurbakir.storefront.StorefrontSelectedOption
import com.tumiart.mobile.config.TumiArtConfiguration
import com.tumiart.mobile.design.TumiArtThemeProbe
import com.tumiart.mobile.design.TumiArtThemeProbeActivity
import java.math.BigDecimal
import java.net.URI
import kotlin.math.roundToInt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TumiArtArtworkDetailTest {
    @get:Rule val rule = createAndroidComposeRule<TumiArtThemeProbeActivity>()

    @Test
    fun galleryUsesCanonicalFunctionalTitleAndPriceWithoutClippingLongMerchantNames() {
        show {
            ProductDetailScreen(
                artworkState(),
                artworkActions(),
                presentation = TumiArtConfiguration.productPresentation
            )
        }
        val content = rule.onNodeWithTag("product-detail-content")
        content.performScrollToNode(hasTestTag("product-detail-title"))
        val layouts = mutableListOf<TextLayoutResult>()
        rule.onNodeWithTag("product-detail-title").performSemanticsAction(SemanticsActions.GetTextLayoutResult) {
            it(layouts)
        }
        val layout = layouts.single()
        assertEquals(20.sp, layout.layoutInput.style.fontSize)
        assertEquals(27.sp, layout.layoutInput.style.lineHeight)
        assertTrue(!layout.hasVisualOverflow)
        content.performScrollToNode(hasTestTag("product-detail-price"))
        layouts.clear()
        rule.onNodeWithTag("product-detail-price").performSemanticsAction(SemanticsActions.GetTextLayoutResult) {
            it(layouts)
        }
        assertEquals(17.sp, layouts.single().layoutInput.style.fontSize)
    }

    @Test
    fun incompleteAndPendingSelectionsNeverEnablePurchaseAndSoldOutRemainsExplained() {
        var state by mutableStateOf(artworkState(selected = false))
        var adds = 0
        show {
            ProductDetailScreen(
                state,
                artworkActions(add = {
                    adds++
                }),
                presentation = TumiArtConfiguration.productPresentation
            )
        }
        rule.onNodeWithTag("product-detail-add-to-cart").assertIsNotEnabled().assertHeightIsAtLeast(48.dp)
        rule.onNodeWithTag("product-detail-purchase-availability").assertIsDisplayed()
        rule.runOnIdle { state = artworkState().copy(addingToCart = true) }
        rule.onNodeWithTag("product-detail-add-to-cart").assertIsNotEnabled()
        rule.runOnIdle {
            state = artworkState().let { selected ->
                selected.copy(
                    product = selected.product?.let {
                        it.copy(variants = it.variants.map { v -> v.copy(availableForSale = false) })
                    }
                )
            }
        }
        rule.onNodeWithTag("product-detail-add-to-cart").assertIsNotEnabled()
        rule.onNodeWithTag("product-detail-purchase-availability").assertIsDisplayed()
        assertEquals(0, adds)
    }

    @Test
    fun missingMediaIsHonestAndPurchaseStillUsesTheSelectedMerchandise() {
        var adds = 0
        show {
            ProductDetailScreen(
                artworkState(),
                artworkActions(add = {
                    adds++
                }),
                presentation = TumiArtConfiguration.productPresentation
            )
        }
        rule.onNodeWithTag("artwork-media-empty").assertIsDisplayed()
        rule.onNodeWithTag("product-detail-add-to-cart").assertIsEnabled().performClick()
        assertEquals(1, adds)
    }

    @Test
    fun galleryViewerHasAccessibleZoomResetBackAndOpenerFocusRestoration() {
        val media = StorefrontMedia(URI("file:///android_asset/missing-p4-test.png"), "Owned fixture", 240, 480)
        var state by mutableStateOf(
            artworkState().let {
                it.copy(product = it.product?.copy(media = listOf(StorefrontProductMedia("owned", media))))
            }
        )
        show {
            ProductDetailScreen(
                state,
                artworkActions(open = { state = state.copy(mediaViewerOpen = true) }, close = {
                    state =
                        state.copy(mediaViewerOpen = false)
                }),
                presentation = TumiArtConfiguration.productPresentation
            )
        }
        rule.onNodeWithTag("product-detail-media-open").performSemanticsAction(SemanticsActions.OnClick) { it() }
        rule.waitForIdle()
        rule.onNodeWithTag("product-detail-media-viewer").assertIsDisplayed()
        rule.onNodeWithTag("artwork-zoom-in").assertHeightIsAtLeast(48.dp).performClick()
        rule.onNodeWithTag("artwork-zoom-out").assertIsEnabled()
        rule.onNodeWithTag("artwork-zoom-fit").performClick()
        rule.onNodeWithTag("artwork-zoom-out").assertIsNotEnabled()
        pressBack()
        rule.waitForIdle()
        rule.onNodeWithTag("product-detail-media-viewer").assertDoesNotExist()
        rule.onNodeWithTag("product-detail-media-open").assertIsFocused()
    }

    @Test
    fun zoomedArtworkWithMissingMetadataCanPanToItsActualTopEdge() {
        val context = rule.activity
        val file = java.io.File(context.cacheDir, "owned-null-dimensions.png")
        val bitmap = android.graphics.Bitmap.createBitmap(240, 480, android.graphics.Bitmap.Config.ARGB_8888)
        android.graphics.Canvas(bitmap).apply {
            drawColor(android.graphics.Color.BLUE)
            drawRect(0f, 0f, 240f, 48f, android.graphics.Paint().apply { color = android.graphics.Color.RED })
        }
        file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        val media = StorefrontMedia(file.toURI(), "Owned portrait without metadata", null, null)
        val state = artworkState().let {
            it.copy(
                product = it.product?.copy(media = listOf(StorefrontProductMedia("owned", media))),
                mediaViewerOpen = true
            )
        }
        show { ProductDetailScreen(state, artworkActions(), presentation = TumiArtConfiguration.productPresentation) }
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("artwork-image-loading").fetchSemanticsNodes().isEmpty() }
        repeat(2) { rule.onNodeWithTag("artwork-zoom-in").performClick() }
        val viewport = rule.onNodeWithTag("artwork-zoom-viewport")
        val topLabel = rule.activity.getString(com.gurbakir.mobile.core.R.string.artwork_pan_up)
        repeat(4) {
            val top = viewport.fetchSemanticsNode().config[SemanticsActions.CustomActions].single {
                it.label == topLabel
            }
            rule.runOnIdle { top.action() }
        }
        assertEquals(android.graphics.Color.RED, viewportTopPixel(viewport))
    }

    @Test
    fun galleryControlsKeepTheRealMediaCountAndSelectedIndex() {
        val portrait = StorefrontMedia(URI("file:///android_asset/p4-missing-portrait.png"), null, 240, 480)
        val landscape = portrait.copy(
            uri = URI("file:///android_asset/p4-missing-landscape.png"),
            width = 480,
            height = 240
        )
        var state by mutableStateOf(
            artworkState().let {
                it.copy(
                    product = it.product?.copy(
                        media = listOf(
                            StorefrontProductMedia("portrait", portrait),
                            StorefrontProductMedia("landscape", landscape)
                        )
                    )
                )
            }
        )
        show {
            ProductDetailScreen(
                state,
                artworkActions(media = {
                    state = state.copy(mediaIndex = it)
                }),
                presentation = TumiArtConfiguration.productPresentation
            )
        }
        rule.onNodeWithTag("artwork-media-count").assertTextEquals("1 / 2")
        rule.onNodeWithTag("product-detail-media-next").assertHeightIsAtLeast(48.dp)
            .performSemanticsAction(SemanticsActions.OnClick) { it() }
        rule.onNodeWithTag("artwork-media-count").assertTextEquals("2 / 2")
        rule.onNodeWithTag("product-detail-media-next").assertIsNotEnabled()
        rule.onNodeWithTag("product-detail-media-previous").assertIsEnabled()
        assertEquals(1, state.mediaIndex)
    }

    private fun viewportTopPixel(viewport: SemanticsNodeInteraction): Int {
        if (android.os.Build.VERSION.SDK_INT >= 28) {
            val frame = viewport.captureToImage().asAndroidBitmap()
            return frame.getPixel(frame.width / 2, 10)
        }
        // Compose cannot capture Dialog windows below API28; retain the same real-pixel assertion.
        rule.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        android.os.SystemClock.sleep(300)
        val bounds = viewport.fetchSemanticsNode().boundsInWindow
        val frame = captureArtworkSystemFrame()
        return try {
            frame.getPixel(bounds.center.x.roundToInt(), bounds.top.roundToInt() + 10)
        } finally {
            frame.recycle()
        }
    }

    private fun show(content: @Composable () -> Unit) {
        rule.activity.setContent { TumiArtThemeProbe { content() } }
    }
}

internal fun artworkState(selected: Boolean = true): ProductDetailUiState {
    val option =
        StorefrontProductOption("option", "Baskı ölçüsü", listOf(StorefrontProductOptionValue("value", "30 × 40 cm")))
    val variant = StorefrontProductVariant(
        "gid://shopify/ProductVariant/9001",
        "30 × 40 cm",
        true,
        false,
        StorefrontMoney(BigDecimal("1490.00"), "TRY"),
        null,
        null,
        listOf(StorefrontSelectedOption(option.name, option.values.single().name))
    )
    return ProductDetailUiState(
        product = StorefrontProductDetail(
            "gid://shopify/Product/9001",
            "owned-test-art",
            "Uzun eser adı — doğal biçimler ve renklerin sessiz diyaloğu",
            "Proje tarafından üretilmiş test içeriği. Satış veya sanatçı iddiası içermez.",
            true,
            listOf(option),
            listOf(variant),
            emptyList()
        ),
        selectedOptions = if (selected) mapOf(option.name to option.values.single().name) else emptyMap()
    )
}

internal fun artworkActions(
    add: () -> Unit = {},
    media: (Int) -> Unit = {},
    open: () -> Unit = {},
    close: () -> Unit = {},
    option: (String, String) -> Unit = { _, _ -> }
): ProductDetailActions = ProductDetailActions(
    onBack = {},
    onBrowse = {},
    onRetry = {},
    onSelectOption = option,
    onSelectMedia = media,
    onOpenMediaViewer = open,
    onCloseMediaViewer = close,
    onAddToCart = add
)
