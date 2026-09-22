package com.gurbakir.mobile.home

import android.content.Context
import android.os.SystemClock
import androidx.media3.datasource.DataSource
import coil3.ImageLoader
import com.gurbakir.storefront.StorefrontMediaPolicy
import javax.inject.Inject

internal typealias HomePlaybackDataSourceProvider =
    (HomePlaybackAttempt, HomeVideoRendition) -> DataSource.Factory

class HomePlaybackCoordinator internal constructor(
    private val mediaPolicy: StorefrontMediaPolicy,
    clock: HomePlaybackClock,
    limits: HomePlaybackLimits = HomePlaybackLimits(),
    private val dataSourceProvider: HomePlaybackDataSourceProvider? = null,
    internal val maxForwardBufferMillis: Int = HomeVideoPlayerPolicy.MAX_FORWARD_BUFFER_MILLIS
) {
    init {
        require(maxForwardBufferMillis >= MIN_PLAYER_BUFFER_MILLIS)
    }

    @Inject
    constructor(mediaPolicy: StorefrontMediaPolicy) : this(
        mediaPolicy,
        HomePlaybackClock(SystemClock::elapsedRealtime)
    )

    private val registry = HomePlaybackSessionRegistry(clock, limits = limits)
    private var v2ImageLoader: ImageLoader? = null

    internal fun session(section: HomeRenderedSection.Video): HomePlaybackSession =
        registry.session(section.identity(), section.renditions())

    internal fun dataSourceFactory(attempt: HomePlaybackAttempt, rendition: HomeVideoRendition): DataSource.Factory =
        dataSourceProvider?.invoke(attempt, rendition)
            ?: HomePlaybackDataSourceFactory(attempt, rendition, mediaPolicy)

    @Synchronized
    internal fun imageLoader(context: Context): ImageLoader =
        v2ImageLoader ?: createHomeV2ImageLoader(context.applicationContext, mediaPolicy).also {
            v2ImageLoader = it
        }

    internal fun reconcile(presentation: HomePresentation?) {
        val identities =
            presentation?.renderedSections.orEmpty()
                .filterIsInstance<HomeRenderedSection.Video>()
                .mapTo(linkedSetOf(), HomeRenderedSection.Video::identity)
        registry.reconcile(identities)
    }

    internal fun finishAll(reason: HomePlaybackTerminalReason) {
        registry.finishAll(reason)
    }

    @Synchronized
    internal fun close(reason: HomePlaybackTerminalReason) {
        registry.finishAll(reason)
        v2ImageLoader?.shutdown()
        v2ImageLoader = null
    }
}

private fun HomeRenderedSection.Video.identity(): HomeVideoIdentity = HomeVideoIdentity(stableId, revisionKey)

private fun HomeRenderedSection.Video.renditions(): List<HomeVideoRendition> = sources
    .asSequence()
    .filter { source ->
        source.mimeType.equals("video/mp4", ignoreCase = true) &&
            source.format.equals("mp4", ignoreCase = true) &&
            source.width in 1..HOME_VIDEO_MAX_WIDTH &&
            source.height in 1..HOME_VIDEO_MAX_HEIGHT
    }
    .map { source ->
        HomeVideoRendition(
            url = source.uri.toASCIIString(),
            mimeType = source.mimeType,
            format = source.format,
            width = source.width,
            height = source.height
        )
    }
    .distinctBy(HomeVideoRendition::stableKey)
    .sortedWith(
        compareByDescending<HomeVideoRendition> { rendition -> rendition.width * rendition.height }
            .thenByDescending(HomeVideoRendition::width)
            .thenBy(HomeVideoRendition::stableKey)
    )
    .toList()

private const val HOME_VIDEO_MAX_WIDTH = 1280
private const val HOME_VIDEO_MAX_HEIGHT = 720
private const val MIN_PLAYER_BUFFER_MILLIS = 1_000
