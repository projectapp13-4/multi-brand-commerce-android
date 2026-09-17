package com.gurbakir.mobile.home

import com.gurbakir.storefront.StorefrontMediaLimitExceededException
import com.gurbakir.storefront.StorefrontMediaRejectedException
import com.gurbakir.storefront.StorefrontMediaRequestGuard
import java.io.IOException

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

    fun shouldRetry(exception: IOException): Boolean = when (exception) {
        is StorefrontMediaRejectedException,
        is StorefrontMediaLimitExceededException -> false

        else -> attempt.permitTransportRetry(request)
    }
}
