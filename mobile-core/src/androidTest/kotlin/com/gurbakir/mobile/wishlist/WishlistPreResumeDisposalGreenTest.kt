@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package com.gurbakir.mobile.wishlist

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavBackStackEntry
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.gurbakir.foundation.config.PrimaryNavigationDestination
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Additional future connected GREEN only. The actual incoming navigation entry has never RESUMED.
 * The Activity may be RESUMED. No LifecycleRegistry, ViewModel hook, direct cancel or forced lifecycle.
 * Requires the separately reviewed observer-only extension to the frozen GREEN fixture.
 */
@RunWith(AndroidJUnit4::class)
class WishlistPreResumeDisposalGreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<WishlistLifecycleProbeActivity>()

    private val ownerProbe = PreResumeEntryProbe()
    private val callerProbe = PreResumeCallerProbe()
    private lateinit var fixture: WishlistGreenDestinationFixture
    private lateinit var publications: PreResumePublicationProbe

    @Test
    fun actualStartedEntryDisposalBeforeFirstResumeRejectsBootstrapAndReentryIsFresh() {
        var primaryFailure: Throwable? = null
        try {
            val room = WishlistRoomGreenFixture(composeRule.activity.applicationContext)
            fixture = WishlistGreenDestinationFixture(
                room = room,
                onActualEntry = ownerProbe::observe,
                onLoadStarted = callerProbe::observe
            )
            runBlocking(Dispatchers.IO) { room.seed(StoredWishlistEntry(roomGreenId(1), 10)) }
            composeRule.setContent { fixture.Content() }
            composeRule.onNodeWithTag(ROOM_GREEN_HOME).assertIsDisplayed()
            composeRule.waitForIdle()

            composeRule.mainClock.autoAdvance = false
            val transitionStart = composeRule.mainClock.currentTime
            composeRule.runOnIdle { fixture.navigate(PrimaryNavigationDestination.WISHLIST) }
            repeat(3) { commitOneHeldFrame() }
            composeRule.onNodeWithTag(WishlistTestTags.ROOT).assertExists()
            assertStartedWithoutResume(transitionStart)
            awaitHeldRound(1)
            composeRule.waitUntil(timeoutMillis = 5_000) {
                fixture.currentModel.state.value.entries.singleOrNull()?.productId == roomGreenId(1)
            }
            assertTrue("The new model bootstrap is explicitly fresh", callerProbe.call(1).forceRefresh)
            val retained = fixture.currentModel
            val entryId = fixture.currentEntry.id
            assertPendingRows(retained)

            // Remove only the real destination branch; retain its actual entry and VM store.
            composeRule.runOnIdle { fixture.attached.value = false }
            commitOneHeldFrame()
            composeRule.onNodeWithTag(ROOM_GREEN_DISPOSED).assertExists()
            composeRule.onNodeWithTag(WishlistTestTags.ROOT).assertDoesNotExist()
            assertStartedWithoutResume(transitionStart)
            composeRule.waitUntil(timeoutMillis = 5_000) { callerProbe.call(1).job.isCancelled }
            assertTrue(
                "Disposal must cancel its actual caller before physical return",
                callerProbe.call(1).job.isCancelled
            )
            assertEquals(RoomGreenPhysicalSnapshot(1, 1, 0, 1), room.gateway.snapshot())
            assertEquals(1, fixture.observedRepository.snapshot().started)
            assertEquals(1, callerProbe.count())
            assertEquals(1, fixture.creations)
            val closedState = retained.state.value
            assertPendingRows(retained)
            publications = composeRule.runOnIdle { PreResumePublicationProbe(retained.state) }

            // Call 1 captured Initial Room product on entry; changing this reply cannot repair it.
            room.gateway.setProduct(FRESH_TITLE)
            room.gateway.release(1)
            awaitSettledRound(1)
            assertStartedWithoutResume(transitionStart)
            assertEquals("The obsolete return must not replace the closed state", closedState, retained.state.value)
            assertClosedPublications(closedState)
            assertPendingRows(retained)
            composeRule.onNodeWithTag(ROOM_GREEN_DISPOSED).assertExists()
            composeRule.onNodeWithTag(WishlistTestTags.ROOT).assertDoesNotExist()
            composeRule.onNodeWithText(OBSOLETE_TITLE).assertDoesNotExist()

            // Reattachment below first RESUME must not resurrect the closed bootstrap intent.
            composeRule.runOnIdle { fixture.attached.value = true }
            commitOneHeldFrame()
            composeRule.onNodeWithTag(WishlistTestTags.ROOT).assertExists()
            assertStartedWithoutResume(transitionStart)
            assertEquals(RoomGreenPhysicalSnapshot(1, 0, 1, 1), room.gateway.snapshot())
            assertEquals(RoomGreenLoadSnapshot(1, 0, 1), fixture.observedRepository.snapshot())
            assertEquals(1, callerProbe.count())
            assertPendingRows(retained)
            composeRule.onNodeWithText(OBSOLETE_TITLE).assertDoesNotExist()
            assertClosedPublications(closedState)
            runBlocking(Dispatchers.IO) { publications.close() }

            // Let the genuine ProductionNavHost transition finish. Do not call a VM lifecycle hook.
            composeRule.mainClock.autoAdvance = true
            composeRule.waitForIdle()
            composeRule.waitUntil(timeoutMillis = 5_000) {
                composeRule.runOnIdle { fixture.currentEntry.lifecycle.currentState == Lifecycle.State.RESUMED }
            }
            awaitHeldRound(2)
            assertEquals(1, ownerProbe.resumeCount())
            assertTrue(callerProbe.call(2).forceRefresh)
            assertFalse(callerProbe.call(2).job.isCancelled)
            assertPendingRows(retained)
            room.gateway.release(2)
            awaitSettledRound(2)
            composeRule.waitUntil(timeoutMillis = 5_000) {
                retained.state.value.entries.singleOrNull()?.product?.title == FRESH_TITLE
            }
            composeRule.waitForIdle()
            composeRule.runOnIdle {
                assertSame(retained, fixture.currentModel)
                assertEquals(entryId, fixture.currentEntry.id)
                assertEquals(1, fixture.creations)
                assertEquals(1, ownerProbe.resumeCount())
                assertEquals(2, callerProbe.count())
            }
            composeRule.onNodeWithText(FRESH_TITLE).assertIsDisplayed()
            composeRule.onNodeWithText(OBSOLETE_TITLE).assertDoesNotExist()
        } catch (failure: Throwable) {
            primaryFailure = failure
            throw failure
        } finally {
            try {
                closeOwnedResources()
            } catch (cleanupFailure: Throwable) {
                if (primaryFailure != null) {
                    primaryFailure.addSuppressed(cleanupFailure)
                } else {
                    throw cleanupFailure
                }
            }
        }
    }

    private fun commitOneHeldFrame() {
        check(!composeRule.mainClock.autoAdvance)
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.waitForIdle()
    }

    private fun assertStartedWithoutResume(transitionStart: Long) {
        composeRule.runOnIdle {
            assertSame(ownerProbe.entry(), fixture.currentEntry)
            assertEquals(Lifecycle.State.STARTED, fixture.currentEntry.lifecycle.currentState)
            assertEquals(0, ownerProbe.resumeCount())
            assertFalse(ownerProbe.initialState() == Lifecycle.State.RESUMED)
            assertEquals(1, fixture.creations)
        }
        assertFalse(composeRule.mainClock.autoAdvance)
        assertTrue(
            "Only bounded setup frames may advance the held enter transition",
            composeRule.mainClock.currentTime - transitionStart < 700
        )
    }

    private fun assertPendingRows(model: WishlistViewModel) {
        val row = model.state.value.entries.single()
        assertEquals(roomGreenId(1), row.productId)
        assertEquals(10L, row.addedAtEpochMillis)
        assertNull(row.product)
        assertNull(row.issue)
    }

    private fun assertClosedPublications(closedState: WishlistUiState) {
        val observed = publications.values()
        assertTrue("The continuously subscribed probe must observe its initial projection", observed.isNotEmpty())
        observed.forEach { state ->
            assertEquals("No observed obsolete-return publication may change the closed projection", closedState, state)
            assertTrue(state.entries.none { it.product != null })
        }
    }

    private fun awaitHeldRound(total: Int) {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            fixture.room.gateway.snapshot() == RoomGreenPhysicalSnapshot(total, 1, total - 1, 1)
        }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            fixture.observedRepository.snapshot() == RoomGreenLoadSnapshot(total, 1, total - 1)
        }
        assertEquals(RoomGreenPhysicalSnapshot(total, 1, total - 1, 1), fixture.room.gateway.snapshot())
        assertEquals(RoomGreenLoadSnapshot(total, 1, total - 1), fixture.observedRepository.snapshot())
        assertEquals(total, callerProbe.count())
    }

    private fun awaitSettledRound(total: Int) {
        runBlocking(Dispatchers.IO) {
            fixture.room.gateway.awaitSnapshot {
                it == RoomGreenPhysicalSnapshot(total, 0, total, 1)
            }
        }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            fixture.observedRepository.snapshot() == RoomGreenLoadSnapshot(total, 0, total) &&
                callerProbe.calls().all { it.job.isCompleted }
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        composeRule.waitForIdle()
        assertEquals(RoomGreenPhysicalSnapshot(total, 0, total, 1), fixture.room.gateway.snapshot())
        assertEquals(RoomGreenLoadSnapshot(total, 0, total), fixture.observedRepository.snapshot())
        assertEquals(total, callerProbe.count())
    }

    private fun closeOwnedResources() {
        composeRule.mainClock.autoAdvance = true
        if (!::fixture.isInitialized) return
        var cleanupFailure: Throwable? = null
        var hostDestroyed = false
        fun attempt(block: () -> Unit) {
            try {
                block()
            } catch (failure: Throwable) {
                if (cleanupFailure == null) {
                    cleanupFailure = failure
                } else {
                    cleanupFailure?.addSuppressed(failure)
                }
            }
        }
        // Release first on failure so native destruction/caller completion can never wait on a held callback.
        fixture.room.gateway.releaseAll()
        attempt {
            composeRule.activityRule.scenario.moveToState(Lifecycle.State.DESTROYED)
            hostDestroyed = composeRule.activityRule.scenario.state == Lifecycle.State.DESTROYED
            check(hostDestroyed) { "Destroy the actual host before closing its Room database" }
        }
        attempt {
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                if (hostDestroyed) {
                    fixture.attached.value = false
                    fixture.clearOwnedModel()
                }
                ownerProbe.detach()
            }
        }
        attempt { runBlocking(Dispatchers.IO) { fixture.room.finishPhysicalReads() } }
        attempt {
            composeRule.waitUntil(timeoutMillis = 5_000) {
                val loads = fixture.observedRepository.snapshot()
                loads.active == 0 && loads.completed == loads.started && callerProbe.calls().all { it.job.isCompleted }
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        }
        if (::publications.isInitialized) attempt { runBlocking(Dispatchers.IO) { publications.close() } }
        if (hostDestroyed && fixture.room.gateway.snapshot().active == 0 &&
            fixture.observedRepository.snapshot().active == 0 && callerProbe.calls().all { it.job.isCompleted }
        ) {
            attempt { fixture.room.closeDatabase() }
        } else {
            attempt { error("Cleanup did not settle the owner; keep Room open instead of closing under active work") }
        }
        cleanupFailure?.let { throw it }
    }

    private companion object {
        const val OBSOLETE_TITLE = "Initial Room product"
        const val FRESH_TITLE = "Fresh after pre-resume disposal"
    }
}

