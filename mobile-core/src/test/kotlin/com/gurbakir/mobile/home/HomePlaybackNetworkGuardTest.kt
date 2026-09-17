package com.gurbakir.mobile.home

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HomePlaybackNetworkGuardTest {
    private val rendition = HomeVideoRendition(
        url = "https://cdn.shopify.com/videos/home.mp4",
        mimeType = "video/mp4",
        format = "mp4",
        width = 1280,
        height = 720
    )

    @Test
    fun `same URL starts a fresh redirect chain for range while keeping one byte budget`() {
        val attempt = attempt(maxBytes = 10)
        val initial = requireNotNull(attempt.beginRequest(rendition, HomePlaybackRequestKind.INITIAL))
        val initialGuard = HomePlaybackNetworkGuard(attempt, initial)

        assertTrue(initialGuard.onRequestStarted(rendition.url))
        assertEquals(6, initialGuard.maximumResponseBytesForRead(6))
        assertTrue(initialGuard.onResponseBytes(6))

        val range = requireNotNull(attempt.beginRequest(rendition, HomePlaybackRequestKind.RANGE))
        val rangeGuard = HomePlaybackNetworkGuard(attempt, range)
        assertTrue(rangeGuard.onRequestStarted(rendition.url))
        assertEquals(4, rangeGuard.maximumResponseBytesForRead(8))
        assertTrue(rangeGuard.onResponseBytes(4))
        assertEquals(10, attempt.responseBytesRead)
        assertEquals(0, rangeGuard.maximumResponseBytesForRead(1))
        assertEquals(HomePlaybackTerminalReason.BYTE_BUDGET, attempt.terminalReason)
        assertFalse(rangeGuard.onResponseBytes(1))
    }

    @Test
    fun `redirect guard rejects a loop and permanently excludes its rendition`() {
        val attempt = attempt()
        val request = requireNotNull(attempt.beginRequest(rendition, HomePlaybackRequestKind.INITIAL))
        val guard = HomePlaybackNetworkGuard(attempt, request)

        assertTrue(guard.onRequestStarted(rendition.url))
        assertTrue(guard.onRedirect("https://cdn.shopify.com/videos/other.mp4"))
        assertFalse(guard.onRedirect(rendition.url))
        assertTrue(rendition in attempt.rejectedRenditions)
    }

    @Test
    fun `retry controller permits one transport retry but never retries policy or budget rejection`() {
        val attempt = attempt()
        val request = requireNotNull(attempt.beginRequest(rendition, HomePlaybackRequestKind.INITIAL))
        val controller = HomePlaybackRetryController(attempt, request)

        assertTrue(controller.shouldRetry(java.io.IOException("transport")))
        assertFalse(controller.shouldRetry(java.io.IOException("second transport")))
        assertFalse(controller.shouldRetry(com.gurbakir.storefront.StorefrontMediaRejectedException()))
        assertFalse(controller.shouldRetry(com.gurbakir.storefront.StorefrontMediaLimitExceededException()))
    }

    private fun attempt(maxBytes: Long = 32L * 1024L * 1024L): HomePlaybackAttempt = HomePlaybackAttempt(
        id = 9,
        clock = HomePlaybackClock { 0 },
        limits = HomePlaybackLimits(maxResponseBytes = maxBytes)
    ).also(HomePlaybackAttempt::start)
}
