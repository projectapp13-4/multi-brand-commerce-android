package com.gurbakir.mobile.wishlist

import android.database.sqlite.SQLiteException
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.migration.Migration
import androidx.room.withTransaction
import com.gurbakir.mobile.search.LocalCommerceDatabase
import com.gurbakir.storefront.ProductDetailRequest
import com.gurbakir.storefront.StorefrontFailure
import com.gurbakir.storefront.StorefrontProductDetail
import com.gurbakir.storefront.StorefrontProductGateway
import com.gurbakir.storefront.StorefrontProductIds
import com.gurbakir.storefront.StorefrontResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit

private const val MAX_CONCURRENT_WISHLIST_HYDRATIONS = 4

@Entity(
    tableName = "wishlist",
    primaryKeys = ["environmentId", "marketId", "productId"],
    indices = [Index(value = ["environmentId", "marketId", "addedAtEpochMillis"])]
)
data class WishlistEntity(
    val environmentId: String,
    val marketId: String,
    val productId: String,
    val addedAtEpochMillis: Long
)

@Dao
interface WishlistDao {
    @Query(
        """
        SELECT * FROM wishlist
        WHERE environmentId = :environmentId AND marketId = :marketId
        ORDER BY addedAtEpochMillis DESC, productId ASC
        """
    )
    fun observe(environmentId: String, marketId: String): Flow<List<WishlistEntity>>

    @Query(
        """
        SELECT * FROM wishlist
        WHERE environmentId = :environmentId AND marketId = :marketId
        ORDER BY addedAtEpochMillis DESC, productId ASC
        """
    )
    suspend fun load(environmentId: String, marketId: String): List<WishlistEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: WishlistEntity): Long

    @Query(
        """
        DELETE FROM wishlist
        WHERE environmentId = :environmentId AND marketId = :marketId AND productId = :productId
        """
    )
    suspend fun delete(environmentId: String, marketId: String, productId: String): Int

    @Query("DELETE FROM wishlist WHERE environmentId = :environmentId AND marketId = :marketId")
    suspend fun clear(environmentId: String, marketId: String)
}

