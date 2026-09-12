package com.gurbakir.storefront

import com.apollographql.apollo.ApolloClient
import java.math.BigDecimal
import java.net.URI
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ApolloStorefrontProductGatewayTest {
    private lateinit var server: MockWebServer
    private lateinit var client: ApolloClient
    private val mediaPolicy = StorefrontMediaPolicy("gurbakir.com")

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
    fun `detail exhausts cursors and maps exact variant price availability and media`() = runBlocking {
        server.enqueue(MockResponse().setBody(firstPage()))
        server.enqueue(MockResponse().setBody(secondPage()))

        val result =
            ApolloStorefrontProductGateway(client, mediaPolicy).loadProductDetail(
                ProductDetailRequest("gid://shopify/Product/1")
            )

        val product =
            assertInstanceOf(
                StorefrontProductDetail::class.java,
                assertInstanceOf(StorefrontResult.Success::class.java, result).value
            )
        assertEquals("bakir-tava", product.handle)
        assertEquals("Copper Pan", product.title)
        assertEquals(2, product.options.single().values.size)
        assertEquals(2, product.variants.size)
        assertEquals(BigDecimal("120.00"), product.variants.first().price.amount)
        assertEquals(BigDecimal("150.00"), product.variants.first().compareAtPrice?.amount)
        assertTrue(product.variants.first().availableForSale)
        assertFalse(product.variants.last().availableForSale)
        assertEquals(URI("https://cdn.shopify.com/s/files/1/tava.jpg"), product.media.single().image.uri)
        assertEquals(2, server.requestCount)
        val firstRequest = server.takeRequest().body.readUtf8()
        val secondRequest = server.takeRequest().body.readUtf8()
        assertTrue(firstRequest.contains("\"variantFirst\":250"))
        assertTrue(firstRequest.contains("\"mediaFirst\":50"))
        assertTrue(secondRequest.contains("variant-cursor-1"))
        assertTrue(secondRequest.contains("media-cursor-1"))
    }

    @Test
    fun `invalid product id fails locally without network access`() = runBlocking {
        val result =
            ApolloStorefrontProductGateway(client, mediaPolicy).loadProductDetail(
                ProductDetailRequest("gid://shopify/Collection/1")
            )

        val failure = assertInstanceOf(StorefrontResult.Failure::class.java, result)
        assertInstanceOf(StorefrontFailure.Configuration::class.java, failure.error)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `missing product is a successful not found result`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"data":{"product":null}}"""))

        val result =
            ApolloStorefrontProductGateway(client, mediaPolicy).loadProductDetail(
                ProductDetailRequest("gid://shopify/Product/9")
            )

        assertNull(assertInstanceOf(StorefrontResult.Success::class.java, result).value)
    }

    @Test
    fun `detail and variant images both use the injected media policy`() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                firstPage().replace(
                    "https://cdn.shopify.com/s/files/1/tava.jpg",
                    "https://gurbakir.com/cdn/shop/files/tava.jpg"
                )
            )
        )
        server.enqueue(MockResponse().setBody(secondPage()))

        val result =
            ApolloStorefrontProductGateway(client, StorefrontMediaPolicy("other.invalid"))
                .loadProductDetail(ProductDetailRequest("gid://shopify/Product/1"))

        val product =
            assertInstanceOf(
                StorefrontProductDetail::class.java,
                assertInstanceOf(StorefrontResult.Success::class.java, result).value
            )
        assertTrue(product.media.isEmpty())
        assertNull(product.variants.first().image)
    }

    private fun firstPage(): String = productPage(
        PageFixture(
            variant =
                """
            {
              "id":"gid://shopify/ProductVariant/11",
              "title":"Red",
              "availableForSale":true,
              "currentlyNotInStock":false,
              "price":{"amount":"120.00","currencyCode":"TRY"},
              "compareAtPrice":{"amount":"150.00","currencyCode":"TRY"},
              "image":{"__typename":"Image","url":"https://cdn.shopify.com/s/files/1/tava.jpg","altText":"Copper pan","width":600,"height":800},
              "selectedOptions":[{"name":"Color","value":"Red"}]
            }
                """.trimIndent(),
            variantEndCursor = "variant-cursor-1",
            hasNextVariantPage = true,
            media =
                """
            {
              "__typename":"MediaImage",
              "id":"gid://shopify/MediaImage/21",
              "image":{"__typename":"Image","url":"https://cdn.shopify.com/s/files/1/tava.jpg","altText":"Copper pan","width":600,"height":800}
            }
                """.trimIndent(),
            mediaEndCursor = "media-cursor-1",
            hasNextMediaPage = false
        )
    )

    private fun secondPage(): String = productPage(
        PageFixture(
            variant =
                """
            {
              "id":"gid://shopify/ProductVariant/12",
              "title":"Mavi",
              "availableForSale":false,
              "currentlyNotInStock":false,
              "price":{"amount":"130.00","currencyCode":"TRY"},
              "compareAtPrice":null,
              "image":null,
              "selectedOptions":[{"name":"Color","value":"Blue"}]
            }
                """.trimIndent(),
            variantEndCursor = "variant-cursor-2",
            hasNextVariantPage = false,
            media = "",
            mediaEndCursor = null,
            hasNextMediaPage = false
        )
    )

    private data class PageFixture(
        val variant: String,
        val variantEndCursor: String?,
        val hasNextVariantPage: Boolean,
        val media: String,
        val mediaEndCursor: String?,
        val hasNextMediaPage: Boolean
    )

    private fun productPage(page: PageFixture): String =
        """
        {
          "data": {
            "product": {
              "id":"gid://shopify/Product/1",
              "handle":"bakir-tava",
              "title":"Copper Pan",
              "description":"Handmade copper pan.",
              "availableForSale":true,
              "options":[{
                "id":"gid://shopify/ProductOption/31",
                "name":"Color",
                "optionValues":[
                  {"id":"gid://shopify/ProductOptionValue/41","name":"Red"},
                  {"id":"gid://shopify/ProductOptionValue/42","name":"Blue"}
                ]
              }],
              "variantsCount":{"count":2},
              "variants":{
                "nodes":[${page.variant}],
                "pageInfo":{"endCursor":${page.variantEndCursor.jsonString()},"hasNextPage":${page.hasNextVariantPage}}
              },
              "media":{
                "nodes":[${page.media.trim()}],
                "pageInfo":{"endCursor":${page.mediaEndCursor.jsonString()},"hasNextPage":${page.hasNextMediaPage}}
              }
            }
          }
        }
        """.trimIndent()
}

private fun String?.jsonString(): String = this?.let { "\"$it\"" } ?: "null"
