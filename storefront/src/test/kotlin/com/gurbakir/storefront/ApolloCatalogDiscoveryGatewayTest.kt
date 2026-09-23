package com.gurbakir.storefront

import com.apollographql.apollo.ApolloClient
import com.gurbakir.storefront.graphql.CatalogDiscoveryMenuQuery
import java.net.URI
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ApolloCatalogDiscoveryGatewayTest {
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
    fun `invalid menu handle fails before network access`() = runBlocking {
        listOf("", " main-menu", "Main-Menu", "main_menu", "a".repeat(256)).forEach { handle ->
            val result = gateway().loadCatalogDiscovery(CatalogDiscoveryRequest(handle))

            assertEquals(
                StorefrontResult.Failure(StorefrontFailure.Configuration(setOf("catalog.discovery.request"))),
                result
            )
        }

        assertEquals(0, server.requestCount)
    }

    @Test
    fun `generated operation sends selector and maps nested collection resources in provider order`() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                menuResponse(
                    items =
                        listOf(
                            collectionItem(
                                itemId = "menu-item-parent",
                                title = "Parent label",
                                collectionId = "gid://shopify/Collection/parent",
                                handle = "parent",
                                collectionTitle = "Parent collection",
                                image = null,
                                productImage = image("product-parent.jpg", "Parent product"),
                                children =
                                    listOf(
                                        unsupportedItem(
                                            itemId = "menu-item-container",
                                            title = "Container",
                                            children =
                                                listOf(
                                                    collectionItem(
                                                        itemId = "menu-item-child",
                                                        title = "Child label",
                                                        collectionId = "gid://shopify/Collection/child",
                                                        handle = "child",
                                                        collectionTitle = "Child collection",
                                                        image = image("collection-child.jpg", null),
                                                        productImage = image("product-child.jpg", "Unused")
                                                    )
                                                )
                                        )
                                    )
                            )
                        )
                )
            )
        )

        val result = gateway().loadCatalogDiscovery(CatalogDiscoveryRequest("catalog-menu"))

        val menu = assertSuccess(result)
        assertEquals("catalog-menu", menu?.handle)
        val parent = menu!!.items.single()
        assertEquals("menu-item-parent", parent.id)
        assertEquals("Parent label", parent.title)
        assertFalse(parent.hasCollectionFilters)
        val parentCollection = (parent.target as CatalogDiscoveryTarget.Collection).collection
        assertEquals("parent", parentCollection.handle)
        assertEquals(URI("https://cdn.shopify.com/s/files/1/product-parent.jpg"), parentCollection.media.uri)
        val child = parent.children.single().children.single()
        assertEquals("Child label", child.title)
        assertEquals(URI("https://cdn.shopify.com/s/files/1/collection-child.jpg"), child.collection().media.uri)

        val requestBody = server.takeRequest().body.readUtf8()
        assertTrue(requestBody.contains("CatalogDiscoveryMenu"))
        assertTrue(requestBody.contains("\"handle\":\"catalog-menu\""))
    }

    @Test
    fun `generated document queries tags and a terminal sentinel without selecting menu item url`() {
        val document = CatalogDiscoveryMenuQuery.OPERATION_DOCUMENT
        val itemFields =
            document.substringAfter("fragment CatalogDiscoveryItemFields")
                .substringBefore("fragment CatalogDiscoveryLevel3")
        val imageFields =
            document.substringAfter("fragment HomeImageFields")
                .substringBefore("fragment CatalogDiscoveryItemFields")

        assertTrue(document.contains("menu(handle:"))
        assertTrue(itemFields.contains(" tags "))
        assertFalse(Regex("""\burl\b""").containsMatchIn(itemFields))
        assertTrue(Regex("""\burl\b""").containsMatchIn(imageFields))
        assertTrue(
            document.contains(
                "fragment CatalogDiscoveryLevel3 on MenuItem { __typename " +
                    "...CatalogDiscoveryItemFields items { id } }"
            )
        )
    }

    @Test
    fun `third level children reject the whole result instead of truncating`() = runBlocking {
        val third =
            unsupportedItem(
                itemId = "level-three",
                title = "Level three",
                sentinelChildren = listOf("level-four")
            )
        val second = unsupportedItem("level-two", "Level two", children = listOf(third))
        val first = unsupportedItem("level-one", "Level one", children = listOf(second))
        server.enqueue(MockResponse().setBody(menuResponse(listOf(first))))

        val result = gateway().loadCatalogDiscovery(CatalogDiscoveryRequest("catalog-menu"))

        assertEquals(
            StorefrontFailure.Configuration(setOf("catalog.discovery.depth")),
            assertFailure(result)
        )
    }

    @Test
    fun `node limit counts unsupported entries before filtering`() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                menuResponse(
                    (1..CatalogDiscoveryBounds.MAX_NODES).map { index ->
                        unsupportedItem("item-$index", "Item $index")
                    }
                )
            )
        )
        val accepted = gateway().loadCatalogDiscovery(CatalogDiscoveryRequest("catalog-menu"))

        assertEquals(CatalogDiscoveryBounds.MAX_NODES, assertSuccess(accepted)?.items?.size)

        server.enqueue(
            MockResponse().setBody(
                menuResponse(
                    (1..CatalogDiscoveryBounds.MAX_NODES + 1).map { index ->
                        unsupportedItem("next-$index", "Next $index")
                    }
                )
            )
        )
        val rejected = gateway().loadCatalogDiscovery(CatalogDiscoveryRequest("catalog-menu"))

        assertEquals(
            StorefrontFailure.Configuration(setOf("catalog.discovery.nodes")),
            assertFailure(rejected)
        )
    }

    @Test
    fun `null menu remains a successful nullable provider result`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"data":{"menu":null}}"""))

        val result = gateway().loadCatalogDiscovery(CatalogDiscoveryRequest("catalog-menu"))

        assertEquals(null, assertSuccess(result))
    }

    @Test
    fun `mismatched handle and invalid root identity are configuration failures`() = runBlocking {
        server.enqueue(MockResponse().setBody(menuResponse(emptyList(), handle = "other-menu")))
        assertEquals(
            StorefrontFailure.Configuration(setOf("catalog.discovery.root")),
            assertFailure(gateway().loadCatalogDiscovery(CatalogDiscoveryRequest("catalog-menu")))
        )

        server.enqueue(MockResponse().setBody(menuResponse(emptyList(), id = " root-id ")))
        assertEquals(
            StorefrontFailure.Configuration(setOf("catalog.discovery.root")),
            assertFailure(gateway().loadCatalogDiscovery(CatalogDiscoveryRequest("catalog-menu")))
        )
    }

    @Test
    fun `provider discrimination carries filters and never guesses malformed or unknown actions`() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                menuResponse(
                    listOf(
                        collectionItem(
                            itemId = "filtered",
                            title = "Filtered",
                            collectionId = "gid://shopify/Collection/filtered",
                            handle = "filtered",
                            collectionTitle = "Filtered collection",
                            image = image("filtered.jpg", "Filtered"),
                            productImage = image("unused.jpg", null),
                            tags = listOf("")
                        ),
                        item(
                            itemId = "wrong-resource",
                            title = "Wrong resource",
                            type = "COLLECTION",
                            resource = """{"__typename":"Page"}"""
                        ),
                        item(
                            itemId = "missing-resource",
                            title = "Missing resource",
                            type = "COLLECTION",
                            resource = "null"
                        ),
                        item(
                            itemId = "unknown",
                            title = "Future item",
                            type = "FUTURE_TYPE",
                            resource =
                                collectionResource(
                                    collectionId = "gid://shopify/Collection/future",
                                    handle = "future",
                                    collectionTitle = "Future collection",
                                    image = image("future.jpg", null),
                                    productImage = image("future-product.jpg", null)
                                )
                        )
                    )
                )
            )
        )

        val nodes = assertSuccess(gateway().loadCatalogDiscovery(CatalogDiscoveryRequest("catalog-menu")))!!.items

        assertTrue(nodes[0].hasCollectionFilters)
        assertTrue(nodes[0].target is CatalogDiscoveryTarget.Collection)
        assertTrue(nodes[1].target is CatalogDiscoveryTarget.Malformed)
        assertTrue(nodes[2].target is CatalogDiscoveryTarget.UnavailableCollection)
        assertTrue(nodes[3].target is CatalogDiscoveryTarget.Unsupported)
    }

    @Test
    fun `present rejected collection image does not fall back and empty collection is unavailable`() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                menuResponse(
                    listOf(
                        collectionItem(
                            itemId = "unsafe-image",
                            title = "Unsafe",
                            collectionId = "gid://shopify/Collection/unsafe",
                            handle = "unsafe",
                            collectionTitle = "Unsafe collection",
                            image = image("https://evil.invalid/image.jpg", "Unsafe", absolute = true),
                            productImage = image("safe-fallback.jpg", "Safe")
                        ),
                        collectionItem(
                            itemId = "empty",
                            title = "Empty",
                            collectionId = "gid://shopify/Collection/empty",
                            handle = "empty",
                            collectionTitle = "Empty collection",
                            image = image("empty.jpg", null),
                            productImage = null
                        )
                    )
                )
            )
        )

        val nodes = assertSuccess(gateway().loadCatalogDiscovery(CatalogDiscoveryRequest("catalog-menu")))!!.items

        assertTrue(nodes.all { it.target is CatalogDiscoveryTarget.UnavailableCollection })
    }

    @Test
    fun `graphql errors override returned data and retryable transport remains distinct`() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """
                {
                  "data":{"menu":null},
                  "errors":[{"message":"redacted","extensions":{"code":"ACCESS_DENIED"}}]
                }
                """.trimIndent()
            )
        )
        assertEquals(
            StorefrontFailure.GraphQl(setOf("ACCESS_DENIED")),
            assertFailure(gateway().loadCatalogDiscovery(CatalogDiscoveryRequest("catalog-menu")))
        )

        server.enqueue(MockResponse().setResponseCode(503))
        assertEquals(
            StorefrontFailure.Transport(retryable = true),
            assertFailure(gateway().loadCatalogDiscovery(CatalogDiscoveryRequest("catalog-menu")))
        )
    }

    @Test
    fun `caller cancellation propagates instead of becoming a storefront result`() {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))

        assertThrows<TimeoutCancellationException> {
            runBlocking {
                withTimeout(100) {
                    gateway(requestTimeoutMillis = 5_000).loadCatalogDiscovery(
                        CatalogDiscoveryRequest("catalog-menu")
                    )
                }
            }
        }
    }

    private fun gateway(requestTimeoutMillis: Long = 5_000): ApolloStorefrontCatalogGateway =
        ApolloStorefrontCatalogGateway(client, mediaPolicy, requestTimeoutMillis = requestTimeoutMillis)

    private fun assertSuccess(result: StorefrontResult<CatalogDiscoveryMenu?>): CatalogDiscoveryMenu? {
        assertTrue(result is StorefrontResult.Success) { "Expected catalog discovery success." }
        return (result as StorefrontResult.Success).value
    }

    private fun assertFailure(result: StorefrontResult<CatalogDiscoveryMenu?>): StorefrontFailure {
        assertTrue(result is StorefrontResult.Failure) { "Expected catalog discovery failure." }
        return (result as StorefrontResult.Failure).error
    }

    private fun CatalogDiscoveryNode.collection(): CatalogDiscoveryCollection =
        (target as CatalogDiscoveryTarget.Collection).collection

    private fun menuResponse(
        items: List<String>,
        id: String = "gid://shopify/Menu/catalog",
        handle: String = "catalog-menu"
    ): String = """{"data":{"menu":{"id":"$id","handle":"$handle","items":[${items.joinToString()}]}}}"""

    private fun unsupportedItem(
        itemId: String,
        title: String,
        children: List<String> = emptyList(),
        sentinelChildren: List<String> = emptyList()
    ): String = item(
        itemId = itemId,
        title = title,
        type = "PAGE",
        resource = "null",
        children = children,
        sentinelChildren = sentinelChildren
    )

    @Suppress("LongParameterList")
    private fun collectionItem(
        itemId: String,
        title: String,
        collectionId: String,
        handle: String,
        collectionTitle: String,
        image: String?,
        productImage: String?,
        tags: List<String> = emptyList(),
        children: List<String> = emptyList()
    ): String {
        val resource = collectionResource(collectionId, handle, collectionTitle, image, productImage)
        return item(itemId, title, "COLLECTION", resource, tags, children)
    }

    private fun collectionResource(
        collectionId: String,
        handle: String,
        collectionTitle: String,
        image: String?,
        productImage: String?
    ): String {
        val productNodes = productImage?.let { """[{"featuredImage":$it}]""" } ?: "[]"
        return """
            {
              "__typename":"Collection",
              "id":"$collectionId",
              "handle":"$handle",
              "title":"$collectionTitle",
              "image":${image ?: "null"},
              "products":{"nodes":$productNodes}
            }
        """.trimIndent()
    }

    @Suppress("LongParameterList")
    private fun item(
        itemId: String,
        title: String,
        type: String,
        resource: String,
        tags: List<String> = emptyList(),
        children: List<String> = emptyList(),
        sentinelChildren: List<String> = emptyList()
    ): String {
        val renderedChildren =
            if (sentinelChildren.isNotEmpty()) {
                sentinelChildren.joinToString(prefix = "[", postfix = "]") { id -> """{"id":"$id"}""" }
            } else {
                children.joinToString(prefix = "[", postfix = "]")
            }
        return """
            {
              "__typename":"MenuItem",
              "id":"$itemId",
              "title":"$title",
              "type":"$type",
              "tags":[${tags.joinToString { tag -> "\"$tag\"" }}],
              "resource":$resource,
              "items":$renderedChildren
            }
        """.trimIndent()
    }

    private fun image(value: String, altText: String?, absolute: Boolean = false): String {
        val url = if (absolute) value else "https://cdn.shopify.com/s/files/1/$value"
        val alt = altText?.let { "\"$it\"" } ?: "null"
        return """
            {
              "__typename":"Image",
              "url":"$url",
              "altText":$alt,
              "width":320,
              "height":428
            }
        """.trimIndent()
    }
}
