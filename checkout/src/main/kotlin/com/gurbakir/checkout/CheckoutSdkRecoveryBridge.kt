package com.gurbakir.checkout

import com.shopify.checkoutsheetkit.CheckoutException
import com.shopify.checkoutsheetkit.ErrorRecovery

internal fun interface CheckoutEventPoster {
    fun post(action: () -> Unit)
}

/** Settles raw failures only after the SDK's synchronous recovery decision has returned. */
internal class CheckoutSdkRecoveryBridge(
    private val checkoutUrl: String,
    private val sink: (CheckoutEvent) -> Unit,
    private val poster: CheckoutEventPoster
) : CheckoutPresentationOwner {
    private val lock = Any()
    private var closed = false
    private var generation = 0L
    private var pending: PendingFailure? = null

    fun onFailure(error: CheckoutException) {
        val failure = synchronized(lock) {
            if (closed) null else PendingFailure(error, ++generation).also { pending = it }
        }
        if (failure != null) poster.post { settle(failure) }
    }

    fun captureRecovery(error: CheckoutException, url: String): (() -> Unit)? = synchronized(lock) {
        pending?.takeIf { !closed && it.error === error && checkoutUrl == url }?.let { failure ->
            { selectRecovery(failure) }
        }
    }

    fun onCompleted() = finish(CheckoutEvent.Completed)

    fun onCancelled() = finish(CheckoutEvent.Cancelled)

    fun onInvalidLink() = finish(CheckoutEvent.Failed(CheckoutFailure.FATAL))

    fun onExternalLink(uri: java.net.URI) {
        val deliver = synchronized(lock) { !closed }
        if (deliver) sink(CheckoutEvent.ExternalLinkRequested(uri))
    }

    override fun dispose() {
        synchronized(lock) {
            closed = true
            pending = null
            generation++
        }
    }

    private fun selectRecovery(failure: PendingFailure) {
        synchronized(lock) {
            if (!closed && pending === failure && generation == failure.generation) failure.recoverySelected = true
        }
    }

    private fun settle(failure: PendingFailure) {
        val event = synchronized(lock) {
            if (!closed && pending === failure && generation == failure.generation) {
                pending = null
                if (failure.recoverySelected) {
                    CheckoutEvent.RecoveryStarted(failure.error.toProjectFailure())
                } else {
                    closed = true
                    CheckoutEvent.Failed(failure.error.toProjectFailure())
                }
            } else {
                null
            }
        }
        if (event != null) sink(event)
    }

    private fun finish(event: CheckoutEvent) {
        val deliver = synchronized(lock) {
            if (closed) {
                false
            } else {
                closed = true
                pending = null
                generation++
                true
            }
        }
        if (deliver) sink(event)
    }

    private class PendingFailure(val error: CheckoutException, val generation: Long) {
        var recoverySelected = false
    }
}

/** The singleton SDK hook must observe the same presentation callback assembly used by present. */
internal class CheckoutRecoveryRegistry {
    private val lock = Any()
    private var current: CheckoutSdkRecoveryBridge? = null

    fun register(bridge: CheckoutSdkRecoveryBridge): CheckoutPresentationOwner {
        val previous = synchronized(lock) { current.also { current = bridge } }
        previous?.dispose()
        return CheckoutPresentationOwner {
            synchronized(lock) { if (current === bridge) current = null }
            bridge.dispose()
        }
    }

    fun wrap(delegate: ErrorRecovery): ErrorRecovery = object : ErrorRecovery {
        override fun shouldRecoverFromError(checkoutException: CheckoutException): Boolean =
            delegate.shouldRecoverFromError(checkoutException)

        override fun preRecoveryActions(exception: CheckoutException, checkoutUrl: String) {
            val bridge = synchronized(lock) { current }
            val selected = bridge?.captureRecovery(exception, checkoutUrl)
            delegate.preRecoveryActions(exception, checkoutUrl)
            // Capture before the delegate: reentrant callbacks cannot select a newer generation.
            if (synchronized(lock) { current === bridge }) selected?.invoke()
        }
    }
}
