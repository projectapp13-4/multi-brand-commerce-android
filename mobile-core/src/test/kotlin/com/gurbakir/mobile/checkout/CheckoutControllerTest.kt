package com.gurbakir.mobile.checkout

import android.app.Activity
import com.gurbakir.checkout.CheckoutAdapter
import com.gurbakir.checkout.CheckoutEvent
import com.gurbakir.checkout.CheckoutFailure as SdkCheckoutFailure
import com.gurbakir.checkout.CheckoutResult
import com.gurbakir.checkout.CheckoutSessionEvent
import com.gurbakir.checkout.CheckoutSessionId
import com.gurbakir.mobile.cart.CartActionResult
import com.gurbakir.mobile.cart.CartCheckoutResolution
import com.gurbakir.mobile.cart.CartRepository
import com.gurbakir.mobile.cart.CartState
import com.gurbakir.mobile.cart.CartStatus
import com.gurbakir.storefront.CartCompletionResolution
import com.gurbakir.storefront.SensitiveCartId
import com.gurbakir.storefront.SensitiveCartLineId
import com.gurbakir.storefront.SensitiveCheckoutUrl
import java.net.URI
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CheckoutControllerTest {
    @Test
    fun `only the matching presented session can complete and clear its cart`() = runTest {
        val repository = FakeCartRepository(eligible())
        val completer = FakeCheckoutCartCompleter(CartCompletionResolution.CLEARED)
        val adapter = FakeCheckoutAdapter()
        val controller = CheckoutController(repository, completer, adapter)
        val prepared = requireNotNull(controller.prepare())
        controller.acceptPresentation(prepared, presentation(1))

        controller.acceptEvent(CheckoutSessionEvent(CheckoutSessionId(2), CheckoutEvent.Completed))
        assertEquals(CheckoutStatus.IN_PROGRESS, controller.state.value.status)
        assertEquals(0, completer.calls)

        controller.acceptEvent(CheckoutSessionEvent(CheckoutSessionId(1), CheckoutEvent.Completed))
        assertEquals(CheckoutStatus.COMPLETED, controller.state.value.status)
        assertEquals(1, completer.calls)
        assertEquals(1, repository.refreshCalls)
        assertEquals(1, adapter.invalidateCalls)
    }

    @Test
    fun `completed session terminal event cannot replay into a later checkout owner`() = runTest {
        val repository = FakeCartRepository(eligible())
        val completer = FakeCheckoutCartCompleter(CartCompletionResolution.CLEARED)
        val controller = CheckoutController(repository, completer, FakeCheckoutAdapter())
        val firstPrepared = requireNotNull(controller.prepare())
        controller.acceptPresentation(firstPrepared, presentation(1))
        controller.acceptEvent(CheckoutSessionEvent(CheckoutSessionId(1), CheckoutEvent.Completed))

        val laterPrepared = requireNotNull(controller.prepare())
        controller.acceptPresentation(laterPrepared, presentation(2))
        controller.acceptEvent(CheckoutSessionEvent(CheckoutSessionId(1), CheckoutEvent.Completed))

        assertEquals(CheckoutStatus.IN_PROGRESS, controller.state.value.status)
        assertEquals(1, completer.calls)
    }

    @Test
    fun `cancellation refreshes and retains the current cart`() = runTest {
        val repository = FakeCartRepository(eligible())
        val controller = CheckoutController(repository, FakeCheckoutCartCompleter(), FakeCheckoutAdapter())
        val prepared = requireNotNull(controller.prepare())
        controller.acceptPresentation(prepared, presentation(7))

        controller.acceptEvent(CheckoutSessionEvent(CheckoutSessionId(7), CheckoutEvent.Cancelled))

        assertEquals(CheckoutStatus.CANCELLED, controller.state.value.status)
        assertEquals(true, controller.state.value.cartRetained)
        assertEquals(1, repository.refreshCalls)
    }

    @Test
    fun `checkout failure is typed and never clears the cart`() = runTest {
        val repository = FakeCartRepository(eligible())
        val completer = FakeCheckoutCartCompleter()
        val controller = CheckoutController(repository, completer, FakeCheckoutAdapter())
        val prepared = requireNotNull(controller.prepare())
        controller.acceptPresentation(prepared, presentation(3))

        controller.acceptEvent(
            CheckoutSessionEvent(
                CheckoutSessionId(3),
                CheckoutEvent.Failed(SdkCheckoutFailure.NETWORK)
            )
        )

        assertEquals(CheckoutStatus.FAILED, controller.state.value.status)
        assertEquals(CheckoutFailureCategory.NETWORK, controller.state.value.failure?.category)
        assertEquals(0, completer.calls)
        assertEquals(true, controller.state.value.cartRetained)
    }

    @Test
    fun `external links stay blocked while the matching checkout can still complete`() = runTest {
        val repository = FakeCartRepository(eligible())
        val completer = FakeCheckoutCartCompleter()
        val controller = CheckoutController(repository, completer, FakeCheckoutAdapter())
        val sessionId = CheckoutSessionId(8)
        val prepared = requireNotNull(controller.prepare())
        controller.acceptPresentation(prepared, presentation(sessionId.value))

        controller.acceptEvent(
            CheckoutSessionEvent(
                sessionId,
                CheckoutEvent.ExternalLinkRequested(URI("https://outside.invalid/path"))
            )
        )
        assertEquals(CheckoutStatus.EXTERNAL_LINK_BLOCKED, controller.state.value.status)
        assertEquals(0, completer.calls)

        controller.acceptEvent(CheckoutSessionEvent(sessionId, CheckoutEvent.Completed))
        assertEquals(CheckoutStatus.COMPLETED, controller.state.value.status)
        assertEquals(1, completer.calls)
    }

    @Test
    fun `completion preserves a different current cart`() = runTest {
        val repository = FakeCartRepository(eligible())
        val controller =
            CheckoutController(
                repository,
                FakeCheckoutCartCompleter(CartCompletionResolution.DIFFERENT_CART),
                FakeCheckoutAdapter()
            )
        val prepared = requireNotNull(controller.prepare())
        controller.acceptPresentation(prepared, presentation(4))

        controller.acceptEvent(CheckoutSessionEvent(CheckoutSessionId(4), CheckoutEvent.Completed))

        assertEquals(CheckoutStatus.COMPLETED_CURRENT_CART_PRESERVED, controller.state.value.status)
        assertEquals(true, controller.state.value.cartRetained)
    }

    @Test
    fun `secure cleanup failure records genuine completion without allowing false success cleanup`() = runTest {
        val repository = FakeCartRepository(eligible())
        val controller =
            CheckoutController(
                repository,
                FakeCheckoutCartCompleter(CartCompletionResolution.SECURE_PERSISTENCE_FAILED),
                FakeCheckoutAdapter()
            )
        val prepared = requireNotNull(controller.prepare())
        controller.acceptPresentation(prepared, presentation(5))

        controller.acceptEvent(CheckoutSessionEvent(CheckoutSessionId(5), CheckoutEvent.Completed))

        assertEquals(CheckoutStatus.CLEANUP_REQUIRED, controller.state.value.status)
        assertEquals(CheckoutFailureCategory.SECURE_STORAGE, controller.state.value.failure?.category)
    }

    @Test
    fun `empty preparation fails closed and process recreation infers no completion`() = runTest {
        val repository = FakeCartRepository(CartCheckoutResolution.Empty, CartState(status = CartStatus.EMPTY))
        val completer = FakeCheckoutCartCompleter()
        val first = CheckoutController(repository, completer, FakeCheckoutAdapter())

        assertEquals(null, first.prepare())
        assertEquals(CheckoutFailureCategory.CART_EMPTY, first.state.value.failure?.category)

        val recreated = CheckoutController(repository, completer, FakeCheckoutAdapter())
        assertEquals(CheckoutStatus.IDLE, recreated.state.value.status)
        assertEquals(0, completer.calls)
    }

    private fun eligible(): CartCheckoutResolution.Eligible = CartCheckoutResolution.Eligible(
        checkoutCartId("gid://shopify/Cart/checkout?key=synthetic"),
        checkoutUrl(URI("https://shop.example/cart/c/synthetic"))
    )
}

