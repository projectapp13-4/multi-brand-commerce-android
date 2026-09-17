package com.gurbakir.mobile.home

import java.net.URI
import java.util.concurrent.atomic.AtomicLong

private const val DEFAULT_PLAYBACK_RESPONSE_BUDGET_BYTES = 32L * 1024L * 1024L
private const val DEFAULT_FIRST_FRAME_DEADLINE_MILLIS = 20_000L
private const val DEFAULT_CONTINUOUS_BUFFERING_LIMIT_MILLIS = 20_000L
private const val DEFAULT_CUMULATIVE_BUFFERING_LIMIT_MILLIS = 30_000L
private const val DEFAULT_MAX_REDIRECT_HOPS = 5
private const val HTTPS_DEFAULT_PORT = 443
private const val HTTP_DEFAULT_PORT = 80

fun interface HomePlaybackClock {
    fun nowMillis(): Long
}

data class HomePlaybackLimits(
    val maxResponseBytes: Long = DEFAULT_PLAYBACK_RESPONSE_BUDGET_BYTES,
    val firstFrameDeadlineMillis: Long = DEFAULT_FIRST_FRAME_DEADLINE_MILLIS,
    val continuousBufferingLimitMillis: Long = DEFAULT_CONTINUOUS_BUFFERING_LIMIT_MILLIS,
    val cumulativeBufferingLimitMillis: Long = DEFAULT_CUMULATIVE_BUFFERING_LIMIT_MILLIS,
    val maxRedirectHops: Int = DEFAULT_MAX_REDIRECT_HOPS
) {
    init {
        require(maxResponseBytes > 0)
        require(firstFrameDeadlineMillis > 0)
        require(continuousBufferingLimitMillis > 0)
        require(cumulativeBufferingLimitMillis >= continuousBufferingLimitMillis)
        require(maxRedirectHops > 0)
    }
}

data class HomeVideoRendition(
    val url: String,
    val mimeType: String,
    val format: String,
    val width: Int,
    val height: Int
) {
    val stableKey: String = listOf(url, mimeType.lowercase(), format.lowercase(), width, height).joinToString("|")
}

data class HomeVideoIdentity(val stableId: String, val revisionKey: String)

enum class HomePlaybackRequestKind {
    INITIAL,
    RANGE,
    SEEK
}

class HomePlaybackRequestToken internal constructor(
    val id: Long,
    internal val attemptId: Long,
    internal val rendition: HomeVideoRendition,
    val kind: HomePlaybackRequestKind
)

enum class HomeRedirectDecision {
    ALLOWED,
    LOOP,
    HOP_LIMIT,
    INVALID
}

enum class HomeRenditionRejection {
    MIME,
    CONTAINER,
    DIMENSION,
    CODEC,
    ORIGIN,
    REDIRECT_LOOP,
    REDIRECT_HOP_LIMIT,
    TERMINAL_PLAYBACK
}

enum class HomePlaybackPauseReason {
    USER,
    VISIBILITY_LOST,
    AUDIO_FOCUS_LOST,
    BECOMING_NOISY
}

enum class HomePlaybackTerminalReason {
    BYTE_BUDGET,
    FIRST_FRAME_TIMEOUT,
    CONTINUOUS_BUFFERING_TIMEOUT,
    CUMULATIVE_BUFFERING_TIMEOUT,
    SOURCE_CHANGED,
    NAVIGATION,
    COMPLETED,
    USER_STOP,
    USER_RETRY,
    NO_RENDITION,
    PLAYER_ERROR
}

enum class HomePlaybackAttemptState {
    IDLE,
    ACTIVE,
    TERMINAL
}

