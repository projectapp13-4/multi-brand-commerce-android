package com.gurbakir.mobile.checkout

import android.app.Activity
import com.gurbakir.checkout.CheckoutAdapter
import com.gurbakir.checkout.CheckoutEvent
import com.gurbakir.checkout.CheckoutFailure as SdkCheckoutFailure
import com.gurbakir.checkout.CheckoutPresentationOwner
import com.gurbakir.checkout.CheckoutResult
import com.gurbakir.checkout.CheckoutSessionEvent
import com.gurbakir.checkout.CheckoutSessionId
import com.gurbakir.mobile.cart.CartCheckoutResolution
import com.gurbakir.mobile.cart.CartRepository
import com.gurbakir.storefront.CartCompletionResolution
import com.gurbakir.storefront.SensitiveCartId
import com.gurbakir.storefront.SensitiveCheckoutUrl
import java.net.URI
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal data class PreparedCheckout(val cartId: SensitiveCartId, val checkoutUrl: SensitiveCheckoutUrl) {
    override fun toString(): String = "PreparedCheckout(cartId=<redacted>, checkoutUrl=<redacted>)"
}

@Suppress("TooManyFunctions") // Preparation, bounded launch and provider events share one checkout state and mutex.
class CheckoutController
@Inject
constructor(
    private val cartRepository: CartRepository,
    private val cartCompleter: CheckoutCartCompleter,
    private val adapter: CheckoutAdapter
) {
    private val lock = Mutex()
    private val ownership = Any()
    private val _state = MutableStateFlow(CheckoutState())
    val state: StateFlow<CheckoutState> = _state.asStateFlow()
    private var current: CheckoutRun? = null
    private var pendingCleanup: CheckoutCleanup? = null
    private var cleanupAttempt: CheckoutCleanupAttempt? = null
    private var followUp: Job? = null

    suspend fun start(activity: Activity) = startWithPresentation { url -> adapter.present(activity, url) }

    /** The bounded SDK launch shares the verified session lease; event collection never holds it. */
    internal suspend fun startWithPresentation(present: suspend (URI) -> CheckoutResult) {
        val run = begin(currentCoroutineContext()[Job]) ?: return
        var unadopted: CheckoutResult.Presented? = null
        try {
            val launched = lock.withLock {
                cartRepository.withPreparedCheckout { resolution ->
                    val prepared = applyPreparation(run, resolution) ?: return@withPreparedCheckout null
                    adapter.invalidate()
                    val result = prepared.checkoutUrl.useSuspending(present)
                    unadopted = result as? CheckoutResult.Presented
                    prepared to result
                }
            }
            if (launched != null) {
                val presentation = adopt(run, launched.first, launched.second)
                if (presentation != null) unadopted = null
                presentation?.events?.collect { acceptEvent(CheckoutSessionEvent(presentation.sessionId, it)) }
            }
        } finally {
            unadopted?.owner?.dispose()
            release(run)
        }
    }

    suspend fun acceptEvent(sessionEvent: CheckoutSessionEvent) {
        val run = synchronized(ownership) {
            current?.takeIf { !it.settled && it.sessionId == sessionEvent.sessionId }
        } ?: return
        when (val event = sessionEvent.event) {
            CheckoutEvent.Completed -> acceptTerminal(run, CheckoutStatus.CLEANUP_REQUIRED)
            CheckoutEvent.Cancelled -> acceptTerminal(run, CheckoutStatus.CANCELLED)
            is CheckoutEvent.Failed -> acceptTerminal(run, CheckoutStatus.FAILED, event.failure)
            is CheckoutEvent.RecoveryStarted -> publishActive(run, CheckoutStatus.IN_PROGRESS)
            is CheckoutEvent.ExternalLinkRequested -> publishActive(run, CheckoutStatus.EXTERNAL_LINK_BLOCKED)
        }
    }

    suspend fun retryCleanup() {
        val attempt = synchronized(ownership) {
            pendingCleanup?.takeIf { cleanupAttempt == null }?.let { cleanup ->
                CheckoutCleanupAttempt(cleanup).also { cleanupAttempt = it }
            }
        } ?: return
        try {
            val oldJob = synchronized(ownership) { followUp }
            if (oldJob != null && oldJob !== currentCoroutineContext()[Job]) oldJob.cancelAndJoin()
            lock.withLock {
                finishCleanup(attempt.cleanup)
                releaseCleanupAttempt(attempt)
                refreshAfterTerminal(currentCoroutineContext()[Job])
            }
        } finally {
            releaseCleanupAttempt(attempt)
        }
    }

    internal suspend fun prepare(): PreparedCheckout? {
        val run = begin(currentCoroutineContext()[Job]) ?: return null
        var prepared: PreparedCheckout? = null
        try {
            prepared = lock.withLock { applyPreparation(run, cartRepository.prepareCheckout()) }
            return prepared
        } finally {
            if (prepared == null) release(run)
        }
    }

    internal suspend fun acceptPresentation(prepared: PreparedCheckout, result: CheckoutResult) {
        val run = synchronized(ownership) { current } ?: begin(currentCoroutineContext()[Job])
        if (run == null) {
            (result as? CheckoutResult.Presented)?.owner?.dispose()
        } else {
            try {
                val ownerJob = currentCoroutineContext()[Job]
                synchronized(ownership) { run.job = ownerJob }
                val presentation = adopt(run, prepared, result)
                if (presentation == null) (result as? CheckoutResult.Presented)?.owner?.dispose()
                presentation?.events?.collect { acceptEvent(CheckoutSessionEvent(presentation.sessionId, it)) }
            } finally {
                release(run)
            }
        }
    }

    private fun begin(job: Job?): CheckoutRun? = synchronized(ownership) {
        if (_state.value.busy || pendingCleanup != null || cleanupAttempt != null) {
            null
        } else {
            CheckoutRun(job).also {
                current = it
                _state.value = CheckoutState(CheckoutStatus.PREPARING)
            }
        }
    }

    private fun applyPreparation(run: CheckoutRun, resolution: CartCheckoutResolution): PreparedCheckout? =
        synchronized(ownership) {
            if (current !== run) {
                null
            } else {
                when (resolution) {
                    is CartCheckoutResolution.Eligible -> {
                        _state.value = CheckoutState(CheckoutStatus.PRESENTING)
                        PreparedCheckout(resolution.cartId, resolution.checkoutUrl)
                    }

                    CartCheckoutResolution.Empty -> fail(run, CheckoutFailureCategory.CART_EMPTY, false)

                    CartCheckoutResolution.Restricted -> fail(run, CheckoutFailureCategory.CART_RESTRICTED, true)

                    CartCheckoutResolution.Unavailable -> fail(run, CheckoutFailureCategory.CART_UNAVAILABLE, true)

                    is CartCheckoutResolution.Failed -> fail(run, resolution.failure.toCheckoutFailure())
                }
            }
        }

    private fun adopt(run: CheckoutRun, prepared: PreparedCheckout, result: CheckoutResult): CheckoutResult.Presented? =
        synchronized(ownership) {
            if (current !== run || run.settled) {
                null
            } else {
                when (result) {
                    is CheckoutResult.Presented -> {
                        run.cartId = prepared.cartId
                        run.sessionId = result.sessionId
                        run.owner = result.owner
                        _state.value = CheckoutState(CheckoutStatus.IN_PROGRESS)
                        result
                    }

                    CheckoutResult.Preloaded -> fail(run, CheckoutFailureCategory.FATAL, true)

                    is CheckoutResult.Rejected -> fail(run, result.reason.toFailureCategory(), true)
                }
            }
        }

    private suspend fun acceptTerminal(run: CheckoutRun, status: CheckoutStatus, failure: SdkCheckoutFailure? = null) {
        var acceptedAttempt: CheckoutCleanupAttempt? = null
        val accepted = synchronized(ownership) {
            if (current !== run || run.settled) {
                false
            } else {
                run.settled = true
                if (status == CheckoutStatus.CLEANUP_REQUIRED) {
                    val cleanup = CheckoutCleanup(requireNotNull(run.cartId))
                    pendingCleanup = cleanup
                    acceptedAttempt = CheckoutCleanupAttempt(cleanup).also { cleanupAttempt = it }
                    _state.value = cleanupRequired()
                } else {
                    val retained = cartRepository.hasProtectedCart()
                    _state.value = CheckoutState(status, failure?.toProjectFailure(retained), retained)
                }
                true
            }
        }
        if (accepted) {
            val attempt = acceptedAttempt
            try {
                disposeOwner(run)
                adapter.invalidate()
                lock.withLock {
                    if (attempt != null) completeAttempt(attempt)
                    if (synchronized(ownership) { current === run }) refreshAfterTerminal(run.job)
                }
            } finally {
                if (attempt != null) releaseCleanupAttempt(attempt)
            }
        }
    }

    private suspend fun completeAttempt(attempt: CheckoutCleanupAttempt) {
        try {
            finishCleanup(attempt.cleanup)
        } finally {
            releaseCleanupAttempt(attempt)
        }
    }

    private suspend fun finishCleanup(cleanup: CheckoutCleanup) {
        val result = cartCompleter.complete(cleanup.cartId)
        synchronized(ownership) {
            if (pendingCleanup === cleanup) {
                _state.value = when (result) {
                    CartCompletionResolution.CLEARED,
                    CartCompletionResolution.ALREADY_ABSENT ->
                        CheckoutState(CheckoutStatus.COMPLETED, cartRetained = false)

                    CartCompletionResolution.DIFFERENT_CART ->
                        CheckoutState(CheckoutStatus.COMPLETED_CURRENT_CART_PRESERVED, cartRetained = true)

                    CartCompletionResolution.SECURE_PERSISTENCE_FAILED -> cleanupRequired()
                }
                if (result != CartCompletionResolution.SECURE_PERSISTENCE_FAILED) pendingCleanup = null
            }
        }
    }

    private fun publishActive(run: CheckoutRun, status: CheckoutStatus) {
        synchronized(ownership) {
            if (current === run && !run.settled) _state.value = CheckoutState(status, cartRetained = true)
        }
    }

    private fun releaseCleanupAttempt(attempt: CheckoutCleanupAttempt) {
        synchronized(ownership) { if (cleanupAttempt === attempt) cleanupAttempt = null }
    }

    private suspend fun refreshAfterTerminal(job: Job?) {
        synchronized(ownership) { followUp = job }
        try {
            cartRepository.refresh()
        } finally {
            synchronized(ownership) { if (followUp === job) followUp = null }
        }
    }

    private fun release(run: CheckoutRun) {
        synchronized(ownership) {
            if (current === run) {
                current = null
                if (!run.settled) _state.value = CheckoutState(CheckoutStatus.INTERRUPTED, cartRetained = true)
            }
        }
        disposeOwner(run)
    }

    private fun disposeOwner(run: CheckoutRun) {
        val owner = synchronized(ownership) { run.owner.also { run.owner = null } }
        owner?.dispose()
    }

    private fun fail(run: CheckoutRun, category: CheckoutFailureCategory, retained: Boolean): Nothing? =
        fail(run, CheckoutFailure(category, category.isRetryable(), retained))

    private fun fail(run: CheckoutRun, failure: CheckoutFailure): Nothing? {
        run.settled = true
        _state.value = CheckoutState(CheckoutStatus.FAILED, failure, failure.cartRetained)
        return null
    }
}

private class CheckoutRun(var job: Job?) {
    var cartId: SensitiveCartId? = null
    var sessionId: CheckoutSessionId? = null
    var owner: CheckoutPresentationOwner? = null
    var settled = false
}

private class CheckoutCleanup(val cartId: SensitiveCartId)

private class CheckoutCleanupAttempt(val cleanup: CheckoutCleanup)

private fun cleanupRequired() = CheckoutState(
    status = CheckoutStatus.CLEANUP_REQUIRED,
    failure = CheckoutFailure(CheckoutFailureCategory.SECURE_STORAGE, retryable = true, cartRetained = true),
    cartRetained = true
)
