package com.gurbakir.mobile.wishlist

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Existing-API GREEN guards, not additions to the frozen 29 old-source RED inventory. */
@RunWith(AndroidJUnit4::class)
class WishlistRoomObservationGreenTest {
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
    fun continuousRoomObserveOrdersTimestampThenIdentity() = runBlocking(Dispatchers.IO) {
        val probe = fixture.rowsProbe()
        try {
            probe.await(emptyList())
            val older = StoredWishlistEntry(roomGreenId(3), 10)
            val tiedLater = StoredWishlistEntry(roomGreenId(2), 30)
            val tiedFirst = StoredWishlistEntry(roomGreenId(1), 30)
            fixture.seed(older)
            probe.await(listOf(older))
            fixture.seed(tiedLater)
            probe.await(listOf(tiedLater, older))
            fixture.seed(tiedFirst)
            val expected = listOf(tiedFirst, tiedLater, older)
            assertEquals(expected, probe.await(expected))
            assertEquals(expected, fixture.store.load(fixture.partition))
            assertEquals(0, fixture.gateway.snapshot().started)
        } finally {
            probe.close()
        }
    }

    @Test
    fun repositoryDuplicateSaveKeepsFirstTimestampAndPartition() = runBlocking(Dispatchers.IO) {
        val probe = fixture.rowsProbe()
        try {
            probe.await(emptyList())
            val id = roomGreenId(1)
            fixture.now.set(10)
            assertEquals(WishlistMutationResult.Success, fixture.repository.setSaved(id, true))
            probe.await(listOf(StoredWishlistEntry(id, 10)))
            fixture.now.set(90)
            assertEquals(WishlistMutationResult.Success, fixture.repository.setSaved(id, true))
            val other = StoredWishlistEntry(roomGreenId(2), 50)
            fixture.seed(other)
            val expected = listOf(other, StoredWishlistEntry(id, 10))
            assertEquals(expected, probe.await(expected))
            fixture.directInsert(StoredWishlistEntry(id, 200), fixture.foreign)
            assertEquals(expected, fixture.store.load(fixture.partition))
            assertEquals(listOf(StoredWishlistEntry(id, 200)), fixture.store.load(fixture.foreign))
            assertEquals(0, fixture.gateway.snapshot().started)
        } finally {
            probe.close()
        }
    }

    @Test
    fun directDaoClearPublishesEmptyAndPreservesForeignPartition() = runBlocking(Dispatchers.IO) {
        val probe = fixture.rowsProbe()
        try {
            probe.await(emptyList())
            val own = StoredWishlistEntry(roomGreenId(1), 10)
            val foreign = StoredWishlistEntry(roomGreenId(2), 20)
            fixture.seed(own)
            probe.await(listOf(own))
            fixture.directInsert(foreign, fixture.foreign)
            fixture.directClear()
            assertEquals(emptyList<StoredWishlistEntry>(), probe.await(emptyList()))
            assertEquals(emptyList<StoredWishlistEntry>(), fixture.store.load(fixture.partition))
            assertEquals(listOf(foreign), fixture.store.load(fixture.foreign))
            assertEquals(0, fixture.gateway.snapshot().started)
        } finally {
            probe.close()
        }
    }

    @Test
    fun directDaoDeleteReaddPublishesReplacementTimestamp() = runBlocking(Dispatchers.IO) {
        val probe = fixture.rowsProbe()
        try {
            probe.await(emptyList())
            val id = roomGreenId(1)
            fixture.seed(StoredWishlistEntry(id, 10))
            probe.await(listOf(StoredWishlistEntry(id, 10)))
            assertEquals(1, fixture.directDelete(id))
            probe.await(emptyList())
            val readded = StoredWishlistEntry(id, 200)
            fixture.directInsert(readded)
            assertEquals(listOf(readded), probe.await(listOf(readded)))
            assertEquals(listOf(readded), fixture.store.load(fixture.partition))
            assertEquals(0, fixture.gateway.snapshot().started)
        } finally {
            probe.close()
        }
    }
}
