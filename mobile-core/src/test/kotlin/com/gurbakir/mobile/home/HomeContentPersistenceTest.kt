package com.gurbakir.mobile.home

import com.gurbakir.storefront.HomeResourceKey
import com.gurbakir.storefront.HomeResourceKind
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test

class HomeContentPersistenceTest {
    private val codec = HomeContentCodec()
    private val partition =
        HomeContentPartition(
            applicationId = "com.example.app",
            environmentId = "staging",
            storefrontDomain = "merchant.example",
            rootType = "mobile_home",
            rootHandle = "primary"
        )

    @Test
    fun `strict codec round trips remote editorial data`() {
        val stored = storedSnapshot(empty = false)

        val decoded = codec.decodeSnapshot(codec.encodeSnapshot(stored))

        assertEquals(HomeCodecDecode.Accepted(stored), decoded)
    }

    @Test
    fun `strict codec rejects packaged variants and unknown fields`() {
        val valid = codec.encodeSnapshot(storedSnapshot(empty = true))
        val withPackagedResource = valid.dropLast(1) + ",\"resourceId\":7}"

        val decoded = codec.decodeSnapshot(withPackagedResource)

        assertInstanceOf(HomeCodecDecode.Rejected::class.java, decoded)
    }

    @Test
    fun `strict codec distinguishes invalid value shape from malformed JSON`() {
        val valid = codec.encodeSnapshot(storedSnapshot(empty = true))
        val missingKnownField = valid.replace("\"rootType\":\"mobile_home\",", "")

        assertEquals(
            HomeCodecDecode.Rejected(HomeCodecRejection.INVALID_VALUE),
            codec.decodeSnapshot(missingKnownField)
        )
        assertEquals(
            HomeCodecDecode.Rejected(HomeCodecRejection.MALFORMED),
            codec.decodeSnapshot("{")
        )
    }

    @Test
    fun `strict codec rejects a typed gid with additional path segments`() {
        val valid = codec.encodeSnapshot(storedSnapshot(empty = false))
        val nestedGid = valid.replace(
            "gid://shopify/Product/1",
            "gid://shopify/Product/archive/1"
        )

        assertEquals(
            HomeCodecDecode.Rejected(HomeCodecRejection.INVALID_VALUE),
            codec.decodeSnapshot(nestedGid)
        )
    }

    @Test
    fun `marker and snapshot are partition aware`() {
        val marker = HomeEstablishmentRecord(partition, 100L, 1)
        val different = marker.copy(partition = partition.copy(rootHandle = "other"))

        assertEquals(HomeCodecDecode.Accepted(marker), codec.decodeMarker(codec.encodeMarker(marker)))
        assertEquals(HomeCodecDecode.Accepted(different), codec.decodeMarker(codec.encodeMarker(different)))
        assertEquals(false, marker.partition == different.partition)
    }

    @Test
    fun `editorial deadline treats equality as expired and rejects overflow`() {
        assertEquals(86_401_000L, HomeEditorialClockPolicy.deadline(1_000L, 86_400_000L))
        assertEquals(null, HomeEditorialClockPolicy.deadline(Long.MAX_VALUE, 1L))
        assertEquals(
            HomeEditorialFreshness.EXPIRED,
            HomeEditorialClockPolicy.freshness(1_000L, 2_000L, 2_000L)
        )
    }

    @Test
    fun `snapshot codec rejects a timestamp interval that is not the configured day`() {
        val malformed =
            codec.encodeSnapshot(storedSnapshot(empty = true))
                .replace("\"expiresAtMillis\":86401000", "\"expiresAtMillis\":86401001")

        assertInstanceOf(HomeCodecDecode.Rejected::class.java, codec.decodeSnapshot(malformed))
    }

    @Test
    fun `editorial freshness clamps small backward skew and rejects large skew`() {
        assertEquals(
            HomeEditorialFreshness.FRESH,
            HomeEditorialClockPolicy.freshness(10_000L, 20_000L, 9_999L)
        )
        assertEquals(
            HomeEditorialFreshness.CLOCK_INVALID,
            HomeEditorialClockPolicy.freshness(400_001L, 500_000L, 100_000L)
        )
    }

