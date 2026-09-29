package com.gurbakir.mobile.home

import androidx.compose.ui.unit.dp
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class HomeVideoViewportTest {
    @Test
    fun `portrait playback is capped while landscape retains its proportion`() {
        assertEquals(480.dp, homeVideoViewportHeight(360.dp, 720, 1080))
        assertEquals(220.dp, homeVideoViewportHeight(360.dp, 1280, 720))
        assertEquals(300.dp, homeVideoViewportHeight(200.dp, 720, 1080))
        assertEquals(220.dp, homeVideoViewportHeight(360.dp, 0, 1080))
    }
}
