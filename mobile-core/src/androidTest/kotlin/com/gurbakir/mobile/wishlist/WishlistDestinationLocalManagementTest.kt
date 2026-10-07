@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package com.gurbakir.mobile.wishlist

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gurbakir.foundation.config.PrimaryNavigationDestination
import com.gurbakir.mobile.core.R
import com.gurbakir.mobile.performDeterministicClick
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Actual repository + ViewModel + destination + rendered actions, while physical callback replies stay held. */
@RunWith(AndroidJUnit4::class)
class WishlistDestinationLocalManagementTest {
    @get:Rule
    val composeRule = createComposeRule()
    private lateinit var fixture: WishlistDestinationFixture

    @After
    fun closeOwnedHost() {
        if (::fixture.isInitialized) composeRule.runOnIdle { fixture.close() }
    }

    @Test
    fun pendingLocalRowRemoveWorksBeforePhysicallyHeldHydrationReturns() {
        show(count = 1, held = true)
        val id = fixture.initial.single().productId
        composeRule.onNodeWithTag(WishlistTestTags.item(id)).assertIsDisplayed()
        composeRule.onNodeWithText(fixture.context.getString(R.string.wishlist_product_service)).assertDoesNotExist()
        val removeText = fixture.context.getString(R.string.wishlist_remove)
        composeRule.onNode(hasText(removeText) and hasAnyAncestor(hasTestTag(WishlistTestTags.item(id))))
            .assertIsEnabled().performDeterministicClick()
        assertLocalEmptyWithReadsStillHeld()
        composeRule.onNodeWithTag(WishlistTestTags.EMPTY).assertIsDisplayed()
        releaseAndAssertNoResurrection()
    }

    @Test
    fun pendingRowsClearAndActualConfirmationStayUsableAtDoubleFontDuringPhysicalHydration() {
        show(count = 2, held = true, fontScale = 2f)
        composeRule.onNodeWithTag(WishlistTestTags.CLEAR)
            .assertExists("Local Clear must exist while physical product responses are still held")
        composeRule.onNodeWithTag(WishlistTestTags.CONTENT)
            .performScrollToNode(hasTestTag(WishlistTestTags.CLEAR))
        composeRule.onNodeWithTag(WishlistTestTags.CLEAR).assertIsDisplayed().assertIsEnabled()
            .performDeterministicClick()
        composeRule.onNodeWithTag(WishlistTestTags.CLEAR_CONFIRM).assertIsDisplayed().performDeterministicClick()
        assertLocalEmptyWithReadsStillHeld()
        composeRule.runOnIdle { assertEquals(1, fixture.store.clears.get()) }
        composeRule.onNodeWithTag(WishlistTestTags.EMPTY).assertIsDisplayed()
        releaseAndAssertNoResurrection()
    }

    @Test
    fun healthyLoadedActualDestinationRemoveAndConfirmedClearStillUpdateLocalStore() {
        show(count = 2, held = false)
        val id = fixture.initial.first().productId
        composeRule.onNodeWithTag(WishlistTestTags.toggle(id)).performScrollTo().performDeterministicClick()
        composeRule.waitUntil(timeoutMillis = 5_000) { fixture.store.snapshot().size == 1 }
        composeRule.runOnIdle {
            assertFalse(fixture.store.snapshot().any { it.productId == id })
            assertFalse(fixture.currentModel.state.value.mutating)
        }
        composeRule.onNodeWithTag(WishlistTestTags.CONTENT)
            .performScrollToNode(hasTestTag(WishlistTestTags.CLEAR))
        composeRule.onNodeWithTag(WishlistTestTags.CLEAR).assertIsEnabled().performDeterministicClick()
        composeRule.onNodeWithTag(WishlistTestTags.CLEAR_CONFIRM).performDeterministicClick()
        composeRule.waitUntil(timeoutMillis = 5_000) { fixture.store.snapshot().isEmpty() }
        composeRule.onNodeWithTag(WishlistTestTags.EMPTY).assertIsDisplayed()
        composeRule.runOnIdle { assertEquals(1, fixture.store.clears.get()) }
    }

    private fun show(count: Int, held: Boolean, fontScale: Float = 1f) {
        fixture = WishlistDestinationFixture(count, holdReads = held, fontScale = fontScale)
        composeRule.setContent { fixture.Content() }
        composeRule.onNodeWithTag(WISHLIST_HOST_HOME).assertIsDisplayed()
        composeRule.runOnIdle { fixture.navigate(PrimaryNavigationDestination.WISHLIST) }
        composeRule.onNodeWithTag(WishlistTestTags.ROOT).assertIsDisplayed()
        val expectedActive = if (held) count else 0
        val expectedCompleted = if (held) 0 else count
        composeRule.waitUntil(timeoutMillis = 5_000) {
            fixture.gateway.started == count && fixture.gateway.active == expectedActive &&
                fixture.gateway.completed == expectedCompleted
        }
        composeRule.runOnIdle {
            assertEquals(1, fixture.creations)
            assertEquals(count, fixture.store.snapshot().size)
            assertEquals(if (held) count else 0, fixture.gateway.active)
            assertEquals(if (held) 0 else count, fixture.gateway.completed)
        }
    }

    private fun assertLocalEmptyWithReadsStillHeld() {
        composeRule.waitUntil(timeoutMillis = 5_000) { fixture.store.snapshot().isEmpty() }
        composeRule.runOnIdle {
            assertTrue(fixture.currentModel.state.value.entries.isEmpty())
            assertFalse(fixture.currentModel.state.value.mutating)
            assertEquals(fixture.initial.size, fixture.gateway.active)
            assertEquals(0, fixture.gateway.completed)
        }
    }

    private fun releaseAndAssertNoResurrection() {
        composeRule.runOnIdle { fixture.gateway.releaseAll() }
        composeRule.waitUntil(timeoutMillis = 5_000) { fixture.gateway.active == 0 }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(WishlistTestTags.EMPTY).assertIsDisplayed()
        composeRule.runOnIdle {
            assertTrue(fixture.store.snapshot().isEmpty())
            assertTrue(fixture.currentModel.state.value.entries.isEmpty())
            assertFalse(fixture.currentModel.state.value.mutating)
        }
    }
}
