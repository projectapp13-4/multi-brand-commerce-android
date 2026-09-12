package com.gurbakir.firebase

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FirebaseRemoteFeatureFlagsTest {
    @Test
    fun `factory construction failure returns local defaults`() = runTest {
        val flags = createFirebaseRemoteFeatureFlags { error("no default Firebase app") }

        assertInstanceOf(LocalDefaultFeatureFlags::class.java, flags)
        assertEquals(RemoteConfigResult.LocalDefaults, flags.refresh())
    }

    @Test
    fun `fetch reads only the approved typed policy keys with explicit cadence`() = runTest {
        val client =
            FakeRemoteConfigClient(
                values =
                    mapOf(
                        "maintenance_message_enabled" to "true",
                        "checkout_preload_enabled" to "false",
                        "optional_update_message_enabled" to "true",
                        "mobile_policy_schema_version" to "1",
                        "mobile_policy_revision" to "7",
                        "recommended_version_code" to "12",
                        "unapproved_endpoint_override" to "https://attacker.invalid"
                    )
            )
        val flags = FirebaseRemoteFeatureFlags(client)

        assertEquals(RemoteConfigResult.Fetched(true, NOW), flags.refresh())
        assertEquals(REMOTE_CONFIG_MINIMUM_FETCH_INTERVAL_SECONDS, client.minimumFetchIntervalSeconds)
        assertEquals(REMOTE_CONFIG_FETCH_TIMEOUT_SECONDS, client.fetchTimeoutSeconds)
        assertEquals(
            setOf(
                "maintenance_message_enabled",
                "checkout_preload_enabled",
                "optional_update_message_enabled",
                "mobile_policy_schema_version",
                "mobile_policy_revision",
                "recommended_version_code"
            ),
            client.installedDefaults.keys
        )
        assertEquals(client.installedDefaults.keys, client.readKeys)
        assertTrue(flags.boolean(ApprovedRemoteFlag.MAINTENANCE_MESSAGE_ENABLED))
        assertFalse(flags.boolean(ApprovedRemoteFlag.CHECKOUT_PRELOAD_ENABLED))
        assertTrue(flags.boolean(ApprovedRemoteFlag.OPTIONAL_UPDATE_MESSAGE_ENABLED))
        assertEquals(
            RemotePolicySnapshot(
                maintenanceMessageEnabled = true,
                checkoutPreloadEnabled = false,
                optionalUpdateMessageEnabled = true,
                recommendedVersionCode = 12,
                policyRevision = 7L
            ),
            flags.policySnapshot()
        )
    }

    @Test
    fun `malformed fields fall back individually and unknown schema fails closed`() = runTest {
        val malformed =
            FirebaseRemoteFeatureFlags(
                FakeRemoteConfigClient(
                    values =
                        mapOf(
                            "maintenance_message_enabled" to "1",
                            "checkout_preload_enabled" to "false",
                            "optional_update_message_enabled" to "TRUE",
                            "mobile_policy_schema_version" to "1",
                            "mobile_policy_revision" to "-1",
                            "recommended_version_code" to "not-a-number"
                        )
                )
            )

        malformed.refresh()

        assertEquals(RemotePolicySnapshot.SAFE_DEFAULTS, malformed.policySnapshot())

        val incompatible =
            FirebaseRemoteFeatureFlags(
                FakeRemoteConfigClient(
                    values =
                        mapOf(
                            "mobile_policy_schema_version" to "2",
                            "maintenance_message_enabled" to "true"
                        )
                )
            )

        incompatible.refresh()

        assertEquals(RemotePolicySnapshot.SAFE_DEFAULTS, incompatible.policySnapshot())
    }

    @Test
    fun `failure discards prior activated policy and fails closed to local defaults`() = runTest {
        val client =
            FakeRemoteConfigClient(
                values =
                    mapOf(
                        "maintenance_message_enabled" to "true",
                        "optional_update_message_enabled" to "true",
                        "mobile_policy_schema_version" to "1",
                        "recommended_version_code" to "2"
                    )
            )
        val flags = FirebaseRemoteFeatureFlags(client)
        flags.refresh()
        assertTrue(flags.boolean(ApprovedRemoteFlag.MAINTENANCE_MESSAGE_ENABLED))
        client.failFetch = true

        assertEquals(RemoteConfigResult.LocalDefaults, flags.refresh())
        ApprovedRemoteFlag.entries.forEach { flag -> assertFalse(flags.boolean(flag)) }
        assertEquals(RemotePolicySnapshot.SAFE_DEFAULTS, flags.policySnapshot())
    }

    private class FakeRemoteConfigClient(private val values: Map<String, String>, var failFetch: Boolean = false) :
        RemoteConfigClient {
        var installedDefaults: Map<String, Any> = emptyMap()
        var minimumFetchIntervalSeconds: Long? = null
        var fetchTimeoutSeconds: Long? = null
        val readKeys = linkedSetOf<String>()

        override suspend fun configure(minimumFetchIntervalSeconds: Long, fetchTimeoutSeconds: Long) {
            this.minimumFetchIntervalSeconds = minimumFetchIntervalSeconds
            this.fetchTimeoutSeconds = fetchTimeoutSeconds
        }

        override suspend fun installDefaults(defaults: Map<String, Any>) {
            installedDefaults = defaults
        }

        override suspend fun fetchAndActivate(): Boolean {
            if (failFetch) error("synthetic failure")
            return true
        }

        override fun string(key: String): String {
            readKeys += key
            return values[key] ?: installedDefaults[key].toString()
        }

        override fun lastSuccessfulFetchEpochMillis(): Long = NOW
    }

    private companion object {
        const val NOW = 1_786_612_800_000L
    }
}
