package com.example.gate2synthetic

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import coil3.SingletonImageLoader
import com.example.gate2synthetic.config.Gate2SyntheticConfiguration
import com.gurbakir.mobile.catalog.CatalogTestTags
import com.gurbakir.mobile.localization.effectiveForegroundLocale
import com.gurbakir.mobile.localization.resolveAppLocales
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Gate2SyntheticLaunchTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun hiltActivityLaunchesSharedApplicationWithSyntheticResourceOwnershipMarker() {
        composeRule.onNodeWithContentDescription("Synthetic Lab").assertIsDisplayed()
        assertEquals(Gate2SyntheticApplication::class.java, composeRule.activity.application::class.java)
        assertEquals(
            expectedForegroundLocaleTag(),
            effectiveForegroundLocale(composeRule.activity.resources.configuration).toLanguageTag()
        )
        val factory = composeRule.activity.application as SingletonImageLoader.Factory
        factory.newImageLoader(composeRule.activity)
    }

    @Test
    fun sharedShellUsesSearchFirstCompositionAndStartsOnHome() {
        val orderedTags = listOf(
            "production-primary-search",
            "production-primary-home",
            "production-primary-categories"
        )
        val positions = orderedTags.map { tag ->
            composeRule.onNodeWithTag(tag).assertIsDisplayed().fetchSemanticsNode().boundsInRoot.center.x
        }
        assertEquals(positions.sorted(), positions)
        composeRule.onNodeWithTag("production-primary-home").assertIsSelected()
        composeRule.onNodeWithTag("production-primary-wishlist", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithTag("production-primary-account", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun categoriesFailClosedWithoutAConfiguredStorefrontAndDoNotOfferRetry() {
        composeRule.onNodeWithTag("production-primary-categories").performClick()

        composeRule.onNodeWithTag(CatalogTestTags.CATEGORIES_ERROR).assertIsDisplayed()
        composeRule
            .onNodeWithText(composeRule.activity.getString(com.gurbakir.mobile.core.R.string.retry))
            .assertDoesNotExist()
    }

    private fun expectedForegroundLocaleTag(): String {
        val requested = composeRule.activity.application.resources.configuration
        val requestedLocales =
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                (0 until requested.locales.size()).map(requested.locales::get)
            } else {
                @Suppress("DEPRECATION")
                listOf(requested.locale)
            }
        return resolveAppLocales(
            policy = Gate2SyntheticConfiguration.app.localization,
            requestedLocales = requestedLocales,
            allowPseudoLocales = BuildConfig.DEBUG
        ).firstOrNull()?.toLanguageTag() ?: Locale.ROOT.toLanguageTag()
    }
}
