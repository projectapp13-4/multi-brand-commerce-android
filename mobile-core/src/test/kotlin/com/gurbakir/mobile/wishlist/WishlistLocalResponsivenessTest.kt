package com.gurbakir.mobile.wishlist

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** Original P-A3 desired outcomes; every assertion observes real repository/Flow/ViewModel state. */
@OptIn(ExperimentalCoroutinesApi::class)
class WishlistLocalResponsivenessTest {
    private val dispatcher = StandardTestDispatcher()

    @BeforeEach
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `repository remove commits and emits membership before held remote replies`() = runTest(dispatcher) {
        val fixture = WishlistHydrationFixture()
        val emissions = mutableListOf<WishlistMembershipState>()
        try {
            backgroundScope.launch { fixture.repository.observeMembership().collect { emissions += it } }
            fixture.track(async { fixture.repository.load() })
            runCurrent()
            assertEquals(3, fixture.gateway.active)
            val removed = fixture.initial.first().productId
            val mutation = fixture.track(async { fixture.repository.setSaved(removed, false) })
            runCurrent()
            assertTrue(mutation.isCompleted, "Local remove must complete with all remote replies still held")
            assertEquals(WishlistMutationResult.Success, mutation.await())
            assertFalse(fixture.store.load(WAVE5_PARTITION).any { it.productId == removed })
            assertFalse((emissions.last() as WishlistMembershipState.Available).productIds.contains(removed))
            assertEquals(0, fixture.gateway.completed)
            fixture.gateway.releaseAll()
            runCurrent()
            val current = fixture.repository.load() as WishlistLoadResult.Content
            assertFalse(current.entries.any { it.productId == removed })
        } finally {
            fixture.close()
            runCurrent()
        }
    }

    @Test
    fun `repository clear commits and emits empty membership before held remote replies`() = runTest(dispatcher) {
        val fixture = WishlistHydrationFixture()
        val emissions = mutableListOf<WishlistMembershipState>()
        try {
            backgroundScope.launch { fixture.repository.observeMembership().collect { emissions += it } }
            fixture.track(async { fixture.repository.load() })
            runCurrent()
            assertEquals(3, fixture.gateway.active)
            val clearing = fixture.track(async { fixture.repository.clear() })
            runCurrent()
            assertTrue(clearing.isCompleted, "Local clear must complete with all remote replies still held")
            assertEquals(WishlistMutationResult.Success, clearing.await())
            assertTrue(fixture.store.load(WAVE5_PARTITION).isEmpty())
            assertTrue((emissions.last() as WishlistMembershipState.Available).productIds.isEmpty())
            assertEquals(0, fixture.gateway.completed)
            fixture.gateway.releaseAll()
            runCurrent()
            assertTrue((fixture.repository.load() as WishlistLoadResult.Content).entries.isEmpty())
        } finally {
            fixture.close()
            runCurrent()
        }
    }

    @Test
    fun `cold actual model exposes ordered local identities while hydration is held`() = runTest(dispatcher) {
        val fixture = WishlistHydrationFixture()
        try {
            val model = fixture.wishlist()
            runCurrent()
            assertEquals(3, fixture.gateway.active)
            assertEquals(0, fixture.gateway.completed)
            assertEquals(fixture.initial.map { it.productId }, model.state.value.entries.map { it.productId })
            assertTrue(model.state.value.entries.all { it.product == null && it.issue == null })
            assertTrue(model.state.value.storageAvailable)
        } finally {
            fixture.close()
            runCurrent()
        }
    }

    @Test
    fun `cold actual model remove settles while hydration is held`() = runTest(dispatcher) {
        val fixture = WishlistHydrationFixture()
        try {
            val model = fixture.wishlist()
            val membership = fixture.membership()
            runCurrent()
            assertEquals(3, fixture.gateway.active)
            val removed = fixture.initial.first().productId
            model.remove(removed)
            runCurrent()
            assertFalse(model.state.value.mutating, "Local management must not stay busy behind hydration")
            assertFalse(membership.state.value.isSaved(removed))
            assertFalse(fixture.store.load(WAVE5_PARTITION).any { it.productId == removed })
            assertEquals(fixture.initial.drop(1).map { it.productId }, model.state.value.entries.map { it.productId })
            assertEquals(0, fixture.gateway.completed)
        } finally {
            fixture.close()
            runCurrent()
        }
    }

    @Test
    fun `cold actual model clear settles while hydration is held`() = runTest(dispatcher) {
        val fixture = WishlistHydrationFixture()
        try {
            val model = fixture.wishlist()
            val membership = fixture.membership()
            runCurrent()
            assertEquals(3, fixture.gateway.active)
            model.clear()
            runCurrent()
            assertFalse(model.state.value.mutating, "Clear must settle without a remote response")
            assertTrue(model.state.value.entries.isEmpty())
            assertTrue(membership.state.value.productIds.isEmpty())
            assertTrue(fixture.store.load(WAVE5_PARTITION).isEmpty())
            assertEquals(0, fixture.gateway.completed)
        } finally {
            fixture.close()
            runCurrent()
        }
    }

    @Test
    fun `shared actual membership action settles and removes current row during held refresh`() = runTest(dispatcher) {
        val fixture = WishlistHydrationFixture()
        try {
            fixture.gateway.holdReads = false
            val model = fixture.wishlist()
            val membership = fixture.membership()
            runCurrent()
            assertFalse(model.state.value.loading)
            assertEquals(3, model.state.value.entries.size)
            val before = fixture.gateway.completed
            fixture.gateway.holdReads = true
            model.refresh()
            runCurrent()
            assertEquals(3, fixture.gateway.active)
            val removed = fixture.initial.first().productId
            membership.setSaved(removed, false)
            runCurrent()
            assertFalse(membership.state.value.isUpdating(removed), "Shared local action must settle while reads wait")
            assertFalse(membership.state.value.isSaved(removed))
            assertFalse(model.state.value.entries.any { it.productId == removed })
            assertEquals(before, fixture.gateway.completed)
            fixture.gateway.releaseAll()
            runCurrent()
            assertFalse(model.state.value.entries.any { it.productId == removed })
        } finally {
            fixture.close()
            runCurrent()
        }
    }
}
