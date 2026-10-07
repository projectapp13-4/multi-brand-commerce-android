package com.gurbakir.mobile.wishlist

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.gurbakir.foundation.config.PrimaryNavigationDestination
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Final GREEN-only lifetime draft. The actual destination must own paired LifecycleResumeEffect. */
@RunWith(AndroidJUnit4::class)
class WishlistDestinationLifetimeGreenTest {
    @get:Rule val composeRule = createAndroidComposeRule<WishlistLifecycleProbeActivity>()
    private lateinit var fixture: WishlistGreenDestinationFixture

    @After
    fun closeOwnedRoomAndModel() {
        if (!::fixture.isInitialized) return
        composeRule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        composeRule.runOnIdle { fixture.attached.value = false }
        composeRule.waitForIdle()
        composeRule.runOnIdle { fixture.clearOwnedModel() }
        runBlocking(Dispatchers.IO) { fixture.room.finishPhysicalReads() }
        awaitReturnedLoads()
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        fixture.room.closeDatabase()
    }

    @Test
    fun nativePauseRejectsLateResultAndResumeStartsOneFreshInterval() {
        showHeld()
        val retained = fixture.currentModel
        val entryId = fixture.currentEntry.id
        composeRule.activityRule.scenario.moveToState(Lifecycle.State.STARTED)
        composeRule.activityRule.scenario.onActivity {
            assertFalse(fixture.currentEntry.lifecycle.currentState == Lifecycle.State.RESUMED)
        }
        fixture.room.gateway.setProduct("Fresh after pause")
        releaseHeldAndSettle(expectedDelegateStarts = 1)
        assertEquals(null, retained.state.value.entries.single().product)
        composeRule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        awaitHeldRound(2)
        composeRule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        composeRule.runOnIdle { fixture.layoutInset.value++ }
        composeRule.waitForIdle()
        assertEquals(2, fixture.room.gateway.snapshot().started)
        assertEquals(RoomGreenLoadSnapshot(2, 1, 1), fixture.observedRepository.snapshot())
        fixture.room.gateway.release(2)
        awaitSettledRound(2)
        composeRule.runOnIdle {
            assertSame(retained, fixture.currentModel)
            assertEquals(entryId, fixture.currentEntry.id)
            assertEquals(1, fixture.creations)
            assertEquals("Fresh after pause", retained.state.value.entries.single().product?.title)
        }
    }

    @Test
    fun nativeStopKeepsLocalRoomProjectionAndEmptyResumeMakesNoProductRead() {
        showHeld()
        val retained = fixture.currentModel
        composeRule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        runBlocking(Dispatchers.IO) { fixture.room.directClear() }
        composeRule.waitUntil(timeoutMillis = 5_000) { retained.state.value.entries.isEmpty() }
        assertEquals(1, fixture.room.gateway.snapshot().started)
        assertEquals(1, fixture.observedRepository.snapshot().started)
        releaseHeldAndSettle(expectedDelegateStarts = 1)
        assertTrue(retained.state.value.entries.isEmpty())
        composeRule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        composeRule.onNodeWithTag(WishlistTestTags.EMPTY).assertIsDisplayed()
        composeRule.runOnIdle {
            assertSame(retained, fixture.currentModel)
            assertEquals(1, fixture.room.gateway.snapshot().started)
            assertEquals(1, fixture.creations)
        }
    }

    @Test
    fun compositionDisposalInvalidatesWhileActualEntryRemainsResumed() {
        showHeld()
        val retained = fixture.currentModel
        val entryId = fixture.currentEntry.id
        composeRule.runOnIdle { fixture.attached.value = false }
        composeRule.onNodeWithTag(ROOM_GREEN_DISPOSED).assertIsDisplayed()
        composeRule.runOnIdle { assertEquals(Lifecycle.State.RESUMED, fixture.currentEntry.lifecycle.currentState) }
        fixture.room.gateway.setProduct("Fresh after disposal")
        releaseHeldAndSettle(expectedDelegateStarts = 1)
        assertEquals(null, retained.state.value.entries.single().product)
        composeRule.runOnIdle { fixture.attached.value = true }
        composeRule.onNodeWithTag(WishlistTestTags.ROOT).assertIsDisplayed()
        awaitHeldRound(2)
        fixture.room.gateway.release(2)
        awaitSettledRound(2)
        composeRule.runOnIdle {
            assertSame(retained, fixture.currentModel)
            assertEquals(entryId, fixture.currentEntry.id)
            assertEquals(1, fixture.creations)
            assertEquals("Fresh after disposal", retained.state.value.entries.single().product?.title)
        }
    }

