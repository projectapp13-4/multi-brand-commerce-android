package com.gurbakir.mobile.home

import java.util.concurrent.atomic.AtomicLong
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HomePlaybackAttemptTest {
    private val first = rendition("https://cdn.shopify.com/videos/a.mp4", width = 1280)
    private val second = rendition("https://cdn.shopify.com/videos/b.mp4", width = 960)

    @Test
    fun `initial range and seek may reuse one valid URL without resetting the attempt budget`() {
        val attempt = attempt(maxBytes = 32)

        val initial = requireNotNull(attempt.beginRequest(first, HomePlaybackRequestKind.INITIAL))
        assertTrue(attempt.recordResponseBytes(8))
        val range = requireNotNull(attempt.beginRequest(first, HomePlaybackRequestKind.RANGE))
        assertTrue(attempt.recordResponseBytes(9))
        val seek = requireNotNull(attempt.beginRequest(first, HomePlaybackRequestKind.SEEK))
        assertTrue(attempt.recordResponseBytes(10))

        assertNotEquals(initial.id, range.id)
        assertNotEquals(range.id, seek.id)
        assertEquals(27, attempt.responseBytesRead)
        assertEquals(HomePlaybackAttemptState.ACTIVE, attempt.state)
    }

    @Test
    fun `one transport retry is allowed per request and a second automatic retry is denied`() {
        val attempt = attempt()
        val initial = requireNotNull(attempt.beginRequest(first, HomePlaybackRequestKind.INITIAL))

        assertTrue(attempt.permitTransportRetry(initial))
        assertFalse(attempt.permitTransportRetry(initial))

        val range = requireNotNull(attempt.beginRequest(first, HomePlaybackRequestKind.RANGE))
        assertTrue(attempt.permitTransportRetry(range))
        assertFalse(attempt.permitTransportRetry(range))
    }

    @Test
    fun `permanently rejected rendition is excluded while fallback keeps the same budget`() {
        val attempt = attempt(maxBytes = 12)

        assertEquals(first, attempt.selectRendition(listOf(first, second)))
        assertTrue(attempt.recordResponseBytes(7))
        attempt.rejectRendition(first, HomeRenditionRejection.TERMINAL_PLAYBACK)

        assertEquals(second, attempt.selectRendition(listOf(first, second)))
        assertTrue(attempt.recordResponseBytes(5))
        assertFalse(attempt.recordResponseBytes(1))
        assertEquals(12, attempt.responseBytesRead)
        assertEquals(HomePlaybackTerminalReason.BYTE_BUDGET, attempt.terminalReason)
    }

    @Test
    fun `redirect loops stop at the first repeated normalized URL`() {
        val attempt = attempt()
        val request = requireNotNull(attempt.beginRequest(first, HomePlaybackRequestKind.INITIAL))

        assertEquals(
            HomeRedirectDecision.ALLOWED,
            attempt.followRedirect(request, "https://cdn.shopify.com/videos/b.mp4")
        )
        assertEquals(
            HomeRedirectDecision.LOOP,
            attempt.followRedirect(request, "https://CDN.SHOPIFY.COM:443/videos/./a.mp4")
        )
        assertTrue(first in attempt.rejectedRenditions)

        val selfAttempt = attempt()
        val self = requireNotNull(selfAttempt.beginRequest(first, HomePlaybackRequestKind.INITIAL))
        assertEquals(HomeRedirectDecision.LOOP, selfAttempt.followRedirect(self, first.url))
    }

    @Test
    fun `redirect hop limit is five and a new valid range request starts a fresh chain`() {
        val attempt = attempt()
        val request = requireNotNull(attempt.beginRequest(first, HomePlaybackRequestKind.INITIAL))

        repeat(5) { index ->
            assertEquals(
                HomeRedirectDecision.ALLOWED,
                attempt.followRedirect(request, "https://cdn.shopify.com/videos/hop-$index.mp4")
            )
        }
        assertEquals(
            HomeRedirectDecision.HOP_LIMIT,
            attempt.followRedirect(request, "https://cdn.shopify.com/videos/too-far.mp4")
        )

        val freshAttempt = attempt()
        val initial = requireNotNull(freshAttempt.beginRequest(first, HomePlaybackRequestKind.INITIAL))
        assertEquals(HomeRedirectDecision.ALLOWED, freshAttempt.followRedirect(initial, second.url))
        val range = requireNotNull(freshAttempt.beginRequest(first, HomePlaybackRequestKind.RANGE))
        assertEquals(HomeRedirectDecision.ALLOWED, freshAttempt.followRedirect(range, second.url))
    }

    @Test
    fun `pause resume rotation and rebuild preserve attempt identity bytes and deadlines`() {
        val clock = FakePlaybackClock()
        val session = session(clock, maxBytes = 20)
        val original = session.play()
        assertTrue(original.recordResponseBytes(7))
        session.updatePosition(4_321)

        clock.advanceBy(5_000)
        session.pause(HomePlaybackPauseReason.USER)
        clock.advanceBy(5_000)
        assertSame(original, session.play())
        session.detachForRebuild()
        clock.advanceBy(9_999)
        assertSame(original, session.attachAfterRebuild())

        assertEquals(7, original.responseBytesRead)
        assertEquals(4_321, session.playbackPositionMillis)
        assertNull(original.checkDeadlines())
        clock.advanceBy(1)
        assertEquals(HomePlaybackTerminalReason.FIRST_FRAME_TIMEOUT, original.checkDeadlines())
    }

    @Test
    fun `post first frame buffering has continuous and cumulative finite limits`() {
        val clock = FakePlaybackClock()
        val attempt = attempt(clock = clock)
        attempt.renderedFirstFrame()

        attempt.bufferingStarted()
        clock.advanceBy(15_000)
        assertNull(attempt.bufferingEnded())
        attempt.bufferingStarted()
        clock.advanceBy(14_999)
        assertNull(attempt.checkDeadlines())
        clock.advanceBy(1)
        assertEquals(HomePlaybackTerminalReason.CUMULATIVE_BUFFERING_TIMEOUT, attempt.bufferingEnded())

        val continuousClock = FakePlaybackClock()
        val continuous = attempt(clock = continuousClock)
        continuous.renderedFirstFrame()
        continuous.bufferingStarted()
        continuousClock.advanceBy(20_000)
        assertEquals(HomePlaybackTerminalReason.CONTINUOUS_BUFFERING_TIMEOUT, continuous.checkDeadlines())
    }

    @Test
    fun `visibility audio focus and noisy events pause without automatic resume`() {
        val session = session(FakePlaybackClock())
        val original = session.play()

        listOf(
            HomePlaybackPauseReason.VISIBILITY_LOST,
            HomePlaybackPauseReason.AUDIO_FOCUS_LOST,
            HomePlaybackPauseReason.BECOMING_NOISY
        ).forEach { reason ->
            session.markPlaying()
            session.pause(reason)
            assertFalse(session.isPlaying)
            assertFalse(session.shouldAutoResume)
            assertSame(original, session.currentAttempt)
        }
    }

    @Test
    fun `explicit retry is the only operation that creates a new attempt after terminal failure`() {
        val clock = FakePlaybackClock()
        val session = session(clock, maxBytes = 1)
        val failed = session.play()
        assertTrue(failed.recordResponseBytes(1))
        assertFalse(failed.recordResponseBytes(1))

        assertSame(failed, session.play())
        val retried = session.retry()

        assertNotEquals(failed.id, retried.id)
        assertEquals(0, retried.responseBytesRead)
        assertEquals(HomePlaybackAttemptState.ACTIVE, retried.state)
    }

    @Test
    fun `source revision reconciliation cancels and removes the old session`() {
        val registry = HomePlaybackSessionRegistry(FakePlaybackClock(), AtomicLong()::incrementAndGet)
        val oldIdentity = HomeVideoIdentity("video-section", "revision-one")
        val nextIdentity = HomeVideoIdentity("video-section", "revision-two")
        val oldSession = registry.session(oldIdentity, listOf(first))
        val oldAttempt = oldSession.play()

        registry.reconcile(setOf(nextIdentity))

        assertEquals(HomePlaybackTerminalReason.SOURCE_CHANGED, oldAttempt.terminalReason)
        assertNull(registry.existing(oldIdentity))
        assertNotEquals(oldSession, registry.session(nextIdentity, listOf(second)))
    }

    @Test
    fun `returning after background termination obtains a clean paused session`() {
        val registry = HomePlaybackSessionRegistry(FakePlaybackClock(), AtomicLong()::incrementAndGet)
        val identity = HomeVideoIdentity("video-section", "revision")
        val oldSession = registry.session(identity, listOf(first))
        val oldAttempt = oldSession.play()

        registry.finishAll(HomePlaybackTerminalReason.NAVIGATION)
        val resumedSession = registry.session(identity, listOf(first))

        assertEquals(HomePlaybackTerminalReason.NAVIGATION, oldAttempt.terminalReason)
        assertNotEquals(oldSession, resumedSession)
        assertNull(resumedSession.currentAttempt)
        assertFalse(resumedSession.isPlaying)
    }

    private fun attempt(
        clock: FakePlaybackClock = FakePlaybackClock(),
        maxBytes: Long = 32L * 1024L * 1024L
    ): HomePlaybackAttempt = HomePlaybackAttempt(
        id = 1,
        clock = clock,
        limits = HomePlaybackLimits(maxResponseBytes = maxBytes)
    ).also(HomePlaybackAttempt::start)

    private fun session(clock: FakePlaybackClock, maxBytes: Long = 32L * 1024L * 1024L): HomePlaybackSession =
        HomePlaybackSession(
            identity = HomeVideoIdentity("video-section", "revision"),
            renditions = listOf(first, second),
            clock = clock,
            limits = HomePlaybackLimits(maxResponseBytes = maxBytes),
            nextAttemptId = AtomicLong()::incrementAndGet
        )

    private fun rendition(url: String, width: Int): HomeVideoRendition = HomeVideoRendition(
        url = url,
        mimeType = "video/mp4",
        format = "mp4",
        width = width,
        height = 720
    )
}

private class FakePlaybackClock : HomePlaybackClock {
    private var now = 0L

    override fun nowMillis(): Long = now

    fun advanceBy(durationMillis: Long) {
        now += durationMillis
    }
}
