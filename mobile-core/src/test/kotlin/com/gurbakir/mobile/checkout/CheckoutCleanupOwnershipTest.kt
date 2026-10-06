@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.gurbakir.mobile.checkout

import com.gurbakir.checkout.CheckoutEvent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CheckoutCleanupOwnershipTest {
    @Test
    fun `old terminal refresh finalizer cannot release a newer live cleanup retry`() = runTest {
        val fixture = CheckoutProtocolFixture()
        val originalCart = fixture.cartStore.cart
        val owner = fixture.launch(this)
        var firstRetry: kotlinx.coroutines.Job? = null
        var secondRetry: kotlinx.coroutines.Job? = null
        try {
            runCurrent()
            fixture.cartStore.clearFailure = IllegalStateException("synthetic initial clear refusal")
            fixture.sessionStore.readGate = CompletableDeferred()
            fixture.sdk.emit(CheckoutEvent.Completed)
            runCurrent()
            assertTrue(fixture.sessionStore.readEntered.isCompleted)
            assertEquals(CheckoutStatus.CLEANUP_REQUIRED, fixture.controller.state.value.status)
            assertEquals(1, fixture.cartStore.clears)

            fixture.cartStore.clearFailure = null
            fixture.cartStore.clearGate = CompletableDeferred()
            firstRetry = launch { fixture.controller.retryCleanup() }
            runCurrent()
            assertTrue(owner.isCompleted, "Retry must cancel and join the original terminal refresh")
            assertTrue(fixture.cartStore.clearEntered.isCompleted, "first Retry must reach real checked clear")
            assertEquals(2, fixture.cartStore.clears)
            assertFalse(firstRetry.isCompleted)

            secondRetry = launch { fixture.controller.retryCleanup() }
            runCurrent()
            assertTrue(secondRetry.isCompleted, "second Retry must coalesce while first checked clear is held")
            assertEquals(2, fixture.cartStore.clears)
            assertEquals(originalCart, fixture.cartStore.cart)
            assertEquals(1, fixture.sdk.presentations)
            assertEquals(0, fixture.gateway.merchandiseMutations)

            firstRetry.cancelAndJoin()
            assertEquals(CheckoutStatus.CLEANUP_REQUIRED, fixture.controller.state.value.status)
            fixture.cartStore.clearGate = null
            fixture.sessionStore.readGate = null
            fixture.controller.retryCleanup()
            assertEquals(CheckoutStatus.COMPLETED, fixture.controller.state.value.status)
            assertNull(fixture.cartStore.cart)
            assertEquals(3, fixture.cartStore.clears)
            assertEquals(1, fixture.sdk.presentations)
        } finally {
            secondRetry?.cancelAndJoin()
            firstRetry?.cancelAndJoin()
            owner.cancelAndJoin()
        }
    }

    @Test
    fun `actual cleanup cancellation preserves completion and captured ID for exact retry`() = runTest {
        val fixture = CheckoutProtocolFixture()
        val original = CancellationException("synthetic exact cleanup cancellation")
        fixture.cartStore.clearFailure = original
        val owner = async {
            runCatching {
                fixture.controller.startWithPresentation { fixture.sdk.capture(fixture.adapter, it) }
            }.exceptionOrNull()
        }
        runCurrent()
        fixture.sdk.emit(CheckoutEvent.Completed)
        assertSame(original, owner.await())
        assertEquals(CheckoutStatus.CLEANUP_REQUIRED, fixture.controller.state.value.status)
        assertFalse(fixture.controller.state.value.busy)
        assertEquals(fixture.cartA, fixture.cartStore.cart?.id)
        fixture.cartStore.clearFailure = null
        fixture.controller.retryCleanup()
        assertEquals(CheckoutStatus.COMPLETED, fixture.controller.state.value.status)
        assertNull(fixture.cartStore.cart)
        assertEquals(2, fixture.cartStore.clears)
        assertEquals(1, fixture.sdk.presentations)
        assertEquals(1, fixture.sdk.disposals)
    }

    @Test
    fun `live exact cleanup coalesces retry and cancellation releases only its attempt`() = runTest {
        val fixture = CheckoutProtocolFixture()
        fixture.cartStore.clearGate = CompletableDeferred()
        val owner = fixture.launch(this)
        runCurrent()
        fixture.sdk.emit(CheckoutEvent.Completed)
        runCurrent()
        assertTrue(fixture.cartStore.clearEntered.isCompleted)
        assertEquals(CheckoutStatus.CLEANUP_REQUIRED, fixture.controller.state.value.status)
        val retry = launch { fixture.controller.retryCleanup() }
        runCurrent()
        assertTrue(retry.isCompleted)
        assertEquals(1, fixture.cartStore.clears)
        owner.cancelAndJoin()
        assertEquals(CheckoutStatus.CLEANUP_REQUIRED, fixture.controller.state.value.status)
        fixture.cartStore.clearGate = null
        fixture.controller.retryCleanup()
        assertEquals(CheckoutStatus.COMPLETED, fixture.controller.state.value.status)
        assertNull(fixture.cartStore.cart)
        assertEquals(2, fixture.cartStore.clears)
        assertEquals(1, fixture.sdk.presentations)
    }

    @Test
    fun `failed clear receipt after applying is retried from accepted completion only`() = runTest {
        val fixture = CheckoutProtocolFixture()
        fixture.cartStore.clearAppliesBeforeFailure = true
        fixture.cartStore.clearFailure = IllegalStateException("synthetic receipt refusal")
        val owner = fixture.launch(this)
        runCurrent()
        fixture.sdk.emit(CheckoutEvent.Completed)
        owner.join()
        assertNull(fixture.cartStore.cart)
        assertEquals(CheckoutStatus.CLEANUP_REQUIRED, fixture.controller.state.value.status)
        fixture.cartStore.clearFailure = null
        fixture.controller.retryCleanup()
        assertEquals(CheckoutStatus.COMPLETED, fixture.controller.state.value.status)
        assertEquals(1, fixture.cartStore.clears)
        assertEquals(1, fixture.sdk.presentations)
    }

    @Test
    fun `cancellation during actual leased preparation releases busy owner without SDK or cleanup`() = runTest {
        val fixture = CheckoutProtocolFixture()
        val originalCart = fixture.cartStore.cart
        fixture.sessionStore.readGate = CompletableDeferred()
        val owner = fixture.launch(this)
        runCurrent()
        assertTrue(fixture.sessionStore.readEntered.isCompleted)
        assertEquals(CheckoutStatus.PREPARING, fixture.controller.state.value.status)
        owner.cancelAndJoin()
        assertEquals(CheckoutStatus.INTERRUPTED, fixture.controller.state.value.status)
        assertFalse(fixture.controller.state.value.busy)
        assertEquals(originalCart, fixture.cartStore.cart)
        assertEquals(0, fixture.sdk.presentations)
        assertEquals(0, fixture.cartStore.clears)
        fixture.sessionStore.readGate = null
        fixture.repository.refresh()
        assertEquals(originalCart, fixture.cartStore.cart)
    }

    @Test
    fun `old terminal finalizer cannot wait for or interrupt a newer preparation owner`() = runTest {
        val fixture = CheckoutProtocolFixture()
        val first = fixture.launch(this)
        runCurrent()
        val release = CompletableDeferred<Unit>()
        fixture.sessionStore.readGate = release
        fixture.sdk.emit(CheckoutEvent.Cancelled)
        runCurrent()
        assertEquals(CheckoutStatus.CANCELLED, fixture.controller.state.value.status)
        val newer = fixture.launch(this)
        runCurrent()
        assertEquals(CheckoutStatus.PREPARING, fixture.controller.state.value.status)
        first.cancelAndJoin()
        assertEquals(CheckoutStatus.PREPARING, fixture.controller.state.value.status)
        assertTrue(fixture.controller.state.value.busy)
        release.complete(Unit)
        runCurrent()
        assertEquals(CheckoutStatus.IN_PROGRESS, fixture.controller.state.value.status)
        assertEquals(2, fixture.sdk.presentations)
        newer.cancelAndJoin()
        assertEquals(CheckoutStatus.INTERRUPTED, fixture.controller.state.value.status)
        assertEquals(0, fixture.cartStore.clears)
        assertEquals(0, fixture.gateway.merchandiseMutations)
    }
}
