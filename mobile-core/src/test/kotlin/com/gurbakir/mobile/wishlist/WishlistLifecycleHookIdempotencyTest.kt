package com.gurbakir.mobile.wishlist

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** Final-API JVM hook contract; actual Activity lifecycle/disposal remains covered by connected cases. */
@OptIn(ExperimentalCoroutinesApi::class)
class WishlistLifecycleHookIdempotencyTest {
    private val dispatcher = StandardTestDispatcher()

    @BeforeEach
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `constructor bootstrap is adopted and repeated hooks preserve fresh intervals and hidden local rows`() =
        runTest(dispatcher) {
            assertHookContract(bootstrapPending = false)
            assertHookContract(bootstrapPending = true)
        }

    private suspend fun TestScope.assertHookContract(bootstrapPending: Boolean) {
        val fixture = WishlistHydrationFixture(1)
        try {
            fixture.gateway.holdReads = false
            fixture.gateway.title = "Cached synthetic snapshot"
            val cached = fixture.repository.load() as WishlistLoadResult.Content
            assertEquals("Cached synthetic snapshot", cached.entries.single().product?.title)
            assertReads(fixture, started = 1, active = 0, completed = 1, canceled = 0)

            fixture.gateway.holdReads = true
            fixture.gateway.title = "Fresh synthetic constructor"
            val model = fixture.wishlist()
            runCurrent()
            assertReads(fixture, started = 2, active = 1, completed = 1, canceled = 0)
            assertLocalRows(model.state.value, fixture.initial)
            assertNull(model.state.value.entries.single().product)
            assertTrue(model.state.value.refreshing)

            assertBootstrapAdoptionAndRepeatedHide(model, fixture, bootstrapPending)
            val completedAfterHide = if (bootstrapPending) 1 else 2
            val canceledAfterHide = if (bootstrapPending) 1 else 0
            assertHiddenFeedAndFreshReentry(model, fixture, completedAfterHide, canceledAfterHide)
            assertEquals(2, fixture.gateway.maximumActive)
            assertEquals(1, fixture.store.writes)
        } finally {
            fixture.close()
            advanceUntilIdle()
            assertEquals(0, fixture.gateway.active, "Owned models and controlled physical reads must drain")
        }
    }

    private fun TestScope.assertBootstrapAdoptionAndRepeatedHide(
        model: WishlistViewModel,
        fixture: WishlistHydrationFixture,
        bootstrapPending: Boolean
    ) {
        if (!bootstrapPending) {
            fixture.gateway.releaseThrough(2)
            runCurrent()
            assertReads(fixture, started = 2, active = 0, completed = 2, canceled = 0)
            assertEquals("Fresh synthetic constructor", model.state.value.entries.single().product?.title)
        }
        model.onResumed()
        model.onResumed()
        runCurrent()
        assertReads(
            fixture,
            started = 2,
            active = if (bootstrapPending) 1 else 0,
            completed = if (bootstrapPending) 1 else 2,
            canceled = 0
        )
        model.onHidden()
        runCurrent()
        val hidden = model.state.value
        assertPendingRows(hidden, fixture.initial)
        val completed = if (bootstrapPending) 1 else 2
        val canceled = if (bootstrapPending) 1 else 0
        assertReads(fixture, 2, 0, completed, canceled)
        model.onHidden()
        runCurrent()
        assertEquals(hidden, model.state.value, "Repeated hide must not alter the current local presentation")
        assertReads(fixture, 2, 0, completed, canceled)
    }

    private suspend fun TestScope.assertHiddenFeedAndFreshReentry(
        model: WishlistViewModel,
        fixture: WishlistHydrationFixture,
        completed: Int,
        canceled: Int
    ) {
        val added = StoredWishlistEntry("gid://shopify/Product/2", 201L)
        fixture.now = added.addedAtEpochMillis
        assertEquals(WishlistMutationResult.Success, fixture.repository.setSaved(added.productId, true))
        runCurrent()
        val expectedLocal = listOf(added, fixture.initial.single())
        assertEquals(expectedLocal, fixture.store.load(WAVE5_PARTITION))
        assertEquals(1, fixture.store.writes)
        assertPendingRows(model.state.value, expectedLocal)
        assertReads(fixture, 2, 0, completed, canceled)
        fixture.gateway.title = "Fresh synthetic resumed interval"
        model.onResumed()
        model.onResumed()
        runCurrent()
        assertReads(fixture, 4, 2, completed, canceled)
        assertEquals(expectedLocal.map { it.productId }, fixture.gateway.requestedIds.takeLast(2))
        assertLocalRows(model.state.value, expectedLocal)
        fixture.gateway.releaseThrough(4)
        runCurrent()
        assertReads(fixture, 4, 0, completed + 2, canceled)
        assertResolvedRows(model.state.value, expectedLocal, "Fresh synthetic resumed interval")

        // No local mutation invalidates the new cache before this later legitimate resume.
        model.onHidden()
        runCurrent()
        val hidden = model.state.value
        assertPendingRows(hidden, expectedLocal)
        model.onHidden()
        runCurrent()
        assertEquals(hidden, model.state.value)
        assertReads(fixture, 4, 0, completed + 2, canceled)
        fixture.gateway.title = "Fresh synthetic cached reentry"
        model.onResumed()
        model.onResumed()
        runCurrent()
        assertReads(fixture, 6, 2, completed + 2, canceled)
        fixture.gateway.releaseThrough(6)
        runCurrent()
        assertReads(fixture, 6, 0, completed + 4, canceled)
        assertResolvedRows(model.state.value, expectedLocal, "Fresh synthetic cached reentry")
    }

    private fun assertReads(
        fixture: WishlistHydrationFixture,
        started: Int,
        active: Int,
        completed: Int,
        canceled: Int
    ) {
        assertEquals(started, fixture.gateway.started, "Physical read starts")
        assertEquals(active, fixture.gateway.active, "Physical reads still active")
        assertEquals(completed, fixture.gateway.completed, "Physical successful completions")
        assertEquals(canceled, fixture.gateway.canceled, "Cooperative physical cancellations")
    }

    private fun assertLocalRows(state: WishlistUiState, expected: List<StoredWishlistEntry>) {
        assertEquals(expected, state.entries.map { StoredWishlistEntry(it.productId, it.addedAtEpochMillis) })
        assertTrue(state.storageAvailable)
        assertFalse(state.loading)
        assertFalse(state.mutating)
    }

    private fun assertPendingRows(state: WishlistUiState, expected: List<StoredWishlistEntry>) {
        assertLocalRows(state, expected)
        assertFalse(state.refreshing)
        state.entries.forEach {
            assertNull(it.product)
            assertNull(it.issue)
        }
    }

    private fun assertResolvedRows(state: WishlistUiState, expected: List<StoredWishlistEntry>, title: String) {
        assertLocalRows(state, expected)
        assertFalse(state.refreshing)
        state.entries.forEach {
            assertEquals(it.productId, it.product?.id)
            assertEquals(title, it.product?.title)
            assertNull(it.issue)
        }
    }
}
