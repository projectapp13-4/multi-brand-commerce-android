@file:Suppress("MagicNumber", "LongMethod")

package com.tumiart.mobile.home

import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import com.gurbakir.mobile.home.DefaultHomeContentRepository
import com.gurbakir.mobile.home.HomeActions
import com.gurbakir.mobile.home.HomeContentAcceptanceCoordinator
import com.gurbakir.mobile.home.HomeContentPartition
import com.gurbakir.mobile.home.HomeContentStore
import com.gurbakir.mobile.home.HomeContentValidator
import com.gurbakir.mobile.home.HomeEditorialClock
import com.gurbakir.mobile.home.HomeEditorialState
import com.gurbakir.mobile.home.HomeEstablishmentRecord
import com.gurbakir.mobile.home.HomeLoadFailure
import com.gurbakir.mobile.home.HomeLoadFailureCategory
import com.gurbakir.mobile.home.HomeLoadResult
import com.gurbakir.mobile.home.HomeLoadTrigger
import com.gurbakir.mobile.home.HomeRenderedSection
import com.gurbakir.mobile.home.HomeResourceStatus
import com.gurbakir.mobile.home.HomeScreen
import com.gurbakir.mobile.home.HomeStoreRead
import com.gurbakir.mobile.home.HomeStoreWrite
import com.gurbakir.mobile.home.HomeStoredSnapshot
import com.gurbakir.mobile.home.HomeUiState
import com.gurbakir.storefront.StorefrontApolloClientFactory
import com.gurbakir.storefront.StorefrontMedia
import com.gurbakir.storefront.StorefrontMediaPolicy
import com.tumiart.mobile.config.TumiArtConfiguration
import com.tumiart.mobile.design.TumiArtThemeProbe
import com.tumiart.mobile.design.TumiArtThemeProbeActivity
import com.tumiart.mobile.product.captureArtworkSystemFrame
import java.io.File
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Opt-in screenshots remain private; real system width and font density are never overridden here. */
@RunWith(AndroidJUnit4::class)
class TumiArtHomeEvidenceTest {
    @get:Rule val rule = createAndroidComposeRule<TumiArtThemeProbeActivity>()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val args get() = InstrumentationRegistry.getArguments()
    private val directory get() = File(instrumentation.targetContext.getExternalFilesDir(null), "p5-home").apply {
        mkdirs()
    }
    private var generation = 0

    @Test
    fun captureNativeMatrix() {
        assumeTrue(args.getString("captureP5") == "true")
        val media = ownedMedia()
        decode(media)
        val single = homeFixture(media)
        val original = requireNotNull(single.presentation)
        val grid = original.renderedSections.first() as HomeRenderedSection.CollectionGrid
        val state = single.copy(
            presentation = original.copy(
                renderedSections = listOf(
                    grid.copy(
                        items = (1..5).map { index ->
                            grid.items.single().copy(
                                stableId = "fixture-$index",
                                label = com.gurbakir.mobile.home.HomeText.Remote("$LONG_COLLECTION $index"),
                                summary = grid.items.single().summary.copy(handle = "fixture-$index")
                            )
                        }
                    ),
                    original.renderedSections.last()
                )
            )
        )
        for (locale in listOf("tr-TR", "en-US")) {
            for (dark in listOf(false, true)) {
                val label = label(locale, dark)
                show(state, locale, dark)
                capture("$label-initial")
                rule.onNodeWithTag("gallery-home-collection-image-fixture-1", useUnmergedTree = true).performScrollTo()
                // Scroll semantics use the full layout bounds, including the edge-to-edge system-bar area.
                val image = rule.onNodeWithTag("gallery-home-collection-image-fixture-1", useUnmergedTree = true)
                    .fetchSemanticsNode().boundsInRoot
                val decor = rule.activity.window.decorView
                val systemBottom = ViewCompat.getRootWindowInsets(decor)
                    ?.getInsets(WindowInsetsCompat.Type.systemBars())?.bottom ?: 0
                val hidden = image.bottom - (decor.height - systemBottom)
                if (hidden > 0) {
                    rule.onNodeWithTag("home-content").performSemanticsAction(SemanticsActions.ScrollBy) {
                        it(0f, hidden + 1)
                    }
                }
                capture("$label-collections", requireEdges = true)
                rule.onNodeWithTag("home-content").performScrollToIndex(2)
                rule.onNodeWithTag("artwork-image").assertIsDisplayed()
                capture("$label-artwork", requireEdges = true)
                rule.onNodeWithTag("home-content").performScrollToNode(hasTestTag("gallery-home-open-artwork"))
                val layouts = mutableListOf<TextLayoutResult>()
                rule.onNodeWithTag(
                    "gallery-home-artwork-title"
                ).performSemanticsAction(SemanticsActions.GetTextLayoutResult) {
                    it(layouts)
                }
                assertFalse(layouts.single().hasVisualOverflow)
                capture("$label-title-action")
                directory.resolve("$label.json").writeText(
                    JSONObject()
                        .put("sourceDigest", args.getString("sourceDigest"))
                        .put("systemWidthDp", rule.activity.resources.configuration.screenWidthDp)
                        .put("systemHeightDp", rule.activity.resources.configuration.screenHeightDp)
                        .put("systemFontScale", rule.activity.resources.configuration.fontScale)
                        .put("composeFontScale", layouts.single().layoutInput.density.fontScale)
                        .put("localeScope", "Android configuration context for localized native resources")
                        .put("locale", locale).put("dark", dark).toString(2)
                )
            }
        }
        val ready = requireNotNull(state.presentation)
        for ((name, alternative) in listOf(
            "loading" to HomeUiState(),
            "slow" to HomeUiState(slowLoading = true),
            "error" to
                HomeUiState(
                    loading = false,
                    requestActive = false,
                    failure = HomeLoadFailure(HomeLoadFailureCategory.CONNECTION, true)
                ),
            "empty" to
                state.copy(
                    presentation = ready.copy(
                        editorial = HomeEditorialState.IntentionalEmpty,
                        renderedSections = emptyList()
                    )
                ),
            "unavailable" to
                state.copy(
                    presentation = ready.copy(
                        resourceStatus = HomeResourceStatus.NONE_RENDERABLE,
                        renderedSections = emptyList()
                    )
                ),
            "partial" to state.copy(presentation = ready.copy(resourceStatus = HomeResourceStatus.PARTIAL)),
            "refresh-error" to state.copy(failure = HomeLoadFailure(HomeLoadFailureCategory.SERVICE, false))
        )) {
            show(alternative, "tr-TR", true)
            if (name == "partial") {
                rule.onNodeWithTag("home-content").performScrollToNode(hasTestTag("gallery-home-partial"))
            }
            capture("${label("tr-TR", true)}-$name")
        }
    }

