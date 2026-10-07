@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package com.gurbakir.mobile.wishlist

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gurbakir.foundation.config.PrimaryNavigationDestination
import com.gurbakir.mobile.core.R
import java.math.BigDecimal
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Actual production destination/primary-stack entry; no refresh/new API/elapsed time manufactures freshness. */
@RunWith(AndroidJUnit4::class)
class WishlistDestinationFreshnessTest {
    @get:Rule
    val composeRule = createComposeRule()
    private lateinit var fixture: WishlistDestinationFixture

    @After
    fun closeOwnedHost() {
        if (::fixture.isInitialized) composeRule.runOnIdle { fixture.close() }
    }

    @Test
    fun retainedPrimaryEntryReadsChangedProductTruthAfterSuccessfulCache() {
        show()
        val retained = fixture.currentModel
        val entryId = fixture.currentEntry.id
        composeRule.onNodeWithText("Initial offline product").assertIsDisplayed()
        navigate(PrimaryNavigationDestination.CATEGORIES, WISHLIST_HOST_CATEGORIES)
        composeRule.runOnIdle {
            fixture.gateway.title = "Changed offline product"
            fixture.gateway.available = false
            fixture.gateway.price = "125.00"
        }
        navigate(PrimaryNavigationDestination.WISHLIST, WishlistTestTags.ROOT)
        awaitSettledRound(minimumReads = 2)
        composeRule.runOnIdle {
            assertSame(retained, fixture.currentModel)
            assertEquals(entryId, fixture.currentEntry.id)
            assertEquals(1, fixture.creations)
            assertEquals(2, fixture.gateway.started)
            val product = checkNotNull(fixture.currentModel.state.value.entries.single().product)
            assertEquals("Changed offline product", product.title)
            assertFalse(product.availableForSale)
            assertEquals(BigDecimal("125.00"), product.variants.single().price.amount)
        }
        composeRule.onNodeWithText("Changed offline product").assertIsDisplayed()
    }

    @Test
    fun retainedPrimaryEntryDiscoversUnpublishedProductWithoutPressingRetry() {
        show()
        val retained = fixture.currentModel
        assertFalse(retained.state.value.hasRetryableItems)
        navigate(PrimaryNavigationDestination.CATEGORIES, WISHLIST_HOST_CATEGORIES)
        composeRule.runOnIdle { fixture.gateway.removed = true }
        navigate(PrimaryNavigationDestination.WISHLIST, WishlistTestTags.ROOT)
        awaitSettledRound(minimumReads = 2)
        composeRule.runOnIdle {
            assertSame(retained, fixture.currentModel)
            assertEquals(2, fixture.gateway.started)
            assertEquals(WishlistItemIssue.REMOVED, fixture.currentModel.state.value.entries.single().issue)
            assertEquals(null, fixture.currentModel.state.value.entries.single().product)
        }
        composeRule.onNodeWithTag(WishlistTestTags.item(fixture.initial.single().productId)).assertIsDisplayed()
        composeRule.onNodeWithText(fixture.context.getString(R.string.wishlist_product_removed)).assertIsDisplayed()
    }

    @Test
    fun newActualDestinationModelWithSameRepositoryReadsFreshAfterOwnedHostDestruction() {
        show()
        val oldModel = fixture.currentModel
        val oldController = fixture.navController
        composeRule.runOnIdle {
            fixture.gateway.removed = true
            fixture.replaceOwnedHost()
        }
        composeRule.onNodeWithTag(WISHLIST_HOST_HOME).assertIsDisplayed()
        composeRule.runOnIdle { assertNotSame(oldController, fixture.navController) }
        navigate(PrimaryNavigationDestination.WISHLIST, WishlistTestTags.ROOT)
        awaitSettledRound(minimumReads = 2)
        composeRule.runOnIdle {
            assertNotSame(oldModel, fixture.currentModel)
            assertEquals(2, fixture.creations)
            assertEquals(2, fixture.gateway.started)
            assertEquals(WishlistItemIssue.REMOVED, fixture.currentModel.state.value.entries.single().issue)
        }
        composeRule.onNodeWithText(fixture.context.getString(R.string.wishlist_product_removed)).assertIsDisplayed()
    }

    @Test
    fun initialActualEntryHydratesOnceAndParentLayoutRecompositionDoesNotReadAgain() {
        show()
        val retained = fixture.currentModel
        composeRule.runOnIdle { fixture.layoutInset.value = 1 }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Initial offline product").assertIsDisplayed()
        composeRule.runOnIdle {
            assertSame(retained, fixture.currentModel)
            assertEquals(1, fixture.creations)
            assertEquals(1, fixture.gateway.started)
            assertEquals(Lifecycle.State.RESUMED, fixture.currentEntry.lifecycle.currentState)
        }
    }

    private fun show() {
        fixture = WishlistDestinationFixture(holdReads = false)
        composeRule.setContent { fixture.Content() }
        composeRule.onNodeWithTag(WISHLIST_HOST_HOME).assertIsDisplayed()
        navigate(PrimaryNavigationDestination.WISHLIST, WishlistTestTags.ROOT)
        awaitSettledRound(minimumReads = 1)
        composeRule.runOnIdle {
            assertEquals(1, fixture.gateway.started)
            assertEquals(1, fixture.creations)
            assertEquals(1, fixture.currentModel.state.value.entries.size)
        }
    }

    private fun awaitSettledRound(minimumReads: Int) {
        // Observe actual callbacks; a missing new round after reentry remains the intended freshness failure.
        composeRule.waitUntil(timeoutMillis = 5_000) {
            fixture.gateway.started >= minimumReads && fixture.gateway.active == 0 &&
                fixture.gateway.completed == fixture.gateway.started
        }
        composeRule.waitUntil(timeoutMillis = 5_000) { !fixture.currentModel.state.value.loading }
        composeRule.waitForIdle()
    }

    private fun navigate(destination: PrimaryNavigationDestination, tag: String) {
        composeRule.runOnIdle { fixture.navigate(destination) }
        composeRule.onNodeWithTag(tag).assertIsDisplayed()
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            assertEquals(Lifecycle.State.RESUMED, fixture.navController.currentBackStackEntry?.lifecycle?.currentState)
        }
    }
}