@Suppress("TooManyFunctions") // The closed attempt state machine exposes one method per bounded player/network event.
class HomePlaybackAttempt internal constructor(
    val id: Long,
    private val clock: HomePlaybackClock,
    private val limits: HomePlaybackLimits = HomePlaybackLimits()
) {
    private val lock = Any()
    private val requestIds = AtomicLong()
    private val requests = mutableMapOf<Long, HomePlaybackRequestToken>()
    private val retriedRequests = mutableSetOf<Long>()
    private val rejectionReasons = linkedMapOf<HomeVideoRendition, HomeRenditionRejection>()
    private var firstFrameDeadlineStartedAtMillis: Long? = null
    private var bufferingStartedAtMillis: Long? = null
    private var redirectRequestId: Long? = null
    private val redirectChain = linkedSetOf<String>()

    @Volatile
    var state: HomePlaybackAttemptState = HomePlaybackAttemptState.IDLE
        private set

    @Volatile
    var terminalReason: HomePlaybackTerminalReason? = null
        private set

    @Volatile
    var responseBytesRead: Long = 0
        private set

    @Volatile
    var postFirstFrameBufferingMillis: Long = 0
        private set

    @Volatile
    var firstFrameRendered: Boolean = false
        private set

    val rejectedRenditions: Set<HomeVideoRendition>
        get() = synchronized(lock) { rejectionReasons.keys.toSet() }

    val currentRedirectChain: List<String>
        get() = synchronized(lock) { redirectChain.toList() }

    fun start() {
        synchronized(lock) {
            if (state != HomePlaybackAttemptState.IDLE) return
            state = HomePlaybackAttemptState.ACTIVE
            firstFrameDeadlineStartedAtMillis = clock.nowMillis()
        }
    }

    fun selectRendition(candidates: List<HomeVideoRendition>): HomeVideoRendition? = synchronized(lock) {
        candidates.firstOrNull { candidate -> candidate !in rejectionReasons }
    }

    fun rejectRendition(rendition: HomeVideoRendition, reason: HomeRenditionRejection) {
        synchronized(lock) { rejectionReasons.putIfAbsent(rendition, reason) }
    }

    fun beginRequest(rendition: HomeVideoRendition, kind: HomePlaybackRequestKind): HomePlaybackRequestToken? =
        synchronized(lock) {
            if (state != HomePlaybackAttemptState.ACTIVE || rendition in rejectionReasons) return@synchronized null
            val normalized = normalizePlaybackUrl(rendition.url) ?: run {
                rejectionReasons.putIfAbsent(rendition, HomeRenditionRejection.ORIGIN)
                return@synchronized null
            }
            val token = HomePlaybackRequestToken(requestIds.incrementAndGet(), id, rendition, kind)
            requests[token.id] = token
            redirectRequestId = token.id
            redirectChain.clear()
            redirectChain += normalized
            token
        }

    fun restartRedirectChain(token: HomePlaybackRequestToken, rawInitialUrl: String = token.rendition.url): Boolean =
        synchronized(lock) {
            val normalized = normalizePlaybackUrl(rawInitialUrl)
            if (!owns(token) || normalized == null || state != HomePlaybackAttemptState.ACTIVE) {
                false
            } else {
                redirectRequestId = token.id
                redirectChain.clear()
                redirectChain += normalized
                true
            }
        }

    fun followRedirect(token: HomePlaybackRequestToken, rawTargetUrl: String): HomeRedirectDecision =
        synchronized(lock) {
            if (!owns(token) || redirectRequestId != token.id || state != HomePlaybackAttemptState.ACTIVE) {
                return@synchronized HomeRedirectDecision.INVALID
            }
            val target = normalizePlaybackUrl(rawTargetUrl) ?: return@synchronized HomeRedirectDecision.INVALID
            if (target in redirectChain) {
                rejectionReasons.putIfAbsent(token.rendition, HomeRenditionRejection.REDIRECT_LOOP)
                return@synchronized HomeRedirectDecision.LOOP
            }
            if (redirectChain.size - 1 >= limits.maxRedirectHops) {
                rejectionReasons.putIfAbsent(token.rendition, HomeRenditionRejection.REDIRECT_HOP_LIMIT)
                return@synchronized HomeRedirectDecision.HOP_LIMIT
            }
            redirectChain += target
            HomeRedirectDecision.ALLOWED
        }

    fun permitTransportRetry(token: HomePlaybackRequestToken): Boolean = synchronized(lock) {
        owns(token) && state == HomePlaybackAttemptState.ACTIVE && retriedRequests.add(token.id)
    }

    fun recordResponseBytes(byteCount: Long): Boolean = synchronized(lock) {
        require(byteCount >= 0)
        if (state != HomePlaybackAttemptState.ACTIVE) return@synchronized false
        val remaining = limits.maxResponseBytes - responseBytesRead
        if (byteCount > remaining) {
            responseBytesRead = limits.maxResponseBytes
            terminateLocked(HomePlaybackTerminalReason.BYTE_BUDGET)
            false
        } else {
            responseBytesRead += byteCount
            true
        }
    }

    fun remainingResponseBytes(): Long = synchronized(lock) {
        (limits.maxResponseBytes - responseBytesRead).coerceAtLeast(0)
    }

    fun renderedFirstFrame(): HomePlaybackTerminalReason? = synchronized(lock) {
        checkDeadlinesLocked()?.let { return@synchronized it }
        firstFrameRendered = true
        bufferingStartedAtMillis = null
        null
    }

    fun bufferingStarted() {
        synchronized(lock) {
            if (state == HomePlaybackAttemptState.ACTIVE && bufferingStartedAtMillis == null) {
                bufferingStartedAtMillis = clock.nowMillis()
            }
        }
    }

    fun bufferingEnded(): HomePlaybackTerminalReason? = synchronized(lock) {
        val startedAt = bufferingStartedAtMillis ?: return@synchronized checkDeadlinesLocked()
        val duration = (clock.nowMillis() - startedAt).coerceAtLeast(0)
        bufferingStartedAtMillis = null
        if (firstFrameRendered) postFirstFrameBufferingMillis += duration
        when {
            firstFrameRendered && duration >= limits.continuousBufferingLimitMillis ->
                terminateLocked(HomePlaybackTerminalReason.CONTINUOUS_BUFFERING_TIMEOUT)

            firstFrameRendered && postFirstFrameBufferingMillis >= limits.cumulativeBufferingLimitMillis ->
                terminateLocked(HomePlaybackTerminalReason.CUMULATIVE_BUFFERING_TIMEOUT)

            else -> terminalReason
        }
    }

    fun checkDeadlines(): HomePlaybackTerminalReason? = synchronized(lock, ::checkDeadlinesLocked)

    fun terminate(reason: HomePlaybackTerminalReason): HomePlaybackTerminalReason = synchronized(lock) {
        terminateLocked(reason)
    }

    private fun owns(token: HomePlaybackRequestToken): Boolean = token.attemptId == id && requests[token.id] == token

    private fun checkDeadlinesLocked(): HomePlaybackTerminalReason? {
        val settledReason = terminalReason
        if (settledReason != null || state != HomePlaybackAttemptState.ACTIVE) return settledReason
        val now = clock.nowMillis()
        val firstFrameStartedAt = firstFrameDeadlineStartedAtMillis
        val bufferingStartedAt = bufferingStartedAtMillis
        val currentBufferingDuration = bufferingStartedAt?.let { startedAt -> (now - startedAt).coerceAtLeast(0) }
        val expired = when {
            !firstFrameRendered &&
                firstFrameStartedAt != null &&
                now - firstFrameStartedAt >= limits.firstFrameDeadlineMillis ->
                HomePlaybackTerminalReason.FIRST_FRAME_TIMEOUT

            firstFrameRendered &&
                currentBufferingDuration != null &&
                currentBufferingDuration >= limits.continuousBufferingLimitMillis ->
                HomePlaybackTerminalReason.CONTINUOUS_BUFFERING_TIMEOUT

            firstFrameRendered &&
                currentBufferingDuration != null &&
                postFirstFrameBufferingMillis + currentBufferingDuration >= limits.cumulativeBufferingLimitMillis ->
                HomePlaybackTerminalReason.CUMULATIVE_BUFFERING_TIMEOUT

            else -> null
        }
        return expired?.let(::terminateLocked)
    }

    private fun terminateLocked(reason: HomePlaybackTerminalReason): HomePlaybackTerminalReason {
        if (state != HomePlaybackAttemptState.TERMINAL) {
            state = HomePlaybackAttemptState.TERMINAL
            terminalReason = reason
            bufferingStartedAtMillis = null
        }
        return requireNotNull(terminalReason)
    }
}

