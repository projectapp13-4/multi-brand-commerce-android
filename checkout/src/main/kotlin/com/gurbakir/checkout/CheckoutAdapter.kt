package com.gurbakir.checkout

import android.app.Activity
import java.net.URI
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.consumeAsFlow

private const val UNSPECIFIED_PORT = -1
private const val HTTPS_PORT = 443

interface CheckoutAdapter {
    suspend fun preload(activity: Activity, checkoutUrl: URI): CheckoutResult

    suspend fun present(activity: Activity, checkoutUrl: URI): CheckoutResult

    fun invalidate()
}

sealed interface CheckoutResult {
    data object Preloaded : CheckoutResult

    data class Presented(
        val sessionId: CheckoutSessionId,
        val events: Flow<CheckoutEvent>,
        val owner: CheckoutPresentationOwner
    ) : CheckoutResult

    data class Rejected(val reason: CheckoutFailure) : CheckoutResult
}

@JvmInline
value class CheckoutSessionId(val value: Long) {
    init {
        require(value > 0)
    }
}

data class CheckoutSessionEvent(val sessionId: CheckoutSessionId, val event: CheckoutEvent)

sealed interface CheckoutEvent {
    data object Completed : CheckoutEvent

    data object Cancelled : CheckoutEvent

    data class Failed(val failure: CheckoutFailure) : CheckoutEvent

    data class RecoveryStarted(val failure: CheckoutFailure) : CheckoutEvent

    data class ExternalLinkRequested(val uri: URI) : CheckoutEvent {
        override fun toString(): String = "ExternalLinkRequested(uri=<redacted>)"
    }
}

enum class CheckoutFailure {
    INVALID_CHECKOUT_URL,
    SDK_UNAVAILABLE,
    NETWORK,
    EXPIRED_OR_COMPLETED_CART,
    CONFIGURATION,
    RECOVERABLE,
    FATAL
}

class ShopifyCheckoutAdapter(
    private val checkoutKitClient: CheckoutKitClient = OfficialCheckoutKitClient(),
    private val checkoutUrlPolicy: CheckoutUrlPolicy = CheckoutUrlPolicy(emptySet())
) : CheckoutAdapter {
    private val sessionSequence = AtomicLong(0)

    override suspend fun preload(activity: Activity, checkoutUrl: URI): CheckoutResult = executePreload(checkoutUrl) {
        checkoutKitClient.preload(activity, checkoutUrl) {}
    }

    override suspend fun present(activity: Activity, checkoutUrl: URI): CheckoutResult =
        presentWithOperation(checkoutUrl) { eventSink ->
            checkoutKitClient.present(activity, checkoutUrl, eventSink)
        }

    override fun invalidate() {
        checkoutKitClient.invalidate()
    }

    internal fun presentWithOperation(
        checkoutUrl: URI,
        operation: ((CheckoutEvent) -> Unit) -> CheckoutPresentationOwner?
    ): CheckoutResult {
        if (!checkoutUrlPolicy.validate(checkoutUrl)) {
            return CheckoutResult.Rejected(CheckoutFailure.INVALID_CHECKOUT_URL)
        }
        val sessionId = CheckoutSessionId(sessionSequence.incrementAndGet())
        val eventChannel = Channel<CheckoutEvent>(capacity = Channel.UNLIMITED)
        val lifetime = CheckoutPresentationLifetime(eventChannel)
        return try {
            val owner = operation(lifetime::send)
            if (owner == null) {
                lifetime.dispose()
                CheckoutResult.Rejected(CheckoutFailure.SDK_UNAVAILABLE)
            } else {
                lifetime.adopt(owner)
                CheckoutResult.Presented(sessionId, eventChannel.consumeAsFlow(), lifetime)
            }
        } catch (cancelled: CancellationException) {
            lifetime.dispose()
            throw cancelled
        } catch (_: Exception) {
            lifetime.dispose()
            CheckoutResult.Rejected(CheckoutFailure.FATAL)
        }
    }

    private inline fun executePreload(checkoutUrl: URI, operation: () -> Boolean): CheckoutResult {
        if (!checkoutUrlPolicy.validate(checkoutUrl)) {
            return CheckoutResult.Rejected(CheckoutFailure.INVALID_CHECKOUT_URL)
        }
        return runCatching(operation).fold(
            onSuccess = { started ->
                if (started) CheckoutResult.Preloaded else CheckoutResult.Rejected(CheckoutFailure.SDK_UNAVAILABLE)
            },
            onFailure = { CheckoutResult.Rejected(CheckoutFailure.FATAL) }
        )
    }
}

internal fun CheckoutEvent.isTerminal(): Boolean =
    this is CheckoutEvent.Completed || this is CheckoutEvent.Cancelled || this is CheckoutEvent.Failed

interface CheckoutKitClient {
    fun preload(activity: Activity, checkoutUrl: URI, eventSink: (CheckoutEvent) -> Unit): Boolean

    fun present(activity: Activity, checkoutUrl: URI, eventSink: (CheckoutEvent) -> Unit): CheckoutPresentationOwner?

    fun invalidate()
}

fun interface CheckoutPresentationOwner {
    fun dispose()
}

class CheckoutUrlPolicy(allowedHosts: Set<String>) {
    private val normalizedAllowedHosts = allowedHosts.mapTo(mutableSetOf()) { it.lowercase() }

    fun validate(uri: URI): Boolean {
        val host = uri.host?.lowercase() ?: return false
        return uri.scheme == "https" &&
            host in normalizedAllowedHosts &&
            uri.userInfo == null &&
            uri.fragment == null &&
            uri.port in setOf(UNSPECIFIED_PORT, HTTPS_PORT)
    }
}
