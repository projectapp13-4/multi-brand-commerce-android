@file:Suppress("DEPRECATION")

package com.gurbakir.mobile.home

import android.net.Uri
import android.os.SystemClock
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.media3.common.C
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.gurbakir.mobile.CoreTestTheme
import com.gurbakir.storefront.StorefrontMediaPolicy
import com.gurbakir.storefront.StorefrontVideoSource
import java.io.InterruptedIOException
import java.net.URI
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeVideoPlayerDeadlineTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun realPlayerFirstFrameStallCancelsPendingIoAndWaitsForExplicitRetry() {
        val policy = StorefrontMediaPolicy(TRIAL_SHOP_DOMAIN)
        val probe = StallProbe()
        val coordinator =
            controlledCoordinator(shortFirstFrameLimits()) { _, _ ->
                DataSource.Factory { BlockingDataSource(probe) }
            }
        val section = ownedSection("https://cdn.shopify.com/videos/first-frame-stall.mp4", "first-frame-stall")
        show(section, coordinator)

        composeRule.onNodeWithTag(HomeTestTags.videoPlay(section.stableId)).performClick()
        val session = coordinator.session(section)
        composeRule.waitUntil(5_000) {
            session.currentAttempt?.terminalReason == HomePlaybackTerminalReason.FIRST_FRAME_TIMEOUT
        }
        val failedAttempt = requireNotNull(session.currentAttempt)
        composeRule.waitUntil(3_000) { probe.cancelledCount.get() > 0 }

        composeRule.onNodeWithTag(HomeTestTags.videoError(section.stableId)).assertIsDisplayed()
        composeRule.onNodeWithTag(HomeTestTags.videoPlay(section.stableId)).assertIsDisplayed()
        assertSame(failedAttempt, session.currentAttempt)
        assertEquals(HomePlaybackTerminalReason.FIRST_FRAME_TIMEOUT, failedAttempt.terminalReason)
        assertTrue(probe.readEntered.await(0, TimeUnit.MILLISECONDS))

        composeRule.onNodeWithTag(HomeTestTags.videoPlay(section.stableId)).performClick()
        composeRule.waitUntil(2_000) { session.currentAttempt?.id != failedAttempt.id }
        assertNotEquals(failedAttempt.id, requireNotNull(session.currentAttempt).id)
    }

    @Test
    fun realPlayerPostFrameStallUsesFiniteBufferingDeadlineCancelsIoAndWaitsForRetry() {
        val mediaUrl = InstrumentationRegistry.getArguments().getString("ownedHomeMediaUrl").orEmpty()
        assumeTrue("Requires an explicitly selected project-owned video", mediaUrl.isNotBlank())
        val policy = StorefrontMediaPolicy(TRIAL_SHOP_DOMAIN)
        val probe = StallProbe()
        val coordinator =
            controlledCoordinator(shortPostFrameLimits()) { attempt, rendition ->
                PostFirstFrameStallFactory(
                    delegate = HomePlaybackDataSourceFactory(attempt, rendition, policy),
                    attempt = attempt,
                    probe = probe
                )
            }
        val section = ownedSection(mediaUrl, "post-frame-stall")
        show(section, coordinator)

        composeRule.onNodeWithTag(HomeTestTags.videoPlay(section.stableId)).performClick()
        val session = coordinator.session(section)
        composeRule.waitUntil(15_000) { session.currentAttempt?.firstFrameRendered == true }
        val failedAttempt = requireNotNull(session.currentAttempt)
        composeRule.waitUntil(5_000) { probe.stallEntryCount.get() > 0 }
        val reachedTerminal = runCatching {
            composeRule.waitUntil(25_000) { failedAttempt.terminalReason != null }
        }.isSuccess
        assertTrue(
            "terminal=${failedAttempt.terminalReason}, stallEntered=${probe.readEntered.count == 0L}, " +
                "bytes=${failedAttempt.responseBytesRead}, buffering=${failedAttempt.postFirstFrameBufferingMillis}, " +
                "playing=${session.isPlaying}, pause=${session.lastPauseReason}",
            reachedTerminal
        )
        assertEquals(HomePlaybackTerminalReason.CONTINUOUS_BUFFERING_TIMEOUT, failedAttempt.terminalReason)
        composeRule.waitUntil(3_000) { probe.cancelledCount.get() > 0 }

        composeRule.onNodeWithTag(HomeTestTags.videoError(section.stableId)).assertIsDisplayed()
        composeRule.onNodeWithTag(HomeTestTags.videoPlay(section.stableId)).assertIsDisplayed()
        assertSame(failedAttempt, session.currentAttempt)
        assertTrue(failedAttempt.firstFrameRendered)
        assertTrue(failedAttempt.responseBytesRead > 0)
        assertEquals(HomePlaybackTerminalReason.CONTINUOUS_BUFFERING_TIMEOUT, failedAttempt.terminalReason)
        assertTrue(probe.readEntered.await(0, TimeUnit.MILLISECONDS))

        composeRule.onNodeWithTag(HomeTestTags.videoPlay(section.stableId)).performClick()
        composeRule.waitUntil(2_000) { session.currentAttempt?.id != failedAttempt.id }
        assertNotEquals(failedAttempt.id, requireNotNull(session.currentAttempt).id)
    }

    private fun controlledCoordinator(
        limits: HomePlaybackLimits,
        provider: (HomePlaybackAttempt, HomeVideoRendition) -> DataSource.Factory
    ): HomePlaybackCoordinator {
        val constructor = HomePlaybackCoordinator::class.java.declaredConstructors.singleOrNull { candidate ->
            val parameters = candidate.parameterTypes
            parameters.size == 5 &&
                parameters[0] == StorefrontMediaPolicy::class.java &&
                parameters[1] == HomePlaybackClock::class.java &&
                parameters[2] == HomePlaybackLimits::class.java &&
                parameters[3] == Function2::class.java &&
                parameters[4] == Int::class.javaPrimitiveType
        } ?: throw AssertionError("Controlled real-player data-source seam is missing")
        constructor.isAccessible = true
        return constructor.newInstance(
            StorefrontMediaPolicy(TRIAL_SHOP_DOMAIN),
            HomePlaybackClock(SystemClock::elapsedRealtime),
            limits,
            provider,
            CONTROLLED_MAX_FORWARD_BUFFER_MILLIS
        ) as HomePlaybackCoordinator
    }

    private fun show(section: HomeRenderedSection.Video, coordinator: HomePlaybackCoordinator) {
        composeRule.setContent {
            CoreTestTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    HomeVideoPlayer(section, coordinator)
                }
            }
        }
    }

    private fun ownedSection(mediaUrl: String, stableId: String) = HomeRenderedSection.Video(
        stableId = stableId,
        title = HomeText.Remote("Video"),
        sources = listOf(StorefrontVideoSource(URI(mediaUrl), "video/mp4", "mp4", 1280, 720)),
        poster = null,
        altText = "Owned deadline video",
        caption = null,
        target = null,
        revisionKey = "$stableId-revision"
    )
}