class HomePlaybackSession internal constructor(
    val identity: HomeVideoIdentity,
    val renditions: List<HomeVideoRendition>,
    private val clock: HomePlaybackClock,
    private val limits: HomePlaybackLimits = HomePlaybackLimits(),
    private val nextAttemptId: () -> Long
) {
    var currentAttempt: HomePlaybackAttempt? = null
        private set

    var isPlaying: Boolean = false
        private set

    var shouldAutoResume: Boolean = false
        private set

    var lastPauseReason: HomePlaybackPauseReason? = null
        private set

    var playbackPositionMillis: Long = 0
        private set

    fun play(): HomePlaybackAttempt {
        val existing = currentAttempt
        if (existing != null) {
            if (existing.state == HomePlaybackAttemptState.ACTIVE) isPlaying = true
            shouldAutoResume = false
            lastPauseReason = null
            return existing
        }
        return newAttempt().also {
            currentAttempt = it
            isPlaying = true
            shouldAutoResume = false
            lastPauseReason = null
        }
    }

    fun retry(): HomePlaybackAttempt {
        currentAttempt?.terminate(HomePlaybackTerminalReason.USER_RETRY)
        playbackPositionMillis = 0
        return newAttempt().also {
            currentAttempt = it
            isPlaying = true
            shouldAutoResume = false
            lastPauseReason = null
        }
    }

    fun markPlaying() {
        if (currentAttempt?.state == HomePlaybackAttemptState.ACTIVE) isPlaying = true
        shouldAutoResume = false
        lastPauseReason = null
    }

    fun pause(reason: HomePlaybackPauseReason) {
        isPlaying = false
        shouldAutoResume = false
        lastPauseReason = reason
    }

    fun detachForRebuild() {
        isPlaying = false
        shouldAutoResume = false
    }

    fun updatePosition(positionMillis: Long) {
        playbackPositionMillis = positionMillis.coerceAtLeast(0)
    }

    fun attachAfterRebuild(): HomePlaybackAttempt? {
        isPlaying = false
        shouldAutoResume = false
        return currentAttempt
    }

    fun finish(reason: HomePlaybackTerminalReason) {
        currentAttempt?.terminate(reason)
        isPlaying = false
        shouldAutoResume = false
    }

    private fun newAttempt(): HomePlaybackAttempt =
        HomePlaybackAttempt(nextAttemptId(), clock, limits).also(HomePlaybackAttempt::start)
}