    @Test
    fun hiddenRetainedEntryProjectsDirectClearAndReaddBeforeReentryWithoutRead() {
        showHeld()
        val retained = fixture.currentModel
        val id = roomGreenId(1)
        composeRule.runOnIdle { fixture.navigate(PrimaryNavigationDestination.CATEGORIES) }
        composeRule.onNodeWithTag(ROOM_GREEN_CATEGORIES).assertIsDisplayed()
        composeRule.runOnIdle { assertFalse(fixture.currentEntry.lifecycle.currentState == Lifecycle.State.RESUMED) }
        runBlocking(Dispatchers.IO) { fixture.room.directClear() }
        composeRule.waitUntil(timeoutMillis = 5_000) { retained.state.value.entries.isEmpty() }
        runBlocking(Dispatchers.IO) { fixture.room.directInsert(StoredWishlistEntry(id, 200)) }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            retained.state.value.entries.singleOrNull()?.let { it.productId == id && it.addedAtEpochMillis == 200L } ==
                true
        }
        assertEquals(null, retained.state.value.entries.single().product)
        assertEquals(1, fixture.room.gateway.snapshot().started)
        assertEquals(1, fixture.observedRepository.snapshot().started)
        fixture.room.gateway.setProduct("Current readded Room product")
        releaseHeldAndSettle(expectedDelegateStarts = 1)
        assertEquals(200L, retained.state.value.entries.single().addedAtEpochMillis)
        assertEquals(null, retained.state.value.entries.single().product)
        composeRule.runOnIdle { fixture.navigate(PrimaryNavigationDestination.WISHLIST) }
        composeRule.onNodeWithTag(WishlistTestTags.ROOT).assertIsDisplayed()
        awaitHeldRound(2)
        fixture.room.gateway.release(2)
        awaitSettledRound(2)
        composeRule.runOnIdle {
            assertSame(retained, fixture.currentModel)
            assertEquals(200L, retained.state.value.entries.single().addedAtEpochMillis)
            assertEquals("Current readded Room product", retained.state.value.entries.single().product?.title)
        }
    }

    @Test
    fun unrelatedRecompositionAndSameNativeResumedStateDoNotDuplicateVisibleRound() {
        showHeld()
        fixture.room.gateway.release(1)
        awaitSettledRound(1)
        val retained = fixture.currentModel
        composeRule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        composeRule.runOnIdle { fixture.layoutInset.value++ }
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            assertSame(retained, fixture.currentModel)
            assertEquals(Lifecycle.State.RESUMED, fixture.currentEntry.lifecycle.currentState)
            assertEquals(1, fixture.creations)
            assertEquals(1, fixture.room.gateway.snapshot().started)
            assertEquals(1, fixture.observedRepository.snapshot().started)
            assertEquals(RoomGreenLoadSnapshot(1, 0, 1), fixture.observedRepository.snapshot())
        }
    }

    private fun showHeld() {
        val room = WishlistRoomGreenFixture(composeRule.activity.applicationContext)
        fixture = WishlistGreenDestinationFixture(room)
        runBlocking(Dispatchers.IO) { room.seed(StoredWishlistEntry(roomGreenId(1), 10)) }
        composeRule.setContent { fixture.Content() }
        composeRule.onNodeWithTag(ROOM_GREEN_HOME).assertIsDisplayed()
        composeRule.runOnIdle { fixture.navigate(PrimaryNavigationDestination.WISHLIST) }
        composeRule.onNodeWithTag(WishlistTestTags.ROOT).assertIsDisplayed()
        awaitHeldRound(1)
        composeRule.waitUntil(timeoutMillis = 5_000) { fixture.currentModel.state.value.entries.size == 1 }
    }

    private fun awaitHeldRound(total: Int) {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            val value = fixture.room.gateway.snapshot()
            value.started == total && value.active == 1 && value.completed == total - 1
        }
        awaitExactDelegate(total, active = 1, completed = total - 1)
    }

    private fun awaitReturnedLoads() {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            val value = fixture.observedRepository.snapshot()
            value.active == 0 && value.completed == value.started
        }
    }

    private fun awaitExactDelegate(total: Int, active: Int, completed: Int) {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            val value = fixture.observedRepository.snapshot()
            value.started == total && value.active == active && value.completed == completed
        }
        assertEquals(RoomGreenLoadSnapshot(total, active, completed), fixture.observedRepository.snapshot())
    }

    private fun releaseHeldAndSettle(expectedDelegateStarts: Int) {
        fixture.room.gateway.release(fixture.room.gateway.snapshot().started)
        runBlocking(Dispatchers.IO) {
            fixture.room.gateway.awaitSnapshot { it.active == 0 && it.completed == it.started }
        }
        awaitExactDelegate(expectedDelegateStarts, active = 0, completed = expectedDelegateStarts)
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
    }

    private fun awaitSettledRound(total: Int) {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            val value = fixture.room.gateway.snapshot()
            value.started == total && value.active == 0 && value.completed == total
        }
        awaitExactDelegate(total, active = 0, completed = total)
        composeRule.waitUntil(timeoutMillis = 5_000) { !fixture.currentModel.state.value.loading }
        composeRule.waitForIdle()
    }
}
