package com.gurbakir.mobile.wishlist

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** A direct local-store writer bypasses repository mutation counters; latest exact rows remain the acceptance gate. */
@OptIn(ExperimentalCoroutinesApi::class)
class WishlistExternalIdentityTest {
    @Test
    fun `external local clear while held hydration runs cannot return the obsolete identity snapshot`() = runTest {
        val fixture = WishlistHydrationFixture(3)
        try {
            val loading = fixture.track(async { fixture.repository.load() })
            runCurrent()
            assertEquals(3, fixture.gateway.active)
            assertEquals(0, fixture.gateway.completed)
            fixture.store.seed(WAVE5_PARTITION, emptyList())
            val membership = fixture.repository.observeMembership().first() as WishlistMembershipState.Available
            assertTrue(membership.productIds.isEmpty())
            fixture.gateway.releaseAll()
            runCurrent()
            val current = loading.await() as WishlistLoadResult.Content
            assertTrue(current.entries.isEmpty(), "The active caller must receive the latest local identity truth")
            assertTrue((fixture.repository.load() as WishlistLoadResult.Content).entries.isEmpty())
            assertEquals(0, fixture.gateway.active)
        } finally {
            fixture.close()
            runCurrent()
        }
    }

    @Test
    fun `external timestamp replacement rejects the old generation despite unchanged ids`() = runTest {
        val fixture = WishlistHydrationFixture(3)
        try {
            val loading = fixture.track(async { fixture.repository.load() })
            runCurrent()
            assertEquals(3, fixture.gateway.active)
            val replacement = fixture.initial.map { it.copy(addedAtEpochMillis = it.addedAtEpochMillis + 300L) }
            fixture.store.seed(WAVE5_PARTITION, replacement)
            fixture.gateway.title = "Current external generation"
            fixture.gateway.releaseAll()
            runCurrent()
            val current = loading.await() as WishlistLoadResult.Content
            assertEquals(replacement, current.entries.map { StoredWishlistEntry(it.productId, it.addedAtEpochMillis) })
            assertTrue(current.entries.all { it.product?.title == "Current external generation" })
            assertEquals(current, fixture.repository.load())
            assertEquals(0, fixture.gateway.active)
        } finally {
            fixture.close()
            runCurrent()
        }
    }
}
