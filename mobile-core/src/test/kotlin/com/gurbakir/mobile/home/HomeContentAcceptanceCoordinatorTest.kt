package com.gurbakir.mobile.home

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class HomeContentAcceptanceCoordinatorTest {
    private val partition =
        HomeContentPartition(
            applicationId = "com.example.app",
            environmentId = "staging",
            storefrontDomain = "merchant.example",
            rootType = "mobile_home",
            rootHandle = "primary"
        )

    @Test
    fun `a superseded request cannot replace visible or persisted authority`() = kotlinx.coroutines.test.runTest {
        val store = RecordingStore()
        val coordinator = HomeContentAcceptanceCoordinator(store)
        val tokenA = coordinator.begin()
        val tokenB = coordinator.begin()

        val acceptedB = coordinator.accept(tokenB, marker(), stored("B"))
        val lateA = coordinator.accept(tokenA, marker(), stored("A"))

        assertEquals(HomeAcceptance.Accepted(HomeStoreWrite.CONFIRMED), acceptedB)
        assertEquals(HomeAcceptance.Superseded, lateA)
        assertEquals("B", coordinator.current(partition)?.snapshot?.rootHandle)
        assertEquals("B", store.persisted?.snapshot?.rootHandle)
    }

    @Test
    fun `unconfirmed empty promotion remains session authoritative`() = kotlinx.coroutines.test.runTest {
        val store = RecordingStore(write = HomeStoreWrite.UNCONFIRMED, persisted = stored("A"))
        val coordinator = HomeContentAcceptanceCoordinator(store)
        val token = coordinator.begin()

        val result = coordinator.accept(token, marker(), stored("empty"))

        assertEquals(HomeAcceptance.Accepted(HomeStoreWrite.UNCONFIRMED), result)
        assertEquals(emptyList<RemoteHomeSection>(), coordinator.current(partition)?.snapshot?.sections)
        assertEquals("A", store.persisted?.snapshot?.rootHandle)
    }

    private fun marker() = HomeEstablishmentRecord(partition, 1_000L, 1)

    private fun stored(handle: String) = HomeStoredSnapshot(
        partition = partition,
        acceptedAtMillis = 1_000L,
        expiresAtMillis = 86_401_000L,
        snapshot =
            RemoteHomeSnapshot(
                rootGid = "gid://shopify/Metaobject/$handle",
                rootType = "mobile_home",
                rootHandle = handle,
                rootUpdatedAt = "2026-09-13T20:00:00Z",
                contentVersion = 1,
                sections = emptyList()
            )
    )

    private class RecordingStore(
        private val write: HomeStoreWrite = HomeStoreWrite.CONFIRMED,
        var persisted: HomeStoredSnapshot? = null
    ) : HomeContentStore {
        override suspend fun read(
            partition: HomeContentPartition,
            supportedContentVersion: Int,
            nowMillis: Long
        ): HomeStoreRead = HomeStoreRead.NeverEstablished

        override suspend fun replace(marker: HomeEstablishmentRecord, snapshot: HomeStoredSnapshot): HomeStoreWrite {
            if (write == HomeStoreWrite.CONFIRMED) persisted = snapshot
            return write
        }

        override suspend fun evictSnapshot(partition: HomeContentPartition): HomeStoreWrite = write
    }
}
