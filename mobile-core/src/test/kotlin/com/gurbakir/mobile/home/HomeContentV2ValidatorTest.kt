package com.gurbakir.mobile.home

import com.gurbakir.storefront.HomeDocumentObservation
import com.gurbakir.storefront.HomeDocumentSelector
import com.gurbakir.storefront.HomeFieldObservation
import com.gurbakir.storefront.HomeMediaFieldObservation
import com.gurbakir.storefront.HomeMediaObservation
import com.gurbakir.storefront.HomeResourceKey
import com.gurbakir.storefront.HomeResourceKind
import com.gurbakir.storefront.HomeResourceNodeObservation
import com.gurbakir.storefront.HomeSectionNodeObservation
import com.gurbakir.storefront.HomeSectionObservation
import com.gurbakir.storefront.HomeSectionReferencesObservation
import com.gurbakir.storefront.HomeSectionsFieldObservation
import com.gurbakir.storefront.HomeTargetFieldObservation
import com.gurbakir.storefront.StorefrontHomeResource
import com.gurbakir.storefront.StorefrontMedia
import com.gurbakir.storefront.StorefrontVideoSource
import java.net.URI
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HomeContentV2ValidatorTest {
    private val validator = HomeContentValidator()
    private val selector = HomeDocumentSelector("mobile_home_v2", "primary")

    @Test
    fun `explicit v2 zero count is intentional empty rather than a failed document`() {
        val validation = validator.validate(selector, root(emptyList()), 2)

        val accepted = assertInstanceOf(HomeDocumentValidation.Accepted::class.java, validation)
        assertEquals(0, accepted.snapshot.declaredSectionCount)
        assertEquals(emptyList<RemoteHomeSection>(), accepted.snapshot.sections)
        assertEquals(HomeDocumentQuality.COMPLETE, accepted.snapshot.quality)
    }

    @Test
    fun `wrong type rejects only that section and persists the valid section as partial`() {
        val image = imageSection("301", resolvedImage("501"))
        val wrongVideo = videoSection("302", resolvedImage("502"))

        val validation = validator.validate(selector, root(listOf(image, wrongVideo)), 2)

        val accepted = assertInstanceOf(HomeDocumentValidation.Accepted::class.java, validation)
        assertEquals(HomeDocumentQuality.PARTIAL, accepted.snapshot.quality)
        assertEquals(2, accepted.snapshot.declaredSectionCount)
        assertEquals(listOf("gid://shopify/Metaobject/301"), accepted.snapshot.sections.map { it.sectionGid })
        assertInstanceOf(RemoteHomeSection.Image::class.java, accepted.snapshot.sections.single())
    }

    @Test
    fun `all structurally rejected sections are none renderable and cannot look empty`() {
        val validation = validator.validate(selector, root(listOf(videoSection("302", resolvedImage("502")))), 2)

        assertInstanceOf(HomeDocumentValidation.NoneRenderable::class.java, validation)
    }

    @Test
    fun `temporarily unresolved video retains text and gid as non playable`() {
        val validation =
            validator.validate(
                selector,
                root(listOf(videoSection("303", reference = null))),
                2
            )

        val accepted = assertInstanceOf(HomeDocumentValidation.Accepted::class.java, validation)
        assertEquals(HomeDocumentQuality.NON_PLAYABLE, accepted.snapshot.quality)
        val video = assertInstanceOf(RemoteHomeSection.Video::class.java, accepted.snapshot.sections.single())
        assertEquals("Workshop film", video.title)
        assertEquals(HomeResourceKey(HomeResourceKind.VIDEO, "gid://shopify/Video/6303"), video.media)
    }

    @Test
    fun `unresolved optional target produces no navigation target`() {
        val section = imageSection("304", resolvedImage("504")).copy(
            productTarget =
                HomeTargetFieldObservation(
                    type = "product_reference",
                    value = "gid://shopify/Product/704",
                    reference = null,
                    key = "product_target"
                )
        )

        val validation = validator.validate(selector, root(listOf(section)), 2)

        val accepted = assertInstanceOf(HomeDocumentValidation.Accepted::class.java, validation)
        val image = accepted.snapshot.sections.single() as RemoteHomeSection.Image
        assertNull(image.target)
    }

    @Test
    fun `child revision changes digest even when parent timestamp is unchanged`() {
        val before = validator.validate(selector, root(listOf(imageSection("305", resolvedImage("505")))), 2)
        val afterSection = imageSection("305", resolvedImage("506")).copy(
            updatedAt = "2026-09-17T08:09:00Z",
            title = field("title", "single_line_text_field", "Second revision")
        )
        val after = validator.validate(selector, root(listOf(afterSection)), 2)

        val beforeDigest = (before as HomeDocumentValidation.Accepted).snapshot.sectionRevisionDigest
        val afterDigest = (after as HomeDocumentValidation.Accepted).snapshot.sectionRevisionDigest
        assertTrue(beforeDigest.matches(Regex("[a-f0-9]{64}")))
        assertTrue(afterDigest.matches(Regex("[a-f0-9]{64}")))
        assertNotEquals(beforeDigest, afterDigest)
    }

    private fun root(sections: List<HomeSectionObservation>): HomeDocumentObservation {
        val ids = sections.map { it.sectionGid }
        return HomeDocumentObservation(
            rootGid = "gid://shopify/Metaobject/200",
            rootHandle = "primary",
            rootType = "mobile_home_v2",
            rootUpdatedAt = "2026-09-17T08:10:00Z",
            schemaVersion = field("schema_version", "number_integer", "2"),
            declaredSectionCount = field("declared_section_count", "number_integer", ids.size.toString()),
            sections =
                HomeSectionsFieldObservation(
                    type = "list.mixed_reference",
                    value = ids.joinToString(prefix = "[\"", postfix = "\"]", separator = "\",\"")
                        .takeIf { ids.isNotEmpty() } ?: "[]",
                    references =
                        HomeSectionReferencesObservation(
                            sections.map { HomeSectionNodeObservation("Metaobject", it) },
                            hasNextPage = false
                        ),
                    key = "sections"
                )
        )
    }

    private fun imageSection(suffix: String, reference: HomeResourceNodeObservation?): HomeSectionObservation =
        HomeSectionObservation(
            sectionGid = "gid://shopify/Metaobject/$suffix",
            handle = "image-$suffix",
            type = "mobile_home_image_v1",
            title = field("title", "single_line_text_field", "Image $suffix"),
            collections = null,
            product = null,
            updatedAt = "2026-09-17T08:00:00Z",
            presentation = field("presentation", "single_line_text_field", "banner"),
            media =
                HomeMediaFieldObservation(
                    type = "file_reference",
                    value = reference?.resource?.key?.gid ?: "gid://shopify/MediaImage/5$suffix",
                    reference = reference,
                    key = "media"
                ),
            altText = field("alt_text", "single_line_text_field", "Accessible image $suffix")
        )

    private fun videoSection(suffix: String, reference: HomeResourceNodeObservation?): HomeSectionObservation =
        HomeSectionObservation(
            sectionGid = "gid://shopify/Metaobject/$suffix",
            handle = "video-$suffix",
            type = "mobile_home_video_v1",
            title = field("title", "single_line_text_field", "Workshop film"),
            collections = null,
            product = null,
            updatedAt = "2026-09-17T08:00:00Z",
            media =
                HomeMediaFieldObservation(
                    type = "file_reference",
                    value = "gid://shopify/Video/6$suffix",
                    reference = reference,
                    key = "media"
                ),
            altText = field("alt_text", "single_line_text_field", "Craft process")
        )

    private fun resolvedImage(suffix: String): HomeResourceNodeObservation = HomeResourceNodeObservation(
        runtimeType = "MediaImage",
        resource =
            StorefrontHomeResource.MediaImage(
                key = HomeResourceKey(HomeResourceKind.MEDIA_IMAGE, "gid://shopify/MediaImage/5$suffix"),
                contentType = "IMAGE",
                media =
                    HomeMediaObservation.Accepted(
                        StorefrontMedia(
                            URI("https://cdn.shopify.com/s/files/1/image-$suffix.jpg"),
                            "Image $suffix",
                            1200,
                            800
                        )
                    )
            )
    )

    @Suppress("unused")
    private fun resolvedVideo(suffix: String): HomeResourceNodeObservation = HomeResourceNodeObservation(
        runtimeType = "Video",
        resource =
            StorefrontHomeResource.Video(
                key = HomeResourceKey(HomeResourceKind.VIDEO, "gid://shopify/Video/6$suffix"),
                contentType = "VIDEO",
                sources =
                    listOf(
                        StorefrontVideoSource(
                            URI("https://cdn.shopify.com/videos/c/o/v/video-$suffix.mp4"),
                            "video/mp4",
                            "mp4",
                            1280,
                            720
                        )
                    ),
                observedSourceCount = 1,
                preview = HomeMediaObservation.Absent
            )
    )

    private fun field(key: String, type: String, value: String): HomeFieldObservation =
        HomeFieldObservation(type = type, value = value, key = key)
}
