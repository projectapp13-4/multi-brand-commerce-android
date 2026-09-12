package com.gurbakir.mobile.update

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences

internal class AndroidUpdatePolicyStore(context: Context, preferencesName: String = PREFERENCES_NAME) :
    UpdatePolicyStore {
    private val preferences: SharedPreferences =
        context.applicationContext.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)

    override fun readPolicy(nowEpochMillis: Long): CachedUpdatePolicy? = runCatching {
        if (preferences.getInt(KEY_SCHEMA_VERSION, 0) != STORAGE_SCHEMA_VERSION) return null
        if (preferences.getString(KEY_SOURCE, null) != SOURCE_REMOTE_CONFIG) return null
        val fetchedAt = preferences.getLong(KEY_FETCHED_AT, 0L)
        val expiresAt = preferences.getLong(KEY_EXPIRES_AT, 0L)
        if (!validWindow(fetchedAt, expiresAt, nowEpochMillis)) return null
        val recommendedVersionCode = preferences.getLong(KEY_RECOMMENDED_VERSION, -1L)
        val revision = preferences.getLong(KEY_POLICY_REVISION, -1L)
        if (recommendedVersionCode !in 0L..Int.MAX_VALUE.toLong()) return null
        if (revision !in 0L..MAXIMUM_STORED_POLICY_REVISION) return null
        CachedUpdatePolicy(
            snapshot =
                UpdatePolicySnapshot(
                    maintenanceMessageEnabled = preferences.getBoolean(KEY_MAINTENANCE, false),
                    checkoutPreloadEnabled = preferences.getBoolean(KEY_CHECKOUT_PRELOAD, false),
                    optionalUpdateMessageEnabled = preferences.getBoolean(KEY_UPDATE_MESSAGE, false),
                    recommendedVersionCode = recommendedVersionCode.toInt(),
                    policyRevision = revision
                ),
            fetchedAtEpochMillis = fetchedAt,
            expiresAtEpochMillis = expiresAt
        )
    }.getOrNull()

    @SuppressLint("UseKtx") // The boolean commit result is part of the fail-closed store contract.
    override fun writePolicy(policy: CachedUpdatePolicy): Boolean {
        if (!validPolicyForWrite(policy)) return false
        return preferences.edit()
            .putInt(KEY_SCHEMA_VERSION, STORAGE_SCHEMA_VERSION)
            .putString(KEY_SOURCE, SOURCE_REMOTE_CONFIG)
            .putLong(KEY_FETCHED_AT, policy.fetchedAtEpochMillis)
            .putLong(KEY_EXPIRES_AT, policy.expiresAtEpochMillis)
            .putBoolean(KEY_MAINTENANCE, policy.snapshot.maintenanceMessageEnabled)
            .putBoolean(KEY_CHECKOUT_PRELOAD, policy.snapshot.checkoutPreloadEnabled)
            .putBoolean(KEY_UPDATE_MESSAGE, policy.snapshot.optionalUpdateMessageEnabled)
            .putLong(KEY_RECOMMENDED_VERSION, policy.snapshot.recommendedVersionCode.toLong())
            .putLong(KEY_POLICY_REVISION, policy.snapshot.policyRevision)
            .commit()
    }

    override fun isUpdateDeferred(recommendedVersionCode: Int, nowEpochMillis: Long): Boolean = runCatching {
        if (recommendedVersionCode <= 0) return false
        if (preferences.getInt(KEY_DEFERRED_VERSION, 0) != recommendedVersionCode) return false
        val deferredAt = preferences.getLong(KEY_DEFERRED_AT, 0L)
        val deferredUntil = preferences.getLong(KEY_DEFERRED_UNTIL, 0L)
        validWindow(deferredAt, deferredUntil, nowEpochMillis)
    }.getOrDefault(false)

    @SuppressLint("UseKtx") // Deferral is acknowledged only after a synchronous successful write.
    override fun deferUpdate(recommendedVersionCode: Int, nowEpochMillis: Long, untilEpochMillis: Long): Boolean {
        if (recommendedVersionCode <= 0 || !validWriteWindow(nowEpochMillis, untilEpochMillis)) return false
        return preferences.edit()
            .putInt(KEY_DEFERRED_VERSION, recommendedVersionCode)
            .putLong(KEY_DEFERRED_AT, nowEpochMillis)
            .putLong(KEY_DEFERRED_UNTIL, untilEpochMillis)
            .commit()
    }

    private fun validPolicyForWrite(policy: CachedUpdatePolicy): Boolean =
        validWriteWindow(policy.fetchedAtEpochMillis, policy.expiresAtEpochMillis) &&
            policy.snapshot.recommendedVersionCode >= 0 &&
            policy.snapshot.policyRevision in 0L..MAXIMUM_STORED_POLICY_REVISION

    private companion object {
        const val PREFERENCES_NAME = "bounded-update-policy"
        const val STORAGE_SCHEMA_VERSION = 1
        const val SOURCE_REMOTE_CONFIG = "firebase-remote-config"
        const val MAXIMUM_STORED_POLICY_REVISION = 1_000_000_000L
        const val KEY_SCHEMA_VERSION = "schema_version"
        const val KEY_SOURCE = "source"
        const val KEY_FETCHED_AT = "fetched_at_epoch_millis"
        const val KEY_EXPIRES_AT = "expires_at_epoch_millis"
        const val KEY_MAINTENANCE = "maintenance_message_enabled"
        const val KEY_CHECKOUT_PRELOAD = "checkout_preload_enabled"
        const val KEY_UPDATE_MESSAGE = "optional_update_message_enabled"
        const val KEY_RECOMMENDED_VERSION = "recommended_version_code"
        const val KEY_POLICY_REVISION = "policy_revision"
        const val KEY_DEFERRED_VERSION = "deferred_recommended_version_code"
        const val KEY_DEFERRED_AT = "deferred_at_epoch_millis"
        const val KEY_DEFERRED_UNTIL = "deferred_until_epoch_millis"
    }
}

private fun validWriteWindow(startEpochMillis: Long, endEpochMillis: Long): Boolean = startEpochMillis > 0L &&
    endEpochMillis > startEpochMillis &&
    endEpochMillis - startEpochMillis == UPDATE_POLICY_CACHE_TTL_MILLIS

private fun validWindow(startEpochMillis: Long, endEpochMillis: Long, nowEpochMillis: Long): Boolean {
    if (!validWriteWindow(startEpochMillis, endEpochMillis)) return false
    val timestampNotTooFarInFuture =
        startEpochMillis <= UPDATE_POLICY_CLOCK_SKEW_TOLERANCE_MILLIS ||
            nowEpochMillis >= startEpochMillis - UPDATE_POLICY_CLOCK_SKEW_TOLERANCE_MILLIS
    return timestampNotTooFarInFuture && nowEpochMillis < endEpochMillis
}
