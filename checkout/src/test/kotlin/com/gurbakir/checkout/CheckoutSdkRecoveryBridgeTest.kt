package com.gurbakir.checkout

import com.shopify.checkoutsheetkit.CheckoutException
import com.shopify.checkoutsheetkit.ErrorRecovery
import com.shopify.checkoutsheetkit.HttpException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CheckoutSdkRecoveryBridgeTest {
    @Test
    fun `original selected recovery is queued until the SDK stack returns`() {
        val fixture = RecoveryFixture()
        val error = HttpException(statusCode = 500, isRecoverable = true)
        var hooks = 0
        val delegate = object : ErrorRecovery {
            override fun preRecoveryActions(exception: CheckoutException, checkoutUrl: String) {
                assertSame(error, exception)
                assertEquals(fixture.url, checkoutUrl)
                hooks++
            }
        }
        val policy = fixture.registry.wrap(delegate)
        fixture.bridge.onFailure(error)
        assertTrue(policy.shouldRecoverFromError(error))
        policy.preRecoveryActions(error, fixture.url)
        assertEquals(1, hooks)
        assertTrue(fixture.events.isEmpty())
        fixture.drain()
        assertEquals(listOf(CheckoutEvent.RecoveryStarted(CheckoutFailure.NETWORK)), fixture.events)
        fixture.bridge.onCompleted()
        assertEquals(CheckoutEvent.Completed, fixture.events.last())
    }

    @Test
    fun `recoverable error without actual hook is terminal even when original policy accepts`() {
        val fixture = RecoveryFixture()
        val error = HttpException(statusCode = 500, isRecoverable = true)
        fixture.bridge.onFailure(error)
        assertTrue(fixture.registry.wrap(object : ErrorRecovery {}).shouldRecoverFromError(error))
        assertTrue(fixture.events.isEmpty())
        fixture.drain()
        assertEquals(listOf(CheckoutEvent.Failed(CheckoutFailure.NETWORK)), fixture.events)
    }

    @Test
    fun `recovery exhaustion is final after a prior selected recovery`() {
        val fixture = RecoveryFixture()
        val error = HttpException(statusCode = 500, isRecoverable = true)
        val policy = fixture.registry.wrap(object : ErrorRecovery {})
        fixture.bridge.onFailure(error)
        policy.preRecoveryActions(error, fixture.url)
        fixture.drain()
        fixture.bridge.onFailure(error)
        fixture.drain()
        fixture.bridge.onCompleted()
        assertEquals(
            listOf(
                CheckoutEvent.RecoveryStarted(CheckoutFailure.NETWORK),
                CheckoutEvent.Failed(CheckoutFailure.NETWORK)
            ),
            fixture.events
        )
    }

    @Test
    fun `mismatched exception and private URL do not select pending failure`() {
        val fixture = RecoveryFixture()
        val error = HttpException(statusCode = 500, isRecoverable = true)
        val policy = fixture.registry.wrap(object : ErrorRecovery {})
        fixture.bridge.onFailure(error)
        policy.preRecoveryActions(HttpException(statusCode = 500, isRecoverable = true), fixture.url)
        policy.preRecoveryActions(error, "https://shop.example/different")
        fixture.drain()
        assertEquals(listOf(CheckoutEvent.Failed(CheckoutFailure.NETWORK)), fixture.events)
    }

    @Test
    fun `completion cancellation and disposal invalidate queued selected failure`() {
        val endings: List<(RecoveryFixture) -> Unit> = listOf(
            { it.bridge.onCompleted() },
            { it.bridge.onCancelled() },
            { it.registration.dispose() }
        )
        endings.forEachIndexed { index, end ->
            val fixture = RecoveryFixture()
            val error = HttpException(statusCode = 500, isRecoverable = true)
            fixture.bridge.onFailure(error)
            fixture.registry.wrap(object : ErrorRecovery {}).preRecoveryActions(error, fixture.url)
            end(fixture)
            fixture.drain()
            val expected = when (index) {
                0 -> listOf(CheckoutEvent.Completed)
                1 -> listOf(CheckoutEvent.Cancelled)
                else -> emptyList()
            }
            assertEquals(expected, fixture.events)
        }
    }

    @Test
    fun `delegate reentrancy reusing exception cannot select a newer failure generation`() {
        val fixture = RecoveryFixture()
        val error = HttpException(statusCode = 500, isRecoverable = true)
        val policy = fixture.registry.wrap(object : ErrorRecovery {
            override fun preRecoveryActions(exception: CheckoutException, checkoutUrl: String) {
                fixture.bridge.onFailure(error)
            }
        })
        fixture.bridge.onFailure(error)
        policy.preRecoveryActions(error, fixture.url)
        fixture.drain()
        assertEquals(listOf(CheckoutEvent.Failed(CheckoutFailure.NETWORK)), fixture.events)
    }

    @Test
    fun `delegate new presentation cannot select its reused exception from an old hook`() {
        val fixture = RecoveryFixture()
        val newEvents = mutableListOf<CheckoutEvent>()
        val newQueue = ArrayDeque<() -> Unit>()
        val later = CheckoutSdkRecoveryBridge(fixture.url, newEvents::add, CheckoutEventPoster(newQueue::addLast))
        val error = HttpException(statusCode = 500, isRecoverable = true)
        val policy = fixture.registry.wrap(object : ErrorRecovery {
            override fun preRecoveryActions(exception: CheckoutException, checkoutUrl: String) {
                fixture.registry.register(later)
                later.onFailure(error)
            }
        })
        fixture.bridge.onFailure(error)
        policy.preRecoveryActions(error, fixture.url)
        fixture.drain()
        while (newQueue.isNotEmpty()) newQueue.removeFirst().invoke()
        assertTrue(fixture.events.isEmpty())
        assertEquals(listOf(CheckoutEvent.Failed(CheckoutFailure.NETWORK)), newEvents)
    }

    @Test
    fun `original false policy decision and hook are delegated unchanged`() {
        val fixture = RecoveryFixture()
        val error = HttpException(statusCode = 500, isRecoverable = true)
        var policies = 0
        var hooks = 0
        val policy = fixture.registry.wrap(object : ErrorRecovery {
            override fun shouldRecoverFromError(checkoutException: CheckoutException): Boolean {
                assertSame(error, checkoutException)
                policies++
                return false
            }
            override fun preRecoveryActions(exception: CheckoutException, checkoutUrl: String) {
                assertSame(error, exception)
                assertEquals(fixture.url, checkoutUrl)
                hooks++
            }
        })
        assertFalse(policy.shouldRecoverFromError(error))
        policy.preRecoveryActions(error, fixture.url)
        assertEquals(1, policies)
        assertEquals(1, hooks)
        assertTrue(fixture.events.isEmpty())
    }

    @Test
    fun `original hook exception is preserved without inventing selected recovery`() {
        val fixture = RecoveryFixture()
        val error = HttpException(statusCode = 500, isRecoverable = true)
        val original = IllegalStateException("synthetic delegate refusal")
        val policy = fixture.registry.wrap(object : ErrorRecovery {
            override fun preRecoveryActions(exception: CheckoutException, checkoutUrl: String): Unit = throw original
        })
        fixture.bridge.onFailure(error)
        assertSame(
            original,
            assertThrows(IllegalStateException::class.java) {
                policy.preRecoveryActions(error, fixture.url)
            }
        )
        fixture.drain()
        assertEquals(listOf(CheckoutEvent.Failed(CheckoutFailure.NETWORK)), fixture.events)
    }
}

private class RecoveryFixture {
    val url = "https://shop.example/cart/c/synthetic"
    val queue = ArrayDeque<() -> Unit>()
    val events = mutableListOf<CheckoutEvent>()
    val registry = CheckoutRecoveryRegistry()
    val bridge = CheckoutSdkRecoveryBridge(url, events::add, CheckoutEventPoster(queue::addLast))
    val registration = registry.register(bridge)

    fun drain() {
        while (queue.isNotEmpty()) queue.removeFirst().invoke()
    }
}
