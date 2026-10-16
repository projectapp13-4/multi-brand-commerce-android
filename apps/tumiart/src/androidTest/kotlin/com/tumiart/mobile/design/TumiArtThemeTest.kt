@file:Suppress("MagicNumber", "LongMethod")

package com.tumiart.mobile.design

import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.gurbakir.foundation.ui.LocalBrandEditorialTypography
import com.tumiart.mobile.R
import com.tumiart.mobile.TumiArtApp
import com.tumiart.mobile.brand.TumiArtFontFamilies
import com.tumiart.mobile.config.TumiArtConfiguration
import java.io.File
import java.security.MessageDigest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TumiArtThemeTest {
    @get:Rule val composeRule = createAndroidComposeRule<TumiArtThemeProbeActivity>()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val arguments get() = InstrumentationRegistry.getArguments()

    @Test
    fun actualUnconfiguredSetupPaintsItsDarkBackground() {
        // ATD disables drawing by default. Pixel evidence is explicitly requested on a rendering target.
        assumeTrue(arguments.getString("captureTheme") == "true")
        assumeFalse(TumiArtConfiguration.isLaunchReady())
        composeRule.runOnUiThread {
            composeRule.activity.applyProbeAppearance(dark = true)
            composeRule.activity.setContent {
                val configuration = Configuration(LocalConfiguration.current).apply {
                    uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or Configuration.UI_MODE_NIGHT_YES
                }
                CompositionLocalProvider(LocalConfiguration provides configuration) { TumiArtApp() }
            }
        }
        composeRule.onNodeWithText("TumiArt").assertIsDisplayed()
        awaitPaintedBackground(dark = true)
    }

    @Test
    fun actualPackagedFontsKeepTheirLicensedBytesAndTurkishGlyphs() {
        val resources = instrumentation.targetContext.resources
        val expected = mapOf(
            R.font.karla_regular to "1a4e409e44eb3c3c541cac5e885219bd66d43262214186634f5811449100a090",
            R.font.karla_medium to "6f13001c17a8df1081836e9a625f6aca49041b66a1cca5995527ba8672a3a6b3",
            R.font.karla_semibold to "cfd540e5f9d55ce84d2ba45d0efa2b8697c321d25dfb54a522e1a977c68e017d",
            R.font.newsreader_regular to "0636887c9f72f77ce188f0b31029be58e65adae5a47e6d2887ad5020c2c75036"
        )
        expected.forEach { (resource, hash) ->
            val bytes = resources.openRawResource(resource).use { it.readBytes() }
            assertEquals(
                hash,
                MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") {
                    "%02x".format(it)
                }
            )
            val face = ResourcesCompat.getFont(instrumentation.targetContext, resource)
            assertNotNull(face)
            val paint = Paint().apply {
                typeface = face
                textSize = 32f
            }
            (TURKISH_GLYPH_SAMPLE + "₺0123456789").filterNot(Char::isWhitespace).forEach {
                assertTrue(paint.hasGlyph(it.toString()))
            }
        }
        listOf(R.raw.karla_ofl, R.raw.newsreader_ofl).forEach { resource ->
            val license = resources.openRawResource(resource).bufferedReader().use { it.readText() }
            assertTrue(license.contains("Copyright"))
            assertTrue(license.contains("SIL OPEN FONT LICENSE Version 1.1"))
        }
    }

    @Test
    fun functionalAndEditorialFontsRenderWithoutClippingAndControlsStayAccessible() {
        val dark = arguments.getString("probeDark") == "true"
        var scale = 0f
        var functional: Typeface? = null
        var editorial: Typeface? = null
        composeRule.runOnUiThread {
            composeRule.activity.applyProbeAppearance(dark)
            composeRule.activity.setContent {
                TumiArtThemeProbe(darkTheme = dark) {
                    val typography = MaterialTheme.typography
                    val editorialTypography = LocalBrandEditorialTypography.current
                    val resolver = LocalFontFamilyResolver.current
                    val functionalFace = resolver.resolve(
                        TumiArtFontFamilies.functional,
                        FontWeight.Normal
                    ).value as Typeface
                    val editorialFace = resolver.resolve(
                        TumiArtFontFamilies.editorial,
                        FontWeight.Normal
                    ).value as Typeface
                    val density = LocalDensity.current
                    SideEffect {
                        assertEquals(TumiArtFontFamilies.functional, typography.headlineLarge.fontFamily)
                        assertEquals(TumiArtFontFamilies.functional, typography.titleSmall.fontFamily)
                        assertEquals(TumiArtFontFamilies.functional, typography.labelLarge.fontFamily)
                        assertEquals(TumiArtFontFamilies.editorial, editorialTypography.hero.fontFamily)
                        assertEquals(TumiArtFontFamilies.editorial, editorialTypography.title.fontFamily)
                        scale = density.fontScale
                        functional = functionalFace
                        editorial = editorialFace
                    }
                    TumiArtThemeSpecimen()
                }
            }
        }
        composeRule.waitForIdle()
        assertEquals(instrumentation.targetContext.resources.configuration.fontScale, scale, 0.01f)
        assertTypefaceMetricsMatchResource(functional, R.font.karla_regular)
        assertTypefaceMetricsMatchResource(editorial, R.font.newsreader_regular)
        awaitPaintedBackground(dark)
        composeRule.onNodeWithTag("editorial-hero").assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
        val safeBounds = composeRule.onNodeWithTag("specimen-root").fetchSemanticsNode().boundsInWindow
        composeRule.runOnUiThread {
            val decor = composeRule.activity.window.decorView
            val insets = checkNotNull(
                ViewCompat.getRootWindowInsets(decor)
            ).getInsets(WindowInsetsCompat.Type.systemBars())
            assertTrue("Safe top $safeBounds, bars $insets", safeBounds.top >= insets.top - 1)
            assertTrue("Safe bottom $safeBounds, bars $insets", safeBounds.bottom <= decor.height - insets.bottom + 1)
        }
        capture("top", dark, scale)
        buildList {
            add("editorial-hero")
            add("editorial-glyphs")
            add("functional-glyphs")
            add("long-name")
            add("price-glyphs")
            add("editorial-title")
        }.forEach { tag ->
            val node = composeRule.onNodeWithTag(tag).performScrollTo().assertIsDisplayed()
            node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { action ->
                val results = mutableListOf<TextLayoutResult>()
                assertTrue(action(results))
                assertTrue(results.isNotEmpty())
                val details = results.map {
                    "size=${it.size}, paragraph=${it.multiParagraph.width}x${it.multiParagraph.height}, " +
                        "lines=${it.lineCount}, overflowWidth=${it.didOverflowWidth}, overflowHeight=${it.didOverflowHeight}"
                }
                results.forEach { result ->
                    // A wrap-content Text may retain a wider paragraph constraint than its ink bounds.
                    // Check visible lines and the full text, rather than that unused paragraph width.
                    assertTrue("$tag: $details", !result.multiParagraph.didExceedMaxLines)
                    assertEquals(result.layoutInput.text.length, result.getLineEnd(result.lineCount - 1))
                    repeat(result.lineCount) { line ->
                        assertTrue("$tag: $details", result.getLineLeft(line) >= -0.5f)
                        assertTrue("$tag: $details", result.getLineRight(line) <= result.size.width + 0.5f)
                        assertTrue("$tag: $details", result.getLineBottom(line) <= result.size.height + 0.5f)
                    }
                }
            }
        }
        val selection = composeRule.onNodeWithTag("selection-action").performScrollTo()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox))
            .assertIsOff().assertHasClickAction().assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithTag("secondary-action").assertIsNotEnabled()
        composeRule.onNodeWithTag(
            "primary-action"
        ).performScrollTo().assertIsEnabled().assertHasClickAction().assertHeightIsAtLeast(48.dp).performClick()
        composeRule.onNodeWithText("Seçildi").assertIsDisplayed()
        selection.performScrollTo().assertIsOn().performClick().assertIsOff()
        composeRule.onNodeWithTag("secondary-action").assertIsNotEnabled()
        selection.performClick().assertIsOn()
        composeRule.onNodeWithTag("secondary-action").performScrollTo()
            .assertIsEnabled().assertHasClickAction().assertHeightIsAtLeast(48.dp).performClick()
        selection.assertIsOff()
        composeRule.onNodeWithTag("primary-action").performScrollTo().performClick()
        composeRule.onNodeWithTag("error-field").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Lütfen alanı kontrol edin.").assertIsDisplayed()
        capture("controls", dark, scale)
        val licenseLabel = instrumentation.targetContext.getString(R.string.tumiart_font_licenses)
        composeRule.onNodeWithText(licenseLabel).performScrollTo().performClick()
        composeRule.onNodeWithText(
            "Copyright 2020 The Newsreader Project Authors",
            substring = true
        ).assertIsDisplayed()
        composeRule.onNodeWithText(instrumentation.targetContext.getString(R.string.tumiart_close)).performClick()
    }

    @Test
    fun mediumAndSemiboldFunctionalFontsRenderTheirPackagedWeight() {
        var medium: Typeface? = null
        var semibold: Typeface? = null
        composeRule.runOnUiThread {
            composeRule.activity.setContent {
                TumiArtThemeProbe {
                    val resolver = LocalFontFamilyResolver.current
                    val mediumFace = resolver.resolve(
                        TumiArtFontFamilies.functional,
                        FontWeight.Medium
                    ).value as Typeface
                    val semiboldFace = resolver.resolve(
                        TumiArtFontFamilies.functional,
                        FontWeight.SemiBold
                    ).value as Typeface
                    SideEffect {
                        medium = mediumFace
                        semibold = semiboldFace
                    }
                }
            }
        }
        composeRule.waitForIdle()
        assertTypefaceInkMatchesResource(medium, R.font.karla_medium)
        assertTypefaceInkMatchesResource(semibold, R.font.karla_semibold)
    }

    private fun assertTypefaceInkMatchesResource(actual: Typeface?, resource: Int) {
        val expected = checkNotNull(ResourcesCompat.getFont(instrumentation.targetContext, resource))
        fun renderedInk(face: Typeface): Bitmap = Bitmap.createBitmap(480, 100, Bitmap.Config.ARGB_8888).also {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = face
                textSize = 42f
                color = android.graphics.Color.BLACK
            }
            Canvas(it).drawText("İ ı Ş ş Ğ ğ Karla", 6f, 70f, paint)
        }
        val expectedInk = renderedInk(expected)
        val actualInk = renderedInk(checkNotNull(actual))
        try {
            assertTrue("Resolved font must render the packaged weight: $resource", actualInk.sameAs(expectedInk))
        } finally {
            expectedInk.recycle()
            actualInk.recycle()
        }
    }

    private fun assertTypefaceMetricsMatchResource(actual: Typeface?, resource: Int) {
        val expected = ResourcesCompat.getFont(instrumentation.targetContext, resource)
        assertNotNull(actual)
        val actualPaint = Paint().apply {
            typeface = actual
            textSize = 36f
        }
        val expectedPaint = Paint().apply {
            typeface = expected
            textSize = 36f
        }
        assertEquals(
            expectedPaint.measureText(TURKISH_GLYPH_SAMPLE),
            actualPaint.measureText(TURKISH_GLYPH_SAMPLE),
            0.01f
        )
        assertEquals(expectedPaint.fontMetrics.ascent, actualPaint.fontMetrics.ascent, 0.01f)
    }

    private fun awaitPaintedBackground(dark: Boolean) {
        if (arguments.getString("captureTheme") != "true") return
        val expected = if (dark) 0xFF201F1E.toInt() else 0xFFFAF7F2.toInt()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            val bitmap = instrumentation.uiAutomation.takeScreenshot() ?: return@waitUntil false
            val actual = bitmap.getPixel(1, bitmap.height / 2)
            bitmap.recycle()
            actual == expected
        }
    }

    private fun capture(part: String, dark: Boolean, scale: Float) {
        if (arguments.getString("captureTheme") != "true") return
        composeRule.waitForIdle()
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "p2-theme").apply { mkdirs() }
        val name = "api${Build.VERSION.SDK_INT}-${if (dark) "dark" else "light"}-$scale-$part"
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val metadata = JSONObject().put("baseSha", "f0760a9bca441013c429b4e822a89dc62db38fbd")
            .put("sourceDigest", arguments.getString("sourceDigest", "UNRECORDED"))
            .put("api", Build.VERSION.SDK_INT).put("fontScale", scale).put("dark", dark)
            .put("widthPx", bitmap.width).put("heightPx", bitmap.height)
            .put("fixture", "debug-only offline theme specimen")
        File(directory, "$name.json").writeText(metadata.toString(2))
        bitmap.recycle()
    }
}
