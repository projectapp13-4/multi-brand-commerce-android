package com.gurbakir.mobile.di

import com.gurbakir.firebase.ApprovedRemoteFlag
import com.gurbakir.firebase.RemoteConfigResult
import com.gurbakir.firebase.RemoteFeatureFlags
import com.gurbakir.firebase.RemotePolicySnapshot
import com.gurbakir.mobile.update.UpdatePolicyRefreshResult
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class UpdatePolicyModuleTest {
    @Test
    fun `unconfigured selection returns local defaults without resolving Firebase`() = runTest {
        var invocations = 0
        val gateway = selectUpdatePolicyRemoteGateway(firebaseConfigured = false) {
            invocations += 1
            error("Firebase provider must remain lazy")
        }

        assertEquals(UpdatePolicyRefreshResult.LocalDefaults, gateway.refresh())
        assertEquals(0, invocations)
    }

    @Test
    fun `configured selection resolves Firebase exactly once`() = runTest {
        var invocations = 0
        val gateway = selectUpdatePolicyRemoteGateway(firebaseConfigured = true) {
            invocations += 1
            FakeRemoteFeatureFlags()
        }

        assertEquals(UpdatePolicyRefreshResult.LocalDefaults, gateway.refresh())
        assertEquals(1, invocations)
    }

    private class FakeRemoteFeatureFlags : RemoteFeatureFlags {
        override suspend fun refresh(): RemoteConfigResult = RemoteConfigResult.LocalDefaults

        override fun boolean(key: ApprovedRemoteFlag): Boolean = false

        override fun policySnapshot(): RemotePolicySnapshot = RemotePolicySnapshot.SAFE_DEFAULTS
    }
}
