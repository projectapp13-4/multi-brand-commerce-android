package com.gurbakir.mobile.wishlist

import com.gurbakir.mobile.product.productFixture
import com.gurbakir.storefront.StorefrontFailure
import com.gurbakir.storefront.StorefrontResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Existing protections plus the selected forced-fresh floor contract; no virtual delay or TTL is used. */
@OptIn(ExperimentalCoroutinesApi::class)
class WishlistHydrationConcurrencyTest {
    @Test
    fun `equivalent automatic callers share the current round and completed matching cache`() = runTest {
        val fixture = WishlistHydrationFixture(3)
        try {
            val first = fixture.track(async { fixture.repository.load() })
            val second = fixture.track(async { fixture.repository.load() })
            runCurrent()
            assertEquals(3, fixture.gateway.started)
            assertFalse(first.isCompleted)
            assertFalse(second.isCompleted)
            fixture.gateway.releaseAll()
            runCurrent()
            assertEquals(first.await(), second.await())
            assertEquals(first.await(), fixture.repository.load())
            assertEquals(3, fixture.gateway.started)
        } finally {
            fixture.close()
            runCurrent()
        }
    }

    @Test
    fun `canceled partial owner does not cache and queued automatic caller starts clean round`() = runTest {
        val fixture = WishlistHydrationFixture(9)
        try {
            val first = fixture.track(async { fixture.repository.load() })
            runCurrent()
            val queued = fixture.track(async { fixture.repository.load() })
            runCurrent()
            assertEquals(4, fixture.gateway.active)
            assertEquals(4, fixture.gateway.started)
            first.cancel()
            runCurrent()
            assertTrue(first.isCancelled)
            assertEquals(4, fixture.gateway.canceled)
            assertEquals(8, fixture.gateway.started)
            assertEquals(4, fixture.gateway.active)
            fixture.gateway.releaseAll()
            runCurrent()
            val result = queued.await() as WishlistLoadResult.Content
            assertEquals(fixture.initial.map { it.productId }, result.entries.map { it.productId })
            assertTrue(result.entries.all { it.product != null && it.issue == null })
            assertEquals(13, fixture.gateway.started)
            assertEquals(0, fixture.gateway.active)
            assertEquals(4, fixture.gateway.maximumActive)
            assertEquals(result, fixture.repository.load())
            assertEquals(13, fixture.gateway.started)
        } finally {
            fixture.close()
            runCurrent()
        }
    }

    @Test
    fun `physically held canceled reads keep the four read cap across replacement and membership change`() = runTest {
        val fixture = WishlistHydrationFixture(9)
        try {
            fixture.gateway.cooperative = false
            val old = fixture.track(async { fixture.repository.load() })
            runCurrent()
            assertEquals(4, fixture.gateway.active)
            old.cancel()
            val removed = fixture.initial.first().productId
            val mutation = fixture.track(async { fixture.repository.setSaved(removed, false) })
            val next = fixture.track(async { fixture.repository.load(forceRefresh = true) })
            runCurrent()
            assertEquals(4, fixture.gateway.started, "New remote work must await actual old read exits")
            assertEquals(4, fixture.gateway.active)
            assertEquals(0, fixture.gateway.completed)
            fixture.gateway.cooperative = true
            fixture.gateway.releaseThrough(4)
            runCurrent()
            assertEquals(WishlistMutationResult.Success, mutation.await())
            assertTrue(old.isCancelled)
            assertTrue(fixture.gateway.maximumActive <= 4)
            assertTrue(fixture.gateway.active <= 4)
            fixture.gateway.releaseAll()
            runCurrent()
            val content = next.await() as WishlistLoadResult.Content
            assertEquals(fixture.initial.drop(1).map { it.productId }, content.entries.map { it.productId })
            assertEquals(0, fixture.gateway.active)
            assertEquals(4, fixture.gateway.maximumActive)
        } finally {
            fixture.close()
            runCurrent()
        }
    }

    @Test
    fun `fresh callers queued before next round share it while later fresh caller requires another round`() = runTest {
        val fixture = WishlistHydrationFixture(2)
        try {
            val initial = fixture.track(async { fixture.repository.load() })
            runCurrent()
            fixture.gateway.title = "Fresh round two"
            val freshA = fixture.track(async { fixture.repository.load(forceRefresh = true) })
            val freshB = fixture.track(async { fixture.repository.load(forceRefresh = true) })
            runCurrent()
            assertEquals(2, fixture.gateway.started)
            fixture.gateway.releaseThrough(2)
            runCurrent()
            assertTrue(initial.isCompleted)
            assertEquals(4, fixture.gateway.started)
            fixture.gateway.title = "Fresh round three"
            val freshC = fixture.track(async { fixture.repository.load(forceRefresh = true) })
            runCurrent()
            fixture.gateway.releaseThrough(4)
            runCurrent()
            assertTrue(freshA.isCompleted)
            assertTrue(freshB.isCompleted, "Equivalent fresh floors must share the matching round")
            assertEquals(freshA.await(), freshB.await())
            val roundTwo = freshB.await() as WishlistLoadResult.Content
            assertEquals("Fresh round two", roundTwo.entries.first().product?.title)
            assertFalse(freshC.isCompleted)
            assertEquals(6, fixture.gateway.started)
            fixture.gateway.releaseAll()
            runCurrent()
            val roundThree = freshC.await() as WishlistLoadResult.Content
            assertEquals("Fresh round three", roundThree.entries.first().product?.title)
            assertEquals(6, fixture.gateway.started)
        } finally {
            fixture.close()
            runCurrent()
        }
    }

    @Test
    fun `retryable product failure retains local identity and explicit retry recovers`() = runTest {
        val fixture = WishlistHydrationFixture(1)
        try {
            fixture.gateway.holdReads = false
            val id = fixture.initial.single().productId
            fixture.gateway.result(id, StorefrontResult.Failure(StorefrontFailure.Transport(true)))
            val failed = fixture.repository.load() as WishlistLoadResult.Content
            assertEquals(WishlistItemIssue.CONNECTION, failed.entries.single().issue)
            assertEquals(fixture.initial, fixture.store.load(WAVE5_PARTITION))
            fixture.gateway.result(id, StorefrontResult.Success(productFixture().copy(id = id, title = "Recovered")))
            val recovered = fixture.repository.load(forceRefresh = true) as WishlistLoadResult.Content
            assertEquals("Recovered", recovered.entries.single().product?.title)
            assertEquals(recovered, fixture.repository.load())
            assertEquals(2, fixture.gateway.started)
        } finally {
            fixture.close()
            runCurrent()
        }
    }

    @Test
    fun `local storage failure is explicit and invalid identifiers never reach gateway or store`() = runTest {
        val fixture = WishlistHydrationFixture()
        try {
            assertEquals(WishlistMutationResult.InvalidProduct, fixture.repository.setSaved("not-a-product-gid", true))
            assertEquals(0, fixture.store.writes)
            fixture.store.fail(WAVE5_PARTITION)
            assertEquals(WishlistMembershipState.StorageUnavailable, fixture.repository.observeMembership().first())
            assertEquals(WishlistLoadResult.StorageUnavailable, fixture.repository.load())
            assertEquals(WishlistMutationResult.StorageUnavailable, fixture.repository.clear())
            val mutation = fixture.repository.setSaved(fixture.initial[0].productId, false)
            assertEquals(WishlistMutationResult.StorageUnavailable, mutation)
            assertEquals(0, fixture.gateway.started)
        } finally {
            fixture.close()
            runCurrent()
        }
    }
}
