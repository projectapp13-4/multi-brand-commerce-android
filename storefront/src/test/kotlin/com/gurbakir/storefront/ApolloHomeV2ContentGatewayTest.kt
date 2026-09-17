package com.gurbakir.storefront

import com.apollographql.apollo.ApolloClient
import java.net.URI
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ApolloHomeV2ContentGatewayTest {
    private lateinit var server: MockWebServer
    private lateinit var client: ApolloClient

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = ApolloClient.Builder().serverUrl(server.url("graphql").toString()).build()
    }

    @AfterEach
    fun tearDown() {
        client.close()
        server.shutdown()
    }

    @Test
    fun `v2 dispatch preserves child revision media identity and unresolved optional target`() =
        kotlinx.coroutines.runBlocking {
            server.enqueue(MockResponse().setBody(fixture("optional-unresolved-target.json")))

            val result =
                ApolloStorefrontGateway(client, StorefrontMediaPolicy("merchant.example"))
                    .loadHomeDocument(HomeDocumentSelector("mobile_home_v2", "primary"))

            val document = assertInstanceOf(StorefrontResult.Success::class.java, result).value
                as HomeDocumentObservation
            val section = requireNotNull(document.sections?.references?.nodes?.single()?.section)
            assertEquals("2026-09-17T07:55:00Z", section.updatedAt)
            assertEquals("banner", section.presentation?.value)
            assertEquals("gid://shopify/MediaImage/501", section.media?.value)
            assertEquals("MediaImage", section.media?.reference?.runtimeType)
            val image = section.media?.reference?.resource
                as StorefrontHomeResource.MediaImage
            assertEquals(
                HomeMediaObservation.Accepted(
                    StorefrontMedia(
                        URI("https://cdn.shopify.com/s/files/1/0000/0001/files/autumn-banner.jpg"),
                        "Copper cookware on a linen table",
                        1600,
                        900
                    )
                ),
                image.media
            )
            assertEquals("gid://shopify/Product/701", section.productTarget?.value)
            assertNull(section.productTarget?.reference)
            assertEquals(true, server.takeRequest().body.readUtf8().contains("HomeContentV2Metaobject"))
        }

    @Test
    fun `wrong media typename remains observable instead of masquerading as unresolved`() =
        kotlinx.coroutines.runBlocking {
            server.enqueue(MockResponse().setBody(fixture("wrong-typename.json")))

            val result =
                ApolloStorefrontGateway(client, StorefrontMediaPolicy("merchant.example"))
                    .loadHomeDocument(HomeDocumentSelector("mobile_home_v2", "primary"))

            val document = assertInstanceOf(StorefrontResult.Success::class.java, result).value
                as HomeDocumentObservation
            val media = requireNotNull(document.sections?.references?.nodes?.single()?.section?.media)
            assertEquals("gid://shopify/GenericFile/801", media.value)
            assertEquals("GenericFile", media.reference?.runtimeType)
            assertNull(media.reference?.resource)
        }

    @Test
    fun `v2 hydration admits fourteen unique file ids but rejects a fifteenth locally`() =
        kotlinx.coroutines.runBlocking {
            val fourteen = (1..14).map { index ->
                HomeResourceKey(HomeResourceKind.MEDIA_IMAGE, "gid://shopify/MediaImage/$index")
            }
            server.enqueue(MockResponse().setBody(nullNodeBatch(14)))
            val gateway = ApolloStorefrontGateway(client, StorefrontMediaPolicy("merchant.example"))

            val accepted = gateway.loadHomeResources(fourteen)
            val rejected = gateway.loadHomeResources(
                fourteen + HomeResourceKey(HomeResourceKind.VIDEO, "gid://shopify/Video/15")
            )

            val batch = assertInstanceOf(StorefrontResult.Success::class.java, accepted).value
                as HomeResourceBatch
            assertEquals(14, batch.resolutions.size)
            assertInstanceOf(StorefrontFailure.Configuration::class.java, (rejected as StorefrontResult.Failure).error)
            assertEquals(true, server.takeRequest().body.readUtf8().contains("HomeV2Resources"))
            assertEquals(1, server.requestCount)
        }

    private fun fixture(name: String): String = requireNotNull(javaClass.getResource("/home-v2/$name")).readText()

    private fun nullNodeBatch(count: Int): String =
        "{\"data\":{\"nodes\":[${List(count) { "null" }.joinToString(",")}]}}"
}
