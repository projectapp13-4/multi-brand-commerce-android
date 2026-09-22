package com.gurbakir.mobile.home

import com.gurbakir.storefront.StorefrontMediaLimitExceededException
import com.gurbakir.storefront.StorefrontMediaRejectedException
import com.gurbakir.storefront.StorefrontMediaRequestGuard
import java.io.IOException
import java.io.InterruptedIOException
import java.net.SocketTimeoutException
import java.util.Collections
import java.util.IdentityHashMap
import java.util.concurrent.CancellationException

internal class HomePlaybackNetworkGuard(
    private val attempt: HomePlaybackAttempt,
    private val request: HomePlaybackRequestToken
) : StorefrontMediaRequestGuard {
    override fun onRequestStarted(url: String): Boolean = attempt.restartRedirectChain(request, url)

    override fun onRedirect(targetUrl: String): Boolean =
        attempt.followRedirect(request, targetUrl) == HomeRedirectDecision.ALLOWED

    override fun maximumResponseBytesForRead(requestedByteCount: Long): Long {
        val remaining = attempt.remainingResponseBytes()
        if (requestedByteCount > 0 && remaining == 0L) {
            attempt.recordResponseBytes(1)
            return 0
        }
        return minOf(requestedByteCount, remaining)
    }

    override fun onResponseBytes(byteCount: Long): Boolean = attempt.recordResponseBytes(byteCount)
}

internal class HomePlaybackRetryController(
    private val attempt: HomePlaybackAttempt,
    private val request: HomePlaybackRequestToken
) {
    internal val requestToken: HomePlaybackRequestToken
        get() = request

    fun shouldRetry(exception: IOException): Boolean =
        !exception.hasNonRetryablePlaybackCause() && attempt.permitTransportRetry(request)
}

internal fun Throwable.hasNonRetryablePlaybackCause(): Boolean = anyPlaybackCause {
    it is StorefrontMediaRejectedException || it is StorefrontMediaLimitExceededException || it.isPlaybackCancellation()
}

internal fun Throwable.hasPlaybackCancellationCause(): Boolean = anyPlaybackCause { it.isPlaybackCancellation() }

private fun Throwable.isPlaybackCancellation(): Boolean =
    this is CancellationException || (this is InterruptedIOException && this !is SocketTimeoutException) ||
        (this is IOException && message.equals("Canceled", ignoreCase = true))

private fun Throwable.anyPlaybackCause(matches: (Throwable) -> Boolean): Boolean {
    val visited = Collections.newSetFromMap(IdentityHashMap<Throwable, Boolean>())
    var current: Throwable? = this
    while (current != null && visited.add(current)) {
        if (matches(current)) return true
        current = current.cause
    }
    return false
}
