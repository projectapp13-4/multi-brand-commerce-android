package com.gurbakir.mobile.catalog

import com.gurbakir.storefront.CatalogDiscoveryCollection
import com.gurbakir.storefront.StorefrontMedia
import java.net.URI
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class CatalogGalleryPolicyTest {
    private val policy = CatalogPresentation.Gallery("masters", "all", CatalogGalleryCopy(1, 2, 3, 4, 5, 6))

    @Test
    fun `gallery highlights roots without promoting artists and retains every eligible action`() {
        val items = listOf(
            item("masters"), item("artist-one", 1, "masters"), item("artist-two", 2, "masters"),
            item("originals"), item("modern"), item("zen"), item("children"), item("other"),
            item("orphan", 1), item("all")
        )
        val sections = gallerySections(items, policy)
        assertEquals("masters", sections.featured?.collection?.handle)
        assertEquals(listOf("originals", "modern", "zen", "children"), sections.covers.map { it.collection.handle })
        assertEquals(listOf("artist-one", "artist-two"), sections.children.map { it.collection.handle })
        assertEquals(listOf("other", "orphan"), sections.remaining.map { it.collection.handle })
        assertEquals("all", sections.allProducts?.collection?.handle)
        val displayed = listOfNotNull(sections.featured) + sections.covers + sections.children +
            sections.remaining + listOfNotNull(sections.allProducts)
        assertEquals(10, displayed.size)
        assertEquals(items.map { it.stableId }.sorted(), displayed.map { it.stableId }.sorted())
    }

    @Test
    fun `missing configured roots never promote same handle descendants into special actions`() {
        val sections = gallerySections(
            listOf(item("masters", 1), item("all", 1), item("available-root")),
            policy
        )
        assertNull(sections.featured)
        assertNull(sections.allProducts)
        assertEquals(listOf("available-root"), sections.covers.map { it.collection.handle })
        assertEquals(listOf("masters", "all"), sections.remaining.map { it.collection.handle })
        assertEquals(emptyList<CatalogCategoryItem>(), sections.children)
    }

    @Test
    fun `renamed or removed featured root leaves a useful bounded gallery and continuation`() {
        val sections = gallerySections((1..6).map { item("root-$it") }, policy)
        assertNull(sections.featured)
        assertNull(sections.allProducts)
        assertEquals(listOf("root-1", "root-2", "root-3", "root-4"), sections.covers.map { it.collection.handle })
        assertEquals(listOf("root-5", "root-6"), sections.remaining.map { it.collection.handle })
    }

    @Test
    fun `standard catalog keeps its default presentation and gallery phone grids reflow only when needed`() {
        assertEquals(CatalogPresentation.Standard, CatalogConfiguration("menu").presentation)
        assertEquals(2, galleryColumnCount(androidx.compose.ui.unit.Dp(320f), 1f))
        assertEquals(2, galleryColumnCount(androidx.compose.ui.unit.Dp(391f), 1.3f))
        assertEquals(1, galleryColumnCount(androidx.compose.ui.unit.Dp(320f), 2f))
        assertEquals(1, galleryColumnCount(androidx.compose.ui.unit.Dp(240f), 1f))
    }

    private fun item(handle: String, depth: Int = 0, parent: String? = null) = CatalogCategoryItem(
        stableId = handle,
        title = "Merchant $handle",
        collection = CatalogDiscoveryCollection(
            handle,
            handle,
            "Source $handle",
            StorefrontMedia(
                URI("https://cdn.shopify.com/s/files/1/$handle.jpg"),
                null,
                300,
                400
            )
        ),
        depth = depth,
        parentStableId = parent
    )
}
