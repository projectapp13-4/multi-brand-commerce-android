package com.gurbakir.mobile.home

internal object HomeVideoPlayerPolicy {
    const val MAX_FORWARD_BUFFER_MILLIS = 15_000
    const val BACK_BUFFER_MILLIS = 0
    const val RETAIN_BACK_BUFFER_FROM_KEYFRAME = false
    const val AUTOPLAY = false
    const val LOOP = false
    const val PRELOAD = false
    const val VIDEO_BYTE_CACHE = false
    const val BACKGROUND_PLAYBACK = false
}
