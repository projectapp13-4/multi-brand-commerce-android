@file:Suppress("DEPRECATION")

package com.gurbakir.mobile.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.gurbakir.mobile.CoreTestTheme
import com.gurbakir.storefront.StorefrontMediaPolicy
import com.gurbakir.storefront.StorefrontVideoSource
import java.net.URI
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OwnedHomeVideoPlayerTest {
    // Media3 is main-thread confined; the v2 rule dispatches effects on the test thread.
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun duckableTransientAndPermanentAudioFocusLossPauseWithoutAutomaticResume() {
        val (section, coordinator) = startOwnedVideo()
        composeRule.waitUntil(15_000) { coordinator.session(section).currentAttempt?.firstFrameRendered == true }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val audio = context.getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager
        val competingListener = android.media.AudioManager.OnAudioFocusChangeListener { }
        try {
            listOf(
                android.media.AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK,
                android.media.AudioManager.AUDIOFOCUS_GAIN_TRANSIENT,
                android.media.AudioManager.AUDIOFOCUS_GAIN
            ).forEachIndexed { index, gain ->
                if (index > 0) composeRule.onNodeWithContentDescription("Play").performClick()
                composeRule.waitUntil(3_000) { coordinator.session(section).isPlaying }
                assertEquals(
                    android.media.AudioManager.AUDIOFOCUS_REQUEST_GRANTED,
                    audio.requestAudioFocus(competingListener, android.media.AudioManager.STREAM_MUSIC, gain)
                )
                composeRule.waitUntil(3_000) { !coordinator.session(section).isPlaying }
                assertEquals(HomePlaybackPauseReason.AUDIO_FOCUS_LOST, coordinator.session(section).lastPauseReason)
                audio.abandonAudioFocus(competingListener)
                composeRule.waitForIdle()
                assertFalse(coordinator.session(section).isPlaying)
                composeRule.onNodeWithContentDescription("Play").assertIsDisplayed()
            }
        } finally {
            audio.abandonAudioFocus(competingListener)
        }
    }

    @Test
    fun scrollingOnlyPlayerOutOfViewportReleasesItAndRequiresExplicitPlay() {
        val mediaUrl = InstrumentationRegistry.getArguments().getString("ownedHomeMediaUrl").orEmpty()
        assumeTrue("Requires an explicitly selected project-owned video", mediaUrl.isNotBlank())
        val section = ownedSection(mediaUrl).copy(caption = "Caption line\n".repeat(60))
        val coordinator = HomePlaybackCoordinator(StorefrontMediaPolicy("multi-brand-trial-store.myshopify.com"))
        coordinator.session(section).updatePosition(1_000)
        lateinit var scroll: androidx.compose.foundation.ScrollState
        composeRule.setContent {
            CoreTestTheme {
                scroll = rememberScrollState()
                Column(Modifier.verticalScroll(scroll)) { HomeVideoPlayer(section, coordinator) }
            }
        }
        composeRule.onNodeWithTag(HomeTestTags.videoPlay(section.stableId)).performClick()
        composeRule.waitUntil(15_000) { coordinator.session(section).currentAttempt?.firstFrameRendered == true }
        val session = coordinator.session(section)
        val attempt = session.currentAttempt
        val playerHeight = composeRule.onNodeWithTag(HomeTestTags.VIDEO_PLAYER).fetchSemanticsNode().size.height
        composeRule.runOnIdle { kotlinx.coroutines.runBlocking { scroll.scrollTo(playerHeight + 30) } }
        composeRule.waitUntil(3_000) { !session.isPlaying }
        composeRule.onNodeWithTag(HomeTestTags.VIDEO_PLAYER).assertDoesNotExist()
        assertEquals(HomePlaybackPauseReason.VISIBILITY_LOST, session.lastPauseReason)
        assertTrue(session.playbackPositionMillis > 0)
        val position = session.playbackPositionMillis
        val bytes = requireNotNull(attempt).responseBytesRead
        composeRule.runOnIdle { kotlinx.coroutines.runBlocking { scroll.scrollTo(0) } }
        composeRule.onNodeWithTag(HomeTestTags.videoPlay(section.stableId)).assertIsDisplayed()
        assertFalse(session.isPlaying)
        assertEquals(attempt, session.currentAttempt)
        assertEquals(position, session.playbackPositionMillis)
        assertEquals(bytes, attempt.responseBytesRead)
    }

    @Test
    fun playerLeavingViewportWhileItsHomeLazyItemCaptionRemainsComposedPauses() {
        val mediaUrl = InstrumentationRegistry.getArguments().getString("ownedHomeMediaUrl").orEmpty()
        assumeTrue("Requires an explicitly selected project-owned video", mediaUrl.isNotBlank())
        val section = ownedSection(mediaUrl).copy(caption = "Caption line\n".repeat(60))
        val coordinator = HomePlaybackCoordinator(StorefrontMediaPolicy("multi-brand-trial-store.myshopify.com"))
        val session = coordinator.session(section)
        session.updatePosition(1_000)
        composeRule.setContent {
            CoreTestTheme {
                HomeScreen(
                    HomeUiState(
                        presentation = HomePresentation(
                            HomeEditorialState.Packaged,
                            listOf(section),
                            HomeContentSource.REMOTE,
                            HomeResourceStatus.COMPLETE,
                            null
                        ),
                        loading = false,
                        requestActive = false
                    ),
                    "Test",
                    HomeActions(refreshContent = {}, playbackCoordinator = coordinator)
                )
            }
        }
        composeRule.onNodeWithTag(HomeTestTags.videoPlay(section.stableId)).performScrollTo().performClick()
        composeRule.waitUntil(15_000) { session.currentAttempt?.firstFrameRendered == true }
        val attempt = session.currentAttempt
        val height = composeRule.onNodeWithTag(HomeTestTags.VIDEO_PLAYER).fetchSemanticsNode().size.height
        composeRule.onNodeWithTag(HomeTestTags.CONTENT).performSemanticsAction(SemanticsActions.ScrollBy) {
            it(0f, height + 200f)
        }
        composeRule.waitUntil(3_000) { !session.isPlaying }
        composeRule.onNodeWithTag(HomeTestTags.video(section.stableId)).assertExists()
        composeRule.onNodeWithTag(HomeTestTags.VIDEO_PLAYER).assertDoesNotExist()
        assertTrue(session.playbackPositionMillis >= 1_000)
        composeRule.onNodeWithTag(HomeTestTags.CONTENT).performScrollToIndex(0)
        composeRule.onNodeWithTag(HomeTestTags.videoPlay(section.stableId)).assertIsDisplayed()
        assertEquals(attempt, session.currentAttempt)
        assertFalse(session.isPlaying)
    }

    @Test
    fun playingVideoExposesPauseControlInsideScrollableHome() {
        val (section, coordinator) = startOwnedVideo()
        composeRule.onNodeWithContentDescription("Pause").assertIsDisplayed()
        composeRule.waitUntil(15_000) { coordinator.session(section).currentAttempt?.firstFrameRendered == true }
        assertTrue(requireNotNull(coordinator.session(section).currentAttempt).responseBytesRead > 0)
    }

    @Test
    fun playingVideoCanBeMutedAndUnmuted() {
        startOwnedVideo()
        composeRule.onNodeWithContentDescription("Pause").performClick()
        composeRule.onNodeWithContentDescription("Mute").assertIsDisplayed().performClick()
        composeRule.onNodeWithContentDescription("Unmute").assertIsDisplayed().performClick()
        composeRule.onNodeWithContentDescription("Mute").assertIsDisplayed()
    }

    @Test
    fun normalCompletionRestoresPosterWithoutAnError() {
        val (section, coordinator) = startOwnedVideo()
        composeRule.waitUntil(15_000) {
            coordinator.session(section).currentAttempt?.terminalReason == HomePlaybackTerminalReason.COMPLETED
        }
        composeRule.onNodeWithTag(HomeTestTags.videoPlay(section.stableId)).assertIsDisplayed()
        composeRule.onNodeWithTag(HomeTestTags.videoError(section.stableId)).assertDoesNotExist()
    }

    private fun startOwnedVideo(): Pair<HomeRenderedSection.Video, HomePlaybackCoordinator> {
        val mediaUrl = InstrumentationRegistry.getArguments().getString("ownedHomeMediaUrl").orEmpty()
        assumeTrue("Requires an explicitly selected project-owned video", mediaUrl.isNotBlank())
        val section = ownedSection(mediaUrl)
        val coordinator = HomePlaybackCoordinator(StorefrontMediaPolicy("multi-brand-trial-store.myshopify.com"))
        composeRule.setContent {
            CoreTestTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    HomeVideoPlayer(section, coordinator)
                }
            }
        }
        composeRule.onNodeWithTag(HomeTestTags.videoPlay(section.stableId)).performClick()
        return section to coordinator
    }

    private fun ownedSection(mediaUrl: String) = HomeRenderedSection.Video(
        stableId = "owned-video",
        title = HomeText.Remote("Video"),
        sources = listOf(StorefrontVideoSource(URI(mediaUrl), "video/mp4", "mp4", 1280, 720)),
        poster = null,
        altText = "Owned video",
        caption = null,
        target = null,
        revisionKey = "owned-revision"
    )
}
