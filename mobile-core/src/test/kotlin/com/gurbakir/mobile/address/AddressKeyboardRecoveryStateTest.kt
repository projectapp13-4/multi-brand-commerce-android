package com.gurbakir.mobile.address

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AddressKeyboardRecoveryStateTest {
    @Test
    fun alreadyCreatedLiveSessionConsumesNewRecoveryOnlyOnceDespiteMultipleConnections() {
        val recovery = AddressKeyboardRecoveryState()
        val session = recovery.beginSession()
        recovery.connectionCreated(session)
        recovery.arm(1)
        val intent = requireNotNull(recovery.intent)

        assertTrue(recovery.consumeReady(intent, session))
        recovery.connectionCreated(session)
        assertFalse(recovery.consumeReady(intent, session))
        assertNull(recovery.intent)
    }

    @Test
    fun intentBeforeFirstSessionWaitsForThatSessionsConnection() {
        val recovery = AddressKeyboardRecoveryState()
        recovery.arm(1)
        val intent = requireNotNull(recovery.intent)
        assertNull(intent.session)

        val session = recovery.beginSession()
        assertSame(session, intent.session)
        assertFalse(recovery.consumeReady(intent, session))
        assertSame(intent, recovery.intent)
        recovery.connectionCreated(session)
        assertTrue(recovery.consumeReady(intent, session))
    }

    @Test
    fun sessionReplacementCancelsRecoveryRatherThanBorrowingOldReadiness() {
        val recovery = AddressKeyboardRecoveryState()
        val previous = recovery.beginSession()
        recovery.connectionCreated(previous)
        recovery.arm(1)
        val intent = requireNotNull(recovery.intent)

        val replacement = recovery.beginSession()
        recovery.connectionCreated(previous)
        recovery.connectionCreated(replacement)
        assertSame(replacement, recovery.session)
        assertNull(recovery.intent)
        assertFalse(recovery.consumeReady(intent, replacement))
        assertFalse(recovery.consumeReady(intent, previous))
    }

    @Test
    fun oldSessionFinallyCannotClearANewlyArmedRecovery() {
        val recovery = AddressKeyboardRecoveryState()
        val previous = recovery.beginSession()
        recovery.connectionCreated(previous)
        recovery.arm(1)
        recovery.clearOwnership()

        val current = recovery.beginSession()
        recovery.arm(2)
        val intent = requireNotNull(recovery.intent)
        recovery.connectionCreated(current)
        recovery.endSession(previous)
        assertSame(current, recovery.session)
        assertSame(intent, recovery.intent)
        assertTrue(recovery.consumeReady(intent, current))
    }

    @Test
    fun losingFieldOwnershipDropsBothUnboundAndConnectedRecovery() {
        val recovery = AddressKeyboardRecoveryState()
        recovery.arm(1)
        recovery.clearOwnership()
        assertNull(recovery.intent)
        assertNull(recovery.session)

        val session = recovery.beginSession()
        recovery.connectionCreated(session)
        recovery.arm(2)
        val intent = requireNotNull(recovery.intent)
        recovery.clearOwnership()
        recovery.connectionCreated(session)
        assertNull(recovery.intent)
        assertNull(recovery.session)
        assertFalse(recovery.consumeReady(intent, session))
    }

    @Test
    fun staleIntentCannotConsumeANewerRequestOnTheSameLiveSession() {
        val recovery = AddressKeyboardRecoveryState()
        val session = recovery.beginSession()
        recovery.connectionCreated(session)
        recovery.arm(1)
        val previous = requireNotNull(recovery.intent)
        recovery.arm(2)
        val current = requireNotNull(recovery.intent)

        assertFalse(recovery.consumeReady(previous, session))
        assertSame(current, recovery.intent)
        assertTrue(recovery.consumeReady(current, session))
    }

    @Test
    fun userEditCancellationPreservesLiveEditorForTheNextRecoveryRequest() {
        val recovery = AddressKeyboardRecoveryState()
        val session = recovery.beginSession()
        recovery.connectionCreated(session)
        recovery.arm(1)
        val previous = requireNotNull(recovery.intent)

        recovery.cancel()
        assertNull(recovery.intent)
        assertSame(session, recovery.session)
        assertTrue(session.connectionCreated)
        assertFalse(recovery.consumeReady(previous, session))
        recovery.arm(2)
        val current = requireNotNull(recovery.intent)
        assertTrue(recovery.consumeReady(current, session))
    }
}
