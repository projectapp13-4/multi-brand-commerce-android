package com.gurbakir.storefront

import com.apollographql.apollo.ApolloClient
import java.math.BigDecimal
import java.net.URI
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ApolloStorefrontSearchGatewayTest {
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
    fun `search maps only product nodes and forwards normalized cursor variables`() = runBlocking {
        server.enqueue(MockResponse().setBody(searchResponse()))

        val result =
            ApolloStorefrontSearchGateway(client, mediaPolicy).searchProducts(
                ProductSearchPageRequest("  bakır   tava  ", Cursor("next-cursor"))
            )

        val page = assertInstanceOf(StorefrontResult.Success::class.java, result).value as ProductSearchPage
        assertEquals("bakır tava", page.query)
        assertEquals(1, page.products.size)
        assertEquals("bakir-tava", page.products.single().handle)
        assertEquals(BigDecimal("120.00"), page.products.single().minimumPrice.amount)
        assertEquals(URI("https://cdn.shopify.com/s/files/1/tava.jpg"), page.products.single().media?.uri)
        assertEquals(3, page.totalCount)
        assertEquals(Cursor("cursor-2"), page.endCursor)
        assertEquals(true, page.hasNextPage)
        val body = server.takeRequest().body.readUtf8()
        assertEquals(true, body.contains("bakır tava"))
        assertEquals(true, body.contains("next-cursor"))
        assertEquals(true, body.contains("types: [PRODUCT]"))
        assertEquals(true, body.contains("prefix: LAST"))
    }

    @Test
    fun `invalid search query fails locally without network access`() = runBlocking {
        val result =
            ApolloStorefrontSearchGateway(client, mediaPolicy).searchProducts(ProductSearchPageRequest("x"))

        val failure = assertInstanceOf(StorefrontResult.Failure::class.java, result)
        assertInstanceOf(StorefrontFailure.Configuration::class.java, failure.error)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `search rejects merchant media that does not match its injected policy`() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                searchResponse().replace(
                    "https://cdn.shopify.com/s/files/1/tava.jpg",
                    "https://gurbakir.com/cdn/shop/files/tava.jpg"
                )
            )
        )

        val result =
            ApolloStorefrontSearchGateway(client, StorefrontMediaPolicy("other.invalid"))
                .searchProducts(ProductSearchPageRequest("bakır tava"))

        val page = assertInstanceOf(StorefrontResult.Success::class.java, result).value as ProductSearchPage
        assertNull(page.products.single().media)
    }

    private fun searchResponse(): String =
        """
        {
          "data": {
            "search": {
              "nodes": [
                {
                  "__typename": "Product",
                  "id": "gid://shopify/Product/1",
                  "handle": "bakir-tava",
                  "title": "Bakır Tava",
                  "availableForSale": true,
                  "featuredImage": {
                    "__typename": "Image",
                    "url": "https://cdn.shopify.com/s/files/1/tava.jpg",
                    "altText": "Bakır tava",
                    "width": 600,
                    "height": 800
                  },
                  "priceRange": {
                    "minVariantPrice": {"amount": "120.00", "currencyCode": "TRY"},
                    "maxVariantPrice": {"amount": "150.00", "currencyCode": "TRY"}
                  }
                },
                {"__typename": "Page"}
              ],
              "pageInfo": {"endCursor": "cursor-2", "hasNextPage": true},
              "totalCount": 3
            }
          }
        }
        """.trimIndent()
}