private class FakeCartRepository(
    private val preparation: CartCheckoutResolution,
    initialState: CartState = CartState(status = CartStatus.ACTIVE)
) : CartRepository {
    private val mutableState = MutableStateFlow(initialState)
    override val state: StateFlow<CartState> = mutableState
    var refreshCalls = 0

    override suspend fun refresh() {
        refreshCalls += 1
    }

    override suspend fun add(merchandiseId: String, quantity: Int): CartActionResult = CartActionResult.Completed

    override suspend fun update(lineId: SensitiveCartLineId, quantity: Int): CartActionResult =
        CartActionResult.Completed

    override suspend fun remove(lineId: SensitiveCartLineId): CartActionResult = CartActionResult.Completed

    override suspend fun discard(): CartActionResult = CartActionResult.Completed

    override suspend fun prepareCheckout(): CartCheckoutResolution = preparation
}

private class FakeCheckoutCartCompleter(
    private val result: CartCompletionResolution = CartCompletionResolution.CLEARED
) : CheckoutCartCompleter {
    var calls = 0

    override suspend fun complete(cartId: SensitiveCartId): CartCompletionResolution {
        calls += 1
        return result
    }
}

private class FakeCheckoutAdapter : CheckoutAdapter {
    var invalidateCalls = 0

    override suspend fun preload(activity: Activity, checkoutUrl: URI): CheckoutResult = CheckoutResult.Preloaded

    override suspend fun present(activity: Activity, checkoutUrl: URI): CheckoutResult =
        CheckoutResult.Rejected(SdkCheckoutFailure.SDK_UNAVAILABLE)

    override fun invalidate() {
        invalidateCalls += 1
    }
}

private fun presentation(sessionId: Long): CheckoutResult.Presented =
    CheckoutResult.Presented(CheckoutSessionId(sessionId), emptyFlow())

private fun checkoutCartId(value: String): SensitiveCartId =
    SensitiveCartId::class.java.getDeclaredConstructor(String::class.java).run {
        isAccessible = true
        newInstance(value)
    }

private fun checkoutUrl(value: URI): SensitiveCheckoutUrl =
    SensitiveCheckoutUrl::class.java.getDeclaredConstructor(URI::class.java).run {
        isAccessible = true
        newInstance(value)
    }
