package com.gurbakir.mobile.home

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HomeV2ImagePolicyTest {
    @Test
    fun `one image response cannot exceed eight MiB`() {
        val guard = HomeV2ImageRequestGuard()
        val sevenMiB = 7L * 1024L * 1024L

        assertTrue(guard.onRequestStarted("https://cdn.shopify.com/image.jpg"))
        assertTrue(guard.onResponseBytes(sevenMiB))
        assertEquals(1024L * 1024L, guard.maximumResponseBytesForRead(2L * 1024L * 1024L))
        assertTrue(guard.onResponseBytes(1024L * 1024L))
        assertEquals(0, guard.maximumResponseBytesForRead(1))
        assertFalse(guard.onResponseBytes(1))
    }

    @Test
    fun `same URL with a new revision gets a new memory and disk cache identity`() {
        val url = "https://cdn.shopify.com/banner.jpg"

        assertNotEquals(
            homeV2ImageCacheKey(url, "revision-one"),
            homeV2ImageCacheKey(url, "revision-two")
        )
        assertEquals(
            homeV2ImageCacheKey(url, "revision-one"),
            homeV2ImageCacheKey(url, "revision-one")
        )
    }

    @Test
    fun `v2 image decode dimension is bounded independently of app wide image requests`() {
        assertEquals(1600, HOME_V2_IMAGE_MAX_DIMENSION)
    }
}
