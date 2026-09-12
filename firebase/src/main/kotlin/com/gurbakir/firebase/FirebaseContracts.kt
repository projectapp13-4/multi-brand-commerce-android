package com.gurbakir.firebase

import com.google.android.gms.tasks.Task
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import java.util.concurrent.CancellationException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

internal const val REMOTE_CONFIG_MINIMUM_FETCH_INTERVAL_SECONDS = 12L * 60L * 60L
internal const val REMOTE_CONFIG_FETCH_TIMEOUT_SECONDS = 10L
private const val REMOTE_POLICY_SCHEMA_VERSION = 1L
private const val MAXIMUM_POLICY_REVISION = 1_000_000_000L

interface RemoteFeatureFlags {
    suspend fun refresh(): RemoteConfigResult

    fun boolean(key: ApprovedRemoteFlag): Boolean

    fun policySnapshot(): RemotePolicySnapshot
}

enum class ApprovedRemoteFlag(internal val parameterKey: String) {
    MAINTENANCE_MESSAGE_ENABLED("maintenance_message_enabled"),
    CHECKOUT_PRELOAD_ENABLED("checkout_preload_enabled"),
    OPTIONAL_UPDATE_MESSAGE_ENABLED("optional_update_message_enabled")
}

private enum class ApprovedRemoteNumber(val parameterKey: String, val defaultValue: Long) {
    POLICY_SCHEMA_VERSION("mobile_policy_schema_version", REMOTE_POLICY_SCHEMA_VERSION),
    POLICY_REVISION("mobile_policy_revision", 0L),
    RECOMMENDED_VERSION_CODE("recommended_version_code", 0L)
}

sealed interface RemoteConfigResult {
    data class Fetched(val activatedNewValues: Boolean, val fetchedAtEpochMillis: Long) : RemoteConfigResult

    data object LocalDefaults : RemoteConfigResult
}

data class RemotePolicySnapshot(
    val maintenanceMessageEnabled: Boolean = false,
    val checkoutPreloadEnabled: Boolean = false,
    val optionalUpdateMessageEnabled: Boolean = false,
    val recommendedVersionCode: Int = 0,
    val policyRevision: Long = 0L
) {
    companion object {
        val SAFE_DEFAULTS = RemotePolicySnapshot()
    }
}

internal interface RemoteConfigClient {
    suspend fun configure(minimumFetchIntervalSeconds: Long, fetchTimeoutSeconds: Long)

    suspend fun installDefaults(defaults: Map<String, Any>)

    suspend fun fetchAndActivate(): Boolean

    fun string(key: String): String

    fun lastSuccessfulFetchEpochMillis(): Long
}

private class FirebaseRemoteConfigClient(
    private val remoteConfig: FirebaseRemoteConfig = FirebaseRemoteConfig.getInstance()
) : RemoteConfigClient {
    override suspend fun configure(minimumFetchIntervalSeconds: Long, fetchTimeoutSeconds: Long) {
        val settings =
            FirebaseRemoteConfigSettings.Builder()
                .setMinimumFetchIntervalInSeconds(minimumFetchIntervalSeconds)
                .setFetchTimeoutInSeconds(fetchTimeoutSeconds)
                .build()
        remoteConfig.setConfigSettingsAsync(settings).awaitCompletion()
    }

    override suspend fun installDefaults(defaults: Map<String, Any>) {
        remoteConfig.setDefaultsAsync(defaults).awaitCompletion()
    }

    override suspend fun fetchAndActivate(): Boolean = remoteConfig.fetchAndActivate().awaitValue()

    override fun string(key: String): String = remoteConfig.getString(key)

    override fun lastSuccessfulFetchEpochMillis(): Long = remoteConfig.info.fetchTimeMillis
}

internal class FirebaseRemoteFeatureFlags(private val client: RemoteConfigClient) : RemoteFeatureFlags {
    @Volatile
    private var snapshot = RemotePolicySnapshot.SAFE_DEFAULTS

    override suspend fun refresh(): RemoteConfigResult = try {
        client.configure(
            minimumFetchIntervalSeconds = REMOTE_CONFIG_MINIMUM_FETCH_INTERVAL_SECONDS,
            fetchTimeoutSeconds = REMOTE_CONFIG_FETCH_TIMEOUT_SECONDS
        )
        client.installDefaults(remotePolicyDefaults())
        val activatedNewValues = client.fetchAndActivate()
        snapshot = parseRemotePolicy(client)
        RemoteConfigResult.Fetched(
            activatedNewValues = activatedNewValues,
            fetchedAtEpochMillis = client.lastSuccessfulFetchEpochMillis()
        )
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Exception) {
        snapshot = RemotePolicySnapshot.SAFE_DEFAULTS
        RemoteConfigResult.LocalDefaults
    }

