package com.gurbakir.mobile.product

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ArtworkViewportTest {
    @Test
    fun `portrait and landscape art reserve bounded mats without changing their image ratio`() {
        assertEquals(400.dp, artworkGalleryHeight(320.dp, 600, 800))
        assertEquals(420.dp, artworkGalleryHeight(600.dp, 600, 800))
        assertEquals(240.dp, artworkGalleryHeight(360.dp, 1200, 600))
        assertEquals(320.dp, artworkGalleryHeight(320.dp, null, null))
        assertEquals(320.dp, artworkGalleryHeight(320.dp, 0, 800))
        assertEquals(322.dp, artworkGalleryHeight(360.dp, 600, 800, 700.dp))
    }

    @Test
    fun `zoom bounds keep fitted portrait and landscape artwork reachable without empty panning`() {
        val viewport = Size(400f, 400f)
        assertEquals(Offset.Zero, clampArtworkPan(Offset(999f, -999f), 1f, viewport, 200, 400))
        assertEquals(Offset(0f, -200f), clampArtworkPan(Offset(999f, -999f), 2f, viewport, 200, 400))
        assertEquals(Offset(200f, 0f), clampArtworkPan(Offset(999f, -999f), 2f, viewport, 400, 200))
        assertEquals(Offset.Zero, clampArtworkPan(Offset(999f, -999f), 2f, Size.Zero, null, null))
    }

    @Test
    fun `gallery wide layout reserves usable columns at large text while standard threshold stays unchanged`() {
        assertTrue(isGalleryProductLayout(780.dp, 1f))
        assertFalse(isGalleryProductLayout(780.dp, 2f))
        assertTrue(isGalleryProductLayout(1200.dp, 2f))
        assertFalse(isGalleryProductLayout(393.dp, 1f))
        assertFalse(isExpandedProductLayout(780.dp))
        assertTrue(isExpandedProductLayout(840.dp))
    }
}
