package com.gurbakir.mobile.update

import java.util.concurrent.CancellationException
import javax.inject.Inject

internal const val UPDATE_POLICY_CACHE_TTL_MILLIS = 24L * 60L * 60L * 1_000L
internal const val UPDATE_POLICY_CLOCK_SKEW_TOLERANCE_MILLIS = 5L * 60L * 1_000L

internal enum class UpdatePolicySource {
    SAFE_DEFAULTS,
    UNEXPIRED_CACHE,
    REMOTE_CONFIG
}

internal data class UpdatePolicyPresentation(
    val source: UpdatePolicySource = UpdatePolicySource.SAFE_DEFAULTS,
    val maintenanceMessageEnabled: Boolean = false,
    val optionalUpdateVersionCode: Int? = null,
    val policyRevision: Long = 0L,
    val fetchedAtEpochMillis: Long? = null
)

internal data class CachedUpdatePolicy(
    val snapshot: UpdatePolicySnapshot,
    val fetchedAtEpochMillis: Long,
    val expiresAtEpochMillis: Long
)

internal interface UpdatePolicyStore {
    fun readPolicy(nowEpochMillis: Long): CachedUpdatePolicy?

    fun writePolicy(policy: CachedUpdatePolicy): Boolean

    fun isUpdateDeferred(recommendedVersionCode: Int, nowEpochMillis: Long): Boolean

    fun deferUpdate(recommendedVersionCode: Int, nowEpochMillis: Long, untilEpochMillis: Long): Boolean
}

internal fun interface UpdatePolicyClock {
    fun nowEpochMillis(): Long
}

data class CurrentAppVersionCode(val value: Int)

internal interface UpdatePolicyController {
    fun loadCachedPolicy(): UpdatePolicyPresentation

    suspend fun refreshPolicy(): UpdatePolicyPresentation

    fun deferUpdate(recommendedVersionCode: Int): Boolean
}

internal class DefaultUpdatePolicyController
@Inject
constructor(
    private val remoteGateway: UpdatePolicyRemoteGateway,
    private val store: UpdatePolicyStore,
    private val currentVersionCode: CurrentAppVersionCode,
    private val clock: UpdatePolicyClock
) : UpdatePolicyController {
    override fun loadCachedPolicy(): UpdatePolicyPresentation = readCacheOrDefaults(clock.nowEpochMillis())

    override suspend fun refreshPolicy(): UpdatePolicyPresentation {
        val result = try {
            remoteGateway.refresh()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            UpdatePolicyRefreshResult.LocalDefaults
        }
        val now = clock.nowEpochMillis()
        if (result !is UpdatePolicyRefreshResult.Fetched || !result.hasUsableTimestamp(now)) {
            return readCacheOrDefaults(now)
        }

        val expiresAt = result.fetchedAtEpochMillis + UPDATE_POLICY_CACHE_TTL_MILLIS
        val cached =
            CachedUpdatePolicy(
                snapshot = result.snapshot,
                fetchedAtEpochMillis = result.fetchedAtEpochMillis,
                expiresAtEpochMillis = expiresAt
            )
        runCatching { store.writePolicy(cached) }
        return cached.toPresentation(UpdatePolicySource.REMOTE_CONFIG, now)
    }

    override fun deferUpdate(recommendedVersionCode: Int): Boolean {
        val now = clock.nowEpochMillis()
        val until = now + UPDATE_POLICY_CACHE_TTL_MILLIS
        return recommendedVersionCode > currentVersionCode.value &&
            until > now &&
            runCatching {
                store.deferUpdate(
                    recommendedVersionCode = recommendedVersionCode,
                    nowEpochMillis = now,
                    untilEpochMillis = until
                )
            }.getOrDefault(false)
    }

    private fun readCacheOrDefaults(nowEpochMillis: Long): UpdatePolicyPresentation {
        val cached = runCatching { store.readPolicy(nowEpochMillis) }.getOrNull()
        return cached?.toPresentation(UpdatePolicySource.UNEXPIRED_CACHE, nowEpochMillis)
            ?: UpdatePolicyPresentation()
    }

    private fun CachedUpdatePolicy.toPresentation(
        source: UpdatePolicySource,
        nowEpochMillis: Long
    ): UpdatePolicyPresentation {
        val recommended =
            snapshot.recommendedVersionCode.takeIf {
                snapshot.optionalUpdateMessageEnabled &&
                    it > currentVersionCode.value &&
                    !isDeferred(it, nowEpochMillis)
            }
        return UpdatePolicyPresentation(
            source = source,
            maintenanceMessageEnabled = snapshot.maintenanceMessageEnabled,
            optionalUpdateVersionCode = recommended,
            policyRevision = snapshot.policyRevision,
            fetchedAtEpochMillis = fetchedAtEpochMillis
        )
    }

    private fun isDeferred(recommendedVersionCode: Int, nowEpochMillis: Long): Boolean =
        runCatching { store.isUpdateDeferred(recommendedVersionCode, nowEpochMillis) }
            .getOrDefault(false)
}

private fun UpdatePolicyRefreshResult.Fetched.hasUsableTimestamp(nowEpochMillis: Long): Boolean {
    if (fetchedAtEpochMillis <= 0L ||
        fetchedAtEpochMillis > Long.MAX_VALUE - UPDATE_POLICY_CACHE_TTL_MILLIS ||
        nowEpochMillis > Long.MAX_VALUE - UPDATE_POLICY_CLOCK_SKEW_TOLERANCE_MILLIS
    ) {
        return false
    }
    val expiresAt = fetchedAtEpochMillis + UPDATE_POLICY_CACHE_TTL_MILLIS
    return fetchedAtEpochMillis <= nowEpochMillis + UPDATE_POLICY_CLOCK_SKEW_TOLERANCE_MILLIS &&
        nowEpochMillis < expiresAt
}
