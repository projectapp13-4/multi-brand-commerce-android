package com.gurbakir.mobile.home

import android.annotation.SuppressLint
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal const val HOME_CONTENT_PREFERENCES_NAME = "home_content_v1"
internal const val HOME_ESTABLISHMENT_KEY = "establishment"
internal const val HOME_SNAPSHOT_KEY = "snapshot"

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class HomeContentIo

@Singleton
class AndroidHomeContentStore
@Inject
constructor(
    @ApplicationContext context: Context,
    private val codec: HomeContentCodec,
    @param:HomeContentIo private val dispatcher: CoroutineDispatcher
) : HomeContentStore {
    private val preferences =
        context.applicationContext.getSharedPreferences(
            HOME_CONTENT_PREFERENCES_NAME,
            Context.MODE_PRIVATE
        )
    private val mutex = Mutex()

    @Suppress("CyclomaticComplexMethod")
    override suspend fun read(
        partition: HomeContentPartition,
        supportedContentVersion: Int,
        nowMillis: Long
    ): HomeStoreRead = withContext(dispatcher) {
        mutex.withLock {
            val markerValue = preferences.all[HOME_ESTABLISHMENT_KEY]
                ?: return@withLock HomeStoreRead.NeverEstablished
            val rawMarker = markerValue as? String
                ?: return@withLock HomeStoreRead.OwnershipUnknown
            val marker = when (val decoded = codec.decodeMarker(rawMarker)) {
                is HomeCodecDecode.Accepted -> decoded.value
                is HomeCodecDecode.Rejected -> return@withLock HomeStoreRead.OwnershipUnknown
            }
            if (marker.partition != partition) return@withLock HomeStoreRead.NeverEstablished

            val snapshotValue = preferences.all[HOME_SNAPSHOT_KEY]
                ?: return@withLock established(marker, HomeSnapshotRecovery.MISSING)
            val rawSnapshot = snapshotValue as? String ?: run {
                removeSnapshot()
                return@withLock established(marker, HomeSnapshotRecovery.CORRUPT)
            }
            val stored = when (val decoded = codec.decodeSnapshot(rawSnapshot)) {
                is HomeCodecDecode.Accepted -> decoded.value

                is HomeCodecDecode.Rejected -> {
                    removeSnapshot()
                    return@withLock established(marker, HomeSnapshotRecovery.CORRUPT)
                }
            }
            if (stored.partition != partition) {
                removeSnapshot()
                return@withLock established(marker, HomeSnapshotRecovery.CORRUPT)
            }
            if (stored.snapshot.contentVersion != supportedContentVersion) {
                removeSnapshot()
                return@withLock established(marker, HomeSnapshotRecovery.INCOMPATIBLE)
            }
            when (
                HomeEditorialClockPolicy.freshness(
                    stored.acceptedAtMillis,
                    stored.expiresAtMillis,
                    nowMillis
                )
            ) {
                HomeEditorialFreshness.CLOCK_INVALID ->
                    return@withLock established(marker, HomeSnapshotRecovery.CLOCK_INVALID)

                HomeEditorialFreshness.EXPIRED ->
                    return@withLock established(marker, HomeSnapshotRecovery.EXPIRED)

                HomeEditorialFreshness.FRESH -> Unit
            }
            HomeStoreRead.Established(marker, stored, HomeSnapshotRecovery.AVAILABLE)
        }
    }

    @SuppressLint("UseKtx") // KTX edit(commit = true) discards the commit result required by this contract.
    override suspend fun replace(marker: HomeEstablishmentRecord, snapshot: HomeStoredSnapshot): HomeStoreWrite {
        require(marker.partition == snapshot.partition)
        val encodedMarker = codec.encodeMarker(marker)
        val encodedSnapshot = codec.encodeSnapshot(snapshot)
        return withContext(dispatcher) {
            mutex.withLock {
                preferences.edit()
                    .putString(HOME_ESTABLISHMENT_KEY, encodedMarker)
                    .putString(HOME_SNAPSHOT_KEY, encodedSnapshot)
                    .commit()
                    .toStoreWrite()
            }
        }
    }

    override suspend fun evictSnapshot(partition: HomeContentPartition): HomeStoreWrite = withContext(dispatcher) {
        mutex.withLock {
            val marker = preferences.getString(HOME_ESTABLISHMENT_KEY, null)
                ?.let(codec::decodeMarker)
                ?.let { it as? HomeCodecDecode.Accepted }
                ?.value
            if (marker?.partition != partition) {
                HomeStoreWrite.CONFIRMED
            } else {
                removeSnapshot().toStoreWrite()
            }
        }
    }

    @SuppressLint("UseKtx") // Cleanup reports the synchronous commit result instead of assuming durability.
    private fun removeSnapshot(): Boolean = preferences.edit().remove(HOME_SNAPSHOT_KEY).commit()

    private fun established(marker: HomeEstablishmentRecord, recovery: HomeSnapshotRecovery) =
        HomeStoreRead.Established(marker, snapshot = null, recovery = recovery)
}

private fun Boolean.toStoreWrite(): HomeStoreWrite = if (this) HomeStoreWrite.CONFIRMED else HomeStoreWrite.UNCONFIRMED
