package com.gurbakir.checkout

import java.net.URI
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CheckoutAdapterSessionTest {
    private val target = URI("https://gurbakir.com/cart/c/synthetic")

    @Test
    fun `synchronous terminal event is retained by its presentation and closes the stream`() = runTest {
        val adapter = ShopifyCheckoutAdapter(checkoutUrlPolicy = CheckoutUrlPolicy(setOf("gurbakir.com")))

        val result =
            adapter.presentWithOperation(target) { eventSink ->
                eventSink(CheckoutEvent.Cancelled)
                true
            }
        val presented = assertInstanceOf(CheckoutResult.Presented::class.java, result)
        assertEquals(listOf(CheckoutEvent.Cancelled), presented.events.toList())
    }

    @Test
    fun `sequential presentations cannot share a callback session identity`() = runTest {
        val adapter = ShopifyCheckoutAdapter(checkoutUrlPolicy = CheckoutUrlPolicy(setOf("gurbakir.com")))

        val first = adapter.presentWithOperation(target) { true } as CheckoutResult.Presented
        val second = adapter.presentWithOperation(target) { true } as CheckoutResult.Presented

        assertNotEquals(first.sessionId, second.sessionId)
    }

    @Test
    fun `concurrent presentation owners receive only their own terminal event`() = runTest {
        val adapter = ShopifyCheckoutAdapter(checkoutUrlPolicy = CheckoutUrlPolicy(setOf("gurbakir.com")))
        lateinit var firstSink: (CheckoutEvent) -> Unit
        lateinit var secondSink: (CheckoutEvent) -> Unit
        val first = adapter.presentWithOperation(target) {
            firstSink = it
            true
        } as CheckoutResult.Presented
        val second = adapter.presentWithOperation(target) {
            secondSink = it
            true
        } as CheckoutResult.Presented
        val firstEvents = async { first.events.toList() }
        val secondEvents = async { second.events.toList() }
        runCurrent()

        secondSink(CheckoutEvent.Failed(CheckoutFailure.NETWORK))
        firstSink(CheckoutEvent.Completed)

        assertEquals(listOf(CheckoutEvent.Completed), firstEvents.await())
        assertEquals(
            listOf(CheckoutEvent.Failed(CheckoutFailure.NETWORK)),
            secondEvents.await()
        )
    }

    @Test
    fun `terminal event is never replayed to a later presentation owner`() = runTest {
        val adapter = ShopifyCheckoutAdapter(checkoutUrlPolicy = CheckoutUrlPolicy(setOf("gurbakir.com")))
        val first =
            adapter.presentWithOperation(target) { sink ->
                sink(CheckoutEvent.Completed)
                true
            } as CheckoutResult.Presented
        assertEquals(listOf(CheckoutEvent.Completed), first.events.toList())

        lateinit var laterSink: (CheckoutEvent) -> Unit
        val later =
            adapter.presentWithOperation(target) { sink ->
                laterSink = sink
                true
            } as CheckoutResult.Presented
        val laterEvents = async { later.events.toList() }
        runCurrent()
        laterSink(CheckoutEvent.Cancelled)

        assertEquals(listOf(CheckoutEvent.Cancelled), laterEvents.await())
    }

    @Test
    fun `rejected target creates no presentation session or operation`() = runTest {
        val adapter = ShopifyCheckoutAdapter(checkoutUrlPolicy = CheckoutUrlPolicy(setOf("gurbakir.com")))
        var invoked = false

        val result =
            adapter.presentWithOperation(URI("https://attacker.example/checkout")) {
                invoked = true
                true
            }

        assertEquals(CheckoutResult.Rejected(CheckoutFailure.INVALID_CHECKOUT_URL), result)
        assertEquals(false, invoked)
    }
}
