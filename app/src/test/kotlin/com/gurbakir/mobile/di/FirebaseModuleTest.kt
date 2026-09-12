package com.gurbakir.mobile.di

import com.gurbakir.firebase.ApprovedRemoteFlag
import com.gurbakir.firebase.LocalDefaultFeatureFlags
import com.gurbakir.firebase.RemoteConfigResult
import com.gurbakir.firebase.RemoteFeatureFlags
import com.gurbakir.firebase.RemotePolicySnapshot
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class FirebaseModuleTest {
    @Test
    fun `unconfigured selection does not invoke Firebase factory`() {
        var invocations = 0

        val selected = selectRemoteFeatureFlags(firebaseConfigured = false) {
            invocations += 1
            error("Firebase factory must remain lazy")
        }

        assertEquals(0, invocations)
        assertInstanceOf(LocalDefaultFeatureFlags::class.java, selected)
    }

    @Test
    fun `configured selection invokes Firebase factory exactly once`() {
        var invocations = 0
        val expected = FakeRemoteFeatureFlags()

        val selected = selectRemoteFeatureFlags(firebaseConfigured = true) {
            invocations += 1
            expected
        }

        assertEquals(1, invocations)
        assertSame(expected, selected)
    }

    private class FakeRemoteFeatureFlags : RemoteFeatureFlags {
        override suspend fun refresh(): RemoteConfigResult = RemoteConfigResult.LocalDefaults

        override fun boolean(key: ApprovedRemoteFlag): Boolean = false

        override fun policySnapshot(): RemotePolicySnapshot = RemotePolicySnapshot.SAFE_DEFAULTS
    }
}
