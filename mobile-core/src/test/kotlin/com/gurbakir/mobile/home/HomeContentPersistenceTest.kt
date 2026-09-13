package com.gurbakir.mobile.home

import com.gurbakir.storefront.HomeResourceKey
import com.gurbakir.storefront.HomeResourceKind
import org.junit.jupiter.api.Assertions.assertEquals
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
