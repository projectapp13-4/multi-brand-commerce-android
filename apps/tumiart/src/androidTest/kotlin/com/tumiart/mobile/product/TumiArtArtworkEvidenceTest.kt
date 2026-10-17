@file:Suppress("MagicNumber", "LongMethod")

package com.tumiart.mobile.product

import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.os.ConfigurationCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import com.gurbakir.mobile.product.DefaultProductDetailRepository
import com.gurbakir.mobile.product.ProductDetailLoad
import com.gurbakir.mobile.product.ProductDetailScreen
import com.gurbakir.mobile.product.ProductDetailUiState
import com.gurbakir.storefront.StorefrontApolloClientFactory
import com.gurbakir.storefront.StorefrontMedia
import com.gurbakir.storefront.StorefrontMediaPolicy
import com.gurbakir.storefront.StorefrontProductMedia
import com.tumiart.mobile.config.TumiArtConfiguration
import com.tumiart.mobile.design.TumiArtThemeProbe
import com.tumiart.mobile.design.TumiArtThemeProbeActivity
import java.io.File
import java.util.Locale
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Opt-in, private native evidence. Live mode only reads products/images; never constructs a cart. */
@RunWith(AndroidJUnit4::class)
class TumiArtArtworkEvidenceTest {
    @get:Rule val rule = createAndroidComposeRule<TumiArtThemeProbeActivity>()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val arguments get() = InstrumentationRegistry.getArguments()
    private var contentGeneration = 0
    private fun directory() = File(instrumentation.targetContext.getExternalFilesDir(null), "p4-artwork").apply {
        mkdirs()
    }