private class StallProbe {
    val readEntered = CountDownLatch(1)
    val stallEntryCount = AtomicInteger()
    val cancelledCount = AtomicInteger()
}

private class BlockingDataSource(private val probe: StallProbe) : DataSource {
    private val released = CountDownLatch(1)
    private var uri: Uri? = null

    override fun addTransferListener(transferListener: TransferListener) = Unit

    override fun open(dataSpec: DataSpec): Long {
        uri = dataSpec.uri
        return C.LENGTH_UNSET.toLong()
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        probe.stallEntryCount.incrementAndGet()
        probe.readEntered.countDown()
        try {
            released.await()
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
        probe.cancelledCount.incrementAndGet()
        throw InterruptedIOException("Controlled player read cancelled")
    }

    override fun getUri(): Uri? = uri

    override fun close() {
        released.countDown()
    }
}

private class PostFirstFrameStallFactory(
    private val delegate: DataSource.Factory,
    private val attempt: HomePlaybackAttempt,
    private val probe: StallProbe
) : DataSource.Factory {
    override fun createDataSource(): DataSource =
        PostFirstFrameStallDataSource(delegate.createDataSource(), attempt, probe)
}

private class PostFirstFrameStallDataSource(
    private val delegate: DataSource,
    private val attempt: HomePlaybackAttempt,
    private val probe: StallProbe
) : DataSource {
    private val released = CountDownLatch(1)

    override fun addTransferListener(transferListener: TransferListener) {
        delegate.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long = delegate.open(dataSpec)

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (attempt.firstFrameRendered) {
            probe.stallEntryCount.incrementAndGet()
            probe.readEntered.countDown()
            try {
                released.await()
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            }
            probe.cancelledCount.incrementAndGet()
            throw InterruptedIOException("Controlled post-frame read cancelled")
        }
        val result = delegate.read(buffer, offset, minOf(length, MAX_PRE_FRAME_READ_BYTES))
        if (result > 0) {
            try {
                Thread.sleep(PRE_FRAME_READ_DELAY_MILLIS)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                throw InterruptedIOException("Controlled pre-frame read cancelled")
            }
        }
        return result
    }

    override fun getUri(): Uri? = delegate.uri

    override fun getResponseHeaders(): Map<String, List<String>> = delegate.responseHeaders

    override fun close() {
        released.countDown()
        delegate.close()
    }
}

private fun shortFirstFrameLimits() = HomePlaybackLimits(
    firstFrameDeadlineMillis = 750,
    continuousBufferingLimitMillis = 1_000,
    cumulativeBufferingLimitMillis = 1_500
)

private fun shortPostFrameLimits() = HomePlaybackLimits(
    firstFrameDeadlineMillis = 12_000,
    continuousBufferingLimitMillis = 1_000,
    cumulativeBufferingLimitMillis = 1_500
)

private const val TRIAL_SHOP_DOMAIN = "multi-brand-trial-store.myshopify.com"
private const val MAX_PRE_FRAME_READ_BYTES = 8 * 1024
private const val PRE_FRAME_READ_DELAY_MILLIS = 5L
private const val CONTROLLED_MAX_FORWARD_BUFFER_MILLIS = 1_500
