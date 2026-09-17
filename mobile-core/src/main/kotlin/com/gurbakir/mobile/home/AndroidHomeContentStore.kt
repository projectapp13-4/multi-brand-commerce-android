package com.gurbakir.mobile.home

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal const val HOME_CONTENT_PREFERENCES_NAME = "home_content_v1"
internal const val HOME_CONTENT_PREFERENCES_NAME_V2 = "home_content_v2"
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
    private val v1Preferences =
        context.applicationContext.getSharedPreferences(
            HOME_CONTENT_PREFERENCES_NAME,
            Context.MODE_PRIVATE
        )
    private val v2Preferences =
        context.applicationContext.getSharedPreferences(
            HOME_CONTENT_PREFERENCES_NAME_V2,
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
            val preferences = preferencesFor(partition, supportedContentVersion)
                ?: return@withLock HomeStoreRead.OwnershipUnknown
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
                removeSnapshot(preferences)
                return@withLock established(marker, HomeSnapshotRecovery.CORRUPT)
            }
            val stored = when (val decoded = codec.decodeSnapshot(rawSnapshot)) {
                is HomeCodecDecode.Accepted -> decoded.value

                is HomeCodecDecode.Rejected -> {
                    removeSnapshot(preferences)
                    return@withLock established(marker, HomeSnapshotRecovery.CORRUPT)
                }
            }
            if (stored.partition != partition) {
                removeSnapshot(preferences)
                return@withLock established(marker, HomeSnapshotRecovery.CORRUPT)
            }
            if (stored.snapshot.contentVersion != supportedContentVersion) {
                removeSnapshot(preferences)
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
    override suspend fun replace(marker: HomeEstablishmentRecord, snapshot: HomeStoredSnapshot): HomeStoreWrite =
        withContext(dispatcher) {
            mutex.withLock {
                val encoded = runCatching {
                    require(marker.partition == snapshot.partition)
                    codec.encodeMarker(marker) to codec.encodeSnapshot(snapshot)
                }.getOrNull() ?: return@withLock HomeStoreWrite.UNCONFIRMED
                val preferences = preferencesFor(snapshot.partition, snapshot.snapshot.contentVersion)
                    ?: return@withLock HomeStoreWrite.UNCONFIRMED
                preferences.edit()
                    .putString(HOME_ESTABLISHMENT_KEY, encoded.first)
                    .putString(HOME_SNAPSHOT_KEY, encoded.second)
                    .commit()
                    .toStoreWrite()
            }
        }

    override suspend fun evictSnapshot(partition: HomeContentPartition): HomeStoreWrite = withContext(dispatcher) {
        mutex.withLock {
            val preferences = preferencesForPartition(partition)
                ?: return@withLock HomeStoreWrite.UNCONFIRMED
            val marker = (preferences.all[HOME_ESTABLISHMENT_KEY] as? String)
                ?.let(codec::decodeMarker)
                ?.let { it as? HomeCodecDecode.Accepted }
                ?.value
            if (marker?.partition != partition) {
                HomeStoreWrite.CONFIRMED
            } else {
                removeSnapshot(preferences).toStoreWrite()
            }
        }
    }

    @SuppressLint("UseKtx") // Cleanup reports the synchronous commit result instead of assuming durability.
    private fun removeSnapshot(preferences: SharedPreferences): Boolean =
        preferences.edit().remove(HOME_SNAPSHOT_KEY).commit()

    private fun preferencesFor(partition: HomeContentPartition, contentVersion: Int): SharedPreferences? = when {
        partition.rootType == "mobile_home" && contentVersion == 1 -> v1Preferences
        partition.rootType == "mobile_home_v2" && contentVersion == 2 -> v2Preferences
        else -> null
    }

    private fun preferencesForPartition(partition: HomeContentPartition): SharedPreferences? =
        when (partition.rootType) {
            "mobile_home" -> v1Preferences
            "mobile_home_v2" -> v2Preferences
            else -> null
        }

    private fun established(marker: HomeEstablishmentRecord, recovery: HomeSnapshotRecovery) =
        HomeStoreRead.Established(marker, snapshot = null, recovery = recovery)
}

private fun Boolean.toStoreWrite(): HomeStoreWrite = if (this) HomeStoreWrite.CONFIRMED else HomeStoreWrite.UNCONFIRMED
