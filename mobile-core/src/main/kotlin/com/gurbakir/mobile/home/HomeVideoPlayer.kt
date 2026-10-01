@file:Suppress("FunctionNaming", "LongMethod")
@file:androidx.annotation.OptIn(
    markerClass = [androidx.media3.common.util.UnstableApi::class, androidx.media3.common.util.ExperimentalApi::class]
)

package com.gurbakir.mobile.home

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import androidx.media3.ui.compose.material3.Player as Media3Player
import androidx.media3.ui.compose.material3.PlayerDefaults
import androidx.media3.ui.compose.material3.buttons.MuteButton
import com.gurbakir.mobile.core.R
import kotlinx.coroutines.delay

@Composable
internal fun HomeVideoPlayer(
    section: HomeRenderedSection.Video,
    coordinator: HomePlaybackCoordinator,
    modifier: Modifier = Modifier
) {
    var session by remember(section.stableId, section.revisionKey) { mutableStateOf(coordinator.session(section)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        session = coordinator.session(section)
    }
    var rendition by remember(section.stableId, section.revisionKey) { mutableStateOf<HomeVideoRendition?>(null) }
    var playerGeneration by remember(section.stableId, section.revisionKey) { mutableIntStateOf(0) }
    var terminalReason by remember(section.stableId, section.revisionKey) {
        mutableStateOf<HomePlaybackTerminalReason?>(null)
    }

    Column(
        modifier = modifier.fillMaxWidth().testTag(HomeTestTags.video(section.stableId)),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val activeRendition = rendition
        if (activeRendition == null) {
            VideoPoster(
                section = section,
                coordinator = coordinator,
                retry = terminalReason != null,
                onPlay = {
                    val existing = session.currentAttempt
                    val attempt = if (existing?.state == HomePlaybackAttemptState.TERMINAL) {
                        session.retry()
                    } else {
                        session.play()
                    }
                    terminalReason = null
                    rendition = attempt.selectRendition(session.renditions)
                    if (rendition == null) {
                        terminalReason = attempt.terminate(HomePlaybackTerminalReason.NO_RENDITION)
                    }
                    playerGeneration += 1
                }
            )
        } else {
            val attempt = requireNotNull(session.currentAttempt)
            ActiveHomeVideoPlayer(
                request =
                    ActiveHomeVideoRequest(
                        section = section,
                        session = session,
                        attempt = attempt,
                        rendition = activeRendition,
                        coordinator = coordinator,
                        generation = playerGeneration
                    ),
                callbacks =
                    HomePlayerCallbacks(
                        onBuffering = {},
                        onVisibilityLost = { rendition = null },
                        onRenditionFailure = {
                            attempt.rejectRendition(activeRendition, HomeRenditionRejection.TERMINAL_PLAYBACK)
                            val fallback = attempt.selectRendition(session.renditions)
                            if (fallback == null) {
                                terminalReason = attempt.terminate(HomePlaybackTerminalReason.NO_RENDITION)
                                rendition = null
                            } else {
                                rendition = fallback
                                playerGeneration += 1
                            }
                        },
                        onTerminal = { reason ->
                            terminalReason = reason.takeUnless {
                                it == HomePlaybackTerminalReason.COMPLETED ||
                                    it == HomePlaybackTerminalReason.NAVIGATION
                            }
                            rendition = null
                        }
                    )
            )
        }
        section.caption?.let { caption ->
            Text(caption, style = MaterialTheme.typography.bodyMedium)
        }
        terminalReason?.let {
            Text(
                stringResource(R.string.home_video_unavailable),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.testTag(HomeTestTags.videoError(section.stableId))
            )
        }
    }
}

private data class ActiveHomeVideoRequest(
    val section: HomeRenderedSection.Video,
    val session: HomePlaybackSession,
    val attempt: HomePlaybackAttempt,
    val rendition: HomeVideoRendition,
    val coordinator: HomePlaybackCoordinator,
    val generation: Int
)

private data class HomePlayerCallbacks(
    val onBuffering: (Boolean) -> Unit,
    val onVisibilityLost: () -> Unit,
    val onRenditionFailure: () -> Unit,
    val onTerminal: (HomePlaybackTerminalReason) -> Unit
)

@Composable
private fun VideoPoster(
    section: HomeRenderedSection.Video,
    coordinator: HomePlaybackCoordinator,
    retry: Boolean,
    onPlay: () -> Unit
) {
    val poster = section.poster
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val source = section.sources.firstOrNull()
        val viewportHeight = if (source == null) {
            HOME_VIDEO_MIN_HEIGHT
        } else {
            homeVideoViewportHeight(maxWidth, source.width, source.height)
        }
        Box(modifier = Modifier.fillMaxWidth().height(viewportHeight), contentAlignment = Alignment.Center) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxSize().testTag(HomeTestTags.VIDEO_POSTER),
                shape = MaterialTheme.shapes.medium
            ) {
                if (poster == null) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                        Text(section.altText, modifier = Modifier.padding(16.dp))
                    }
                } else {
                    HomeV2Image(
                        content = HomeV2ImageContent(poster, section.altText, section.revisionKey),
                        coordinator = coordinator,
                        modifier = Modifier.fillMaxSize(),
                        shape = MaterialTheme.shapes.medium
                    )
                }
            }
            FilledIconButton(
                onClick = onPlay,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = POSTER_CONTROL_ALPHA),
                    contentColor = MaterialTheme.colorScheme.onSurface
                ),
                modifier = Modifier.size(POSTER_CONTROL_SIZE).testTag(HomeTestTags.videoPlay(section.stableId))
            ) {
                Icon(
                    painterResource(if (retry) R.drawable.ic_refresh else R.drawable.ic_play),
                    contentDescription = stringResource(if (retry) R.string.retry else R.string.home_video_play)
                )
            }
        }
    }
}

