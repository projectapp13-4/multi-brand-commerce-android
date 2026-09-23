package com.gurbakir.storefront

import com.gurbakir.storefront.graphql.HomeContentV2MetaobjectQuery
import com.gurbakir.storefront.graphql.type.MetaobjectHandleInput
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test

class OwnedHomeV2ReadProofTest {
    @Test
    fun `configured public client reads the exact Trial Home v2 document and media`() {
        assumeTrue(System.getProperty("onboarding.runOwnedHomeV2Readback") == "true")
        val owned = loadOwnedOnboardingConfiguration()
        assertEquals(HomeContentContractId.PILOT_MEDIA_V2, owned.homeContractId)
        assertEquals("mobile_home_v2", owned.homeType)
        assertEquals("primary", owned.homeHandle)

        val client = StorefrontApolloClientFactory.createClient(owned.storefront)
        try {
            val response =
                runBlocking {
                    client
                        .query(
                            HomeContentV2MetaobjectQuery(
                                MetaobjectHandleInput(handle = owned.homeHandle, type = owned.homeType)
                            )
                        ).execute()
                }
            assertTrue(response.exception == null, "Home v2 public-client request must not throw")
            assertTrue(response.errors.orEmpty().isEmpty(), "Home v2 public-client request must have no GraphQL errors")
            val root = requireNotNull(response.data?.metaobject) { "Selected Home v2 root must exist" }
            assertRootContract(root, owned)
            val children = orderedChildren(root)
            assertChildTypes(children)

            assertImageSection(children[0], "banner", owned.storefront.domain)
            assertCollectionSection(children[1])
            assertImageSection(children[2], "photo", owned.storefront.domain)
            assertFeaturedSection(children[3])
            assertVideoSection(children[4], owned.storefront.domain)
        } finally {
            client.close()
        }
    }

    private fun assertRootContract(root: HomeContentV2MetaobjectQuery.Metaobject, owned: OwnedOnboardingConfiguration) {
        assertEquals(owned.homeType, root.type)
        assertEquals(owned.homeHandle, root.handle)
        assertEquals("schema_version", root.schemaVersion?.key)
        assertEquals("number_integer", root.schemaVersion?.type)
        assertEquals("2", root.schemaVersion?.value)
        assertEquals("declared_section_count", root.declaredSectionCount?.key)
        assertEquals("number_integer", root.declaredSectionCount?.type)
        assertEquals("5", root.declaredSectionCount?.value)
    }

    private fun orderedChildren(
        root: HomeContentV2MetaobjectQuery.Metaobject
    ): List<HomeContentV2MetaobjectQuery.OnMetaobject> {
        val sections = requireNotNull(root.sections)
        assertEquals("sections", sections.key)
        assertEquals("list.mixed_reference", sections.type)
        val orderedIds =
            Json.parseToJsonElement(requireNotNull(sections.value)).jsonArray.map { element ->
                element.jsonPrimitive.content
            }
        assertEquals(5, orderedIds.size)
        assertEquals(orderedIds.size, orderedIds.distinct().size)
        val references = requireNotNull(sections.references)
        assertFalse(references.pageInfo.hasNextPage)
        assertEquals(orderedIds.size, references.nodes.size)
        return references.nodes.mapIndexed { index, node ->
            assertEquals("Metaobject", node.__typename)
            requireNotNull(node.onMetaobject).also { child -> assertEquals(orderedIds[index], child.id) }
        }
    }

    private fun assertChildTypes(children: List<HomeContentV2MetaobjectQuery.OnMetaobject>) {
        assertEquals(
            listOf(
                "mobile_home_image_v1",
                "mobile_home_collection_grid",
                "mobile_home_image_v1",
                "mobile_home_featured_product",
                "mobile_home_video_v1"
            ),
            children.map { it.type }
        )
    }

    private fun assertImageSection(
        section: HomeContentV2MetaobjectQuery.OnMetaobject,
        expectedPresentation: String,
        merchantDomain: String
    ) {
        assertEquals(expectedPresentation, section.presentation?.value)
        assertEquals("file_reference", section.media?.type)
        assertTrue(section.altText?.value?.isNotBlank() == true)
        val media = requireNotNull(section.media?.reference?.onMediaImage)
        assertEquals(section.media.value, media.id)
        assertEquals("IMAGE", media.mediaContentType.rawValue)
        val image = requireNotNull(media.image)
        assertTrue(StorefrontMediaPolicy(merchantDomain).accepts(image.url))
        assertTrue(requireNotNull(image.width) in 1..1600)
        assertTrue(requireNotNull(image.height) in 1..1600)
        assertExclusiveTarget(section)
    }

    private fun assertVideoSection(section: HomeContentV2MetaobjectQuery.OnMetaobject, merchantDomain: String) {
        assertEquals("file_reference", section.media?.type)
        assertTrue(section.altText?.value?.isNotBlank() == true)
        val media = requireNotNull(section.media?.reference?.onVideo)
        assertEquals(section.media.value, media.id)
        assertEquals("VIDEO", media.mediaContentType.rawValue)
        assertTrue(media.sources.isNotEmpty())
        assertTrue(media.sources.any { it.mimeType == "video/mp4" })
        media.sources.forEach { source ->
            assertTrue(StorefrontMediaPolicy(merchantDomain).accepts(source.url))
            assertTrue(source.width in 1..1280)
            assertTrue(source.height in 1..720)
        }
        val poster = requireNotNull(section.poster)
        assertEquals("file_reference", poster.type)
        val posterMedia = requireNotNull(poster.reference?.onMediaImage)
        assertEquals(poster.value, posterMedia.id)
        assertTrue(StorefrontMediaPolicy(merchantDomain).accepts(requireNotNull(posterMedia.image).url))
        assertExclusiveTarget(section)
    }

    private fun assertCollectionSection(section: HomeContentV2MetaobjectQuery.OnMetaobject) {
        assertEquals("list.collection_reference", section.collections?.type)
        val references = requireNotNull(section.collections?.references)
        assertFalse(references.pageInfo.hasNextPage)
        assertEquals(1, references.nodes.size)
        assertEquals("pilot-koleksiyonu", references.nodes.single().onCollection?.handle)
    }

    private fun assertFeaturedSection(section: HomeContentV2MetaobjectQuery.OnMetaobject) {
        assertEquals("product_reference", section.product?.type)
        val product = requireNotNull(section.product?.reference?.onProduct)
        assertEquals("pilot-urun", product.handle)
        assertEquals("TRY", product.priceRange.minVariantPrice.currencyCode.rawValue)
    }

    private fun assertExclusiveTarget(section: HomeContentV2MetaobjectQuery.OnMetaobject) {
        val productTarget = section.productTarget
        val collectionTarget = section.collectionTarget
        val productPopulated = !productTarget?.value.isNullOrBlank()
        val collectionPopulated = !collectionTarget?.value.isNullOrBlank()
        assertFalse(productPopulated && collectionPopulated)
        assertTrue(productPopulated || collectionPopulated)
        if (productPopulated) {
            assertNotNull(requireNotNull(productTarget).reference?.onProduct)
        }
        if (collectionPopulated) {
            assertNotNull(requireNotNull(collectionTarget).reference?.onCollection)
        }
    }
}
