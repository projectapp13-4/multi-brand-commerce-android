package com.gurbakir.mobile.wishlist

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Protective controls; the audit did not establish preexisting resurrection or partition leakage. */
@OptIn(ExperimentalCoroutinesApi::class)
class WishlistHydrationIdentityTest {
    @Test
    fun `late clear completion cannot resurrect local membership or reusable repository content`() = runTest {
        val fixture = WishlistHydrationFixture(3)
        try {
            fixture.track(async { fixture.repository.load() })
            runCurrent()
            val clearing = fixture.track(async { fixture.repository.clear() })
            runCurrent()
            assertEquals(3, fixture.gateway.active)
            fixture.gateway.releaseAll()
            runCurrent()
            assertEquals(WishlistMutationResult.Success, clearing.await())
            assertTrue(fixture.store.load(WAVE5_PARTITION).isEmpty())
            val membership = fixture.repository.observeMembership().first() as WishlistMembershipState.Available
            assertTrue(membership.productIds.isEmpty())
            assertTrue((fixture.repository.load() as WishlistLoadResult.Content).entries.isEmpty())
            assertEquals(0, fixture.gateway.active)
        } finally {
            fixture.close()
            runCurrent()
        }
    }

    @Test
    fun `remove readd same id keeps the replacement timestamp and current product after old response`() = runTest {
        val fixture = WishlistHydrationFixture(1)
        try {
            fixture.gateway.cooperative = false
            fixture.track(async { fixture.repository.load() })
            runCurrent()
            val id = fixture.initial.single().productId
            val replacement = fixture.track(
                async {
                    fixture.repository.setSaved(id, false)
                    fixture.now = 400L
                    fixture.repository.setSaved(id, true)
                }
            )
            runCurrent()
            fixture.gateway.title = "Replacement generation"
            fixture.gateway.releaseAll()
            runCurrent()
            assertEquals(WishlistMutationResult.Success, replacement.await())
            assertEquals(listOf(StoredWishlistEntry(id, 400L)), fixture.store.load(WAVE5_PARTITION))
            val current = fixture.repository.load() as WishlistLoadResult.Content
            assertEquals(400L, current.entries.single().addedAtEpochMillis)
            assertEquals("Replacement generation", current.entries.single().product?.title)
            assertEquals(2, fixture.gateway.started)
        } finally {
            fixture.close()
            runCurrent()
        }
    }

    @Test
    fun `ordered identities repeat save timestamps and environment market partitions remain exact`() = runTest {
        val fixture = WishlistHydrationFixture(0)
        try {
            fixture.gateway.holdReads = false
            val first = StoredWishlistEntry("gid://shopify/Product/1", 20L)
            val ten = StoredWishlistEntry("gid://shopify/Product/10", 10L)
            val two = StoredWishlistEntry("gid://shopify/Product/2", 10L)
            fixture.store.seed(WAVE5_PARTITION, listOf(two, ten, first))
            val environment = WishlistPartition("other-environment", WAVE5_PARTITION.marketId)
            val market = WishlistPartition(WAVE5_PARTITION.environmentId, "other-market")
            fixture.store.seed(environment, listOf(StoredWishlistEntry("gid://shopify/Product/8", 8L)))
            fixture.store.seed(market, listOf(StoredWishlistEntry("gid://shopify/Product/9", 9L)))
            val otherEnvironment = DefaultWishlistRepository(fixture.store, fixture.gateway, environment) { 999L }
            val otherMarket = DefaultWishlistRepository(fixture.store, fixture.gateway, market) { 999L }
            fixture.repository.setSaved(two.productId, true)
            val ordered = (fixture.repository.load() as WishlistLoadResult.Content).entries
            val actualOrder = ordered.map { StoredWishlistEntry(it.productId, it.addedAtEpochMillis) }
            assertEquals(listOf(first, ten, two), actualOrder)
            val environmentIds = otherEnvironment.observeMembership().first() as WishlistMembershipState.Available
            val marketIds = otherMarket.observeMembership().first() as WishlistMembershipState.Available
            assertEquals(setOf("gid://shopify/Product/8"), environmentIds.productIds)
            assertEquals(setOf("gid://shopify/Product/9"), marketIds.productIds)
            otherEnvironment.clear()
            assertEquals(listOf(first, ten, two), fixture.store.load(WAVE5_PARTITION))
            assertEquals(1, fixture.store.load(market).size)
        } finally {
            fixture.close()
            runCurrent()
        }
    }
}
