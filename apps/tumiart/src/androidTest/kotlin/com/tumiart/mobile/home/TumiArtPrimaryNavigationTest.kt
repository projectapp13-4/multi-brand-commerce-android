@file:Suppress("LongMethod", "MagicNumber")

package com.tumiart.mobile.home

import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.gurbakir.mobile.ProductionAppShell
import com.gurbakir.mobile.navigation.AccountRoute
import com.gurbakir.mobile.navigation.CategoriesRoute
import com.gurbakir.mobile.navigation.CollectionRoute
import com.gurbakir.mobile.navigation.HomeRoute
import com.gurbakir.mobile.navigation.ProductRoute
import com.gurbakir.mobile.navigation.SearchRoute
import com.gurbakir.mobile.navigation.WishlistRoute
import com.tumiart.mobile.config.TumiArtConfiguration
import com.tumiart.mobile.design.TumiArtThemeProbe
import com.tumiart.mobile.design.TumiArtThemeProbeActivity
import com.tumiart.mobile.product.captureArtworkSystemFrame
import java.io.File
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TumiArtPrimaryNavigationTest {
    @get:Rule val rule = createAndroidComposeRule<TumiArtThemeProbeActivity>()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val args get() = InstrumentationRegistry.getArguments()
    private var generation = 0

    @Volatile private var expectedPrimary = 0

    @Volatile private var expectedBackground = 0

    @Volatile private var expectedInk = 0

    @Volatile private var bodyInk = 0

    @Test
    fun largeTextRetainsHomeStateAcrossDetailsBackAndRootSwitch() {
        show("en-US", false, forceLargeText = true, statefulHome = true)
        rule.onNodeWithTag("fixture-increment").performClick()
        rule.onNodeWithTag("fixture-scroll").performScrollToIndex(10)
        for (detail in listOf("collection", "product")) {
            rule.onNodeWithTag("fixture-open-$detail").performClick()
            rule.onNodeWithTag("fixture-detail").assertIsDisplayed()
            rule.onNodeWithTag("fixture-back").performClick()
            rule.onNodeWithTag("fixture-home-state").assertTextEquals("Count 1; position 10")
        }
        rule.onNodeWithTag("production-primary-categories").performClick()
        rule.onNodeWithTag("production-primary-home").performClick()
        rule.onNodeWithTag("fixture-home-state").assertTextEquals("Count 1; position 10")
    }

    @Test
    fun largeTextCanStartOnSecondaryRouteAndEnterHome() {
        show("en-US", false, forceLargeText = true, statefulHome = true, secondaryFirst = true)
        rule.onNodeWithTag("fixture-detail").assertIsDisplayed()
        rule.onNodeWithTag("fixture-enter-home").performClick()
        rule.onNodeWithTag("production-primary-caption").assertIsDisplayed()
        rule.onNodeWithTag("fixture-increment").performClick()
        rule.onNodeWithTag("fixture-home-state").assertTextEquals("Count 1; position 0")
    }

    @Test
    fun largeTextShellSuppliesDarkForegroundToItsDestination() {
        // A component regression probe; system-font captures never use this override.
        show("en-US", true, forceLargeText = true)
        rule.onNodeWithTag("production-primary-caption").assertIsDisplayed()
        rule.runOnIdle { assertEquals(expectedInk, bodyInk) }
    }

    @Test
    fun everyPrimaryDestinationKeepsItsFullAccessibleNameTouchTargetAndSelection() {
        show("tr-TR", false)
        val resources = instrumentation.targetContext.createConfigurationContext(
            Configuration(rule.activity.resources.configuration).apply { setLocale(Locale.forLanguageTag("tr-TR")) }
        ).resources
        val labels = listOf(
            "home" to com.gurbakir.mobile.core.R.string.home_title,
            "categories" to com.gurbakir.mobile.core.R.string.categories_title,
            "search" to com.gurbakir.mobile.core.R.string.search_title,
            "wishlist" to com.gurbakir.mobile.core.R.string.wishlist_title,
            "account" to com.gurbakir.mobile.core.R.string.account_title
        )
        for ((name, resource) in labels) {
            val tag = "production-primary-$name"
            rule.onNodeWithTag(tag).assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp).performClick()
            rule.onNodeWithTag(tag).assertIsSelected()
            if (rule.onAllNodesWithTag("production-primary-caption").fetchSemanticsNodes().isNotEmpty()) {
                val caption = rule.onNodeWithTag("production-primary-caption")
                caption.assertIsDisplayed().assertTextEquals(resources.getString(resource))
                val layouts = mutableListOf<TextLayoutResult>()
                caption.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
                assertFalse(layouts.single().hasVisualOverflow)
                rule.onNodeWithTag("$tag-icon-selected", useUnmergedTree = true)
                    .assertContentDescriptionEquals(resources.getString(resource))
            } else {
                rule.onNodeWithTag("$tag-label", useUnmergedTree = true)
                    .assertIsDisplayed().assertContentDescriptionEquals(resources.getString(resource))
            }
            rule.onNodeWithTag("fixture-root-$name").assertIsDisplayed()
        }
    }

    @Test
    fun captureNativeNavigation() {
        assumeTrue(args.getString("captureP5Nav") == "true")
        val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "p5-navigation").apply {
            mkdirs()
        }
        for (locale in listOf("tr-TR", "en-US")) {
            for (dark in listOf(false, true)) {
                show(locale, dark)
                rule.onNodeWithTag("production-primary-home").assertIsDisplayed()
                if (android.os.Build.VERSION.SDK_INT >= 28) {
                    rule.onRoot().captureToImage().asAndroidBitmap().recycle()
                }
                instrumentation.waitForIdleSync()
                instrumentation.uiAutomation.waitForIdle(250, 5_000)
                android.os.SystemClock.sleep(300)
                val frame = CountDownLatch(1)
                rule.activity.runOnUiThread {
                    rule.activity.window.decorView.apply {
                        postInvalidateOnAnimation()
                        postOnAnimation { postOnAnimation { frame.countDown() } }
                    }
                }
                assertTrue(frame.await(5, TimeUnit.SECONDS))
                val config = rule.activity.resources.configuration
                val name = "api${android.os.Build.VERSION.SDK_INT}-${config.screenWidthDp}dp" +
                    "-${config.fontScale}-$locale-$dark"
                val icon = rule.onNodeWithTag("production-primary-home-icon-selected", useUnmergedTree = true)
                    .fetchSemanticsNode().boundsInRoot
                var painted: Bitmap? = null
                rule.waitUntil(5_000) {
                    val candidate = captureArtworkSystemFrame()
                    var found = false
                    for (y in icon.top.toInt() until icon.bottom.toInt()) {
                        for (x in icon.left.toInt() until icon.right.toInt()) {
                            if (candidate.getPixel(x, y) == expectedPrimary) found = true
                        }
                    }
                    found = found && candidate.getPixel(candidate.width / 2, candidate.height / 3) == expectedBackground
                    if (found) painted = candidate else candidate.recycle()
                    found
                }
                val bitmap = requireNotNull(painted)
                directory.resolve("$name.png").outputStream().use {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
                bitmap.recycle()
            }
        }
    }

    private fun show(
        locale: String,
        dark: Boolean,
        forceLargeText: Boolean = false,
        statefulHome: Boolean = false,
        secondaryFirst: Boolean = false
    ) {
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
                            if (forceLargeText) {
                                val density = LocalDensity.current
                                CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                                    Shell(statefulHome, secondaryFirst)
                                }
                            } else {
                                Shell(statefulHome, secondaryFirst)
                            }
                        }
                    }
                }
            }
        }
        rule.waitForIdle()
    }

    @Composable
    private fun Shell(statefulHome: Boolean, secondaryFirst: Boolean) {
        val primary = MaterialTheme.colorScheme.primary.toArgb()
        val background = MaterialTheme.colorScheme.background.toArgb()
        val ink = MaterialTheme.colorScheme.onBackground.toArgb()
        SideEffect {
            expectedPrimary = primary
            expectedBackground = background
            expectedInk = ink
        }
        val navController = rememberNavController()
        val destination = navController.currentBackStackEntryAsState().value?.destination
        ProductionAppShell(
            navController,
            destination,
            applicationComposition = TumiArtConfiguration.app.applicationComposition,
            primaryNavigationPresentation = TumiArtConfiguration.primaryNavigationPresentation
        ) {
            NavHost(
                navController,
                startDestination = if (secondaryFirst) ProductRoute("fixture") else HomeRoute,
                modifier = Modifier.statusBarsPadding().padding(16.dp)
            ) {
                composable<HomeRoute> {
                    val inheritedInk = LocalContentColor.current.toArgb()
                    SideEffect { bodyInk = inheritedInk }
                    if (statefulHome) {
                        var count by rememberSaveable { mutableIntStateOf(0) }
                        val scroll = rememberLazyListState()
                        Column {
                            Text(
                                "Count $count; position ${scroll.firstVisibleItemIndex}",
                                Modifier.testTag("fixture-home-state")
                            )
                            TextButton({ count++ }, Modifier.testTag("fixture-increment")) { Text("Increment") }
                            TextButton({
                                navController.navigate(CollectionRoute("fixture"))
                            }, Modifier.testTag("fixture-open-collection")) {
                                Text("Collection")
                            }
                            TextButton({
                                navController.navigate(ProductRoute("fixture"))
                            }, Modifier.testTag("fixture-open-product")) {
                                Text("Artwork")
                            }
                            LazyColumn(state = scroll, modifier = Modifier.testTag("fixture-scroll")) {
                                items(30) { Text("Owned fixture $it", Modifier.padding(16.dp)) }
                            }
                        }
                    } else {
                        Text("Home fixture", Modifier.testTag("fixture-root-home"))
                    }
                }
                composable<CategoriesRoute> { Text("Catalog fixture", Modifier.testTag("fixture-root-categories")) }
                composable<SearchRoute> { Text("Search fixture", Modifier.testTag("fixture-root-search")) }
                composable<WishlistRoute> { Text("Wishlist fixture", Modifier.testTag("fixture-root-wishlist")) }
                composable<AccountRoute> { Text("Account fixture", Modifier.testTag("fixture-root-account")) }
                composable<CollectionRoute> {
                    Detail({ navController.popBackStack() }, { navController.navigate(HomeRoute) })
                }
                composable<ProductRoute> {
                    Detail({ navController.popBackStack() }, { navController.navigate(HomeRoute) })
                }
            }
        }
    }

    @Composable
    private fun Detail(back: () -> Unit, home: () -> Unit) {
        Column(Modifier.testTag("fixture-detail")) {
            Text("Secondary fixture")
            TextButton(back, Modifier.testTag("fixture-back")) { Text("Back") }
            TextButton(home, Modifier.testTag("fixture-enter-home")) { Text("Home") }
        }
    }
}
