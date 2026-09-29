package com.gurbakir.mobile.home

import com.gurbakir.storefront.StorefrontVideoSource
import java.net.URI
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HomeVideoSourcePolicyTest {
    @Test
    fun `landscape and portrait renditions share one pixel budget`() {
        assertTrue(HomeVideoSourcePolicy.accepts(source(1280, 720)))
        assertTrue(HomeVideoSourcePolicy.accepts(source(720, 1080)))
        assertTrue(HomeVideoSourcePolicy.accepts(source(480, 720)))
        assertFalse(HomeVideoSourcePolicy.accepts(source(1000, 1000)))
        assertFalse(HomeVideoSourcePolicy.accepts(source(1, 1281)))
        assertFalse(HomeVideoSourcePolicy.accepts(source(0, 720)))
        assertFalse(HomeVideoSourcePolicy.accepts(source(720, 0)))
    }

    @Test
    fun `only mp4 sources enter the native player`() {
        assertFalse(HomeVideoSourcePolicy.accepts(source(720, 1080, mimeType = "video/webm")))
        assertFalse(HomeVideoSourcePolicy.accepts(source(720, 1080, format = "webm")))
    }

    private fun source(
        width: Int,
        height: Int,
        mimeType: String = "video/mp4",
        format: String = "mp4"
    ) = StorefrontVideoSource(URI("https://cdn.shopify.com/video.mp4"), mimeType, format, width, height)
}
