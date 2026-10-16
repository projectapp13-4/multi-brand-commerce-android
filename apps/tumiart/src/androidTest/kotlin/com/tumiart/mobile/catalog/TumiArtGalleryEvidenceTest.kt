@file:Suppress("MagicNumber", "LongMethod")

package com.tumiart.mobile.catalog

import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.Build
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import com.gurbakir.mobile.catalog.CatalogCategoryLoad
import com.gurbakir.mobile.catalog.CatalogPageLoad
import com.gurbakir.mobile.catalog.CatalogTestTags
import com.gurbakir.mobile.catalog.CategoriesScreen
import com.gurbakir.mobile.catalog.CategoriesUiState
import com.gurbakir.mobile.catalog.CollectionActions
import com.gurbakir.mobile.catalog.CollectionScreen
import com.gurbakir.mobile.catalog.CollectionUiState
import com.gurbakir.mobile.catalog.DefaultCatalogRepository
import com.gurbakir.mobile.wishlist.WishlistMembershipUiState
import com.gurbakir.storefront.CollectionCatalogSort
import com.gurbakir.storefront.StorefrontApolloClientFactory
import com.gurbakir.storefront.StorefrontMediaPolicy
import com.tumiart.mobile.config.TumiArtConfiguration
import com.tumiart.mobile.design.TumiArtThemeProbe
import com.tumiart.mobile.design.TumiArtThemeProbeActivity
import java.io.File
import java.util.Locale
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Explicit local read-only evidence. Normal CI has no merchant credentials and skips this test. */
@RunWith(AndroidJUnit4::class)
class TumiArtGalleryEvidenceTest {
    @get:Rule val rule = createAndroidComposeRule<TumiArtThemeProbeActivity>()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val arguments get() = InstrumentationRegistry.getArguments()
    private val captureActions = CollectionActions(
        onBack = {},
        onRetry = {},
        onSortSelected = { _ -> },
        onProductTypeToggled = { _ -> },
        onLoadMore = {},
        onSetWishlist = { _, _ -> }
    )

    @Test
    fun captureConfiguredGalleryWithoutMerchantWrites() {
        assumeTrue(arguments.getString("captureLiveGallery") == "true")
        check(TumiArtConfiguration.isLaunchReady()) { "Configured development client required" }
        val configuration = TumiArtConfiguration.app
        val gateway = StorefrontApolloClientFactory.createGateways(
            configuration.storefront,
            StorefrontMediaPolicy(configuration.storefront.domain)
        ).catalog
        val repository = DefaultCatalogRepository(gateway, TumiArtConfiguration.catalog)
        val (categories, first) = runBlocking {
            val categories = repository.loadCategories()
            check(categories is CatalogCategoryLoad.Content) {
                "Discovery failed: ${(categories as? CatalogCategoryLoad.Error)?.failure?.category}; ${projectHttpsProbe()}"
            }
            val first = (
                repository.loadCollectionPage(
                    "ustalar",
                    null,
                    CollectionCatalogSort.COLLECTION_DEFAULT,
                    emptySet()
                ) as CatalogPageLoad.Content
                ).page
            assertTrue(categories.items.isNotEmpty() && first.products.isNotEmpty())
            val next = if (first.hasNextPage) {
                (
                    repository.loadCollectionPage(
                        first.handle,
                        checkNotNull(first.endCursor),
                        CollectionCatalogSort.COLLECTION_DEFAULT,
                        emptySet()
                    ) as CatalogPageLoad.Content
                    ).page
            } else {
                null
            }
            val sorted = (
                repository.loadCollectionPage(
                    first.handle,
                    null,
                    CollectionCatalogSort.NEWEST,
                    emptySet()
                ) as CatalogPageLoad.Content
                ).page
            assertTrue(sorted.products.isNotEmpty())
            val context = instrumentation.targetContext
            val loader = SingletonImageLoader.get(context)
            var mediaFailures = 0
            var decodedMedia = 0
            (
                categories.items.filter { it.depth == 0 }.map { it.collection.media } +
                    first.products.take(4).mapNotNull { it.media }
                )
                .distinctBy { it.uri }.forEach { media ->
                    if (loader.execute(ImageRequest.Builder(context).data(media.uri.toASCIIString()).size(800).build())
                            is SuccessResult
                    ) {
                        decodedMedia++
                    } else {
                        mediaFailures++
                    }
                }
            val record = JSONObject().put("sourceDigest", arguments.getString("sourceDigest", "UNRECORDED"))
                .put(
                    "capturedAtEpochMs",
                    System.currentTimeMillis()
                ).put("api", Build.VERSION.SDK_INT).put("menuActions", categories.items.size)
                .put("rootActions", categories.items.count { it.depth == 0 })
                .put("firstPageProducts", first.products.size).put("nextPageProducts", next?.products?.size ?: 0)
                .put(
                    "newestProducts",
                    sorted.products.size
                ).put("decodedMedia", decodedMedia).put("mediaFailures", mediaFailures)
                .put("scope", "real Storefront data; native screens; isolated empty Wishlist")
            directory().resolve("api${Build.VERSION.SDK_INT}-readback.json").writeText(record.toString(2))
            categories to first
        }
        val width = minOf(rule.activity.resources.configuration.screenWidthDp, 391)
        for (dark in listOf(false, true)) {
            for ((viewport, scale) in listOf(width to 1f, width to 1.3f, 320 to 2f)) {
                val name = "${if (dark) "dark" else "light"}-${viewport}dp-$scale"
                show(viewport, scale, dark) {
                    CategoriesScreen(
                        CategoriesUiState.Content(categories.items, categories.partialFailure),
                        {},
                        {},
                        TumiArtConfiguration.catalog.presentation
                    )
                }
                capture("$name-discovery-top", dark, listOf(CatalogTestTags.categoryMedia("ustalar")))
                val grid = rule.onNodeWithTag(CatalogTestTags.CATEGORIES_GRID)
                grid.performScrollToNode(hasTestTag(CatalogTestTags.category("tumi-art-originals")))
                if (scale >= 2f) {
                    // At large text scale the covers form one column; they cannot both
                    // be fully visible in the same viewport. Verify each after scrolling.
                    capture("$name-discovery-covers", dark, listOf(CatalogTestTags.categoryMedia("tumi-art-originals")))
                    grid.performScrollToNode(hasTestTag(CatalogTestTags.category("modern-forms")))
                    capture("$name-discovery-modern", dark, listOf(CatalogTestTags.categoryMedia("modern-forms")))
                } else {
                    capture(
                        "$name-discovery-covers",
                        dark,
                        listOf("tumi-art-originals", "modern-forms").map(CatalogTestTags::categoryMedia)
                    )
                }
                grid.performScrollToNode(hasTestTag(CatalogTestTags.category("tum-urunler")))
                rule.onNodeWithTag(CatalogTestTags.category("tum-urunler")).assertIsDisplayed()
                capture("$name-discovery-continuation", dark)
                show(viewport, scale, dark) {
                    CollectionScreen(
                        CollectionUiState(
                            handle = first.handle,
                            title = first.title,
                            products = first.products,
                            hasNextPage = first.hasNextPage,
                            productTypeFilter = first.productTypeFilter
                        ),
                        captureActions,
                        WishlistMembershipUiState(),
                        TumiArtConfiguration.catalog.presentation
                    )
                }
                // A 2x single-column viewport contains one artwork; other products are lazy/offscreen.
                // At normal scale the two-column gallery proves the first four media together.
                val productMedia = first.products.take(if (scale >= 2f) 1 else 4)
                    .map { CatalogTestTags.productMedia(it.handle) }
                capture("$name-listing-top", dark, productMedia)
                rule.onNodeWithTag(CatalogTestTags.COLLECTION_GRID).performScrollToIndex(1)
                capture("$name-listing-art", dark, productMedia)
            }
        }
    }