    @Test
    fun captureFixtureMatrix() {
        assumeTrue(arguments.getString("captureP4") == "true")
        val state = ownedArtwork()
        val width = rule.activity.resources.configuration.screenWidthDp
        for (dark in listOf(false, true)) {
            for (locale in listOf("tr-TR", "en-US")) {
                for (scale in listOf(1f, 1.3f, 2f)) {
                    val label = "fixture-${width}dp-$scale-$locale-${if (dark) "dark" else "light"}"
                    show(width, scale, dark, locale) { Screen(state) }
                    capture("$label-art", requireImage = true)
                    details().performScrollToNode(hasTestTag("product-detail-title"))
                    capture("$label-label")
                    details().performScrollToNode(hasTestTag("product-detail-description"))
                    capture("$label-details")
                }
            }
        }
        for ((label, state) in listOf(
            "unselected" to state.copy(selectedOptions = emptyMap()),
            "pending" to state.copy(addingToCart = true),
            "missing-media" to artworkState(),
            "loading" to ProductDetailUiState(loading = true),
            "not-found" to ProductDetailUiState(notFound = true),
            "error" to
                ProductDetailUiState(
                    failure = com.gurbakir.mobile.catalog.CatalogLoadFailure(
                        com.gurbakir.mobile.catalog.CatalogLoadFailureCategory.CONNECTION,
                        true
                    )
                ),
            "unavailable" to
                state.copy(
                    product = state.product?.copy(
                        variants = state.product!!.variants.map {
                            it.copy(availableForSale = false)
                        }
                    )
                )
        )) {
            show(width, 2f, true, "tr-TR") { Screen(state) }
            capture("fixture-${width}dp-state-$label")
        }
        show(width, 1f, false, "tr-TR") { Screen(ownedArtwork(landscape = true)) }
        capture("fixture-${width}dp-landscape-art", requireImage = true)
        val failedMedia = StorefrontMedia(java.net.URI("file:///android_asset/p4-absent-artwork.png"), null, 240, 480)
        val failedState = state.copy(
            product = state.product?.copy(media = listOf(StorefrontProductMedia("failed", failedMedia)))
        )
        show(width, 1f, false, "tr-TR") { Screen(failedState.copy(mediaViewerOpen = true)) }
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("artwork-image-error").fetchSemanticsNodes().isNotEmpty() }
        capture("fixture-${width}dp-error-viewer")
        show(width, 2f, true, "tr-TR") { Screen(state.copy(mediaViewerOpen = true)) }
        capture("fixture-${width}dp-viewer", requireImage = true)
    }

    @Test
    fun captureConfiguredStorefrontProductsWithoutMutations() {
        assumeTrue(arguments.getString("captureLiveP4") == "true")
        check(TumiArtConfiguration.isLaunchReady())
        val configuration = TumiArtConfiguration.app.storefront
        val gateway = StorefrontApolloClientFactory.createGateways(
            configuration,
            StorefrontMediaPolicy(configuration.domain)
        ).product
        val repository = DefaultProductDetailRepository(gateway)
        val ids = checkNotNull(arguments.getString("productIds")).split(',')
        val record = JSONArray()
        for (id in ids) {
            val loaded = runBlocking { repository.load(id) }
            check(loaded is ProductDetailLoad.Content) {
                "Live product read failed: ${(loaded as? ProductDetailLoad.Error)?.failure?.category}"
            }
            val product = loaded.product
            assertTrue(product.variants.isNotEmpty())
            product.variants.forEach { variant ->
                val state = ProductDetailUiState(product, variant.selectedOptions.associate { it.name to it.value })
                assertEquals(variant.id, state.selectedVariant?.id)
                assertEquals(variant.price, state.selectedVariant?.price)
                assertEquals(variant.availableForSale, state.purchaseIntent != null)
                if (variant.availableForSale) {
                    assertEquals(variant.id, state.purchaseIntent?.merchandiseId)
                    assertEquals(1, state.purchaseIntent?.quantity)
                }
            }
            val selected = product.variants.firstOrNull { it.availableForSale } ?: product.variants.first()
            val state = ProductDetailUiState(product, selected.selectedOptions.associate { it.name to it.value })
            val loader = SingletonImageLoader.get(instrumentation.targetContext)
            runBlocking {
                state.displayMedia.forEach { media ->
                    check(
                        loader.execute(
                            ImageRequest.Builder(
                                instrumentation.targetContext
                            ).data(media.uri.toASCIIString()).size(800).build()
                        ) is SuccessResult
                    ) {
                        "Live image decode failed"
                    }
                }
            }
            record.put(
                JSONObject().put(
                    "productId",
                    id
                ).put("handle", product.handle).put("variantCount", product.variants.size)
                    .put("options", JSONArray(product.options.map { it.name })).put("selectedId", selected.id)
                    .put("amount", selected.price.amount.toPlainString()).put("currency", selected.price.currencyCode)
                    .put("mediaCount", state.displayMedia.size)
            )
            val width = rule.activity.resources.configuration.screenWidthDp
            for ((scale, dark, locale) in listOf(
                Triple(1f, false, "tr-TR"),
                Triple(1.3f, true, "tr-TR"),
                Triple(2f, false, "en-US")
            )) {
                val label = "live-${product.handle}-${width}dp-$scale-$locale-${if (dark) "dark" else "light"}"
                show(width, scale, dark, locale) { Screen(state.copy(selectedOptions = emptyMap())) }
                capture("$label-art", requireImage = true)
                details().performScrollToNode(hasTestTag("product-detail-title"))
                capture("$label-unselected")
                show(width, scale, dark, locale) { Screen(state) }
                details().performScrollToNode(hasTestTag("product-detail-description"))
                capture("$label-selected")
            }
        }
        directory().resolve("live-readback-api${android.os.Build.VERSION.SDK_INT}.json").writeText(
            JSONObject().put("scope", "read-only product/media; isolated screen host; zero cart writes")
                .put(
                    "sourceDigest",
                    arguments.getString("sourceDigest", "UNRECORDED")
                ).put("products", record).toString(2)
        )
    }

    @Test
    fun captureNativeSystemFontScale() {
        assumeTrue(arguments.getString("captureNativeP4") == "true")
        val state = ownedArtwork()
        var nativeState by mutableStateOf(state)
        rule.runOnUiThread {
            rule.activity.applyProbeAppearance(false)
            rule.activity.setContent { TumiArtThemeProbe(darkTheme = false) { Screen(nativeState) } }
        }
        rule.waitForIdle()
        val configuration = rule.activity.resources.configuration
        val locale = ConfigurationCompat.getLocales(configuration)[0]?.toLanguageTag().orEmpty()
        val label = "native-api${android.os.Build.VERSION.SDK_INT}-${configuration.screenWidthDp}dp" +
            "-$locale-font${configuration.fontScale}"
        capture("$label-art", requireImage = true)
        details().performScrollToNode(hasTestTag("product-detail-title"))
        val layouts = mutableListOf<TextLayoutResult>()
        rule.onNodeWithTag("product-detail-title").performSemanticsAction(SemanticsActions.GetTextLayoutResult) {
            it(layouts)
        }
        val layout = layouts.single()
        assertTrue(!layout.hasVisualOverflow)
        val titlePixels = with(layout.layoutInput.density) { 20.sp.toPx() }
        capture("$label-title")
        details().performScrollToNode(hasTestTag("product-detail-description"))
        capture("$label-details")
        rule.runOnIdle { nativeState = state.copy(mediaViewerOpen = true) }
        rule.waitForIdle()
        capture("$label-viewer", requireImage = true)
        val failedMedia = StorefrontMedia(java.net.URI("file:///android_asset/p4-absent-artwork.png"), null, 240, 480)
        rule.runOnIdle {
            nativeState = nativeState.copy(
                product = state.product?.copy(media = listOf(StorefrontProductMedia("failed", failedMedia)))
            )
        }
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("artwork-image-error").fetchSemanticsNodes().isNotEmpty() }
        capture("$label-error-viewer")
        directory().resolve("$label.json").writeText(
            JSONObject().put("scope", "actual Android system density; no composition/configuration font override")
                .put("api", android.os.Build.VERSION.SDK_INT).put("widthDp", configuration.screenWidthDp)
                .put("heightDp", configuration.screenHeightDp).put("densityDpi", configuration.densityDpi)
                .put("locale", locale).put("systemFontScale", configuration.fontScale)
                .put("composeFontScale", layout.layoutInput.density.fontScale)
                .put("title20spPixels", titlePixels)
                .put("sourceDigest", arguments.getString("sourceDigest", "UNRECORDED")).toString(2)
        )
    }

    private fun details() = rule.onNode(
        SemanticsMatcher("vertical product details list") {
            it.config.contains(SemanticsProperties.VerticalScrollAxisRange) &&
                it.config.contains(SemanticsActions.ScrollToIndex)
        }
    )

    @Composable
    private fun Screen(state: ProductDetailUiState) {
        ProductDetailScreen(state, artworkActions(), presentation = TumiArtConfiguration.productPresentation)
    }

    private fun show(width: Int, scale: Float, dark: Boolean, locale: String, content: @Composable () -> Unit) {
        rule.runOnUiThread {
            rule.activity.applyProbeAppearance(dark)
            val generation = ++contentGeneration
            rule.activity.setContent {
                key(generation) {
                    val configuration = Configuration(LocalConfiguration.current).apply {
                        setLocale(Locale.forLanguageTag(locale))
                        fontScale = scale
                    }
                    val context = LocalContext.current.createConfigurationContext(configuration)
                    val density = LocalDensity.current
                    CompositionLocalProvider(
                        LocalContext provides context,
                        LocalConfiguration provides configuration,
                        LocalDensity provides Density(density.density, scale)
                    ) {
                        TumiArtThemeProbe(darkTheme = dark) { Box(Modifier.requiredWidth(width.dp)) { content() } }
                    }
                }
            }
        }
        rule.waitForIdle()
    }

    private fun ownedArtwork(landscape: Boolean = false): ProductDetailUiState {
        val width = if (landscape) 480 else 240
        val height = if (landscape) 240 else 480
        val file = directory().resolve(if (landscape) "owned-landscape-pattern.png" else "owned-edge-pattern.png")
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).apply {
            drawColor(Color.rgb(207, 193, 177))
            drawRect(0f, 0f, width.toFloat(), 24f, Paint().apply { color = Color.RED })
            drawRect(0f, height - 24f, width.toFloat(), height.toFloat(), Paint().apply { color = Color.BLUE })
            drawCircle(width / 2f, height / 2f, 80f, Paint().apply { color = Color.rgb(83, 106, 93) })
        }
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        val media = StorefrontMedia(file.toURI(), "Project-owned artwork with red and blue edge markers", width, height)
        return artworkState().let {
            it.copy(product = it.product?.copy(media = listOf(StorefrontProductMedia("owned", media))))
        }
    }

    private fun capture(name: String, requireImage: Boolean = false) {
        rule.waitForIdle()
        val activeRoot = if (name.contains("viewer")) "product-detail-media-viewer" else "product-detail-root"
        rule.waitUntil(15_000) {
            rule.onAllNodesWithTag(activeRoot).fetchSemanticsNodes().any {
                it.boundsInWindow.width > 0 && it.boundsInWindow.height > 0
            }
        }
        rule.onNodeWithTag(activeRoot).assertIsDisplayed()
        if (requireImage) {
            rule.waitUntil(20_000) {
                rule.onAllNodesWithTag("artwork-image", useUnmergedTree = true).fetchSemanticsNodes().any {
                    it.boundsInWindow.width > 48 && it.boundsInWindow.height > 48
                } && rule.onAllNodesWithTag("artwork-image-loading").fetchSemanticsNodes().isEmpty()
            }
            assertTrue(rule.onAllNodesWithTag("artwork-image-error").fetchSemanticsNodes().isEmpty())
        }
        // Compose Dialog capture requires API28; older devices use the real system compositor below.
        val rendered = if (android.os.Build.VERSION.SDK_INT >= 28) {
            rule.onNodeWithTag(activeRoot).captureToImage().asAndroidBitmap()
        } else {
            null
        }
        rendered?.let { check(it.width > 0 && it.height > 0) }
        if (name.contains("error-viewer") && rendered != null) {
            directory().resolve("root-$name.png").outputStream().use {
                rendered.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        }
        instrumentation.waitForIdleSync()
        instrumentation.uiAutomation.waitForIdle(250, 5_000)
        // Accessibility idle does not guarantee that the compositor has presented its next frame.
        android.os.SystemClock.sleep(300)
        val bitmap = captureArtworkSystemFrame()
        rendered?.recycle()
        val sampledColors = mutableSetOf<Int>()
        for (y in 0 until bitmap.height step 32) {
            for (x in 0 until bitmap.width step 32) sampledColors += bitmap.getPixel(x, y)
        }
        check(sampledColors.size > 2) { "Screenshot is blank; device rendering is unavailable: $name" }
        directory().resolve("$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
