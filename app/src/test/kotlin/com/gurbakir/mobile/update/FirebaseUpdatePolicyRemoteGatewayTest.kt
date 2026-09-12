package com.gurbakir.mobile.update

import com.gurbakir.firebase.ApprovedRemoteFlag
import com.gurbakir.firebase.RemoteConfigResult
import com.gurbakir.firebase.RemoteFeatureFlags
import com.gurbakir.firebase.RemotePolicySnapshot
import java.util.concurrent.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class FirebaseUpdatePolicyRemoteGatewayTest {
    @Test
    fun `fetched maps every snapshot field and timestamp independent of activation flag`() = runTest {
        for (activated in listOf(false, true)) {
            val flags = Flags(RemoteConfigResult.Fetched(activated, 123456789L))
            assertEquals(
                UpdatePolicyRefreshResult.Fetched(UpdatePolicySnapshot(true, true, true, 42, 81L), 123456789L),
                FirebaseUpdatePolicyRemoteGateway(flags).refresh()
            )
        }
    }

    @Test
    fun `local defaults do not read a stale provider snapshot`() = runTest {
        val flags = object : RemoteFeatureFlags by Flags(RemoteConfigResult.LocalDefaults) {
            override fun policySnapshot(): RemotePolicySnapshot = error("must not read stale snapshot")
        }
        assertEquals(UpdatePolicyRefreshResult.LocalDefaults, FirebaseUpdatePolicyRemoteGateway(flags).refresh())
    }

    @Test
    fun `provider cancellation propagates unchanged`() = runTest {
        val cancellation = CancellationException("synthetic cancellation")
        val flags = object : RemoteFeatureFlags by Flags(RemoteConfigResult.LocalDefaults) {
            override suspend fun refresh(): RemoteConfigResult = throw cancellation
        }
        val caught = try {
            FirebaseUpdatePolicyRemoteGateway(flags).refresh()
            null
        } catch (failure: CancellationException) {
            failure
        }
        assertSame(cancellation, caught)
    }

    private class Flags(private val result: RemoteConfigResult) : RemoteFeatureFlags {
        override suspend fun refresh(): RemoteConfigResult = result
        override fun boolean(key: ApprovedRemoteFlag): Boolean = error("must map the typed snapshot")
        override fun policySnapshot(): RemotePolicySnapshot = RemotePolicySnapshot(true, true, true, 42, 81L)
    }
}
