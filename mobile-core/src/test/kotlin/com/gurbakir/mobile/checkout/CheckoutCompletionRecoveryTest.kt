@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.gurbakir.mobile.checkout

import com.gurbakir.checkout.CheckoutEvent
import com.gurbakir.checkout.CheckoutFailure
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CheckoutCompletionRecoveryTest {
    @Test
    fun `cleared completion is known before deferred session refresh and survives owner cancellation`() =
        assertDeferredTerminal(CheckoutEvent.Completed, CheckoutStatus.COMPLETED)

    @Test
    fun `refused cleanup remains known before deferred session refresh and owner cancellation`() =
        assertDeferredTerminal(CheckoutEvent.Completed, CheckoutStatus.CLEANUP_REQUIRED, refuseClear = true)

    @Test
    fun `buyer cancellation is known before deferred session refresh and survives owner cancellation`() =
        assertDeferredTerminal(CheckoutEvent.Cancelled, CheckoutStatus.CANCELLED)

    @Test
    fun `definitive failure is known before deferred session refresh and survives owner cancellation`() =
        assertDeferredTerminal(CheckoutEvent.Failed(CheckoutFailure.FATAL), CheckoutStatus.FAILED)

    @Test
    fun `completion preserves replacement current cart byte for byte`() = runTest {
        val fixture = CheckoutProtocolFixture()
        val owner = fixture.launch(this)
        runCurrent()
        fixture.installReplacement()
        val replacement = fixture.cartStore.cart
        fixture.sdk.emit(CheckoutEvent.Completed)
        owner.join()

        assertEquals(CheckoutStatus.COMPLETED_CURRENT_CART_PRESERVED, fixture.controller.state.value.status)
        assertEquals(replacement, fixture.cartStore.cart)
        assertEquals(fixture.cartB, fixture.cartStore.cart?.id)
        assertEquals(0, fixture.cartStore.clears)
        assertEquals(0, fixture.gateway.merchandiseMutations)
    }

    @Test
    fun `ordinary buyer cancellation never performs completion cleanup`() = runTest {
        val fixture = CheckoutProtocolFixture()
        val owner = fixture.launch(this)
        runCurrent()
        fixture.sdk.emit(CheckoutEvent.Cancelled)
        owner.join()

        assertEquals(CheckoutStatus.CANCELLED, fixture.controller.state.value.status)
        assertFalse(fixture.controller.state.value.busy)
        assertEquals(fixture.cartA, fixture.cartStore.cart?.id)
        assertEquals(0, fixture.cartStore.clears)
        assertEquals(0, fixture.gateway.merchandiseMutations)
    }

    private fun assertDeferredTerminal(event: CheckoutEvent, expected: CheckoutStatus, refuseClear: Boolean = false) =
        runTest {
            val fixture = CheckoutProtocolFixture()
            val owner = fixture.launch(this)
            try {
                runCurrent()
                assertEquals(CheckoutStatus.IN_PROGRESS, fixture.controller.state.value.status)
                assertEquals(listOf(fixture.cartA), fixture.gateway.buyerRebinds)
                if (refuseClear) fixture.cartStore.clearFailure = IllegalStateException("synthetic refused clear")
                fixture.sessionStore.readGate = CompletableDeferred()
                fixture.sdk.emit(event)
                runCurrent()
                assertTrue(fixture.sessionStore.readEntered.isCompleted, "real follow-up session read must be held")

                assertEquals(expected, fixture.controller.state.value.status)
                assertFalse(fixture.controller.state.value.busy)
                if (expected == CheckoutStatus.COMPLETED) {
                    assertNull(fixture.cartStore.cart)
                } else {
                    assertEquals(fixture.cartA, fixture.cartStore.cart?.id)
                }
                val clears = if (event == CheckoutEvent.Completed) 1 else 0
                assertEquals(clears, fixture.cartStore.clears)
                owner.cancelAndJoin()
                assertEquals(expected, fixture.controller.state.value.status)
                assertFalse(fixture.controller.state.value.busy)
                assertEquals(0, fixture.gateway.merchandiseMutations)
            } finally {
                owner.cancelAndJoin()
            }
        }
}