private const val POSTER_CONTROL_ALPHA = 0.92f
private val POSTER_CONTROL_SIZE = 48.dp

@Composable
private fun ActiveHomeVideoPlayer(request: ActiveHomeVideoRequest, callbacks: HomePlayerCallbacks) {
    val context = LocalContext.current
    val player = remember(request.attempt.id, request.rendition.stableKey, request.generation) {
        createHomeVideoPlayer(context, request)
    }
    val audioFocus = remember(player) { HomeVideoAudioFocus(context, player, request.session) }
    var buffering by remember(player) { mutableStateOf(true) }
    val listener = remember(player, request.attempt, request.rendition) {
        homePlayerListener(
            player = player,
            session = request.session,
            attempt = request.attempt,
            audioFocus = audioFocus,
            callbacks =
                callbacks.copy(onBuffering = { isBuffering -> buffering = isBuffering })
        )
    }

    DisposableEffect(player, listener) {
        player.addListener(listener)
        player.prepare()
        player.play()
        onDispose {
            request.session.updatePosition(player.currentPosition)
            player.removeListener(listener)
            player.pause()
            request.session.pause(HomePlaybackPauseReason.VISIBILITY_LOST)
            request.session.detachForRebuild()
            audioFocus.abandon()
            player.release()
        }
    }

    val activity = remember(context) { context.findActivity() }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        request.session.updatePosition(player.currentPosition)
        player.pause()
        player.stop()
        if (activity?.isChangingConfigurations == true) {
            request.session.pause(HomePlaybackPauseReason.VISIBILITY_LOST)
            request.session.detachForRebuild()
        } else {
            request.session.finish(HomePlaybackTerminalReason.NAVIGATION)
            callbacks.onTerminal(HomePlaybackTerminalReason.NAVIGATION)
        }
    }

    LaunchedEffect(player, request.attempt) {
        while (request.attempt.state == HomePlaybackAttemptState.ACTIVE) {
            delay(HOME_PLAYBACK_DEADLINE_POLL_MILLIS)
            if (request.attempt.firstFrameRendered && player.playbackState == Player.STATE_BUFFERING) {
                request.attempt.bufferingStarted()
            }
            request.attempt.checkDeadlines()?.let { reason ->
                player.stop()
                callbacks.onTerminal(reason)
                return@LaunchedEffect
            }
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier.fillMaxWidth()
                .height(homeVideoViewportHeight(maxWidth, request.rendition.width, request.rendition.height))
                .onGloballyPositioned { coordinates ->
                    val visible = coordinates.boundsInWindow()
                    if (visible.width <= 0f || visible.height <= 0f) callbacks.onVisibilityLost()
                }
                .testTag(HomeTestTags.VIDEO_PLAYER),
            contentAlignment = Alignment.Center
        ) {
            Media3Player(
                player = player,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
                showControls = true,
                topControls = { controlledPlayer, visible ->
                    PlayerDefaults.TopControls(controlledPlayer, visible, Modifier.fillMaxWidth()) {
                        MuteButton(
                            it,
                            modifier = Modifier.align(Alignment.TopEnd),
                            colors = IconButtonDefaults.filledIconButtonColors()
                        )
                    }
                }
            )
            if (buffering) LinearProgressIndicator(modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter))
        }
    }
}

