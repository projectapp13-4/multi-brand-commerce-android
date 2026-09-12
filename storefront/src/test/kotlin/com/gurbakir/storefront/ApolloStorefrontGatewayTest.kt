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

class ApolloStorefrontGatewayTest {
    private lateinit var server: MockWebServer
    private lateinit var client: ApolloClient
    private val mediaPolicy = StorefrontMediaPolicy("gurbakir.com")

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        client =
            ApolloClient.Builder()
                .serverUrl(server.url("graphql").toString())
                .addHttpHeader("X-Shopify-Storefront-Access-Token", "controlled-test-token")
                .build()
    }

    @AfterEach
    fun tearDown() {
        client.close()
        server.shutdown()
    }

    @Test
    fun `shop query uses generated model and controlled header`() {
        server.enqueue(
            MockResponse().setBody(
                """{"data":{"shop":{"name":"Gür Bakır","primaryDomain":{"host":"gurbakir.com"}}}}"""
            )
        )
        var result: StorefrontResult<ShopSummary>? = null

        kotlinx.coroutines.runBlocking {
            result = ApolloStorefrontGateway(client, mediaPolicy).loadShopSummary()
        }

        assertEquals(
            StorefrontResult.Success(ShopSummary("Gür Bakır", "gurbakir.com")),
            result
        )
        val request = server.takeRequest()
        assertEquals("controlled-test-token", request.getHeader("X-Shopify-Storefront-Access-Token"))
        assertEquals("/graphql", request.path)
    }

    @Test
    fun `catalog mapping preserves cursor nullability and rejects unsafe image uri`() {
        server.enqueue(
            MockResponse().setBody(
                """
                {
                  "data": {
                    "products": {
                      "nodes": [{
                        "id": "gid://shopify/Product/1",
                        "handle": "bakir",
                        "title": "Copper",
                        "featuredImage": {"url": "javascript:alert(1)"},
                        "variants": {
                          "nodes": [{
                            "id": "gid://shopify/ProductVariant/1",
                            "title": "Default",
                            "availableForSale": true
                          }]
                        }
                      }],
                      "pageInfo": {"endCursor": "cursor-1", "hasNextPage": true}
                    }
                  }
                }
                """.trimIndent()
            )
        )
        var result: StorefrontResult<CatalogPage>? = null

        kotlinx.coroutines.runBlocking {
            result = ApolloStorefrontGateway(client, mediaPolicy).loadCatalogPage(after = null)
        }

        val success = assertInstanceOf(StorefrontResult.Success::class.java, result)
        val page = assertInstanceOf(CatalogPage::class.java, success.value)
        assertEquals(Cursor("cursor-1"), page.endCursor)
        assertEquals(true, page.hasNextPage)
        assertNull(page.products.single().primaryImage)
        assertEquals("gid://shopify/ProductVariant/1", page.products.single().variants.single().id)
    }

    @Test
    fun `home collection uses first product media when collection image is absent`() = kotlinx.coroutines.runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """
                    {
                      "data": {
                        "collection": {
                          "id": "gid://shopify/Collection/1",
                          "handle": "bardaklar",
                          "title": "Bardaklar",
                          "image": null,
                          "products": {
                            "nodes": [{
                              "featuredImage": {
                                "__typename": "Image",
                                "url": "https://cdn.shopify.com/s/files/1/product.jpg",
                                "altText": "Bakır bardak",
                                "width": 320,
                                "height": 428
                              }
                            }]
                          }
                        }
                      }
                    }
                """.trimIndent()
            )
        )

        val result = ApolloStorefrontGateway(client, mediaPolicy).loadHomeCollection("bardaklar")

        val summary = assertInstanceOf(StorefrontResult.Success::class.java, result).value as HomeCollectionSummary
        assertEquals("bardaklar", summary.handle)
        assertEquals(URI("https://cdn.shopify.com/s/files/1/product.jpg"), summary.media.uri)
        assertEquals("Bakır bardak", summary.media.altText)
    }

    @Test
    fun `home product preserves zero money and rejects unsafe media without inventing content`() =
        kotlinx.coroutines.runBlocking {
            server.enqueue(
                MockResponse().setBody(
                    """
                    {
                      "data": {
                        "product": {
                          "id": "gid://shopify/Product/1",
                          "handle": "bakir-tava",
                          "title": "Bakır Tava",
                          "availableForSale": true,
                          "featuredImage": {
                            "__typename": "Image",
                            "url": "https://example.test/unowned.jpg",
                            "altText": "Unowned",
                            "width": 640,
                            "height": 856
                          },
                          "priceRange": {
                            "minVariantPrice": {"amount": "0.00", "currencyCode": "TRY"}
                          }
                        }
                      }
                    }
                    """.trimIndent()
                )
            )

            val result = ApolloStorefrontGateway(client, mediaPolicy).loadHomeProduct("bakir-tava")

            val summary = assertInstanceOf(StorefrontResult.Success::class.java, result).value as HomeProductSummary
            assertEquals(BigDecimal("0.00"), summary.price.amount)
            assertEquals("TRY", summary.price.currencyCode)
            assertNull(summary.media)
        }

    @Test
    fun `top level catalog and home mapping use the injected merchant domain`() = kotlinx.coroutines.runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """
                    {"data":{"products":{"nodes":[{
                      "id":"gid://shopify/Product/1",
                      "handle":"bakir",
                      "title":"Copper",
                      "featuredImage":{"url":"https://gurbakir.com/cdn/shop/files/product.jpg"},
                      "variants":{"nodes":[{
                        "id":"gid://shopify/ProductVariant/1",
                        "title":"Default",
                        "availableForSale":true
                      }]}
                    }],"pageInfo":{"endCursor":null,"hasNextPage":false}}}}
                """.trimIndent()
            )
        )
        server.enqueue(
            MockResponse().setBody(
                """
                    {"data":{"collection":{
                      "id":"gid://shopify/Collection/1",
                      "handle":"bardaklar",
                      "title":"Bardaklar",
                      "image":{
                        "__typename":"Image",
                        "url":"https://gurbakir.com/cdn/shop/files/product.jpg",
                        "altText":null,
                        "width":320,
                        "height":428
                      },
                      "products":{"nodes":[]}
                    }}}
                """.trimIndent()
            )
        )
        val gateway = ApolloStorefrontGateway(client, StorefrontMediaPolicy("other.invalid"))

        val catalog = gateway.loadCatalogPage(after = null)
        val home = gateway.loadHomeCollection("bardaklar")

        val page = assertInstanceOf(StorefrontResult.Success::class.java, catalog).value as CatalogPage
        assertNull(page.products.single().primaryImage)
        assertNull(assertInstanceOf(StorefrontResult.Success::class.java, home).value)
    }

    @Test
    fun `invalid home handle fails locally without a request`() = kotlinx.coroutines.runBlocking {
        val result = ApolloStorefrontGateway(client, mediaPolicy).loadHomeCollection("../unsafe")

        assertInstanceOf(StorefrontResult.Failure::class.java, result)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `collection catalog maps cursor money media and only meaningful product type filter`() =
        kotlinx.coroutines.runBlocking {
            server.enqueue(MockResponse().setBody(collectionCatalogResponse()))

            val result =
                ApolloStorefrontCatalogGateway(client, mediaPolicy).loadCollectionCatalogPage(
                    CollectionCatalogPageRequest(
                        handle = "ozel-urunlerimiz",
                        after = Cursor("cursor-1"),
                        sort = CollectionCatalogSort.PRICE_HIGH_TO_LOW,
                        productTypes = setOf("Fondü Tavası")
                    )
                )

            val page = assertInstanceOf(StorefrontResult.Success::class.java, result).value as CollectionCatalogPage
            assertEquals(Cursor("cursor-2"), page.endCursor)
            assertEquals(true, page.hasNextPage)
            assertEquals(BigDecimal("100.00"), page.products.single().minimumPrice.amount)
            assertEquals(true, page.products.single().hasPriceRange)
            assertEquals(URI("https://cdn.shopify.com/s/files/1/fondu.jpg"), page.products.single().media?.uri)
            assertEquals(listOf("Fondü Tavası", "Şişe"), page.productTypeFilter?.values?.map { it.value })

            val requestBody = server.takeRequest().body.readUtf8()
            assertEquals(true, requestBody.contains("PRICE"))
            assertEquals(true, requestBody.contains("reverse"))
            assertEquals(true, requestBody.contains("Fondü Tavası"))
            assertEquals(false, requestBody.contains("filter.p.product_type.fondu"))
        }

    @Test
    fun `invalid collection request fails locally without network access`() = kotlinx.coroutines.runBlocking {
        val result =
            ApolloStorefrontCatalogGateway(client, mediaPolicy).loadCollectionCatalogPage(
                CollectionCatalogPageRequest(handle = "../unsafe")
            )

        val failure = assertInstanceOf(StorefrontResult.Failure::class.java, result)
        assertInstanceOf(StorefrontFailure.Configuration::class.java, failure.error)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `catalog pagination rejects merchant media that does not match its injected policy`() =
        kotlinx.coroutines.runBlocking {
            server.enqueue(
                MockResponse().setBody(
                    collectionCatalogResponse().replace(
                        "https://cdn.shopify.com/s/files/1/fondu.jpg",
                        "https://gurbakir.com/cdn/shop/files/fondu.jpg"
                    )
                )
            )

            val result =
                ApolloStorefrontCatalogGateway(client, StorefrontMediaPolicy("other.invalid"))
                    .loadCollectionCatalogPage(CollectionCatalogPageRequest(handle = "ozel-urunlerimiz"))

            val page = assertInstanceOf(StorefrontResult.Success::class.java, result).value as CollectionCatalogPage
            assertNull(page.products.single().media)
        }

    @Test
    fun `graphql error maps only stable code without message`() {
        server.enqueue(
            MockResponse().setBody(
                """{"errors":[{"message":"sensitive upstream detail","extensions":{"code":"ACCESS_DENIED"}}]}"""
            )
        )
        var result: StorefrontResult<ShopSummary>? = null

        kotlinx.coroutines.runBlocking {
            result = ApolloStorefrontGateway(client, mediaPolicy).loadShopSummary()
        }

        assertEquals(
            StorefrontResult.Failure(StorefrontFailure.GraphQl(setOf("ACCESS_DENIED"))),
            result
        )
    }

    @Test
    fun `invalid cart input fails locally without a request`() {
        var result: StorefrontResult<CartReference>? = null

        kotlinx.coroutines.test.runTest {
            result =
                ApolloStorefrontGateway(client, mediaPolicy).createCart(
                    listOf(CartLineInput(merchandiseId = "", quantity = 0))
                )
        }

        val failure = assertInstanceOf(StorefrontResult.Failure::class.java, result)
        assertInstanceOf(StorefrontFailure.UserErrors::class.java, failure.error)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `generated cart mutations create update and remove a synthetic line`() = kotlinx.coroutines.runBlocking {
        server.enqueue(MockResponse().setBody(cartMutationResponse("cartCreate", quantity = 1)))
        server.enqueue(MockResponse().setBody(cartMutationResponse("cartLinesUpdate", quantity = 2)))
        server.enqueue(MockResponse().setBody(cartMutationResponse("cartLinesRemove", quantity = 0)))
        val gateway = ApolloStorefrontGateway(client, mediaPolicy)

        val created = gateway.createCart(listOf(CartLineInput(VARIANT_ID, 1)))
        val createdCart =
            assertInstanceOf(StorefrontResult.Success::class.java, created, created.toString()).value as CartReference
        val updated =
            gateway.updateCartLines(
                createdCart.id,
                listOf(CartLineUpdate(createdCart.lines.single().id, 2))
            )
        val updatedCart = assertInstanceOf(StorefrontResult.Success::class.java, updated).value as CartReference
        val removed = gateway.removeCartLines(updatedCart.id, listOf(updatedCart.lines.single().id))
        val removedCart = assertInstanceOf(StorefrontResult.Success::class.java, removed).value as CartReference

        assertEquals(1, createdCart.totalQuantity)
        assertEquals(2, updatedCart.totalQuantity)
        assertEquals(0, removedCart.totalQuantity)
        assertEquals(3, server.requestCount)
    }

    @Test
    fun `cart line images use the injected merchant media policy`() = kotlinx.coroutines.runBlocking {
        server.enqueue(
            MockResponse().setBody(
                cartMutationResponse("cartCreate", quantity = 1).replace(
                    "\"image\":null",
                    """
                    "image":{
                      "__typename":"Image",
                      "url":"https://gurbakir.com/cdn/shop/files/cart.jpg",
                      "altText":null,
                      "width":320,
                      "height":428
                    }
                    """.trimIndent()
                )
            )
        )

        val result =
            ApolloStorefrontGateway(client, StorefrontMediaPolicy("other.invalid"))
                .createCart(listOf(CartLineInput(VARIANT_ID, 1)))

        val cart = assertInstanceOf(StorefrontResult.Success::class.java, result).value as CartReference
        assertNull(cart.lines.single().image)
    }

    @Test
    fun `authenticated cart creation sends customer token only as typed buyer identity input`() =
        kotlinx.coroutines.runBlocking {
            server.enqueue(MockResponse().setBody(cartMutationResponse("cartCreate", quantity = 1)))

            val result =
                ApolloStorefrontGateway(client, mediaPolicy).createCart(
                    listOf(CartLineInput(VARIANT_ID, 1)),
                    SensitiveBuyerAccessToken.from("synthetic-buyer-token")
                )

            assertInstanceOf(StorefrontResult.Success::class.java, result, result.toString())
            val requestBody = server.takeRequest().body.readUtf8()
            assertEquals(true, requestBody.contains("synthetic-buyer-token"))
            assertEquals("<redacted-buyer-access-token>", SensitiveBuyerAccessToken.from("x").toString())
        }

    @Test
    fun `missing remote cart maps to definitive invalid cart`() = kotlinx.coroutines.runBlocking {
        server.enqueue(MockResponse().setBody("""{"data":{"cart":null}}"""))

        val result =
            ApolloStorefrontGateway(client, mediaPolicy).loadCart(
                SensitiveCartId.from("gid://shopify/Cart/missing?key=synthetic")
            )

        assertEquals(
            StorefrontResult.Failure(StorefrontFailure.InvalidCart(InvalidCartReason.NOT_FOUND)),
            result
        )
    }

    @Test
    fun `cart read follows the bounded second line page and returns a complete snapshot`() =
        kotlinx.coroutines.runBlocking {
            server.enqueue(MockResponse().setBody(cartReadFirstPageResponse()))
            server.enqueue(MockResponse().setBody(cartReadSecondPageResponse()))

            val result =
                ApolloStorefrontGateway(client, mediaPolicy).loadCart(
                    SensitiveCartId.from(CART_ID)
                )

            val cart = assertInstanceOf(StorefrontResult.Success::class.java, result).value as CartReference
            assertEquals(
                listOf("gid://shopify/CartLine/first", "gid://shopify/CartLine/second"),
                cart.lines.map {
                    it.id.use(String::toString)
                }
            )
            assertEquals(2, cart.totalQuantity)
            assertEquals(false, cart.hasMoreLines)
            assertEquals(2, server.requestCount)
            assertEquals(true, server.takeRequest().body.readUtf8().contains("CartById"))
            assertEquals(true, server.takeRequest().body.readUtf8().contains("cursor-1"))
        }

    @Test
    fun `cart reference mapping accepts only safe checkout uri`() {
        val reference =
            CartReference(
                id = SensitiveCartId.from("gid://shopify/Cart/1?key=synthetic"),
                checkoutUrl = SensitiveCheckoutUrl.from(URI("https://gurbakir.com/checkouts/test")),
                totalQuantity = 1,
                lines = emptyList(),
                hasMoreLines = false,
                warningCodes = emptySet()
            )

        assertEquals(
            "CartReference(id=<redacted>, checkoutUrl=<redacted>, totalQuantity=1, " +
                "lineCount=0, hasMoreLines=false, warningCodes=[])",
            reference.toString()
        )
    }

    private fun cartMutationResponse(operation: String, quantity: Int): String {
        val lines =
            if (quantity == 0) {
                "[]"
            } else {
                """[
                    {"__typename":"CartLine","id":"$LINE_ID","quantity":$quantity,
                     "instructions":{"canRemove":true,"canUpdateQuantity":true},
                     "cost":{
                       "amountPerQuantity":{"amount":"10.00","currencyCode":"TRY"},
                       "totalAmount":{"amount":"${quantity * 10}.00","currencyCode":"TRY"}
                     },
                     "merchandise":{
                      "__typename":"ProductVariant","id":"$VARIANT_ID",
                      "title":"Synthetic variant","availableForSale":true,
                      "currentlyNotInStock":false,
                      "quantityRule":{"minimum":1,"maximum":null,"increment":1},
                      "image":null,
                      "product":{"id":"gid://shopify/Product/synthetic","title":"Synthetic product"}
                    }}
                ]
                """.trimIndent()
            }
        return """
            {
              "data": {
                "$operation": {
                  "cart": {
                    "__typename": "Cart",
                    "id": "$CART_ID",
                    "checkoutUrl": "https://gurbakir.com/cart/c/synthetic?key=synthetic-checkout-secret",
                    "totalQuantity": $quantity,
                    "buyerIdentity": {"customer": null},
                    "cost": {
                      "subtotalAmount": {"amount": "${quantity * 10}.00", "currencyCode": "TRY"},
                      "totalAmount": {"amount": "${quantity * 10}.00", "currencyCode": "TRY"}
                    },
                    "lines": {
                      "nodes": $lines,
                      "pageInfo": {"endCursor": null, "hasNextPage": false}
                    }
                  },
                  "userErrors": [],
                  "warnings": []
                }
              }
            }
        """.trimIndent()
    }

    private fun cartReadFirstPageResponse(): String =
        """
        {
          "data": {
            "cart": {
              "__typename": "Cart",
              "id": "$CART_ID",
              "checkoutUrl": "https://gurbakir.com/cart/c/synthetic?key=synthetic-checkout-secret",
              "totalQuantity": 2,
              "buyerIdentity": {"customer": null},
              "cost": {
                "subtotalAmount": {"amount": "20.00", "currencyCode": "TRY"},
                "totalAmount": {"amount": "20.00", "currencyCode": "TRY"}
              },
              "lines": {
                "nodes": [${cartLineResponse("gid://shopify/CartLine/first")}],
                "pageInfo": {"endCursor": "cursor-1", "hasNextPage": true}
              }
            }
          }
        }
        """.trimIndent()

    private fun cartReadSecondPageResponse(): String =
        """
        {
          "data": {
            "cart": {
              "lines": {
                "nodes": [${cartLineResponse("gid://shopify/CartLine/second")}],
                "pageInfo": {"endCursor": null, "hasNextPage": false}
              }
            }
          }
        }
        """.trimIndent()

    private fun cartLineResponse(lineId: String): String =
        """
        {
          "__typename": "CartLine",
          "id": "$lineId",
          "quantity": 1,
          "instructions": {"canRemove": true, "canUpdateQuantity": true},
          "cost": {
            "amountPerQuantity": {"amount": "10.00", "currencyCode": "TRY"},
            "totalAmount": {"amount": "10.00", "currencyCode": "TRY"}
          },
          "merchandise": {
            "__typename": "ProductVariant",
            "id": "$VARIANT_ID",
            "title": "Synthetic variant",
            "availableForSale": true,
            "currentlyNotInStock": false,
            "quantityRule": {"minimum": 1, "maximum": null, "increment": 1},
            "image": null,
            "product": {"id": "gid://shopify/Product/synthetic", "title": "Synthetic product"}
          }
        }
        """.trimIndent()

    private fun collectionCatalogResponse(): String =
        """
        {
          "data": {
            "collection": {
              "id": "gid://shopify/Collection/1",
              "handle": "ozel-urunlerimiz",
              "title": "Özel Ürünlerimiz",
              "products": {
                "nodes": [{
                  "id": "gid://shopify/Product/1",
                  "handle": "bakir-fondu",
                  "title": "Bakır Fondü",
                  "availableForSale": true,
                  "featuredImage": {
                    "__typename": "Image",
                    "url": "https://cdn.shopify.com/s/files/1/fondu.jpg",
                    "altText": "Bakır fondü",
                    "width": 600,
                    "height": 800
                  },
                  "priceRange": {
                    "minVariantPrice": {"amount": "100.00", "currencyCode": "TRY"},
                    "maxVariantPrice": {"amount": "150.00", "currencyCode": "TRY"}
                  }
                }],
                "filters": [{
                  "id": "filter.p.product_type",
                  "label": "Ürün türü",
                  "type": "LIST",
                  "values": [
                    {"id": "filter.p.product_type.fondu", "label": "Fondü Tavası", "count": 4},
                    {"id": "filter.p.product_type.sise", "label": "Şişe", "count": 1}
                  ]
                }],
                "pageInfo": {"endCursor": "cursor-2", "hasNextPage": true}
              }
            }
          }
        }
        """.trimIndent()

    private companion object {
        const val CART_ID = "gid://shopify/Cart/synthetic?key=synthetic-secret"
        const val LINE_ID = "gid://shopify/CartLine/synthetic"
        const val VARIANT_ID = "gid://shopify/ProductVariant/synthetic"
    }
}
