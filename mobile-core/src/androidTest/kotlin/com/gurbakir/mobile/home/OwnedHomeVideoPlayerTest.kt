@file:Suppress("DEPRECATION")

package com.gurbakir.mobile.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.gurbakir.mobile.CoreTestTheme
import com.gurbakir.storefront.StorefrontMediaPolicy
import com.gurbakir.storefront.StorefrontVideoSource
import java.net.URI
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
        val section = HomeRenderedSection.Video(
            stableId = "owned-video",
            title = HomeText.Remote("Video"),
            sources = listOf(StorefrontVideoSource(URI(mediaUrl), "video/mp4", "mp4", 1280, 720)),
            poster = null,
            altText = "Owned video",
            caption = null,
            target = null,
            revisionKey = "owned-revision"
        )
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
}
