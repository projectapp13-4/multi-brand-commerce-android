package com.gurbakir.mobile.update

import java.util.concurrent.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class UpdatePolicyControllerTest {
    @Test
    fun `gateway exception uses identical cache and safe default fallback`() = runTest {
        val cached = CachedUpdatePolicy(
            UpdatePolicySnapshot(maintenanceMessageEnabled = true),
            NOW,
            NOW + UPDATE_POLICY_CACHE_TTL_MILLIS
        )
        val store = FakeUpdatePolicyStore(cached)
        val gateway = UpdatePolicyRemoteGateway { throw IllegalStateException("synthetic provider failure") }
        assertEquals(UpdatePolicySource.UNEXPIRED_CACHE, controller(gateway, store).refreshPolicy().source)
        assertTrue(controller(gateway, store).refreshPolicy().maintenanceMessageEnabled)
        store.policy = null
        assertEquals(UpdatePolicyPresentation(), controller(gateway, store).refreshPolicy())
    }

    @Test
    fun `cancellation propagates without replacing cache`() = runTest {
        val cancellation = CancellationException("synthetic cancellation")
        val cached = CachedUpdatePolicy(
            UpdatePolicySnapshot(maintenanceMessageEnabled = true),
            NOW,
            NOW + UPDATE_POLICY_CACHE_TTL_MILLIS
        )
        val store = FakeUpdatePolicyStore(cached)
        val gateway = UpdatePolicyRemoteGateway { throw cancellation }
        val caught = try {
            controller(gateway, store).refreshPolicy()
            null
        } catch (failure: CancellationException) {
            failure
        }
        assertSame(cancellation, caught)
        assertEquals(cached, store.policy)
    }

    @Test
    fun `valid fetched policy remains visible when persistence throws or returns false`() = runTest {
        for (throws in listOf(false, true)) {
            val store = object : UpdatePolicyStore by FakeUpdatePolicyStore() {
                override fun writePolicy(policy: CachedUpdatePolicy): Boolean {
                    if (throws) error("synthetic storage failure")
                    return false
                }
            }
            val gateway = UpdatePolicyRemoteGateway {
                UpdatePolicyRefreshResult.Fetched(UpdatePolicySnapshot(maintenanceMessageEnabled = true), NOW)
            }
            val result = controller(gateway, store).refreshPolicy()
            assertEquals(UpdatePolicySource.REMOTE_CONFIG, result.source)
            assertTrue(result.maintenanceMessageEnabled)
        }
    }

    @Test
    fun `fresh remote policy produces only non-blocking maintenance and optional update state`() = runTest {
        val remote =
            FakeRemoteGateway(
                snapshot =
                    UpdatePolicySnapshot(
                        maintenanceMessageEnabled = true,
                        optionalUpdateMessageEnabled = true,
                        recommendedVersionCode = 2,
                        policyRevision = 8L
                    )
            )
        val store = FakeUpdatePolicyStore()
        val controller = controller(remote, store)

        val result = controller.refreshPolicy()

        assertEquals(UpdatePolicySource.REMOTE_CONFIG, result.source)
        assertTrue(result.maintenanceMessageEnabled)
        assertEquals(2, result.optionalUpdateVersionCode)
        assertEquals(8L, result.policyRevision)
        assertEquals(remote.snapshot, store.policy?.snapshot)
    }

    @Test
    fun `same or older installed version never produces an update notice`() = runTest {
        val remote =
            FakeRemoteGateway(
                snapshot =
                    UpdatePolicySnapshot(
                        optionalUpdateMessageEnabled = true,
                        recommendedVersionCode = 1
                    )
            )
        val controller = controller(remote, FakeUpdatePolicyStore())

        assertNull(controller.refreshPolicy().optionalUpdateVersionCode)

        remote.snapshot = remote.snapshot.copy(recommendedVersionCode = 0)

        assertNull(controller.refreshPolicy().optionalUpdateVersionCode)
    }

    @Test
    fun `offline refresh uses only an unexpired bounded cache`() = runTest {
        val cached =
            CachedUpdatePolicy(
                snapshot =
                    UpdatePolicySnapshot(
                        optionalUpdateMessageEnabled = true,
                        recommendedVersionCode = 3,
                        policyRevision = 4L
                    ),
                fetchedAtEpochMillis = NOW - 1_000L,
                expiresAtEpochMillis = NOW - 1_000L + UPDATE_POLICY_CACHE_TTL_MILLIS
            )
        val store = FakeUpdatePolicyStore(policy = cached)
        val remote = FakeRemoteGateway(result = UpdatePolicyRefreshResult.LocalDefaults)

        val result = controller(remote, store).refreshPolicy()

        assertEquals(UpdatePolicySource.UNEXPIRED_CACHE, result.source)
        assertEquals(3, result.optionalUpdateVersionCode)

        store.policy = cached.copy(expiresAtEpochMillis = NOW)

        assertEquals(UpdatePolicyPresentation(), controller(remote, store).refreshPolicy())
    }

    @Test
    fun `stale or future fetch metadata cannot replace a valid cache`() = runTest {
        val cached =
            CachedUpdatePolicy(
                snapshot = UpdatePolicySnapshot(maintenanceMessageEnabled = true),
                fetchedAtEpochMillis = NOW - 1_000L,
                expiresAtEpochMillis = NOW - 1_000L + UPDATE_POLICY_CACHE_TTL_MILLIS
            )
        val store = FakeUpdatePolicyStore(cached)
        val remote =
            FakeRemoteGateway(
                result = UpdatePolicyRefreshResult.Fetched(
                    UpdatePolicySnapshot(),
                    NOW - UPDATE_POLICY_CACHE_TTL_MILLIS
                ),
                snapshot =
                    UpdatePolicySnapshot(
                        optionalUpdateMessageEnabled = true,
                        recommendedVersionCode = 9
                    )
            )

        assertTrue(controller(remote, store).refreshPolicy().maintenanceMessageEnabled)

        remote.result =
            UpdatePolicyRefreshResult.Fetched(
                UpdatePolicySnapshot(),
                NOW + UPDATE_POLICY_CLOCK_SKEW_TOLERANCE_MILLIS + 1L
            )

        assertTrue(controller(remote, store).refreshPolicy().maintenanceMessageEnabled)
        assertEquals(cached, store.policy)
    }

    @Test
    fun `remote false values overwrite a cached notice as the rollback path`() = runTest {
        val store =
            FakeUpdatePolicyStore(
                policy =
                    CachedUpdatePolicy(
                        snapshot =
                            UpdatePolicySnapshot(
                                maintenanceMessageEnabled = true,
                                optionalUpdateMessageEnabled = true,
                                recommendedVersionCode = 4,
                                policyRevision = 1L
                            ),
                        fetchedAtEpochMillis = NOW - 1_000L,
                        expiresAtEpochMillis = NOW - 1_000L + UPDATE_POLICY_CACHE_TTL_MILLIS
                    )
            )
        val remote =
            FakeRemoteGateway(
                snapshot = UpdatePolicySnapshot(policyRevision = 2L)
            )

        val result = controller(remote, store).refreshPolicy()

        assertFalse(result.maintenanceMessageEnabled)
        assertNull(result.optionalUpdateVersionCode)
        assertEquals(UpdatePolicySnapshot(policyRevision = 2L), store.policy?.snapshot)
    }

    @Test
    fun `deferral survives refresh for the same version but a higher recommendation reappears`() = runTest {
        val remote =
            FakeRemoteGateway(
                snapshot =
                    UpdatePolicySnapshot(
                        optionalUpdateMessageEnabled = true,
                        recommendedVersionCode = 2
                    )
            )
        val store = FakeUpdatePolicyStore()
        val controller = controller(remote, store)
        assertEquals(2, controller.refreshPolicy().optionalUpdateVersionCode)

        assertTrue(controller.deferUpdate(2))
        assertNull(controller.refreshPolicy().optionalUpdateVersionCode)

        remote.snapshot = remote.snapshot.copy(recommendedVersionCode = 3)

        assertEquals(3, controller.refreshPolicy().optionalUpdateVersionCode)
    }

    private fun controller(remote: UpdatePolicyRemoteGateway, store: UpdatePolicyStore): DefaultUpdatePolicyController =
        DefaultUpdatePolicyController(
            remoteGateway = remote,
            store = store,
            currentVersionCode = CurrentAppVersionCode(1),
            clock = UpdatePolicyClock { NOW }
        )

    private class FakeRemoteGateway(
        var result: UpdatePolicyRefreshResult = UpdatePolicyRefreshResult.Fetched(UpdatePolicySnapshot(), NOW),
        var snapshot: UpdatePolicySnapshot = UpdatePolicySnapshot()
    ) : UpdatePolicyRemoteGateway {
        override suspend fun refresh(): UpdatePolicyRefreshResult = when (val current = result) {
            is UpdatePolicyRefreshResult.Fetched -> current.copy(snapshot = snapshot)
            UpdatePolicyRefreshResult.LocalDefaults -> current
        }
    }

    private class FakeUpdatePolicyStore(var policy: CachedUpdatePolicy? = null) : UpdatePolicyStore {
        private var deferredVersion: Int? = null
        private var deferredUntil: Long = 0L

        override fun readPolicy(nowEpochMillis: Long): CachedUpdatePolicy? =
            policy?.takeIf { nowEpochMillis < it.expiresAtEpochMillis }

        override fun writePolicy(policy: CachedUpdatePolicy): Boolean {
            this.policy = policy
            return true
        }

        override fun isUpdateDeferred(recommendedVersionCode: Int, nowEpochMillis: Long): Boolean =
            deferredVersion == recommendedVersionCode && nowEpochMillis < deferredUntil

        override fun deferUpdate(recommendedVersionCode: Int, nowEpochMillis: Long, untilEpochMillis: Long): Boolean {
            deferredVersion = recommendedVersionCode
            deferredUntil = untilEpochMillis
            return true
        }
    }

    private companion object {
        const val NOW = 1_786_612_800_000L
    }
}