val WISHLIST_MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `wishlist` (
                `environmentId` TEXT NOT NULL,
                `marketId` TEXT NOT NULL,
                `productId` TEXT NOT NULL,
                `addedAtEpochMillis` INTEGER NOT NULL,
                PRIMARY KEY(`environmentId`, `marketId`, `productId`)
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE INDEX IF NOT EXISTS `index_wishlist_environmentId_marketId_addedAtEpochMillis`
            ON `wishlist` (`environmentId`, `marketId`, `addedAtEpochMillis`)
            """.trimIndent()
        )
    }
}

data class WishlistPartition(val environmentId: String, val marketId: String)

data class StoredWishlistEntry(val productId: String, val addedAtEpochMillis: Long)

interface WishlistStore {
    fun observe(partition: WishlistPartition): Flow<List<StoredWishlistEntry>>

    suspend fun load(partition: WishlistPartition): List<StoredWishlistEntry>

    suspend fun setSaved(partition: WishlistPartition, productId: String, saved: Boolean, addedAtEpochMillis: Long)

    suspend fun clear(partition: WishlistPartition)
}

internal class RoomWishlistStore(
    private val database: LocalCommerceDatabase,
    private val dao: WishlistDao = database.wishlistDao()
) : WishlistStore {
    override fun observe(partition: WishlistPartition): Flow<List<StoredWishlistEntry>> =
        dao.observe(partition.environmentId, partition.marketId).map { values -> values.map(WishlistEntity::toStored) }

    override suspend fun load(partition: WishlistPartition): List<StoredWishlistEntry> =
        dao.load(partition.environmentId, partition.marketId).map(WishlistEntity::toStored)

    override suspend fun setSaved(
        partition: WishlistPartition,
        productId: String,
        saved: Boolean,
        addedAtEpochMillis: Long
    ) {
        database.withTransaction {
            if (saved) {
                dao.insert(WishlistEntity(partition.environmentId, partition.marketId, productId, addedAtEpochMillis))
            } else {
                dao.delete(partition.environmentId, partition.marketId, productId)
            }
        }
    }

    override suspend fun clear(partition: WishlistPartition) {
        dao.clear(partition.environmentId, partition.marketId)
    }
}

fun interface WishlistClock {
    fun nowEpochMillis(): Long
}

sealed interface WishlistMembershipState {
    data class Available(val productIds: Set<String>) : WishlistMembershipState

    data object StorageUnavailable : WishlistMembershipState
}

sealed interface WishlistMutationResult {
    data object Success : WishlistMutationResult

    data object InvalidProduct : WishlistMutationResult

    data object StorageUnavailable : WishlistMutationResult
}

sealed interface WishlistLoadResult {
    data class Content(val entries: List<WishlistResolvedEntry>) : WishlistLoadResult

    data object StorageUnavailable : WishlistLoadResult
}

data class WishlistResolvedEntry(
    val productId: String,
    val addedAtEpochMillis: Long,
    val product: StorefrontProductDetail? = null,
    val issue: WishlistItemIssue? = null
)

enum class WishlistItemIssue(val retryable: Boolean) {
    REMOVED(false),
    CONNECTION(true),
    CONFIGURATION(false),
    SERVICE(true)
}

interface WishlistRepository {
    fun observeMembership(): Flow<WishlistMembershipState>

    suspend fun setSaved(productId: String, saved: Boolean): WishlistMutationResult

    suspend fun load(forceRefresh: Boolean = false): WishlistLoadResult

    suspend fun clear(): WishlistMutationResult
}

class DefaultWishlistRepository(
    private val store: WishlistStore,
    private val gateway: StorefrontProductGateway,
    private val partition: WishlistPartition,
    private val clock: WishlistClock
) : WishlistRepository {
    private val hydrationLock = Mutex()
    private var cachedHydration: CachedWishlistHydration? = null

    override fun observeMembership(): Flow<WishlistMembershipState> = store.observe(partition)
        .map<List<StoredWishlistEntry>, WishlistMembershipState> { entries ->
            WishlistMembershipState.Available(entries.mapTo(linkedSetOf(), StoredWishlistEntry::productId))
        }.catch { error ->
            if (error is CancellationException) throw error
            if (error.isStorageFailure()) emit(WishlistMembershipState.StorageUnavailable) else throw error
        }

    override suspend fun setSaved(productId: String, saved: Boolean): WishlistMutationResult {
        if (!StorefrontProductIds.isProductGid(productId)) return WishlistMutationResult.InvalidProduct
        return guardedMutation {
            hydrationLock.withLock {
                store.setSaved(partition, productId, saved, clock.nowEpochMillis())
                cachedHydration = null
            }
        }
    }

    override suspend fun load(forceRefresh: Boolean): WishlistLoadResult = try {
        hydrationLock.withLock {
            val entries = store.load(partition)
            cachedHydration?.takeIf { !forceRefresh && it.entries == entries }?.result
                ?: WishlistLoadResult.Content(entries.resolveAll()).also { result ->
                    cachedHydration = CachedWishlistHydration(entries, result)
                }
        }
    } catch (error: CancellationException) {
        throw error
    } catch (_: SQLiteException) {
        WishlistLoadResult.StorageUnavailable
    } catch (_: IllegalStateException) {
        WishlistLoadResult.StorageUnavailable
    }

    override suspend fun clear(): WishlistMutationResult = guardedMutation {
        hydrationLock.withLock {
            store.clear(partition)
            cachedHydration = null
        }
    }

    private suspend fun List<StoredWishlistEntry>.resolveAll(): List<WishlistResolvedEntry> = coroutineScope {
        val permits = Semaphore(MAX_CONCURRENT_WISHLIST_HYDRATIONS)
        map { entry ->
            async { permits.withPermit { entry.resolve() } }
        }.awaitAll()
    }

    private suspend fun StoredWishlistEntry.resolve(): WishlistResolvedEntry =
        when (val result = gateway.loadProductDetail(ProductDetailRequest(productId))) {
            is StorefrontResult.Success ->
                result.value?.let { product ->
                    WishlistResolvedEntry(productId, addedAtEpochMillis, product = product)
                } ?: WishlistResolvedEntry(productId, addedAtEpochMillis, issue = WishlistItemIssue.REMOVED)

            is StorefrontResult.Failure ->
                WishlistResolvedEntry(productId, addedAtEpochMillis, issue = result.error.toWishlistIssue())
        }

    private suspend fun guardedMutation(block: suspend () -> Unit): WishlistMutationResult = try {
        block()
        WishlistMutationResult.Success
    } catch (error: CancellationException) {
        throw error
    } catch (_: SQLiteException) {
        WishlistMutationResult.StorageUnavailable
    } catch (_: IllegalStateException) {
        WishlistMutationResult.StorageUnavailable
    }
}

private data class CachedWishlistHydration(
    val entries: List<StoredWishlistEntry>,
    val result: WishlistLoadResult.Content
)

private fun WishlistEntity.toStored() = StoredWishlistEntry(productId, addedAtEpochMillis)

private fun StorefrontFailure.toWishlistIssue(): WishlistItemIssue = when (this) {
    is StorefrontFailure.Transport -> if (retryable) WishlistItemIssue.CONNECTION else WishlistItemIssue.SERVICE
    is StorefrontFailure.Configuration -> WishlistItemIssue.CONFIGURATION
    else -> WishlistItemIssue.SERVICE
}

private fun Throwable.isStorageFailure(): Boolean = this is SQLiteException || this is IllegalStateException
