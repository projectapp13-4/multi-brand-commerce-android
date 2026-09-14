package com.gurbakir.storefront

import com.apollographql.apollo.ApolloClient
import java.math.BigDecimal
import java.net.URI
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ApolloHomeContentGatewayTest {
    private lateinit var server: MockWebServer
    private lateinit var client: ApolloClient
    private val mediaPolicy = StorefrontMediaPolicy("merchant.example")

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
    fun `home document preserves declared values resolved order and current resource outcomes`() =
        kotlinx.coroutines.runBlocking {
            server.enqueue(MockResponse().setBody(nonEmptyHomeResponse))

            val result =
                ApolloStorefrontGateway(client, mediaPolicy).loadHomeDocument(
                    HomeDocumentSelector(type = "mobile_home", handle = "primary")
                )

            val document =
                assertInstanceOf(StorefrontResult.Success::class.java, result).value as HomeDocumentObservation
            assertEquals("gid://shopify/Metaobject/root", document.rootGid)
            assertEquals("1", document.schemaVersion?.value)
            assertEquals("2", document.declaredSectionCount?.value)
            assertEquals(
                "[\"gid://shopify/Metaobject/grid\",\"gid://shopify/Metaobject/featured\"]",
                document.sections?.value
            )
            val references = requireNotNull(document.sections?.references)
            assertEquals(false, references.hasNextPage)
            assertEquals(
                listOf("grid", "featured"),
                references.nodes.map { requireNotNull(it.section).handle }
            )
            val collection =
                requireNotNull(references.nodes.first().section)
                    .collections
                    ?.references
                    ?.nodes
                    ?.single()
                    ?.resource as StorefrontHomeResource.Collection
            assertEquals(true, collection.hasProducts)
            assertEquals(
                HomeMediaObservation.Accepted(
                    StorefrontMedia(
                        URI("https://cdn.shopify.com/s/files/1/grid.jpg"),
                        "Grid",
                        600,
                        600
                    )
                ),
                collection.media
            )
            val product =
                requireNotNull(references.nodes.last().section)
                    .product
                    ?.reference
                    ?.resource as StorefrontHomeResource.Product
            assertEquals(
                HomeMoneyObservation.Accepted(StorefrontMoney(BigDecimal("10.00"), "TRY")),
                product.money
            )
            assertEquals(true, server.takeRequest().body.readUtf8().contains("HomeContentMetaobject"))
        }

    @Test
    fun `home document keeps explicit empty distinct from a nonempty unresolved declaration`() =
        kotlinx.coroutines.runBlocking {
            server.enqueue(MockResponse().setBody(emptyHomeResponse()))
            server.enqueue(MockResponse().setBody(unresolvedSectionResponse()))
            val gateway = ApolloStorefrontGateway(client, mediaPolicy)

            val empty =
                assertInstanceOf(
                    StorefrontResult.Success::class.java,
                    gateway.loadHomeDocument(HomeDocumentSelector("mobile_home", "empty"))
                ).value as HomeDocumentObservation
            val unresolved =
                assertInstanceOf(
                    StorefrontResult.Success::class.java,
                    gateway.loadHomeDocument(HomeDocumentSelector("mobile_home", "unresolved"))
                ).value as HomeDocumentObservation

            assertEquals("0", empty.declaredSectionCount?.value)
            assertEquals("[]", empty.sections?.value)
            assertEquals(0, empty.sections?.references?.nodes?.size)
            assertEquals("1", unresolved.declaredSectionCount?.value)
            assertEquals("[\"gid://shopify/Metaobject/draft-child\"]", unresolved.sections?.value)
            assertEquals(0, unresolved.sections?.references?.nodes?.size)
        }

    @Test
    fun `resource hydration preserves malformed money and blocks rejected direct image fallback`() =
        kotlinx.coroutines.runBlocking {
            server.enqueue(MockResponse().setBody(resourceBatchResponse()))
            val keys =
                listOf(
                    HomeResourceKey(HomeResourceKind.PRODUCT, "gid://shopify/Product/bad-money"),
                    HomeResourceKey(HomeResourceKind.COLLECTION, "gid://shopify/Collection/rejected-image")
                )

            val result = ApolloStorefrontGateway(client, mediaPolicy).loadHomeResources(keys)

            val batch = assertInstanceOf(StorefrontResult.Success::class.java, result).value as HomeResourceBatch
            val product = batch.resolutions[0].resource as StorefrontHomeResource.Product
            val collection = batch.resolutions[1].resource as StorefrontHomeResource.Collection
            assertEquals(HomeMoneyObservation.Malformed, product.money)
            assertEquals(HomeMediaObservation.Rejected, collection.media)
            assertEquals(true, collection.hasProducts)
            val requestBody = server.takeRequest().body.readUtf8()
            assertEquals(true, requestBody.contains("HomeResources"))
            assertEquals(true, requestBody.contains("gid://shopify/Product/bad-money"))
        }

    @Test
    fun `resource hydration retains null nodes as unresolved typed requests`() = kotlinx.coroutines.runBlocking {
        server.enqueue(MockResponse().setBody("""{"data":{"nodes":[null]}}"""))
        val key = HomeResourceKey(HomeResourceKind.PRODUCT, "gid://shopify/Product/missing")

        val result = ApolloStorefrontGateway(client, mediaPolicy).loadHomeResources(listOf(key))

        val batch = assertInstanceOf(StorefrontResult.Success::class.java, result).value as HomeResourceBatch
        assertEquals(HomeResourceResolution(key, null), batch.resolutions.single())
    }

    @Test
    fun `resource hydration rejects a returned identity that differs from its requested key`() =
        kotlinx.coroutines.runBlocking {
            server.enqueue(MockResponse().setBody(wrongResourceIdentityResponse()))
            val key = HomeResourceKey(HomeResourceKind.PRODUCT, "gid://shopify/Product/requested")

            val result = ApolloStorefrontGateway(client, mediaPolicy).loadHomeResources(listOf(key))

            assertEquals(
                StorefrontResult.Failure(StorefrontFailure.GraphQl(setOf("HOME_RESOURCE_ID_MISMATCH"))),
                result
            )
        }

    @Test
    fun `invalid selector and resource inputs fail locally without a Storefront request`() =
        kotlinx.coroutines.runBlocking {
            val gateway = ApolloStorefrontGateway(client, mediaPolicy)

            val selector = gateway.loadHomeDocument(HomeDocumentSelector("mobile_home", "../unsafe"))
            val duplicateKeys =
                gateway.loadHomeResources(
                    listOf(
                        HomeResourceKey(HomeResourceKind.PRODUCT, "gid://shopify/Product/1"),
                        HomeResourceKey(HomeResourceKind.PRODUCT, "gid://shopify/Product/1")
                    )
                )

            assertInstanceOf(StorefrontFailure.Configuration::class.java, (selector as StorefrontResult.Failure).error)
            assertInstanceOf(
                StorefrontFailure.Configuration::class.java,
                (duplicateKeys as StorefrontResult.Failure).error
            )
            assertEquals(0, server.requestCount)
        }

    private val nonEmptyHomeResponse: String =
        """
        {
          "data": {
            "metaobject": {
              "id": "gid://shopify/Metaobject/root",
              "handle": "primary",
              "type": "mobile_home",
              "updatedAt": "2026-09-13T08:00:00Z",
              "schemaVersion": {"type": "number_integer", "value": "1"},
              "declaredSectionCount": {"type": "number_integer", "value": "2"},
              "sections": {
                "type": "list.mixed_reference",
                "value": "[\"gid://shopify/Metaobject/grid\",\"gid://shopify/Metaobject/featured\"]",
                "references": {
                  "nodes": [
                    {
                      "__typename": "Metaobject",
                      "id": "gid://shopify/Metaobject/grid",
                      "handle": "grid",
                      "type": "mobile_home_collection_grid",
                      "title": {"type": "single_line_text_field", "value": "Collections"},
                      "collections": {
                        "type": "list.collection_reference",
                        "value": "[\"gid://shopify/Collection/grid\"]",
                        "references": {
                          "nodes": [{
                            "__typename": "Collection",
                            "id": "gid://shopify/Collection/grid",
                            "handle": "grid",
                            "title": "Grid",
                            "image": {
                              "__typename": "Image",
                              "url": "https://cdn.shopify.com/s/files/1/grid.jpg",
                              "altText": "Grid",
                              "width": 600,
                              "height": 600
                            },
                            "products": {"nodes": [{"featuredImage": null}]}
                          }],
                          "pageInfo": {"hasNextPage": false}
                        }
                      },
                      "product": null
                    },
                    {
                      "__typename": "Metaobject",
                      "id": "gid://shopify/Metaobject/featured",
                      "handle": "featured",
                      "type": "mobile_home_featured_product",
                      "title": {"type": "single_line_text_field", "value": "Featured"},
                      "collections": null,
                      "product": {
                        "type": "product_reference",
                        "value": "gid://shopify/Product/featured",
                        "reference": {
                          "__typename": "Product",
                          "id": "gid://shopify/Product/featured",
                          "handle": "featured",
                          "title": "Featured product",
                          "availableForSale": true,
                          "featuredImage": null,
                          "priceRange": {"minVariantPrice": {"amount": "10.00", "currencyCode": "TRY"}}
                        }
                      }
                    }
                  ],
                  "pageInfo": {"hasNextPage": false}
                }
              }
            }
          }
        }
        """.trimIndent()

    private fun emptyHomeResponse(): String = homeWithSectionObservation(
        handle = "empty",
        count = "0",
        storedValue = "[]"
    )

    private fun unresolvedSectionResponse(): String = homeWithSectionObservation(
        handle = "unresolved",
        count = "1",
        storedValue = "[\"gid://shopify/Metaobject/draft-child\"]"
    )

    private fun homeWithSectionObservation(handle: String, count: String, storedValue: String): String =
        """
        {
          "data": {
            "metaobject": {
              "id": "gid://shopify/Metaobject/$handle",
              "handle": "$handle",
              "type": "mobile_home",
              "updatedAt": "2026-09-13T08:00:00Z",
              "schemaVersion": {"type": "number_integer", "value": "1"},
              "declaredSectionCount": {"type": "number_integer", "value": "$count"},
              "sections": {
                "type": "list.mixed_reference",
                "value": ${jsonString(storedValue)},
                "references": {"nodes": [], "pageInfo": {"hasNextPage": false}}
              }
            }
          }
        }
        """.trimIndent()

    private fun resourceBatchResponse(): String =
        """
        {
          "data": {
            "nodes": [
              {
                "__typename": "Product",
                "id": "gid://shopify/Product/bad-money",
                "handle": "bad-money",
                "title": "Bad money",
                "availableForSale": true,
                "featuredImage": null,
                "priceRange": {"minVariantPrice": {"amount": "not-money", "currencyCode": "TRY"}}
              },
              {
                "__typename": "Collection",
                "id": "gid://shopify/Collection/rejected-image",
                "handle": "rejected-image",
                "title": "Rejected image",
                "image": {
                  "__typename": "Image",
                  "url": "https://untrusted.example/image.jpg",
                  "altText": null,
                  "width": 600,
                  "height": 600
                },
                "products": {"nodes": [{"featuredImage": {
                  "__typename": "Image",
                  "url": "https://cdn.shopify.com/s/files/1/fallback.jpg",
                  "altText": null,
                  "width": 600,
                  "height": 600
                }}]}
              }
            ]
          }
        }
        """.trimIndent()

    private fun wrongResourceIdentityResponse(): String =
        """
        {
          "data": {
            "nodes": [{
              "__typename": "Product",
              "id": "gid://shopify/Product/other",
              "handle": "other",
              "title": "Other",
              "availableForSale": true,
              "featuredImage": null,
              "priceRange": {"minVariantPrice": {"amount": "1.00", "currencyCode": "TRY"}}
            }]
          }
        }
        """.trimIndent()

    private fun jsonString(value: String): String = "\"${value.replace("\\", "\\\\").replace("\"", "\\\"")}\""
}
