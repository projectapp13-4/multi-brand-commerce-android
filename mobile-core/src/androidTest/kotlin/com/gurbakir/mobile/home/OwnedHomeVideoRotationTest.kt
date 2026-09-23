@file:Suppress("DEPRECATION")

package com.gurbakir.mobile.home

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.lifecycle.ViewModel
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.gurbakir.mobile.CoreTestTheme
import com.gurbakir.storefront.StorefrontMediaPolicy
import com.gurbakir.storefront.StorefrontVideoSource
import java.net.URI
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OwnedHomeVideoRotationTest {
    @get:Rule val composeRule = createAndroidComposeRule<OwnedHomeVideoRotationActivity>()

    @Test
    fun rotationRebuildKeepsAttemptBudgetAndReturnsPaused() {
        val mediaUrl = InstrumentationRegistry.getArguments().getString("ownedHomeMediaUrl").orEmpty()
        assumeTrue("Requires an explicitly selected project-owned video", mediaUrl.isNotBlank())
        val beforeActivity = composeRule.activity
        val holder = beforeActivity.holder
        val session = holder.coordinator.session(holder.section)

        composeRule.onNodeWithTag(HomeTestTags.videoPlay(holder.section.stableId)).performClick()
        composeRule.waitUntil(15_000) { session.currentAttempt?.firstFrameRendered == true }
        val attempt = requireNotNull(session.currentAttempt)
        val bytesBefore = attempt.responseBytesRead
        val positionBefore = session.playbackPositionMillis
        val nextOrientation =
            if (beforeActivity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            } else {
                ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            }

        composeRule.runOnIdle {
            beforeActivity.requestedOrientation = nextOrientation
        }
        composeRule.waitUntil(10_000) { composeRule.activity !== beforeActivity }

        val afterActivity = composeRule.activity
        val afterHolder = afterActivity.holder
        val afterSession = afterHolder.coordinator.session(afterHolder.section)
        composeRule.onNodeWithTag(HomeTestTags.videoPlay(afterHolder.section.stableId)).assertIsDisplayed()

        assertNotSame(beforeActivity, afterActivity)
        assertSame(holder, afterHolder)
        assertSame(attempt, afterSession.currentAttempt)
        assertTrue(attempt.firstFrameRendered)
        assertTrue(attempt.responseBytesRead >= bytesBefore)
        assertTrue(afterSession.playbackPositionMillis >= positionBefore)
        assertEquals(HomePlaybackPauseReason.VISIBILITY_LOST, afterSession.lastPauseReason)
        assertFalse(afterSession.isPlaying)
        assertFalse(afterSession.shouldAutoResume)
    }
}

class OwnedHomeVideoRotationActivity : ComponentActivity() {
    internal val holder by viewModels<OwnedHomeVideoRotationHolder>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        holder.initialize(
            InstrumentationRegistry.getArguments().getString("ownedHomeMediaUrl").orEmpty()
        )
        setContent {
            CoreTestTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    HomeVideoPlayer(holder.section, holder.coordinator)
                }
            }
        }
    }
}

class OwnedHomeVideoRotationHolder : ViewModel() {
    lateinit var section: HomeRenderedSection.Video
        private set
    lateinit var coordinator: HomePlaybackCoordinator
        private set

    fun initialize(mediaUrl: String) {
        if (::section.isInitialized) return
        section =
            HomeRenderedSection.Video(
                stableId = "owned-video-rotation",
                title = HomeText.Remote("Video"),
                sources =
                    listOf(
                        StorefrontVideoSource(
                            URI(mediaUrl),
                            "video/mp4",
                            "mp4",
                            1280,
                            720
                        )
                    ),
                poster = null,
                altText = "Owned video rotation",
                caption = null,
                target = null,
                revisionKey = "owned-rotation-revision"
            )
        coordinator = HomePlaybackCoordinator(StorefrontMediaPolicy("multi-brand-trial-store.myshopify.com"))
    }
}
