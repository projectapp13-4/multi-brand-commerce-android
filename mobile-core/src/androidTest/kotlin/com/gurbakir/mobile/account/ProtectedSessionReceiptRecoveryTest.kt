package com.gurbakir.mobile.account

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.gurbakir.mobile.account.ProtectedSessionReceiptFixture.ReceiptMode
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Healthy storage/caller controls; the private-gateway behavioral RED lives in the JVM join. */
@RunWith(AndroidJUnit4::class)
class ProtectedSessionReceiptRecoveryTest {
    @Test
    fun malformedCiphertextWithRefusedRemovalRemainsUncertain() = runBlocking {
        assertCorruptionRecovery(ReceiptMode.REFUSE_BEFORE_APPLY)
    }

    @Test
    fun malformedCiphertextRemovedBeforeFailedReceiptRemainsUncertain() = runBlocking {
        assertCorruptionRecovery(ReceiptMode.APPLY_THEN_REFUSE)
    }

    @Test
    fun logoutRefusedBeforeApplyRetainsSessionUntilCheckedRecovery() = runBlocking {
        assertLogoutRecovery(ReceiptMode.REFUSE_BEFORE_APPLY)
    }

    @Test
    fun logoutAppliedBeforeFailedReceiptDoesNotClaimConfirmedRemoval() = runBlocking {
        assertLogoutRecovery(ReceiptMode.APPLY_THEN_REFUSE)
    }

    @Test
    fun renewalWriteRefusedBeforeApplyCannotAuthorizeTheRotatedSession() = runBlocking {
        assertRenewalRecovery(ReceiptMode.REFUSE_BEFORE_APPLY)
    }

    @Test
    fun renewalWriteAppliedBeforeFailedReceiptRequiresHealthyRecoveryRead() = runBlocking {
        assertRenewalRecovery(ReceiptMode.APPLY_THEN_REFUSE)
    }

    private suspend fun assertCorruptionRecovery(mode: ReceiptMode) = withFixture { fixture ->
        fixture.seed()
        assertTrue(fixture.corruptCiphertext())
        fixture.receiptMode = mode
        assertUncertain(fixture.controller.restore())
        assertEquals(1, fixture.rejectedCommits)
        assertEquals(mode == ReceiptMode.REFUSE_BEFORE_APPLY, fixture.ciphertextPresent)
        assertEquals(0, fixture.identityCalls.get())
        assertEquals(0, fixture.renewalCalls.get())
        fixture.receiptMode = ReceiptMode.HEALTHY
        assertTrue(fixture.controller.restore() is AccountResult.SignedOut)
        assertNull(fixture.healthyStore.read())
        assertFalse(fixture.ciphertextPresent)
    }

    private suspend fun assertLogoutRecovery(mode: ReceiptMode) = withFixture { fixture ->
        fixture.seed()
        fixture.receiptMode = mode
        assertUncertain(fixture.controller.logout())
        assertEquals(1, fixture.rejectedCommits)
        assertEquals(0, fixture.logoutCalls.get())
        assertEquals(0, fixture.identityCalls.get())
        assertEquals(mode == ReceiptMode.REFUSE_BEFORE_APPLY, fixture.healthyStore.read() != null)
        fixture.receiptMode = ReceiptMode.HEALTHY
        assertTrue(fixture.controller.logout() is AccountResult.SignedOut)
        assertNull(fixture.healthyStore.read())
    }

    private suspend fun assertRenewalRecovery(mode: ReceiptMode) = withFixture { fixture ->
        fixture.seed(expired = true)
        fixture.receiptMode = mode
        assertUncertain(fixture.controller.restore())
        assertEquals(1, fixture.rejectedCommits)
        assertEquals(1, fixture.renewalCalls.get())
        assertEquals(0, fixture.identityCalls.get())
        assertTrue(
            if (mode == ReceiptMode.REFUSE_BEFORE_APPLY) fixture.hasOriginalToken() else fixture.hasRenewedToken()
        )
        fixture.receiptMode = ReceiptMode.HEALTHY
        assertTrue(fixture.controller.restore() is AccountResult.Authenticated)
        assertEquals(1, fixture.identityCalls.get())
        assertTrue(fixture.hasRenewedToken())
    }

    private fun assertUncertain(result: AccountResult) {
        assertTrue("Failed receipt must reach typed retained recovery", result is AccountResult.Failed)
        val failure = result as AccountResult.Failed
        assertEquals(AccountFailure.SECURE_STORAGE, failure.reason)
        assertTrue(failure.retryable)
        assertTrue(failure.sessionRetained)
    }

    private suspend fun withFixture(action: suspend (ProtectedSessionReceiptFixture) -> Unit) {
        val fixture = ProtectedSessionReceiptFixture(InstrumentationRegistry.getInstrumentation().targetContext)
        try {
            action(fixture)
        } finally {
            fixture.close()
        }
    }
}