    @Test
    fun captureLiveReadOnlyHome() = runBlocking {
        assumeTrue(args.getString("liveP5") == "true")
        val app = TumiArtConfiguration.app
        val gateway = StorefrontApolloClientFactory.createGateways(
            app.storefront,
            StorefrontMediaPolicy(app.storefront.domain)
        ).api
        val store = object : HomeContentStore {
            override suspend fun read(partition: HomeContentPartition, supportedContentVersion: Int, nowMillis: Long) =
                HomeStoreRead.NeverEstablished
            override suspend fun replace(marker: HomeEstablishmentRecord, snapshot: HomeStoredSnapshot) =
                HomeStoreWrite.UNCONFIRMED
            override suspend fun evictSnapshot(partition: HomeContentPartition) = HomeStoreWrite.UNCONFIRMED
        }
        val repository =
            DefaultHomeContentRepository(
                gateway,
                TumiArtConfiguration.home,
                HomeContentValidator(),
                store,
                HomeContentAcceptanceCoordinator(store),
                object : HomeEditorialClock {
                    override fun nowMillis() = System.currentTimeMillis()
                    override suspend fun awaitUntil(deadlineMillis: Long) = Unit
                },
                HomeContentPartition("p5-read-only-probe", "development", "tumiart.com", "mobile_home_v2", "primary")
            )
        val result = repository.load(HomeLoadTrigger.INITIAL)
        assertTrue("Live read-only Home failed: ${result.javaClass.simpleName}", result is HomeLoadResult.Accepted)
        val presentation = (result as HomeLoadResult.Accepted).presentation
        presentation.renderedSections.forEach {
            when (it) {
                is HomeRenderedSection.CollectionGrid -> it.items.forEach { item -> decode(item.summary.media) }
                is HomeRenderedSection.FeaturedProduct -> decode(requireNotNull(it.item.summary.media))
                else -> Unit
            }
        }
        show(HomeUiState(presentation, loading = false, requestActive = false), "tr-TR", false)
        capture("${label("tr-TR", false)}-live-collections")
        rule.onNodeWithTag("home-content").performScrollToNode(hasTestTag("gallery-home-featured-image"))
        capture("${label("tr-TR", false)}-live-artwork")
        rule.onNodeWithTag("home-content").performScrollToNode(hasTestTag("gallery-home-open-artwork"))
        capture("${label("tr-TR", false)}-live-details")
        directory.resolve("live-${android.os.Build.VERSION.SDK_INT}.json").writeText(
            JSONObject()
                .put("scope", "read-only Storefront Home, private decoded media, no cart or customer calls")
                .put("source", presentation.source).put("status", presentation.resourceStatus)
                .put("sourceDigest", args.getString("sourceDigest")).toString(2)
        )
    }

