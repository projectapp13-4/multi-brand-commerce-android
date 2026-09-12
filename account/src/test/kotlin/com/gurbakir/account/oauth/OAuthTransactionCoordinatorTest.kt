package com.gurbakir.account.oauth

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class OAuthTransactionCoordinatorTest {
    @Test
    fun `pkce uses S256 compatible lengths and unique material`() {
        val generator = PkceGenerator()
        val first = generator.generate()
        val second = generator.generate()

        assertTrue(first.verifier.length in 43..128)
        assertEquals(43, first.challenge.length)
        assertNotEquals(first, second)
    }

    @Test
    fun `state is one time and mismatches are rejected`() {
        val coordinator = OAuthTransactionCoordinator()
        val transaction = coordinator.begin(testDiscovery())

        assertEquals(OAuthCallbackValidation.StateMismatch, coordinator.validateAndConsume("wrong"))
        assertEquals(OAuthCallbackValidation.MissingTransaction, coordinator.validateAndConsume(transaction.state))
    }

    @Test
    fun `accepted callback returns the one time transaction without rendering its values`() {
        val coordinator = OAuthTransactionCoordinator()
        val transaction = coordinator.begin(testDiscovery())

        val accepted = coordinator.validateAndConsume(transaction.state)

        assertTrue(accepted is OAuthCallbackValidation.Accepted)
        assertEquals(transaction, (accepted as OAuthCallbackValidation.Accepted).transaction)
        assertFalse(accepted.toString().contains(transaction.state))
    }

    @Test
    fun `expired state is rejected`() {
        val start = Instant.parse("2026-07-19T00:00:00Z")
        val clock = MutableClock(start)
        val coordinator = OAuthTransactionCoordinator(clock = clock)
        val transaction = coordinator.begin(testDiscovery())
        clock.now = start.plus(Duration.ofMinutes(11))

        assertEquals(
            OAuthCallbackValidation.Expired,
            coordinator.validateAndConsume(transaction.state)
        )
    }

    @Test
    fun `oauth values redact their string representation`() {
        val transaction = OAuthTransactionCoordinator().begin(testDiscovery())

        assertFalse(transaction.toString().contains(transaction.state))
        assertFalse(transaction.toString().contains(transaction.nonce))
        assertFalse(transaction.toString().contains(transaction.pkce.verifier))
        assertFalse(transaction.pkce.toString().contains(transaction.pkce.verifier))
        assertTrue(transaction.toString().contains("<redacted>"))
    }

    private class MutableClock(var now: Instant) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC

        override fun withZone(zone: ZoneId): Clock = this

        override fun instant(): Instant = now
    }
}
