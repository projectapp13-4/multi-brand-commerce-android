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
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

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

sealed interface WishlistLocalState {
    data class Available(val entries: List<StoredWishlistEntry>) : WishlistLocalState

    data object StorageUnavailable : WishlistLocalState
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
    fun observeLocalEntries(): Flow<WishlistLocalState>

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
    private val localMutex = Mutex()
    private val remoteMutex = Mutex()
    private val physicalPermits = Semaphore(MAX_CONCURRENT_WISHLIST_HYDRATIONS)
    private var generation = 0L
    private var startedRound = 0L
    private var activeFlight: WishlistHydrationFlight? = null
    private var cachedHydration: CachedWishlistHydration? = null

    override fun observeLocalEntries(): Flow<WishlistLocalState> = store.observe(partition)
        .map<List<StoredWishlistEntry>, WishlistLocalState> { WishlistLocalState.Available(it.toList()) }
        .catch { error ->
            if (error is CancellationException) throw error
            if (error.isStorageFailure()) emit(WishlistLocalState.StorageUnavailable) else throw error
        }

    override fun observeMembership(): Flow<WishlistMembershipState> = observeLocalEntries().map { local ->
        when (local) {
            is WishlistLocalState.Available ->
                WishlistMembershipState.Available(local.entries.mapTo(linkedSetOf(), StoredWishlistEntry::productId))

            WishlistLocalState.StorageUnavailable -> WishlistMembershipState.StorageUnavailable
        }
    }

    override suspend fun setSaved(productId: String, saved: Boolean): WishlistMutationResult {
        if (!StorefrontProductIds.isProductGid(productId)) return WishlistMutationResult.InvalidProduct
        val addedAtEpochMillis = clock.nowEpochMillis()
        return mutate { store.setSaved(partition, productId, saved, addedAtEpochMillis) }
    }

    override suspend fun clear(): WishlistMutationResult = mutate { store.clear(partition) }

    override suspend fun load(forceRefresh: Boolean): WishlistLoadResult {
        currentCoroutineContext().ensureActive()
        // Capture before waiting: requests made during round N require at least N + 1.
        val minimumRound = localMutex.withLock { if (forceRefresh) startedRound + 1L else 0L }
        return remoteMutex.withLock { loadRound(minimumRound) }
    }

    private suspend fun loadRound(minimumRound: Long): WishlistLoadResult {
        while (true) {
            currentCoroutineContext().ensureActive()
            when (val selection = selectRound(minimumRound)) {
                is WishlistRoundSelection.Ready -> {
                    currentCoroutineContext().ensureActive()
                    return selection.result
                }

                is WishlistRoundSelection.Hydrate -> {
                    val accepted = hydrateRound(selection.flight)
                    if (accepted != null) return accepted
                }
            }
        }
    }

    private suspend fun hydrateRound(flight: WishlistHydrationFlight): WishlistLoadResult? {
        try {
            val result = WishlistLoadResult.Content(
                coroutineScope {
                    flight.entries.map { entry ->
                        async { physicalPermits.withPermit { resolve(entry) } }
                    }.awaitAll()
                }
            )
            currentCoroutineContext().ensureActive()
            val accepted = localMutex.withLock {
                when (val current = store.readStoredEntries(partition)) {
                    WishlistLocalState.StorageUnavailable -> {
                        cachedHydration = null
                        WishlistLoadResult.StorageUnavailable
                    }

                    is WishlistLocalState.Available -> {
                        currentCoroutineContext().ensureActive()
                        if (activeFlight === flight && generation == flight.generation &&
                            current.entries == flight.entries
                        ) {
                            cachedHydration = CachedWishlistHydration(
                                flight.entries,
                                flight.generation,
                                flight.sequence,
                                result
                            )
                            result
                        } else {
                            null
                        }
                    }
                }
            }
            if (accepted != null) currentCoroutineContext().ensureActive()
            return accepted
        } finally {
            // Only metadata cleanup is noncancellable; no gateway, store IO or join here.
            withContext(NonCancellable) {
                localMutex.withLock { if (activeFlight === flight) activeFlight = null }
            }
        }
    }

    private suspend fun selectRound(minimumRound: Long): WishlistRoundSelection = localMutex.withLock {
        when (val local = store.readStoredEntries(partition)) {
            WishlistLocalState.StorageUnavailable -> {
                cachedHydration = null
                WishlistRoundSelection.Ready(WishlistLoadResult.StorageUnavailable)
            }

            is WishlistLocalState.Available -> {
                currentCoroutineContext().ensureActive()
                val cached = cachedHydration?.takeIf {
                    it.entries == local.entries && it.generation == generation && it.sequence >= minimumRound
                }
                if (cached != null) {
                    WishlistRoundSelection.Ready(cached.result)
                } else {
                    cachedHydration = null
                    val flight = WishlistHydrationFlight(local.entries, generation, ++startedRound)
                    activeFlight = flight
                    WishlistRoundSelection.Hydrate(flight)
                }
            }
        }
    }

    private suspend fun resolve(entry: StoredWishlistEntry): WishlistResolvedEntry {
        val result = gateway.loadProductDetail(ProductDetailRequest(entry.productId))
        currentCoroutineContext().ensureActive()
        return when (result) {
            is StorefrontResult.Success ->
                result.value?.let { product ->
                    WishlistResolvedEntry(entry.productId, entry.addedAtEpochMillis, product = product)
                } ?: WishlistResolvedEntry(entry.productId, entry.addedAtEpochMillis, issue = WishlistItemIssue.REMOVED)

            is StorefrontResult.Failure ->
                WishlistResolvedEntry(entry.productId, entry.addedAtEpochMillis, issue = result.error.toWishlistIssue())
        }
    }

    private suspend fun mutate(operation: suspend () -> Unit): WishlistMutationResult = try {
        localMutex.withLock {
            try {
                operation()
            } finally {
                // A failed/cancelled local write can have applied; never retain its old hydrated snapshot.
                generation++
                cachedHydration = null
            }
        }
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
    val generation: Long,
    val sequence: Long,
    val result: WishlistLoadResult.Content
)

private class WishlistHydrationFlight(val entries: List<StoredWishlistEntry>, val generation: Long, val sequence: Long)

private sealed interface WishlistRoundSelection {
    data class Ready(val result: WishlistLoadResult) : WishlistRoundSelection

    data class Hydrate(val flight: WishlistHydrationFlight) : WishlistRoundSelection
}

private suspend fun WishlistStore.readStoredEntries(partition: WishlistPartition): WishlistLocalState = try {
    WishlistLocalState.Available(load(partition).toList())
} catch (error: CancellationException) {
    throw error
} catch (_: SQLiteException) {
    WishlistLocalState.StorageUnavailable
} catch (_: IllegalStateException) {
    WishlistLocalState.StorageUnavailable
}

private fun WishlistEntity.toStored() = StoredWishlistEntry(productId, addedAtEpochMillis)

private fun StorefrontFailure.toWishlistIssue(): WishlistItemIssue = when (this) {
    is StorefrontFailure.Transport -> if (retryable) WishlistItemIssue.CONNECTION else WishlistItemIssue.SERVICE
    is StorefrontFailure.Configuration -> WishlistItemIssue.CONFIGURATION
    else -> WishlistItemIssue.SERVICE
}

private fun Throwable.isStorageFailure(): Boolean = this is SQLiteException || this is IllegalStateException
