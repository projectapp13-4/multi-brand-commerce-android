package com.gurbakir.checkout

import java.net.URI
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CheckoutPresentationLifetimeTest {
    private val target = URI("https://shop.example/cart/c/synthetic")

    @Test
    fun `synchronous terminal buffers completion and disposes late handle once despite reentrancy`() = runTest {
        val adapter = adapter()
        var dismissals = 0
        val result = adapter.presentWithOperation(target) { sink ->
            sink(CheckoutEvent.Completed)
            CheckoutPresentationOwner {
                dismissals++
                sink(CheckoutEvent.Cancelled)
            }
        }
        val presentation = assertInstanceOf(CheckoutResult.Presented::class.java, result)
        assertEquals(1, dismissals)
        assertEquals(listOf(CheckoutEvent.Completed), presentation.events.toList())
        presentation.owner.dispose()
        presentation.owner.dispose()
        assertEquals(1, dismissals)
    }

    @Test
    fun `collector cancellation disposes only its handle and safely rejects late producer callbacks`() = runTest {
        val adapter = adapter()
        lateinit var sink: (CheckoutEvent) -> Unit
        var dismissals = 0
        val presentation = adapter.presentWithOperation(target) {
            sink = it
            CheckoutPresentationOwner { dismissals++ }
        } as CheckoutResult.Presented
        val collector = launch { presentation.events.toList() }
        runCurrent()
        collector.cancelAndJoin()
        sink(CheckoutEvent.Completed)
        sink(CheckoutEvent.Cancelled)
        sink(CheckoutEvent.Failed(CheckoutFailure.FATAL))
        presentation.owner.dispose()
        assertEquals(1, dismissals)
    }

    @Test
    fun `single consumer cannot recollect and relaunch a completed SDK presentation`() = runTest {
        var launches = 0
        val presentation = adapter().presentWithOperation(target) {
            launches++
            it(CheckoutEvent.Cancelled)
            CheckoutPresentationOwner {}
        } as CheckoutResult.Presented
        presentation.events.toList()
        val recollection = runCatching { presentation.events.toList() }.exceptionOrNull()
        assertInstanceOf(IllegalStateException::class.java, recollection)
        assertEquals(1, launches)
    }

    @Test
    fun `launch cancellation propagates its actual exception instead of SDK rejection`() {
        val original = CancellationException("synthetic launch cancellation")
        assertSame(
            original,
            assertThrows(CancellationException::class.java) {
                adapter().presentWithOperation(target) { throw original }
            }
        )
    }

    private fun adapter() = ShopifyCheckoutAdapter(checkoutUrlPolicy = CheckoutUrlPolicy(setOf("shop.example")))
}
