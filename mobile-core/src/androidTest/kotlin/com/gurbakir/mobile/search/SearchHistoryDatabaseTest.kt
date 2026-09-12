package com.gurbakir.mobile.search

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SearchHistoryDatabaseTest {
    private lateinit var database: LocalCommerceDatabase

    @Before
    fun setUp() {
        database =
            Room.inMemoryDatabaseBuilder(
                ApplicationProvider.getApplicationContext(),
                LocalCommerceDatabase::class.java
            ).build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun roomStoreEnforcesPartitionBoundExpiryAndDisabledCleanup() = runTest {
        val store = RoomSearchHistoryStore(database)
        val development = SearchHistoryPartition("development", "TR")
        val staging = SearchHistoryPartition("staging", "TR")
        var now = TimeUnit.DAYS.toMillis(31)
        val normalizationPolicy = SearchHistoryNormalizationPolicy("tr-TR")
        val developmentRepository = DefaultSearchHistoryRepository(store, development, normalizationPolicy) { now }
        val stagingRepository = DefaultSearchHistoryRepository(store, staging, normalizationPolicy) { now }

        repeat(12) { index ->
            now += 1
            developmentRepository.record("ürün $index")
        }
        stagingRepository.record("staging ürünü")

        assertEquals(10, developmentRepository.load().entries.size)
        assertEquals(listOf("staging ürünü"), stagingRepository.load().entries.map { it.display })
        val disabled = developmentRepository.setEnabled(false)
        assertFalse(disabled.enabled)
        assertEquals(emptyList<StoredSearchQuery>(), developmentRepository.load().entries)
        developmentRepository.setEnabled(true)
        assertEquals(emptyList<StoredSearchQuery>(), developmentRepository.load().entries)
        now += TimeUnit.DAYS.toMillis(31)
        assertEquals(emptyList<StoredSearchQuery>(), stagingRepository.load().entries)
    }
}