/** Only observes the genuine entry before its default-key model/destination lookup. */
private class PreResumeEntryProbe {
    private var ownedEntry: NavBackStackEntry? = null
    private var firstState: Lifecycle.State? = null
    private val resumes = AtomicInteger()
    private val observer = LifecycleEventObserver { _, event ->
        if (event == Lifecycle.Event.ON_RESUME) resumes.incrementAndGet()
    }

    fun observe(entry: NavBackStackEntry) {
        if (ownedEntry === entry) return
        check(ownedEntry == null) { "Only one actual incoming entry belongs to this regression" }
        ownedEntry = entry
        firstState = entry.lifecycle.currentState
        entry.lifecycle.addObserver(observer)
    }

    fun entry(): NavBackStackEntry = checkNotNull(ownedEntry)
    fun initialState(): Lifecycle.State = checkNotNull(firstState)
    fun resumeCount(): Int = resumes.get()
    fun detach() {
        ownedEntry?.lifecycle?.removeObserver(observer)
    }
}

private data class PreResumeCaller(val job: Job, val forceRefresh: Boolean)

/** Never cancels or completes a Job; observes only jobs passed by the transparent repository delegate. */
private class PreResumeCallerProbe {
    private val lock = Any()
    private val values = mutableListOf<PreResumeCaller>()
    fun observe(job: Job, forceRefresh: Boolean) = synchronized(lock) { values += PreResumeCaller(job, forceRefresh) }
    fun calls(): List<PreResumeCaller> = synchronized(lock) { values.toList() }
    fun call(number: Int): PreResumeCaller = synchronized(lock) { values[number - 1] }
    fun count(): Int = synchronized(lock) { values.size }
}

/** Passive continuous subscriber; it does not mutate the VM or promise unconflated StateFlow internals. */
private class PreResumePublicationProbe(state: StateFlow<WishlistUiState>) {
    private val lock = Any()
    private val observed = mutableListOf<WishlistUiState>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val collector = scope.launch(start = CoroutineStart.UNDISPATCHED) {
        state.collect { value -> synchronized(lock) { observed += value } }
    }

    fun values(): List<WishlistUiState> = synchronized(lock) { observed.toList() }
    suspend fun close() {
        collector.cancelAndJoin()
        scope.cancel()
    }
}
