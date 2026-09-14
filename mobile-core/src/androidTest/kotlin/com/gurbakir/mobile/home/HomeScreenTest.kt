@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package com.gurbakir.mobile.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gurbakir.mobile.CoreTestTheme
import com.gurbakir.storefront.HomeCollectionSummary
import com.gurbakir.storefront.HomeProductSummary
import com.gurbakir.storefront.StorefrontMedia
import com.gurbakir.storefront.StorefrontMoney
import java.math.BigDecimal
import java.net.URI
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeScreenTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun healthyAndEmptyHomeExposeAccessibleManualRefresh() {
        var refreshes = 0
        var state by mutableStateOf(presentationState())
        val actions = HomeActions(refreshContent = { refreshes += 1 })
        composeRule.setContent { CoreTestTheme { HomeScreen(state, "Test", actions) } }
        composeRule.onNodeWithTag(HomeTestTags.REFRESH).assertIsDisplayed().assertIsEnabled().performClick()
        assertEquals(1, refreshes)

        composeRule.runOnIdle {
            state = presentationState(editorial = HomeEditorialState.IntentionalEmpty, sections = emptyList())
        }
        composeRule.onNodeWithTag(HomeTestTags.EMPTY).assertIsDisplayed()
        composeRule.onNodeWithTag(HomeTestTags.REFRESH).assertIsEnabled().performClick()
        assertEquals(2, refreshes)
    }

    @Test
    fun refreshIsDisabledOnlyWhileRequestIsActive() {
        var state by mutableStateOf(HomeUiState(requestActive = true))
        val actions = HomeActions(refreshContent = {})
        composeRule.setContent { CoreTestTheme { HomeScreen(state, "Test", actions) } }
        composeRule.onNodeWithTag(HomeTestTags.REFRESH).assertIsDisplayed().assertIsNotEnabled()

        composeRule.runOnIdle {
            state = HomeUiState(
                loading = false,
                requestActive = false,
                failure = HomeLoadFailure(HomeLoadFailureCategory.SERVICE, false)
            )
        }
        composeRule.onNodeWithTag(HomeTestTags.HOME_ERROR).assertIsDisplayed()
        composeRule.onNodeWithTag(HomeTestTags.REFRESH).assertIsEnabled()
    }

    @Test
    fun legalSupportIsAppendedAcrossLoadingEmptyErrorAndHealthyStates() {
        val actions = HomeActions(refreshContent = {}, openLegalSupport = {})
        val states = listOf(
            HomeUiState(requestActive = true),
            presentationState(editorial = HomeEditorialState.IntentionalEmpty, sections = emptyList()),
            HomeUiState(
                loading = false,
                requestActive = false,
                failure = HomeLoadFailure(HomeLoadFailureCategory.CONNECTION, true)
            ),
            presentationState()
        )
        var state by mutableStateOf(states.first())
        composeRule.setContent { CoreTestTheme { HomeScreen(state, "Test", actions) } }
        states.forEach { next ->
            composeRule.runOnIdle { state = next }
            composeRule.onNodeWithTag(HomeTestTags.LEGAL_SUPPORT).performScrollTo().assertIsDisplayed()
        }
    }

    @Test
    fun typedCollectionAndProductActionsUseCurrentResolvedIdentity() {
        var collection: String? = null
        var product: String? = null
        setHome(
            presentationState(sections = listOf(collectionSection(), productSection())),
            HomeActions(
                refreshContent = {},
                openCollection = { collection = it },
                openProduct = { product = it }
            )
        )

        composeRule.onNodeWithTag(HomeTestTags.collection(COLLECTION_GID)).performScrollTo().performClick()
        composeRule.onNodeWithTag(HomeTestTags.FEATURED_CARD).performScrollTo().performClick()
        assertEquals("current-collection-handle", collection)
        assertEquals(PRODUCT_GID, product)
    }

    @Test
    fun nonemptyEditorialWithNoRenderableResourcesIsNotIntentionalEmpty() {
        setHome(
            presentationState(
                editorial = HomeEditorialState.NonEmpty(emptyList()),
                sections = emptyList(),
                resourceStatus = HomeResourceStatus.NONE_RENDERABLE
            ),
            HomeActions(refreshContent = {})
        )
        composeRule.onNodeWithTag(HomeTestTags.NONRENDERABLE).assertIsDisplayed()
    }

    private fun setHome(state: HomeUiState, actions: HomeActions) {
        composeRule.setContent { CoreTestTheme { HomeScreen(state, "Test", actions) } }
    }

    private fun presentationState(
        editorial: HomeEditorialState = HomeEditorialState.Packaged,
        sections: List<HomeRenderedSection> = listOf(collectionSection()),
        resourceStatus: HomeResourceStatus = HomeResourceStatus.COMPLETE
    ) = HomeUiState(
        presentation = HomePresentation(
            editorial,
            sections,
            HomeContentSource.PACKAGED,
            resourceStatus,
            null
        ),
        loading = false,
        requestActive = false
    )

    private fun collectionSection() = HomeRenderedSection.CollectionGrid(
        "grid",
        HomeText.Remote("Collections"),
        listOf(
            HomeCollectionItem(
                COLLECTION_GID,
                HomeText.Remote("Collection"),
                HomeCollectionSummary(COLLECTION_GID, "current-collection-handle", "Collection", media())
            )
        )
    )

    private fun productSection() = HomeRenderedSection.FeaturedProduct(
        "featured",
        HomeText.Remote("Featured"),
        HomeFeaturedItem(
            HomeProductSummary(
                PRODUCT_GID,
                "current-product-handle",
                "Product",
                true,
                media(),
                StorefrontMoney(BigDecimal("1.00"), "TRY")
            )
        )
    )

    private fun media() = StorefrontMedia(
        URI("https://cdn.shopify.com/s/files/1/test.jpg"),
        "Product",
        100,
        100
    )

    private companion object {
        const val COLLECTION_GID = "gid://shopify/Collection/1"
        const val PRODUCT_GID = "gid://shopify/Product/1"
    }
}