    private fun show(width: Int, scale: Float, dark: Boolean, content: @Composable () -> Unit) {
        rule.runOnUiThread {
            rule.activity.applyProbeAppearance(dark)
            rule.activity.setContent {
                val configuration = Configuration(LocalConfiguration.current).apply {
                    setLocale(Locale.forLanguageTag("tr-TR"))
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
        rule.waitForIdle()
    }

    private fun projectHttpsProbe(): String = try {
        val connection = java.net.URL("https://tumiart.com").openConnection() as javax.net.ssl.HttpsURLConnection
        try {
            connection.requestMethod = "HEAD"
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            "HTTPS status ${connection.responseCode}"
        } finally {
            connection.disconnect()
        }
    } catch (failure: java.io.IOException) {
        generateSequence(failure as Throwable) { it.cause }.take(6).joinToString(" / ") { it.javaClass.simpleName }
    }

    private fun directory() = File(instrumentation.targetContext.getExternalFilesDir(null), "p3-gallery-live")
        .apply { mkdirs() }

    private fun capture(name: String, dark: Boolean, mediaTags: List<String> = emptyList()) {
        val expected = if (dark) 0xFF201F1E.toInt() else 0xFFFAF7F2.toInt()
        var latestReadiness = "No screenshot available"
        try {
            rule.waitUntil(15_000) {
                val frameGroups = mediaTags.associateWith { tag ->
                    rule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes()
                        .map { it.boundsInWindow }.filter { it.width >= 48 && it.height >= 48 }
                }
                val image = instrumentation.uiAutomation.takeScreenshot() ?: return@waitUntil false
                val backgroundMatches = image.getPixel(1, image.height / 2) == expected
                val painted = frameGroups.mapValues { (_, frames) ->
                    frames.isNotEmpty() && frames.all { image.hasPaintedPhoto(it) }
                }
                latestReadiness = "background=$backgroundMatches, " +
                    "frames=${frameGroups.mapValues { it.value.map(Rect::toString) }}, painted=$painted"
                image.recycle()
                backgroundMatches && painted.values.all { it }
            }
        } catch (timeout: androidx.compose.ui.test.ComposeTimeoutException) {
            instrumentation.uiAutomation.takeScreenshot()?.let { image ->
                directory().resolve("failed-$name.png").outputStream().use {
                    image.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
                image.recycle()
            }
            throw AssertionError("Gallery screenshot '$name' not ready: $latestReadiness", timeout)
        }
        val image = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        directory().resolve("api${Build.VERSION.SDK_INT}-$name.png").outputStream().use {
            image.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        image.recycle()
    }

    // Read-only captures must distinguish a painted merchant image from an empty surface.
    // Four coarse color values also admit legitimately flat-color artwork (e.g. Modern Forms);
    // the gateway/image-loader preload result is checked separately above.
    private fun Bitmap.hasPaintedPhoto(bounds: Rect): Boolean {
        val colors = buildSet {
            for (row in 1..7) {
                for (column in 1..7) {
                    val x = (bounds.left + bounds.width * column / 8).toInt().coerceIn(0, width - 1)
                    val y = (bounds.top + bounds.height * row / 8).toInt().coerceIn(0, height - 1)
                    add(getPixel(x, y) and 0x00F0F0F0)
                }
            }
        }
        return colors.size >= 4
    }
}
