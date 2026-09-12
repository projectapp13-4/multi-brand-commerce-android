package com.gurbakir.mobile.search

import android.database.sqlite.SQLiteException
import java.text.Normalizer
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException

private const val HISTORY_LIMIT = 10
private const val HISTORY_RETENTION_MILLIS = 30L * 24L * 60L * 60L * 1_000L

data class SearchHistoryPartition(val environmentId: String, val marketId: String)

class SearchHistoryNormalizationPolicy(localeTag: String) {
    private val locale = Locale.forLanguageTag(localeTag)

    init {
        require(localeTag.isNotBlank() && locale.toLanguageTag().equals(localeTag, ignoreCase = false)) {
            "Search normalization requires a canonical locale tag."
        }
    }

    fun normalize(value: String): String =
        Normalizer.normalize(value.toDisplaySearchQuery(), Normalizer.Form.NFKC).lowercase(locale)
}

data class StoredSearchQuery(val normalized: String, val display: String, val searchedAtEpochMillis: Long)

data class StoredSearchHistory(val enabled: Boolean, val entries: List<StoredSearchQuery>)

interface SearchHistoryStore {
    suspend fun load(partition: SearchHistoryPartition, cutoffEpochMillis: Long, limit: Int): StoredSearchHistory

    suspend fun record(partition: SearchHistoryPartition, query: StoredSearchQuery, cutoffEpochMillis: Long, limit: Int)

    suspend fun remove(partition: SearchHistoryPartition, normalizedQuery: String)

    suspend fun clear(partition: SearchHistoryPartition)

    suspend fun setEnabled(partition: SearchHistoryPartition, enabled: Boolean)
}

fun interface SearchHistoryClock {
    fun nowEpochMillis(): Long
}

data class SearchHistoryState(
    val enabled: Boolean,
    val entries: List<StoredSearchQuery>,
    val storageAvailable: Boolean = true
)

interface SearchHistoryRepository {
    suspend fun load(): SearchHistoryState

    suspend fun record(displayQuery: String): SearchHistoryState

    suspend fun remove(normalizedQuery: String): SearchHistoryState

    suspend fun clear(): SearchHistoryState

    suspend fun setEnabled(enabled: Boolean): SearchHistoryState
}

class DefaultSearchHistoryRepository(
    private val store: SearchHistoryStore,
    private val partition: SearchHistoryPartition,
    private val normalizationPolicy: SearchHistoryNormalizationPolicy,
    private val clock: SearchHistoryClock
) : SearchHistoryRepository {
    private val storageHealthy = AtomicBoolean(true)

    override suspend fun load(): SearchHistoryState = guardedHistory {
        val now = clock.nowEpochMillis()
        store.load(partition, now - HISTORY_RETENTION_MILLIS, HISTORY_LIMIT).toState()
    }

    override suspend fun record(displayQuery: String): SearchHistoryState = guardedHistory {
        val display = displayQuery.toDisplaySearchQuery()
        val now = clock.nowEpochMillis()
        if (display.isNotEmpty()) {
            store.record(
                partition = partition,
                query = StoredSearchQuery(normalizationPolicy.normalize(display), display, now),
                cutoffEpochMillis = now - HISTORY_RETENTION_MILLIS,
                limit = HISTORY_LIMIT
            )
        }
        store.load(partition, now - HISTORY_RETENTION_MILLIS, HISTORY_LIMIT).toState()
    }

    override suspend fun remove(normalizedQuery: String): SearchHistoryState = guardedHistory {
        store.remove(partition, normalizedQuery)
        loadHealthy()
    }

    override suspend fun clear(): SearchHistoryState = guardedHistory {
        store.clear(partition)
        loadHealthy()
    }

    override suspend fun setEnabled(enabled: Boolean): SearchHistoryState = guardedHistory {
        store.setEnabled(partition, enabled)
        loadHealthy()
    }

    private suspend fun loadHealthy(): SearchHistoryState {
        val now = clock.nowEpochMillis()
        return store.load(partition, now - HISTORY_RETENTION_MILLIS, HISTORY_LIMIT).toState()
    }

    private suspend fun guardedHistory(block: suspend () -> SearchHistoryState): SearchHistoryState {
        if (!storageHealthy.get()) return unavailableHistory()
        return try {
            block()
        } catch (error: CancellationException) {
            throw error
        } catch (_: SQLiteException) {
            storageHealthy.set(false)
            unavailableHistory()
        } catch (_: IllegalStateException) {
            storageHealthy.set(false)
            unavailableHistory()
        }
    }
}

internal fun String.toDisplaySearchQuery(): String = trim().replace(Regex("\\s+"), " ")

private fun StoredSearchHistory.toState() = SearchHistoryState(enabled, entries)

private fun unavailableHistory() = SearchHistoryState(enabled = false, entries = emptyList(), storageAvailable = false)
