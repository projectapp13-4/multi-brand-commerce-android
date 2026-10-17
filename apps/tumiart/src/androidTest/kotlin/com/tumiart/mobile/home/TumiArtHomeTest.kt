@file:Suppress("MagicNumber", "LongMethod")

package com.tumiart.mobile.home

import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.gurbakir.mobile.home.HomeActions
import com.gurbakir.mobile.home.HomeCollectionItem
import com.gurbakir.mobile.home.HomeContentSource
import com.gurbakir.mobile.home.HomeEditorialState
import com.gurbakir.mobile.home.HomeFeaturedItem
import com.gurbakir.mobile.home.HomeLoadFailure
import com.gurbakir.mobile.home.HomeLoadFailureCategory
import com.gurbakir.mobile.home.HomePresentation
import com.gurbakir.mobile.home.HomeRenderedSection
import com.gurbakir.mobile.home.HomeResourceStatus
import com.gurbakir.mobile.home.HomeScreen
import com.gurbakir.mobile.home.HomeText
import com.gurbakir.mobile.home.HomeUiState
import com.gurbakir.storefront.HomeCollectionSummary
import com.gurbakir.storefront.HomeProductSummary
import com.gurbakir.storefront.StorefrontMedia
import com.gurbakir.storefront.StorefrontMoney
import com.tumiart.mobile.config.TumiArtConfiguration
import com.tumiart.mobile.design.TumiArtThemeProbe
import com.tumiart.mobile.design.TumiArtThemeProbeActivity
import java.math.BigDecimal
import java.net.URI
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TumiArtHomeTest {
    @get:Rule val rule = createAndroidComposeRule<TumiArtThemeProbeActivity>()

    @Test
    fun nativeStatesKeepRecoveryAndPartialResourceFeedbackAvailable() {
        var state by mutableStateOf(homeFixture())
        rule.runOnUiThread {
            rule.activity.setContent {
                TumiArtThemeProbe {
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
        val ready = requireNotNull(state.presentation)
        val states = listOf(
            "home-product-range-loading" to HomeUiState(),
            "home-slow-loading" to HomeUiState(slowLoading = true),
            "home-error" to HomeUiState(
                loading = false,
                requestActive = false,
                failure = HomeLoadFailure(HomeLoadFailureCategory.CONNECTION, true)
            ),
            "home-empty" to homeFixture().copy(
                presentation = ready.copy(
                    editorial = HomeEditorialState.IntentionalEmpty,
                    renderedSections = emptyList()
                )
            ),
            "home-nonrenderable" to homeFixture().copy(
                presentation = ready.copy(
                    resourceStatus = HomeResourceStatus.NONE_RENDERABLE,
                    renderedSections = emptyList()
                )
            ),
            "gallery-home-partial" to homeFixture().copy(
                presentation = ready.copy(
                    resourceStatus = HomeResourceStatus.PARTIAL
                )
            )
        )
        for ((tag, next) in states) {
            rule.runOnIdle { state = next }
            rule.onNodeWithTag("home-content").performScrollToNode(hasTestTag(tag))
            rule.onNodeWithTag(tag).assertIsDisplayed()
        }
    }

    @Test
    fun featureLabelsMinimumPriceAndOpensExactProductIdentity() {
        var opened = ""
        rule.runOnUiThread {
            rule.activity.setContent {
                TumiArtThemeProbe {
                    HomeScreen(
                        homeFixture(),
                        "TumiArt",
                        HomeActions({}, openProduct = { opened = it }),
                        screenPresentation = TumiArtConfiguration.homePresentation
                    )
                }
            }
        }
        rule.onNodeWithTag("home-content").performScrollToNode(hasTestTag("gallery-home-open-artwork"))
        val prefix = InstrumentationRegistry.getInstrumentation().targetContext
            .getString(com.gurbakir.mobile.core.R.string.gallery_home_price_from, "").trim()
        rule.onNodeWithText(prefix, substring = true).assertIsDisplayed()
        rule.onNodeWithTag("gallery-home-open-artwork").assertHeightIsAtLeast(48.dp).performClick()
        assertEquals("gid://shopify/Product/1", opened)
    }

    @Test
    fun collectionLabelsNeverEllipsizeAndOpenExactCollectionHandle() {
        var opened = ""
        rule.runOnUiThread {
            rule.activity.setContent {
                TumiArtThemeProbe {
                    HomeScreen(
                        homeFixture(),
                        "TumiArt",
                        HomeActions({}, openCollection = { opened = it }),
                        screenPresentation = TumiArtConfiguration.homePresentation
                    )
                }
            }
        }
        val layouts = mutableListOf<TextLayoutResult>()
        rule.onNodeWithText(LONG_COLLECTION).performSemanticsAction(
            SemanticsActions.GetTextLayoutResult
        ) { it(layouts) }
        assertFalse(layouts.single().hasVisualOverflow)
        rule.onNodeWithText(LONG_COLLECTION).performClick()
        assertEquals("fixture-collection", opened)
    }
}

internal const val LONG_COLLECTION = "Proje koleksiyonu — uzun koleksiyon adları ve Türkçe karakterler"
internal const val LONG_ARTWORK = "Proje eseri — uzun sanatçı ve eser adları kesilmeden okunabilir"

internal fun homeFixture(
    media: StorefrontMedia = StorefrontMedia(
        URI("file:///android_asset/missing-fixture.png"),
        "Owned fixture",
        240,
        480
    )
): HomeUiState {
    val collection = HomeCollectionItem(
        stableId = "fixture-collection",
        label = HomeText.Remote(LONG_COLLECTION),
        summary = HomeCollectionSummary("gid://shopify/Collection/1", "fixture-collection", LONG_COLLECTION, media)
    )
    val artwork = HomeProductSummary(
        id = "gid://shopify/Product/1",
        handle = "fixture-artwork",
        title = LONG_ARTWORK,
        availableForSale = true,
        media = media,
        price = StorefrontMoney(BigDecimal("12.34"), "TRY")
    )
    return HomeUiState(
        presentation = HomePresentation(
            editorial = HomeEditorialState.Packaged,
            renderedSections = listOf(
                HomeRenderedSection.CollectionGrid(
                    "fixture-collections",
                    HomeText.Packaged(com.tumiart.mobile.R.string.home_product_range_title),
                    listOf(collection)
                ),
                HomeRenderedSection.FeaturedProduct(
                    "fixture-feature",
                    HomeText.Packaged(com.tumiart.mobile.R.string.tumiart_home_featured_product),
                    HomeFeaturedItem(artwork)
                )
            ),
            source = HomeContentSource.PACKAGED,
            resourceStatus = HomeResourceStatus.COMPLETE,
            editorialExpiresAtMillis = null
        ),
        loading = false,
        requestActive = false
    )
}
