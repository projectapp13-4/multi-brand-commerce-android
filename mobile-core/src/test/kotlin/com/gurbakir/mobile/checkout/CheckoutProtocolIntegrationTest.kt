@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.gurbakir.mobile.checkout

import com.gurbakir.checkout.CheckoutEvent
import com.gurbakir.checkout.CheckoutFailure
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class CheckoutProtocolIntegrationTest {
    @Test
    fun `actual cancelled collector rejects late callbacks without retaining a busy owner`() = runTest {
        val fixture = CheckoutProtocolFixture()
        val original = fixture.cartStore.cart
        val owner = fixture.launch(this)
        runCurrent()
        assertEquals(CheckoutStatus.IN_PROGRESS, fixture.controller.state.value.status)
        assertEquals(listOf(fixture.cartA), fixture.gateway.buyerRebinds)
        owner.cancelAndJoin()

        val callbackFailure = runCatching { fixture.sdk.emit(CheckoutEvent.Cancelled) }.exceptionOrNull()
        assertNull(callbackFailure, "a disposed producer must harmlessly reject a late SDK callback")
        fixture.sdk.emit(CheckoutEvent.Failed(CheckoutFailure.FATAL))
        fixture.sdk.emit(CheckoutEvent.Completed)
        assertFalse(fixture.controller.state.value.busy)
        assertEquals(CheckoutStatus.INTERRUPTED, fixture.controller.state.value.status)
        assertEquals(original, fixture.cartStore.cart)
        assertEquals(0, fixture.cartStore.clears)
        assertEquals(0, fixture.gateway.merchandiseMutations)
    }

    @Test
    fun `pinned selected recovery can still complete through actual adapter and exact completer`() = runTest {
        val fixture = CheckoutProtocolFixture()
        val owner = fixture.launch(this)
        try {
            runCurrent()
            val mapped = fixture.sdk.selectActualRecovery()
            assertEquals(CheckoutFailure.NETWORK, mapped)
            assertEquals(CheckoutStatus.IN_PROGRESS, fixture.controller.state.value.status)
            fixture.sdk.drainProviderQueue()
            runCurrent()
            assertEquals(CheckoutStatus.IN_PROGRESS, fixture.controller.state.value.status)
            fixture.sdk.emit(CheckoutEvent.Completed)
            owner.join()

            assertEquals(CheckoutStatus.COMPLETED, fixture.controller.state.value.status)
            assertFalse(fixture.controller.state.value.busy)
            assertNull(fixture.cartStore.cart)
            assertEquals(1, fixture.cartStore.clears)
            assertEquals(1, fixture.sdk.presentations)
            assertEquals(0, fixture.gateway.merchandiseMutations)
        } finally {
            owner.cancelAndJoin()
        }
    }

    @Test
    fun `normal completion owns one exact clear despite duplicate terminal callbacks`() = runTest {
        val fixture = CheckoutProtocolFixture()
        val owner = fixture.launch(this)
        runCurrent()
        fixture.sdk.emit(CheckoutEvent.Completed)
        owner.join()
        fixture.sdk.emit(CheckoutEvent.Completed)
        fixture.sdk.emit(CheckoutEvent.Cancelled)

        assertEquals(CheckoutStatus.COMPLETED, fixture.controller.state.value.status)
        assertNull(fixture.cartStore.cart)
        assertEquals(1, fixture.cartStore.clears)
        assertEquals(1, fixture.sdk.presentations)
        assertEquals(0, fixture.gateway.merchandiseMutations)
    }

    @Test
    fun `definitive fatal failure retains cart and rejects invalid later completion`() = runTest {
        val fixture = CheckoutProtocolFixture()
        val original = fixture.cartStore.cart
        val owner = fixture.launch(this)
        runCurrent()
        fixture.sdk.emit(CheckoutEvent.Failed(CheckoutFailure.FATAL))
        owner.join()
        fixture.sdk.emit(CheckoutEvent.Completed)

        assertEquals(CheckoutStatus.FAILED, fixture.controller.state.value.status)
        assertEquals(CheckoutFailureCategory.FATAL, fixture.controller.state.value.failure?.category)
        assertFalse(fixture.controller.state.value.busy)
        assertEquals(original, fixture.cartStore.cart)
        assertEquals(0, fixture.cartStore.clears)
        assertEquals(0, fixture.gateway.merchandiseMutations)
    }
}