    private fun label(locale: String, dark: Boolean) =
        "api${android.os.Build.VERSION.SDK_INT}-${rule.activity.resources.configuration.screenWidthDp}dp-${rule.activity.resources.configuration.fontScale}-$locale-${if (dark) "dark" else "light"}"

    private fun show(state: HomeUiState, locale: String, dark: Boolean) {
        rule.runOnUiThread {
            rule.activity.applyProbeAppearance(dark)
            val current = ++generation
            rule.activity.setContent {
                key(current) {
                    val config = Configuration(LocalConfiguration.current).apply {
                        setLocale(Locale.forLanguageTag(locale))
                    }
                    val context = LocalContext.current.createConfigurationContext(config)
                    CompositionLocalProvider(LocalContext provides context, LocalConfiguration provides config) {
                        TumiArtThemeProbe(dark) {
                            HomeScreen(
                                state,
                                "TumiArt",
                                HomeActions({
                                }),
                                screenPresentation = TumiArtConfiguration.homePresentation
                            )
                        }
                    }
                }
            }
        }
        rule.waitForIdle()
        rule.onNodeWithTag("home-root").assertIsDisplayed()
    }

    private fun decode(media: StorefrontMedia) = runBlocking {
        assertTrue(
            "Media must decode before capture",
            SingletonImageLoader.get(
                instrumentation.targetContext
            ).execute(
                ImageRequest.Builder(instrumentation.targetContext).data(media.uri.toASCIIString()).size(800).build()
            ) is SuccessResult
        )
    }

    private fun ownedMedia(): StorefrontMedia {
        val file = directory.resolve("owned-p5-edge-fixture.png")
        val bitmap = Bitmap.createBitmap(240, 480, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).apply {
            drawColor(Color.rgb(232, 221, 208))
            drawRect(0f, 0f, 240f, 24f, Paint().apply { color = Color.RED })
            drawRect(0f, 456f, 240f, 480f, Paint().apply { color = Color.BLUE })
            drawCircle(120f, 240f, 80f, Paint().apply { color = Color.rgb(83, 106, 93) })
        }
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        return StorefrontMedia(file.toURI(), "Project-owned edge fixture", 240, 480)
    }

    private fun capture(name: String, requireEdges: Boolean = false) {
        rule.waitForIdle()
        if (android.os.Build.VERSION.SDK_INT >= 28) {
            rule.onRoot().captureToImage().asAndroidBitmap().recycle()
        }
        instrumentation.waitForIdleSync()
        instrumentation.uiAutomation.waitForIdle(250, 5_000)
        android.os.SystemClock.sleep(300)
        // Semantics can settle before the system compositor paints setContent's replacement frame.
        val painted = CountDownLatch(1)
        rule.activity.runOnUiThread {
            rule.activity.window.decorView.apply {
                postInvalidateOnAnimation()
                postOnAnimation { postOnAnimation { painted.countDown() } }
            }
        }
        assertTrue("Native paint did not settle", painted.await(5, TimeUnit.SECONDS))
        val bitmap = captureArtworkSystemFrame()
        val colors = mutableSetOf<Int>()
        for (x in 0 until bitmap.width step 20) {
            for (y in 0 until bitmap.height step 20) colors += bitmap.getPixel(x, y)
        }
        assertTrue("Blank native frame is not visual evidence", colors.size > 3)
        if (requireEdges) {
            val tag = if (name.endsWith("-artwork")) {
                "gallery-home-featured-image"
            } else {
                "gallery-home-collection-image-fixture-1"
            }
            val bounds = rule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            val edgeColors = mutableSetOf<Int>()
            for (y in bounds.top.toInt().coerceAtLeast(0) until bounds.bottom.toInt().coerceAtMost(bitmap.height)) {
                for (x in bounds.left.toInt().coerceAtLeast(
                    0
                ) until bounds.right.toInt().coerceAtMost(bitmap.width) step
                    4) {
                    edgeColors += bitmap.getPixel(x, y)
                }
            }
            val complete = Color.RED in edgeColors && Color.BLUE in edgeColors
            if (!complete) {
                directory.resolve("$name-attempt.png").outputStream().use { output ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
                }
            }
            assertTrue("Full owned artwork: $name; bounds=$bounds; frame=${bitmap.width}x${bitmap.height}", complete)
        }
        directory.resolve("$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
