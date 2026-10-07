package com.gurbakir.mobile.wishlist

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Actual Room bypass writes and held physical reads. GREEN-only; old compile is not behavioral RED evidence. */
@RunWith(AndroidJUnit4::class)
class WishlistRoomHydrationGreenTest {
    private lateinit var fixture: WishlistRoomGreenFixture

    @Before
    fun createRoom() {
        fixture = WishlistRoomGreenFixture(ApplicationProvider.getApplicationContext())
    }

    @After
    fun closeRoom() = runBlocking(Dispatchers.IO) {
        fixture.finishPhysicalReads()
        fixture.closeDatabase()
    }

    @Test
    fun directRoomClearDuringHeldReadCannotReturnOrCacheDeletedRow() = runBlocking(Dispatchers.IO) {
        fixture.seed(StoredWishlistEntry(roomGreenId(1), 10))
        val probe = fixture.rowsProbe()
        val load = async { fixture.repository.load() }
        try {
            probe.await(listOf(StoredWishlistEntry(roomGreenId(1), 10)))
            fixture.gateway.awaitSnapshot { it.started == 1 && it.active == 1 && it.completed == 0 }
            fixture.directClear()
            probe.await(emptyList())
            assertEquals(1, fixture.gateway.snapshot().active)
            fixture.gateway.release(1)
            val result = withTimeout(5_000) { load.await() } as WishlistLoadResult.Content
            assertEquals(emptyList<WishlistResolvedEntry>(), result.entries)
            val repeated = fixture.repository.load() as WishlistLoadResult.Content
            assertEquals(emptyList<WishlistResolvedEntry>(), repeated.entries)
            fixture.gateway.awaitSnapshot { it.started == 1 && it.active == 0 && it.completed == 1 }
        } finally {
            fixture.gateway.releaseAll()
            load.cancelAndJoin()
            probe.close()
        }
    }

    @Test
    fun sameIdReaddedDuringHeldReadNeedsNewTimestampAndCurrentPhysicalRound() = runBlocking(Dispatchers.IO) {
        val id = roomGreenId(1)
        fixture.seed(StoredWishlistEntry(id, 10))
        val probe = fixture.rowsProbe()
        val load = async { fixture.repository.load() }
        try {
            probe.await(listOf(StoredWishlistEntry(id, 10)))
            fixture.gateway.awaitSnapshot { it.started == 1 && it.active == 1 && it.completed == 0 }
            fixture.directClear()
            probe.await(emptyList())
            fixture.directInsert(StoredWishlistEntry(id, 200))
            probe.await(listOf(StoredWishlistEntry(id, 200)))
            fixture.gateway.setProduct("Readded Room product")
            fixture.gateway.release(1)
            fixture.gateway.awaitSnapshot { it.started == 2 && it.active == 1 && it.completed == 1 }
            fixture.gateway.release(2)
            val result = withTimeout(5_000) { load.await() } as WishlistLoadResult.Content
            assertEquals(id, result.entries.single().productId)
            assertEquals(200L, result.entries.single().addedAtEpochMillis)
            assertEquals("Readded Room product", result.entries.single().product?.title)
            assertEquals(result, fixture.repository.load())
            fixture.gateway.awaitSnapshot { it.started == 2 && it.active == 0 && it.completed == 2 }
            assertTrue(fixture.gateway.snapshot().maximum <= 4)
        } finally {
            fixture.gateway.releaseAll()
            load.cancelAndJoin()
            probe.close()
        }
    }
}