private fun createHomeVideoPlayer(context: Context, request: ActiveHomeVideoRequest): ExoPlayer {
    val loadControl =
        DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                request.coordinator.maxForwardBufferMillis,
                request.coordinator.maxForwardBufferMillis,
                HOME_BUFFER_FOR_PLAYBACK_MILLIS,
                HOME_BUFFER_AFTER_REBUFFER_MILLIS
            )
            .setBackBuffer(
                HomeVideoPlayerPolicy.BACK_BUFFER_MILLIS,
                HomeVideoPlayerPolicy.RETAIN_BACK_BUFFER_FROM_KEYFRAME
            )
            .build()
    val source =
        ProgressiveMediaSource.Factory(
            request.coordinator.dataSourceFactory(request.attempt, request.rendition)
        )
            .setLoadErrorHandlingPolicy(HomeNoAutomaticLoadRetryPolicy)
            .createMediaSource(
                MediaItem.Builder()
                    .setMediaId("${request.section.stableId}:${request.section.revisionKey}")
                    .setUri(request.rendition.url)
                    .setMimeType(request.rendition.mimeType)
                    .build()
            )
    return ExoPlayer.Builder(context)
        .setLoadControl(loadControl)
        .setAudioAttributes(androidx.media3.common.AudioAttributes.DEFAULT, false)
        .setHandleAudioBecomingNoisy(true)
        .build()
        .apply {
            repeatMode = Player.REPEAT_MODE_OFF
            playWhenReady = false
            setMediaSource(source)
            seekTo(request.session.playbackPositionMillis)
        }
}

private fun homePlayerListener(
    player: ExoPlayer,
    session: HomePlaybackSession,
    attempt: HomePlaybackAttempt,
    audioFocus: HomeVideoAudioFocus,
    callbacks: HomePlayerCallbacks
): Player.Listener = object : Player.Listener {
    override fun onRenderedFirstFrame() {
        attempt.renderedFirstFrame()?.let(callbacks.onTerminal)
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        when (playbackState) {
            Player.STATE_IDLE -> callbacks.onBuffering(false)

            Player.STATE_BUFFERING -> {
                callbacks.onBuffering(true)
                attempt.bufferingStarted()
            }

            Player.STATE_READY -> {
                callbacks.onBuffering(false)
                attempt.bufferingEnded()?.let(callbacks.onTerminal)
            }

            Player.STATE_ENDED -> {
                callbacks.onBuffering(false)
                session.finish(HomePlaybackTerminalReason.COMPLETED)
                callbacks.onTerminal(HomePlaybackTerminalReason.COMPLETED)
            }
        }
    }

    override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
        when {
            playWhenReady -> {
                if (audioFocus.request()) {
                    session.markPlaying()
                } else {
                    player.pause()
                    session.pause(HomePlaybackPauseReason.AUDIO_FOCUS_LOST)
                }
            }

            reason == Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS -> {
                session.pause(HomePlaybackPauseReason.AUDIO_FOCUS_LOST)
                player.pause()
            }

            reason == Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY -> {
                session.pause(HomePlaybackPauseReason.BECOMING_NOISY)
                player.pause()
            }

            else -> {
                audioFocus.abandon()
                session.pause(HomePlaybackPauseReason.USER)
            }
        }
    }

    override fun onPlaybackSuppressionReasonChanged(playbackSuppressionReason: Int) {
        if (playbackSuppressionReason != Player.PLAYBACK_SUPPRESSION_REASON_NONE) {
            session.pause(HomePlaybackPauseReason.AUDIO_FOCUS_LOST)
            player.pause()
        }
    }

    override fun onPlayerError(error: PlaybackException) {
        if (attempt.state == HomePlaybackAttemptState.TERMINAL) {
            callbacks.onTerminal(requireNotNull(attempt.terminalReason))
        } else {
            callbacks.onRenditionFailure()
        }
    }
}

private object HomeNoAutomaticLoadRetryPolicy : DefaultLoadErrorHandlingPolicy(0) {
    override fun getRetryDelayMsFor(loadErrorInfo: LoadErrorHandlingPolicy.LoadErrorInfo): Long = C.TIME_UNSET

    override fun getMinimumLoadableRetryCount(dataType: Int): Int = 0
}

private const val HOME_BUFFER_FOR_PLAYBACK_MILLIS = 500
private const val HOME_BUFFER_AFTER_REBUFFER_MILLIS = 1_000
private const val HOME_PLAYBACK_DEADLINE_POLL_MILLIS = 100L
private val HOME_VIDEO_MIN_HEIGHT = 220.dp
private val HOME_VIDEO_MAX_HEIGHT = 480.dp

internal fun homeVideoViewportHeight(width: Dp, videoWidth: Int, videoHeight: Int): Dp {
    if (videoWidth <= 0 || videoHeight <= 0) return HOME_VIDEO_MIN_HEIGHT
    return (width * (videoHeight.toFloat() / videoWidth))
        .coerceIn(HOME_VIDEO_MIN_HEIGHT, HOME_VIDEO_MAX_HEIGHT)
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