    @Test
    fun `storage v2 normalizes multiline image and video captions and rejects other controls`() {
        val stored = v2StoredSnapshot()
        val image = stored.snapshot.sections[0] as RemoteHomeSection.Image
        val video = stored.snapshot.sections[1] as RemoteHomeSection.Video
        val canonical = stored.copy(
            snapshot = stored.snapshot.copy(
                sections = listOf(
                    image.copy(caption = "First\nSecond\nThird"),
                    video.copy(caption = "First\nSecond\nThird")
                )
            )
        )
        val encoded = codec.encodeSnapshot(canonical)
        assertEquals(HomeCodecDecode.Accepted(canonical), codec.decodeSnapshot(encoded))
        assertEquals(
            HomeCodecDecode.Accepted(canonical),
            codec.decodeSnapshot(encoded.replace("First\\nSecond\\nThird", "First\\r\\nSecond\\rThird"))
        )
        assertInstanceOf(
            HomeCodecDecode.Rejected::class.java,
            codec.decodeSnapshot(encoded.replace("First\\n", "First\\t"))
        )
    }

    @Test
    fun `storage v2 round trips media identities without persisting provider urls`() {
        val stored = v2StoredSnapshot()

        val encoded = codec.encodeSnapshot(stored)

        assertFalse(encoded.contains("http"))
        assertFalse(encoded.contains("url", ignoreCase = true))
        assertEquals(HomeCodecDecode.Accepted(stored), codec.decodeSnapshot(encoded))
    }

    private fun v2StoredSnapshot(): HomeStoredSnapshot = HomeStoredSnapshot(
        partition = partition.copy(rootType = "mobile_home_v2"),
        acceptedAtMillis = 1_000L,
        expiresAtMillis = 86_401_000L,
        snapshot =
            RemoteHomeSnapshot(
                rootGid = "gid://shopify/Metaobject/root-v2",
                rootType = "mobile_home_v2",
                rootHandle = "primary",
                rootUpdatedAt = "2026-09-17T20:00:00Z",
                contentVersion = 2,
                sections =
                    listOf(
                        RemoteHomeSection.Image(
                            sectionGid = "gid://shopify/Metaobject/image",
                            type = "mobile_home_image_v1",
                            handle = "hero",
                            title = "Hero",
                            updatedAt = "2026-09-17T19:59:00Z",
                            presentation = HomeImagePresentation.BANNER,
                            media = HomeResourceKey(HomeResourceKind.MEDIA_IMAGE, "gid://shopify/MediaImage/10"),
                            altText = "Copper cookware",
                            caption = "Made for long dinners",
                            target =
                                RemoteHomeTarget(
                                    HomeResourceKey(HomeResourceKind.COLLECTION, "gid://shopify/Collection/20"),
                                    "tableware"
                                )
                        ),
                        RemoteHomeSection.Video(
                            sectionGid = "gid://shopify/Metaobject/video",
                            type = "mobile_home_video_v1",
                            handle = "workshop",
                            title = "Workshop",
                            updatedAt = "2026-09-17T19:58:00Z",
                            media = HomeResourceKey(HomeResourceKind.VIDEO, "gid://shopify/Video/30"),
                            poster = HomeResourceKey(HomeResourceKind.MEDIA_IMAGE, "gid://shopify/MediaImage/31"),
                            altText = "A craftsperson shaping copper",
                            caption = null,
                            target = null
                        )
                    ),
                declaredSectionCount = 2,
                sectionRevisionDigest = "a".repeat(64),
                quality = HomeDocumentQuality.PARTIAL
            ),
        storageVersion = HOME_CONTENT_STORAGE_VERSION_V2
    )

    private fun storedSnapshot(empty: Boolean): HomeStoredSnapshot = HomeStoredSnapshot(
        partition = partition,
        acceptedAtMillis = 1_000L,
        expiresAtMillis = 86_401_000L,
        snapshot =
            RemoteHomeSnapshot(
                rootGid = "gid://shopify/Metaobject/root",
                rootType = "mobile_home",
                rootHandle = "primary",
                rootUpdatedAt = "2026-09-13T20:00:00Z",
                contentVersion = 1,
                sections =
                    if (empty) {
                        emptyList()
                    } else {
                        listOf(
                            RemoteHomeSection.FeaturedProduct(
                                sectionGid = "gid://shopify/Metaobject/featured",
                                type = "mobile_home_featured_product",
                                handle = "primary",
                                title = "Featured",
                                product =
                                    HomeResourceKey(
                                        HomeResourceKind.PRODUCT,
                                        "gid://shopify/Product/1"
                                    )
                            )
                        )
                    }
            )
    )
}
