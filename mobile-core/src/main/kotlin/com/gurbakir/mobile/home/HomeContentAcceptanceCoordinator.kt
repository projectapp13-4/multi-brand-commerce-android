package com.gurbakir.mobile.home

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@JvmInline
value class HomeRequestToken internal constructor(internal val value: Long)

sealed interface HomeAcceptance {
    data class Accepted(val persistence: HomeStoreWrite) : HomeAcceptance

    data object Superseded : HomeAcceptance
}

sealed interface HomeFailureAuthority {
    data class Current(val sessionSnapshot: HomeStoredSnapshot?, val storedRead: HomeStoreRead) : HomeFailureAuthority

    data object Superseded : HomeFailureAuthority
}

@Singleton
class HomeContentAcceptanceCoordinator
@Inject
constructor(private val store: HomeContentStore) {
    private val mutex = Mutex()
    private var currentToken = 0L
    private val sessionAuthority = mutableMapOf<HomeContentPartition, HomeStoredSnapshot>()

    suspend fun begin(): HomeRequestToken = mutex.withLock {
        currentToken = Math.addExact(currentToken, 1L)
        HomeRequestToken(currentToken)
    }

    suspend fun accept(
        token: HomeRequestToken,
        marker: HomeEstablishmentRecord,
        snapshot: HomeStoredSnapshot
    ): HomeAcceptance = mutex.withLock {
        if (token.value != currentToken) return@withLock HomeAcceptance.Superseded
        require(marker.partition == snapshot.partition)
        val write = store.replace(marker, snapshot)
        sessionAuthority[snapshot.partition] = snapshot
        HomeAcceptance.Accepted(write)
    }

    suspend fun current(partition: HomeContentPartition): HomeStoredSnapshot? =
        mutex.withLock { sessionAuthority[partition] }

    suspend fun resolveFailure(
        token: HomeRequestToken,
        partition: HomeContentPartition,
        supportedContentVersion: Int,
        nowMillis: Long
    ): HomeFailureAuthority = mutex.withLock {
        if (token.value != currentToken) return@withLock HomeFailureAuthority.Superseded
        HomeFailureAuthority.Current(
            sessionSnapshot = sessionAuthority[partition],
            storedRead = store.read(partition, supportedContentVersion, nowMillis)
        )
    }

    suspend fun clearSession(partition: HomeContentPartition) {
        mutex.withLock { sessionAuthority.remove(partition) }
    }
}
