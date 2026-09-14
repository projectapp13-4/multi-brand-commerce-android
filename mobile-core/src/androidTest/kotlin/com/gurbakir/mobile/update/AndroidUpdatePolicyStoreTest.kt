package com.gurbakir.mobile.update

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidUpdatePolicyStoreTest {
    @Test
    fun existingKeyLayoutReadsIntoNeutralSnapshotAndWritesKeepExactTypes() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "update-policy-legacy-${System.nanoTime()}"
        val preferences = context.getSharedPreferences(name, Context.MODE_PRIVATE)
        try {
            val legacy = mapOf<String, Any>(
                "schema_version" to 1,
                "source" to "firebase-remote-config",
                "fetched_at_epoch_millis" to NOW,
                "expires_at_epoch_millis" to NOW + UPDATE_POLICY_CACHE_TTL_MILLIS,
                "maintenance_message_enabled" to true,
                "checkout_preload_enabled" to true,
                "optional_update_message_enabled" to true,
                "recommended_version_code" to 42L,
                "policy_revision" to 81L,
                "deferred_recommended_version_code" to 42,
                "deferred_at_epoch_millis" to NOW,
                "deferred_until_epoch_millis" to NOW + UPDATE_POLICY_CACHE_TTL_MILLIS
            )
            preferences.edit().apply {
                for ((key, value) in legacy) {
                    when (value) {
                        is Int -> putInt(key, value)
                        is Long -> putLong(key, value)
                        is Boolean -> putBoolean(key, value)
                        is String -> putString(key, value)
                    }
                }
            }.commit()
            val store = AndroidUpdatePolicyStore(context, name)
            val expected = CachedUpdatePolicy(
                UpdatePolicySnapshot(true, true, true, 42, 81L),
                NOW,
                NOW + UPDATE_POLICY_CACHE_TTL_MILLIS
            )
            assertEquals(expected, store.readPolicy(NOW + 1))
            assertTrue(store.isUpdateDeferred(42, NOW + 1))
            preferences.edit().clear().commit()
            assertTrue(store.writePolicy(expected))
            assertTrue(store.deferUpdate(42, NOW, NOW + UPDATE_POLICY_CACHE_TTL_MILLIS))
            assertEquals(legacy, preferences.all)
        } finally {
            context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit()
        }
    }

    @Test
    fun boundedPolicyAndDeferralSurviveStoreRecreationThenExpire() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "update-policy-instrumentation-${System.nanoTime()}"
        try {
            val first = AndroidUpdatePolicyStore(context, name)
            val policy =
                CachedUpdatePolicy(
                    snapshot =
                        UpdatePolicySnapshot(
                            maintenanceMessageEnabled = true,
                            optionalUpdateMessageEnabled = true,
                            recommendedVersionCode = 2,
                            policyRevision = 11L
                        ),
                    fetchedAtEpochMillis = NOW,
                    expiresAtEpochMillis = NOW + UPDATE_POLICY_CACHE_TTL_MILLIS
                )

            assertTrue(first.writePolicy(policy))
            assertTrue(first.deferUpdate(2, NOW, NOW + UPDATE_POLICY_CACHE_TTL_MILLIS))

            val recreated = AndroidUpdatePolicyStore(context, name)

            assertEquals(policy, recreated.readPolicy(NOW + 1L))
            assertTrue(recreated.isUpdateDeferred(2, NOW + 1L))
            assertFalse(recreated.isUpdateDeferred(3, NOW + 1L))
            assertNull(recreated.readPolicy(NOW + UPDATE_POLICY_CACHE_TTL_MILLIS))
            assertFalse(
                recreated.isUpdateDeferred(
                    2,
                    NOW + UPDATE_POLICY_CACHE_TTL_MILLIS
                )
            )
        } finally {
            context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit()
        }
    }

    @Test
    fun malformedOrClockSkewedMetadataFailsClosed() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "update-policy-malformed-${System.nanoTime()}"
        try {
            val store = AndroidUpdatePolicyStore(context, name)
            val future = NOW + UPDATE_POLICY_CLOCK_SKEW_TOLERANCE_MILLIS + 1L
            assertTrue(
                store.writePolicy(
                    CachedUpdatePolicy(
                        snapshot = UpdatePolicySnapshot(optionalUpdateMessageEnabled = true),
                        fetchedAtEpochMillis = future,
                        expiresAtEpochMillis = future + UPDATE_POLICY_CACHE_TTL_MILLIS
                    )
                )
            )

            assertNull(store.readPolicy(NOW))

            context.getSharedPreferences(name, Context.MODE_PRIVATE).edit()
                .putString("schema_version", "wrong-type")
                .commit()

            assertNull(store.readPolicy(NOW))
        } finally {
            context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit()
        }
    }

    private companion object {
        const val NOW = 1_786_612_800_000L
    }
}
