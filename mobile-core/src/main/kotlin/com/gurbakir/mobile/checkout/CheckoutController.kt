package com.gurbakir.mobile.checkout

import android.app.Activity
import com.gurbakir.checkout.CheckoutAdapter
import com.gurbakir.checkout.CheckoutEvent
import com.gurbakir.checkout.CheckoutFailure as SdkCheckoutFailure
import com.gurbakir.checkout.CheckoutResult
import com.gurbakir.checkout.CheckoutSessionEvent
import com.gurbakir.checkout.CheckoutSessionId
import com.gurbakir.mobile.cart.CartCheckoutResolution
import com.gurbakir.mobile.cart.CartRepository
import com.gurbakir.storefront.CartCompletionResolution
import com.gurbakir.storefront.SensitiveCartId
import com.gurbakir.storefront.SensitiveCheckoutUrl
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal data class PreparedCheckout(val cartId: SensitiveCartId, val checkoutUrl: SensitiveCheckoutUrl) {
    override fun toString(): String = "PreparedCheckout(cartId=<redacted>, checkoutUrl=<redacted>)"
}

class CheckoutController
@Inject
constructor(
    private val cartRepository: CartRepository,
    private val cartCompleter: CheckoutCartCompleter,
    private val adapter: CheckoutAdapter
) {
    private val lock = Mutex()
    private val _state = MutableStateFlow(CheckoutState())
    val state: StateFlow<CheckoutState> = _state.asStateFlow()
    private var activeCheckout: ActiveCheckout? = null

    suspend fun start(activity: Activity) {
        val prepared = prepare() ?: return
        adapter.invalidate()
        val result = prepared.checkoutUrl.useSuspending { url -> adapter.present(activity, url) }
        acceptPresentation(prepared, result)
    }

    suspend fun acceptEvent(sessionEvent: CheckoutSessionEvent): Unit = lock.withLock {
        val active = activeCheckout?.takeIf { it.sessionId == sessionEvent.sessionId } ?: return@withLock
        when (val event = sessionEvent.event) {
            CheckoutEvent.Completed -> complete(active)

            CheckoutEvent.Cancelled -> finishRetainingCart(CheckoutStatus.CANCELLED)

            is CheckoutEvent.Failed -> finishWithFailure(event.failure)

            is CheckoutEvent.ExternalLinkRequested ->
                _state.value = CheckoutState(CheckoutStatus.EXTERNAL_LINK_BLOCKED, cartRetained = true)
        }
    }

    internal suspend fun prepare(): PreparedCheckout? = lock.withLock {
        if (_state.value.busy) return@withLock null
        _state.value = CheckoutState(CheckoutStatus.PREPARING)
        when (val resolution = cartRepository.prepareCheckout()) {
            is CartCheckoutResolution.Eligible -> {
                _state.value = CheckoutState(CheckoutStatus.PRESENTING)
                PreparedCheckout(resolution.cartId, resolution.checkoutUrl)
            }

            CartCheckoutResolution.Empty -> failPreparation(CheckoutFailureCategory.CART_EMPTY, retained = false)

            CartCheckoutResolution.Restricted -> failPreparation(
                CheckoutFailureCategory.CART_RESTRICTED,
                retained = true
            )

            CartCheckoutResolution.Unavailable -> failPreparation(
                CheckoutFailureCategory.CART_UNAVAILABLE,
                retained = true
            )

            is CartCheckoutResolution.Failed -> failPreparation(resolution.failure.toCheckoutFailure())
        }
    }

    internal suspend fun acceptPresentation(prepared: PreparedCheckout, result: CheckoutResult) {
        val presentation = lock.withLock {
            when (result) {
                is CheckoutResult.Presented -> {
                    activeCheckout = ActiveCheckout(prepared.cartId, result.sessionId)
                    _state.value = CheckoutState(CheckoutStatus.IN_PROGRESS)
                    result
                }

                CheckoutResult.Preloaded -> {
                    failPresentation(CheckoutFailureCategory.FATAL)
                    null
                }

                is CheckoutResult.Rejected -> {
                    failPresentation(result.reason.toFailureCategory())
                    null
                }
            }
        }
        presentation?.events?.collect { event ->
            acceptEvent(CheckoutSessionEvent(presentation.sessionId, event))
        }
    }

    private suspend fun complete(active: ActiveCheckout) {
        activeCheckout = null
        adapter.invalidate()
        when (cartCompleter.complete(active.cartId)) {
            CartCompletionResolution.CLEARED,
            CartCompletionResolution.ALREADY_ABSENT -> {
                cartRepository.refresh()
                _state.value = CheckoutState(CheckoutStatus.COMPLETED, cartRetained = false)
            }

            CartCompletionResolution.DIFFERENT_CART -> {
                cartRepository.refresh()
                _state.value =
                    CheckoutState(CheckoutStatus.COMPLETED_CURRENT_CART_PRESERVED, cartRetained = true)
            }

            CartCompletionResolution.SECURE_PERSISTENCE_FAILED -> {
                cartRepository.refresh()
                _state.value =
                    CheckoutState(
                        status = CheckoutStatus.CLEANUP_REQUIRED,
                        failure =
                            CheckoutFailure(
                                CheckoutFailureCategory.SECURE_STORAGE,
                                retryable = true,
                                cartRetained = true
                            ),
                        cartRetained = true
                    )
            }
        }
    }

    private suspend fun finishRetainingCart(status: CheckoutStatus) {
        activeCheckout = null
        adapter.invalidate()
        cartRepository.refresh()
        _state.value = CheckoutState(status, cartRetained = cartRepository.hasProtectedCart())
    }

    private suspend fun finishWithFailure(failure: SdkCheckoutFailure) {
        activeCheckout = null
        adapter.invalidate()
        cartRepository.refresh()
        val retained = cartRepository.hasProtectedCart()
        _state.value =
            CheckoutState(
                status = CheckoutStatus.FAILED,
                failure = failure.toProjectFailure(retained),
                cartRetained = retained
            )
    }

    private fun failPresentation(category: CheckoutFailureCategory) {
        activeCheckout = null
        _state.value =
            CheckoutState(
                status = CheckoutStatus.FAILED,
                failure = CheckoutFailure(category, category.isRetryable(), cartRetained = true),
                cartRetained = true
            )
    }

    private fun failPreparation(category: CheckoutFailureCategory, retained: Boolean): PreparedCheckout? {
        failPreparation(CheckoutFailure(category, category.isRetryable(), retained))
        return null
    }

    private fun failPreparation(failure: CheckoutFailure): PreparedCheckout? {
        _state.value = CheckoutState(CheckoutStatus.FAILED, failure, failure.cartRetained)
        return null
    }
}

private data class ActiveCheckout(val cartId: SensitiveCartId, val sessionId: CheckoutSessionId)
