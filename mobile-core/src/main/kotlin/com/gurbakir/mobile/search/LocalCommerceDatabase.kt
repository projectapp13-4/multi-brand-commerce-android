package com.gurbakir.mobile.search

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.withTransaction
import com.gurbakir.mobile.wishlist.WishlistDao
import com.gurbakir.mobile.wishlist.WishlistEntity

@Entity(
    tableName = "search_history",
    primaryKeys = ["environmentId", "marketId", "normalizedQuery"],
    indices = [Index(value = ["environmentId", "marketId", "searchedAtEpochMillis"])]
)
data class SearchHistoryEntity(
    val environmentId: String,
    val marketId: String,
    val normalizedQuery: String,
    val displayQuery: String,
    val searchedAtEpochMillis: Long
)

@Entity(
    tableName = "search_history_settings",
    primaryKeys = ["environmentId", "marketId"]
)
data class SearchHistorySettingEntity(val environmentId: String, val marketId: String, val enabled: Boolean)

@Dao
interface SearchHistoryDao {
    @Query(
        """
        SELECT * FROM search_history
        WHERE environmentId = :environmentId
          AND marketId = :marketId
          AND searchedAtEpochMillis >= :cutoffEpochMillis
        ORDER BY searchedAtEpochMillis DESC, normalizedQuery ASC
        LIMIT :limit
        """
    )
    suspend fun loadRecent(
        environmentId: String,
        marketId: String,
        cutoffEpochMillis: Long,
        limit: Int
    ): List<SearchHistoryEntity>

    @Query(
        """
        DELETE FROM search_history
        WHERE environmentId = :environmentId
          AND marketId = :marketId
          AND searchedAtEpochMillis < :cutoffEpochMillis
        """
    )
    suspend fun deleteExpired(environmentId: String, marketId: String, cutoffEpochMillis: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: SearchHistoryEntity)

    @Query(
        """
        DELETE FROM search_history
        WHERE environmentId = :environmentId
          AND marketId = :marketId
          AND normalizedQuery NOT IN (
              SELECT normalizedQuery FROM search_history
              WHERE environmentId = :environmentId AND marketId = :marketId
              ORDER BY searchedAtEpochMillis DESC, normalizedQuery ASC
              LIMIT :limit
          )
        """
    )
    suspend fun trimToLimit(environmentId: String, marketId: String, limit: Int)

    @Query(
        """
        DELETE FROM search_history
        WHERE environmentId = :environmentId
          AND marketId = :marketId
          AND normalizedQuery = :normalizedQuery
        """
    )
    suspend fun deleteOne(environmentId: String, marketId: String, normalizedQuery: String)

    @Query("DELETE FROM search_history WHERE environmentId = :environmentId AND marketId = :marketId")
    suspend fun clear(environmentId: String, marketId: String)

    @Query(
        """
        SELECT enabled FROM search_history_settings
        WHERE environmentId = :environmentId AND marketId = :marketId
        """
    )
    suspend fun loadEnabled(environmentId: String, marketId: String): Boolean?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun storeSetting(setting: SearchHistorySettingEntity)
}

@Database(
    entities = [SearchHistoryEntity::class, SearchHistorySettingEntity::class, WishlistEntity::class],
    version = 2,
    exportSchema = true
)
abstract class LocalCommerceDatabase : RoomDatabase() {
    abstract fun searchHistoryDao(): SearchHistoryDao

    abstract fun wishlistDao(): WishlistDao
}

internal class RoomSearchHistoryStore(
    private val database: LocalCommerceDatabase,
    private val dao: SearchHistoryDao = database.searchHistoryDao()
) : SearchHistoryStore {
    override suspend fun load(partition: SearchHistoryPartition, cutoffEpochMillis: Long, limit: Int) =
        database.withTransaction {
            dao.deleteExpired(partition.environmentId, partition.marketId, cutoffEpochMillis)
            val enabled = dao.loadEnabled(partition.environmentId, partition.marketId) ?: true
            val entries =
                if (enabled) {
                    dao.loadRecent(partition.environmentId, partition.marketId, cutoffEpochMillis, limit)
                        .map { entity -> entity.toStoredQuery() }
                } else {
                    dao.clear(partition.environmentId, partition.marketId)
                    emptyList()
                }
            StoredSearchHistory(enabled = enabled, entries = entries)
        }

    override suspend fun record(
        partition: SearchHistoryPartition,
        query: StoredSearchQuery,
        cutoffEpochMillis: Long,
        limit: Int
    ) {
        database.withTransaction {
            dao.deleteExpired(partition.environmentId, partition.marketId, cutoffEpochMillis)
            if (dao.loadEnabled(partition.environmentId, partition.marketId) != false) {
                dao.insert(query.toEntity(partition))
                dao.trimToLimit(partition.environmentId, partition.marketId, limit)
            }
        }
    }

    override suspend fun remove(partition: SearchHistoryPartition, normalizedQuery: String) {
        dao.deleteOne(partition.environmentId, partition.marketId, normalizedQuery)
    }

    override suspend fun clear(partition: SearchHistoryPartition) {
        dao.clear(partition.environmentId, partition.marketId)
    }

    override suspend fun setEnabled(partition: SearchHistoryPartition, enabled: Boolean) {
        database.withTransaction {
            dao.storeSetting(SearchHistorySettingEntity(partition.environmentId, partition.marketId, enabled))
            if (!enabled) dao.clear(partition.environmentId, partition.marketId)
        }
    }
}

private fun SearchHistoryEntity.toStoredQuery() =
    StoredSearchQuery(normalizedQuery, displayQuery, searchedAtEpochMillis)

private fun StoredSearchQuery.toEntity(partition: SearchHistoryPartition) = SearchHistoryEntity(
    environmentId = partition.environmentId,
    marketId = partition.marketId,
    normalizedQuery = normalized,
    displayQuery = display,
    searchedAtEpochMillis = searchedAtEpochMillis
)
