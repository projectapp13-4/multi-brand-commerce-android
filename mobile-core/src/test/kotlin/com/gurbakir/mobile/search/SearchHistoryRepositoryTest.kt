package com.gurbakir.mobile.search

import android.database.sqlite.SQLiteException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class SearchHistoryRepositoryTest {
    private val partition = SearchHistoryPartition("development", "TR")
    private val turkishNormalization = SearchHistoryNormalizationPolicy("tr-TR")

    @Test
    fun `normalization policy keeps locale separate from foreground resources`() {
        assertEquals("ı bakır tava", turkishNormalization.normalize("  I   BAKIR TAVA "))
        assertEquals(
            "i bakir tava",
            SearchHistoryNormalizationPolicy("en-CA").normalize("  I   BAKIR TAVA ")
        )
    }

    @Test
    fun `history normalizes uniqueness expires and retains only ten newest entries`() = runTest {
        val store = InMemorySearchHistoryStore()
        var now = TimeUnit.DAYS.toMillis(31)
        val repository = DefaultSearchHistoryRepository(store, partition, turkishNormalization) { now }

        repository.record("  Bakır   Tava ")
        now += 1
        repository.record("BAKIR TAVA")
        repeat(10) { index ->
            now += 1
            repository.record("ürün $index")
        }
        now += 1
        repository.record("bakır tava")

        val state = repository.load()

        assertEquals(10, state.entries.size)
        assertEquals(1, state.entries.count { it.normalized == "bakır tava" })
        assertEquals("bakır tava", state.entries.first().display)
        now += TimeUnit.DAYS.toMillis(31)
        assertEquals(emptyList<StoredSearchQuery>(), repository.load().entries)
    }

    @Test
    fun `disabling history clears residue and blocks writes until reenabled`() = runTest {
        val store = InMemorySearchHistoryStore()
        val repository = DefaultSearchHistoryRepository(store, partition, turkishNormalization) { 100L }
        repository.record("cezve")

        val disabled = repository.setEnabled(false)
        repository.record("tava")

        assertFalse(disabled.enabled)
        assertEquals(emptyList<StoredSearchQuery>(), repository.load().entries)
        repository.setEnabled(true)
        assertEquals(emptyList<StoredSearchQuery>(), repository.load().entries)
    }

    @Test
    fun `database failure disables history without leaking or crashing`() = runTest {
        val repository =
            DefaultSearchHistoryRepository(
                store = ThrowingSearchHistoryStore(),
                partition = partition,
                normalizationPolicy = turkishNormalization,
                clock = { 100L }
            )

        val state = repository.record("özel ürün")

        assertFalse(state.enabled)
        assertFalse(state.storageAvailable)
        assertEquals(emptyList<StoredSearchQuery>(), state.entries)
    }

    private class InMemorySearchHistoryStore : SearchHistoryStore {
        private val entries = mutableMapOf<SearchHistoryPartition, MutableMap<String, StoredSearchQuery>>()
        private val settings = mutableMapOf<SearchHistoryPartition, Boolean>()

        override suspend fun load(
            partition: SearchHistoryPartition,
            cutoffEpochMillis: Long,
            limit: Int
        ): StoredSearchHistory {
            val enabled = settings[partition] ?: true
            val values = entries.getOrPut(partition, ::mutableMapOf)
            values.entries.removeIf { it.value.searchedAtEpochMillis < cutoffEpochMillis }
            return StoredSearchHistory(
                enabled,
                if (enabled) {
                    values.values.sortedByDescending { it.searchedAtEpochMillis }.take(limit)
                } else {
                    emptyList()
                }
            )
        }

        override suspend fun record(
            partition: SearchHistoryPartition,
            query: StoredSearchQuery,
            cutoffEpochMillis: Long,
            limit: Int
        ) {
            if (settings[partition] == false) return
            val values = entries.getOrPut(partition, ::mutableMapOf)
            values.entries.removeIf { it.value.searchedAtEpochMillis < cutoffEpochMillis }
            values[query.normalized] = query
            values.values.sortedByDescending { it.searchedAtEpochMillis }.drop(limit)
                .forEach { values.remove(it.normalized) }
        }

        override suspend fun remove(partition: SearchHistoryPartition, normalizedQuery: String) {
            entries[partition]?.remove(normalizedQuery)
        }

        override suspend fun clear(partition: SearchHistoryPartition) {
            entries[partition]?.clear()
        }

        override suspend fun setEnabled(partition: SearchHistoryPartition, enabled: Boolean) {
            settings[partition] = enabled
            if (!enabled) clear(partition)
        }
    }

    private class ThrowingSearchHistoryStore : SearchHistoryStore {
        override suspend fun load(
            partition: SearchHistoryPartition,
            cutoffEpochMillis: Long,
            limit: Int
        ): StoredSearchHistory = throw SQLiteException("synthetic")

        override suspend fun record(
            partition: SearchHistoryPartition,
            query: StoredSearchQuery,
            cutoffEpochMillis: Long,
            limit: Int
        ) = throw SQLiteException("synthetic")

        override suspend fun remove(partition: SearchHistoryPartition, normalizedQuery: String) = Unit

        override suspend fun clear(partition: SearchHistoryPartition) = Unit

        override suspend fun setEnabled(partition: SearchHistoryPartition, enabled: Boolean) = Unit
    }
}
