package com.gurbakir.mobile.home

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class HomeVideoPlayerPolicyTest {
    @Test
    fun `player policy keeps bounded forward buffer and no back buffer`() {
        assertEquals(15_000, HomeVideoPlayerPolicy.MAX_FORWARD_BUFFER_MILLIS)
        assertEquals(0, HomeVideoPlayerPolicy.BACK_BUFFER_MILLIS)
        assertFalse(HomeVideoPlayerPolicy.RETAIN_BACK_BUFFER_FROM_KEYFRAME)
    }

    @Test
    fun `player policy never enables autoplay loop preload cache or background playback`() {
        assertFalse(HomeVideoPlayerPolicy.AUTOPLAY)
        assertFalse(HomeVideoPlayerPolicy.LOOP)
        assertFalse(HomeVideoPlayerPolicy.PRELOAD)
        assertFalse(HomeVideoPlayerPolicy.VIDEO_BYTE_CACHE)
        assertFalse(HomeVideoPlayerPolicy.BACKGROUND_PLAYBACK)
    }
}
