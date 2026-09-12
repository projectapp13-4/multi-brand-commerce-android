package com.gurbakir.mobile.di

import com.gurbakir.firebase.PushRegistrationCoordinator
import com.gurbakir.firebase.PushRegistrationResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class ProofModuleTest {
    @Test
    fun `unconfigured push selection rejects before invoking Firebase factory`() {
        var invocations = 0

        val failure = assertThrows(IllegalStateException::class.java) {
            createConfiguredPushRegistrationCoordinator(firebaseConfigured = false) {
                invocations += 1
                FakePushRegistrationCoordinator
            }
        }

        assertEquals("Firebase proof is unavailable for an unconfigured build.", failure.message)
        assertEquals(0, invocations)
    }

    @Test
    fun `configured push selection invokes Firebase factory exactly once`() {
        var invocations = 0

        val selected = createConfiguredPushRegistrationCoordinator(firebaseConfigured = true) {
            invocations += 1
            FakePushRegistrationCoordinator
        }

        assertSame(FakePushRegistrationCoordinator, selected)
        assertEquals(1, invocations)
    }

    private data object FakePushRegistrationCoordinator : PushRegistrationCoordinator {
        override fun hasStoredConsent(): Boolean = false

        override suspend fun registerAfterConsent(): PushRegistrationResult = PushRegistrationResult.Failed

        override suspend fun unregister(): Boolean = false
    }
}
