package com.gurbakir.checkout

import kotlinx.coroutines.channels.Channel

/** Owns callbacks and the SDK handle even when the SDK settles before returning that handle. */
internal class CheckoutPresentationLifetime(private val events: Channel<CheckoutEvent>) : CheckoutPresentationOwner {
    private val lock = Any()
    private var stopped = false
    private var handle: CheckoutPresentationOwner? = null

    init {
        events.invokeOnClose { retire() }
    }

    fun adopt(owner: CheckoutPresentationOwner) {
        val disposeNow = synchronized(lock) {
            if (stopped) {
                true
            } else {
                handle = owner
                false
            }
        }
        if (disposeNow) owner.dispose()
    }

    fun send(event: CheckoutEvent) {
        val terminal = synchronized(lock) {
            if (stopped) {
                false
            } else {
                val sent = events.trySend(event).isSuccess
                val finished = !sent || event.isTerminal()
                if (finished) stopped = true
                finished
            }
        }
        if (terminal) {
            events.close()
            retire()
        }
    }

    override fun dispose() {
        retire()
        events.cancel()
    }

    private fun retire() {
        val dispose = synchronized(lock) {
            stopped = true
            handle.also { handle = null }
        }
        dispose?.dispose()
    }
}