    override fun boolean(key: ApprovedRemoteFlag): Boolean = when (key) {
        ApprovedRemoteFlag.MAINTENANCE_MESSAGE_ENABLED -> snapshot.maintenanceMessageEnabled
        ApprovedRemoteFlag.CHECKOUT_PRELOAD_ENABLED -> snapshot.checkoutPreloadEnabled
        ApprovedRemoteFlag.OPTIONAL_UPDATE_MESSAGE_ENABLED -> snapshot.optionalUpdateMessageEnabled
    }

    override fun policySnapshot(): RemotePolicySnapshot = snapshot
}

fun createFirebaseRemoteFeatureFlags(): RemoteFeatureFlags =
    createFirebaseRemoteFeatureFlags { FirebaseRemoteConfigClient() }

internal fun createFirebaseRemoteFeatureFlags(clientFactory: () -> RemoteConfigClient): RemoteFeatureFlags =
    runCatching { FirebaseRemoteFeatureFlags(clientFactory()) }
        .getOrElse { LocalDefaultFeatureFlags() }

private fun remotePolicyDefaults(): Map<String, Any> = buildMap {
    ApprovedRemoteFlag.entries.forEach { put(it.parameterKey, false) }
    ApprovedRemoteNumber.entries.forEach { put(it.parameterKey, it.defaultValue) }
}

private fun parseRemotePolicy(client: RemoteConfigClient): RemotePolicySnapshot {
    val schemaVersion = client.strictLong(ApprovedRemoteNumber.POLICY_SCHEMA_VERSION)
    if (schemaVersion != REMOTE_POLICY_SCHEMA_VERSION) return RemotePolicySnapshot.SAFE_DEFAULTS

    val revision =
        client.strictLong(ApprovedRemoteNumber.POLICY_REVISION)
            ?.takeIf { it in 0L..MAXIMUM_POLICY_REVISION }
            ?: 0L
    val recommendedVersionCode =
        client.strictLong(ApprovedRemoteNumber.RECOMMENDED_VERSION_CODE)
            ?.takeIf { it in 0L..Int.MAX_VALUE.toLong() }
            ?.toInt()
            ?: 0
    return RemotePolicySnapshot(
        maintenanceMessageEnabled = client.strictBoolean(ApprovedRemoteFlag.MAINTENANCE_MESSAGE_ENABLED),
        checkoutPreloadEnabled = client.strictBoolean(ApprovedRemoteFlag.CHECKOUT_PRELOAD_ENABLED),
        optionalUpdateMessageEnabled = client.strictBoolean(ApprovedRemoteFlag.OPTIONAL_UPDATE_MESSAGE_ENABLED),
        recommendedVersionCode = recommendedVersionCode,
        policyRevision = revision
    )
}

private fun RemoteConfigClient.strictBoolean(flag: ApprovedRemoteFlag): Boolean =
    string(flag.parameterKey).trim().takeIf { it == "true" || it == "false" }?.toBooleanStrictOrNull() ?: false

private fun RemoteConfigClient.strictLong(number: ApprovedRemoteNumber): Long? =
    string(number.parameterKey).trim().toLongOrNull()

class LocalDefaultFeatureFlags : RemoteFeatureFlags {
    override suspend fun refresh(): RemoteConfigResult = RemoteConfigResult.LocalDefaults

    override fun boolean(key: ApprovedRemoteFlag): Boolean = false

    override fun policySnapshot(): RemotePolicySnapshot = RemotePolicySnapshot.SAFE_DEFAULTS
}

private suspend fun Task<*>.awaitCompletion(): Unit = suspendCancellableCoroutine { continuation ->
    addOnCompleteListener { task ->
        if (!continuation.isActive) return@addOnCompleteListener
        val failure = task.exception
        if (task.isSuccessful) {
            continuation.resume(Unit)
        } else {
            continuation.resumeWithException(failure ?: IllegalStateException("Firebase task failed"))
        }
    }
}

private suspend fun <T> Task<T>.awaitValue(): T = suspendCancellableCoroutine { continuation ->
    addOnCompleteListener { task ->
        if (!continuation.isActive) return@addOnCompleteListener
        val failure = task.exception
        if (task.isSuccessful) {
            continuation.resume(task.result)
        } else {
            continuation.resumeWithException(failure ?: IllegalStateException("Firebase task failed"))
        }
    }
}