class HomePlaybackSessionRegistry internal constructor(
    private val clock: HomePlaybackClock,
    private val nextAttemptId: () -> Long = AtomicLong()::incrementAndGet,
    private val limits: HomePlaybackLimits = HomePlaybackLimits()
) {
    private val sessions = linkedMapOf<HomeVideoIdentity, HomePlaybackSession>()

    @Synchronized
    fun session(identity: HomeVideoIdentity, renditions: List<HomeVideoRendition>): HomePlaybackSession =
        sessions.getOrPut(identity) {
            HomePlaybackSession(identity, renditions, clock, limits, nextAttemptId)
        }

    @Synchronized
    fun existing(identity: HomeVideoIdentity): HomePlaybackSession? = sessions[identity]

    @Synchronized
    fun reconcile(currentIdentities: Set<HomeVideoIdentity>) {
        val obsolete = sessions.keys.filterNot(currentIdentities::contains)
        obsolete.forEach { identity ->
            sessions.remove(identity)?.finish(HomePlaybackTerminalReason.SOURCE_CHANGED)
        }
    }

    @Synchronized
    fun finishAll(reason: HomePlaybackTerminalReason) {
        sessions.values.forEach { session -> session.finish(reason) }
        sessions.clear()
    }
}

private fun normalizePlaybackUrl(rawUrl: String): String? = runCatching {
    val parsed = URI(rawUrl).normalize()
    val scheme = parsed.scheme?.lowercase() ?: return@runCatching null
    val host = parsed.host?.lowercase() ?: return@runCatching null
    val port = when {
        scheme == "https" && parsed.port == HTTPS_DEFAULT_PORT -> -1
        scheme == "http" && parsed.port == HTTP_DEFAULT_PORT -> -1
        else -> parsed.port
    }
    val authority = if (port == -1) host else "$host:$port"
    val path = parsed.rawPath?.ifEmpty { "/" } ?: "/"
    val query = parsed.rawQuery?.let { value -> "?$value" }.orEmpty()
    URI("$scheme://$authority$path$query").normalize().toASCIIString()
}.getOrNull()
