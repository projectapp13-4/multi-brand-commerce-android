package com.gurbakir.mobile.catalog

import com.gurbakir.storefront.CatalogDiscoveryCollection
import com.gurbakir.storefront.CatalogDiscoveryMenu
import com.gurbakir.storefront.CatalogDiscoveryNode
import com.gurbakir.storefront.CatalogDiscoveryRequest
import com.gurbakir.storefront.CatalogDiscoveryTarget
import com.gurbakir.storefront.CollectionCatalogPage
import com.gurbakir.storefront.CollectionCatalogPageRequest
import com.gurbakir.storefront.CollectionCatalogSort
import com.gurbakir.storefront.StorefrontCatalogGateway
import com.gurbakir.storefront.StorefrontFailure
import com.gurbakir.storefront.StorefrontMedia
import com.gurbakir.storefront.StorefrontResult
import java.net.URI
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CatalogRepositoryTest {
    @Test
    fun `categories use menu pre-order and never read the home gateway`() = runTest {
        val gateway = FakeCatalogGateway()
        gateway.discoveryResult = successMenu(node("beta"), node("alpha"))

        val result = repository(gateway).loadCategories()

        assertEquals(listOf("beta", "alpha"), content(result).items.map { it.collection.handle })
        assertEquals(listOf(CatalogDiscoveryRequest("test-catalog-menu")), gateway.discoveryRequests)
    }

    @Test
    fun `eligible parents precede descendants and unusable parents do not suppress children`() = runTest {
        val gateway = FakeCatalogGateway()
        gateway.discoveryResult =
            successMenu(
                node(
                    handle = "parent",
                    children =
                        listOf(
                            node(
                                handle = "container",
                                target = CatalogDiscoveryTarget.Unsupported,
                                children = listOf(node("child"))
                            ),
                            node(
                                handle = "unavailable",
                                target = CatalogDiscoveryTarget.UnavailableCollection,
                                children = listOf(node("survivor"))
                            )
                        )
                )
            )

        val result = repository(gateway).loadCategories()

        val loaded = content(result)
        assertEquals(listOf("parent", "child", "survivor"), loaded.items.map { it.collection.handle })
        assertEquals(null, loaded.partialFailure)
    }

    @Test
    fun `filtered collection is excluded without reserving its destination and descendants remain eligible`() =
        runTest {
            val gateway = FakeCatalogGateway()
            gateway.discoveryResult =
                successMenu(
                    node(
                        handle = "alpha",
                        filtered = true,
                        children = listOf(node("child"))
                    ),
                    node(handle = "alpha", itemId = "menu-item-alpha-unfiltered")
                )

            val loaded = content(repository(gateway).loadCategories())

            assertEquals(listOf("child", "alpha"), loaded.items.map { it.collection.handle })
            assertEquals(CatalogLoadFailureCategory.CONFIGURATION, loaded.partialFailure?.category)
            assertEquals(false, loaded.partialFailure?.retryable)
        }

    @Test
    fun `repeated destination under distinct item ids keeps first eligible action without a false error`() = runTest {
        val gateway = FakeCatalogGateway()
        gateway.discoveryResult =
            successMenu(
                node(handle = "alpha", itemId = "menu-item-first", title = "First label"),
                node(handle = "alpha", itemId = "menu-item-second", title = "Second label")
            )

        val loaded = content(repository(gateway).loadCategories())

        assertEquals(listOf("menu-item-first"), loaded.items.map { it.stableId })
        assertEquals(listOf("First label"), loaded.items.map { it.title })
        assertEquals(null, loaded.partialFailure)
    }

    @Test
    fun `repeated item identity skips its later action but still visits its children`() = runTest {
        val gateway = FakeCatalogGateway()
        gateway.discoveryResult =
            successMenu(
                node(handle = "alpha", itemId = "repeated-item"),
                node(
                    handle = "beta",
                    itemId = "repeated-item",
                    children = listOf(node("child"))
                )
            )

        val loaded = content(repository(gateway).loadCategories())

        assertEquals(listOf("alpha", "child"), loaded.items.map { it.collection.handle })
        assertEquals(CatalogLoadFailureCategory.CONFIGURATION, loaded.partialFailure?.category)
    }

    @Test
    fun `conflicting collection identities retain the first action and report configuration partial`() = runTest {
        val gateway = FakeCatalogGateway()
        gateway.discoveryResult =
            successMenu(
                node(handle = "alpha", collectionId = "collection-one"),
                node(handle = "other", collectionId = "collection-one"),
                node(handle = "alpha", collectionId = "collection-two")
            )

        val loaded = content(repository(gateway).loadCategories())

        assertEquals(listOf("alpha"), loaded.items.map { it.collection.handle })
        assertEquals(CatalogLoadFailureCategory.CONFIGURATION, loaded.partialFailure?.category)
    }

    @Test
    fun `invalid actionable data and malformed targets are partial only when valid content survives`() = runTest {
        val gateway = FakeCatalogGateway()
        gateway.discoveryResult =
            successMenu(
                node("valid"),
                node(handle = "blank-title", title = "  "),
                node(handle = "control-title", title = "Bad\u0001title"),
                node(handle = "bad-id", itemId = " item-id "),
                node(handle = "malformed", target = CatalogDiscoveryTarget.Malformed)
            )

        val loaded = content(repository(gateway).loadCategories())

        assertEquals(listOf("valid"), loaded.items.map { it.collection.handle })
        assertEquals(CatalogLoadFailureCategory.CONFIGURATION, loaded.partialFailure?.category)

        gateway.discoveryResult = successMenu(node(handle = "only", target = CatalogDiscoveryTarget.Malformed))
        assertConfigurationError(repository(gateway).loadCategories())
    }

    @Test
    fun `ordinary unsupported and unavailable nodes produce truthful empty`() = runTest {
        val gateway = FakeCatalogGateway()
        gateway.discoveryResult =
            successMenu(
                node(handle = "page", target = CatalogDiscoveryTarget.Unsupported),
                node(handle = "empty", target = CatalogDiscoveryTarget.UnavailableCollection)
            )

        assertEquals(CatalogCategoryLoad.Empty, repository(gateway).loadCategories())
    }

    @Test
    fun `core rejects fourth depth and node sixty five regardless of filtering`() = runTest {
        val gateway = FakeCatalogGateway()
        val fourth = node("fourth")
        val third = node("third", children = listOf(fourth))
        val second = node("second", children = listOf(third))
        val first = node("first", children = listOf(second))
        gateway.discoveryResult = successMenu(first)

        assertConfigurationError(repository(gateway).loadCategories())

        gateway.discoveryResult =
            successMenu(
                *(1..64).map { index ->
                    node(handle = "unsupported-$index", target = CatalogDiscoveryTarget.Unsupported)
                }.toTypedArray()
            )
        assertEquals(CatalogCategoryLoad.Empty, repository(gateway).loadCategories())

        gateway.discoveryResult =
            successMenu(
                *(1..65).map { index ->
                    node(handle = "unsupported-overflow-$index", target = CatalogDiscoveryTarget.Unsupported)
                }.toTypedArray()
            )
        assertConfigurationError(repository(gateway).loadCategories())
    }

    @Test
    fun `accepted output limit applies after legitimate destination repetition`() = runTest {
        val gateway = FakeCatalogGateway()
        gateway.discoveryResult = successMenu(*(1..24).map { index -> node("item-$index") }.toTypedArray())
        assertEquals(24, content(repository(gateway).loadCategories()).items.size)

        gateway.discoveryResult = successMenu(*(1..25).map { index -> node("overflow-$index") }.toTypedArray())
        assertConfigurationError(repository(gateway).loadCategories())

        gateway.discoveryResult =
            successMenu(
                *(1..30).map { index ->
                    node(
                        handle = "repeated-${(index - 1) % 15}",
                        itemId = "distinct-item-$index"
                    )
                }.toTypedArray()
            )
        val repeated = content(repository(gateway).loadCategories())
        assertEquals(15, repeated.items.size)
        assertEquals(null, repeated.partialFailure)
    }

    @Test
    fun `missing menu and gateway failures retain truthful categories semantics`() = runTest {
        val gateway = FakeCatalogGateway()
        gateway.discoveryResult = StorefrontResult.Success(null)
        assertConfigurationError(repository(gateway).loadCategories())

        gateway.discoveryResult = StorefrontResult.Failure(StorefrontFailure.Transport(retryable = true))
        val connection = repository(gateway).loadCategories() as CatalogCategoryLoad.Error
        assertEquals(CatalogLoadFailure(CatalogLoadFailureCategory.CONNECTION, true), connection.failure)

        gateway.discoveryResult = StorefrontResult.Failure(StorefrontFailure.GraphQl(setOf("ACCESS_DENIED")))
        val service = repository(gateway).loadCategories() as CatalogCategoryLoad.Error
        assertEquals(CatalogLoadFailure(CatalogLoadFailureCategory.SERVICE, false), service.failure)

        gateway.discoveryResult =
            StorefrontResult.Failure(StorefrontFailure.Configuration(setOf("catalog.discovery.request")))
        assertConfigurationError(repository(gateway).loadCategories())
    }

    @Test
    fun `listing forwards bounded sort filter and cursor contract`() = runTest {
        val gateway = FakeCatalogGateway()
        gateway.pageResult = StorefrontResult.Success(page("zeta"))
        val repository = repository(gateway)

        val result =
            repository.loadCollectionPage(
                handle = "zeta",
                after = com.gurbakir.storefront.Cursor("next"),
                sort = CollectionCatalogSort.NEWEST,
                productTypes = setOf("Şişe")
            )

        assertTrue(result is CatalogPageLoad.Content)
        assertEquals(CollectionCatalogSort.NEWEST, gateway.lastPageRequest?.sort)
        assertEquals(setOf("Şişe"), gateway.lastPageRequest?.productTypes)
        assertEquals("next", gateway.lastPageRequest?.after?.value)
    }

    private fun repository(gateway: FakeCatalogGateway): DefaultCatalogRepository =
        DefaultCatalogRepository(gateway, catalogTestConfiguration)

    private fun content(result: CatalogCategoryLoad): CatalogCategoryLoad.Content {
        assertTrue(result is CatalogCategoryLoad.Content) { "Expected Categories content." }
        return result as CatalogCategoryLoad.Content
    }

    private fun assertConfigurationError(result: CatalogCategoryLoad) {
        assertTrue(result is CatalogCategoryLoad.Error) { "Expected Categories error." }
        result as CatalogCategoryLoad.Error
        assertEquals(CatalogLoadFailureCategory.CONFIGURATION, result.failure.category)
        assertEquals(false, result.failure.retryable)
    }

    private fun successMenu(vararg nodes: CatalogDiscoveryNode): StorefrontResult<CatalogDiscoveryMenu?> =
        StorefrontResult.Success(
            CatalogDiscoveryMenu(
                id = "gid://shopify/Menu/catalog",
                handle = "test-catalog-menu",
                items = nodes.toList()
            )
        )

    @Suppress("LongParameterList")
    private fun node(
        handle: String,
        itemId: String = "menu-item-$handle",
        title: String = "Menu $handle",
        collectionId: String = "collection-$handle",
        filtered: Boolean = false,
        target: CatalogDiscoveryTarget = collectionTarget(collectionId, handle),
        children: List<CatalogDiscoveryNode> = emptyList()
    ): CatalogDiscoveryNode = CatalogDiscoveryNode(
        id = itemId,
        title = title,
        hasCollectionFilters = filtered,
        target = target,
        children = children
    )

    private fun collectionTarget(collectionId: String, handle: String): CatalogDiscoveryTarget =
        CatalogDiscoveryTarget.Collection(
            CatalogDiscoveryCollection(
                id = collectionId,
                handle = handle,
                sourceTitle = "Collection $handle",
                media =
                    StorefrontMedia(
                        URI("https://cdn.shopify.com/s/files/1/$handle.jpg"),
                        null,
                        300,
                        400
                    )
            )
        )

    private fun page(handle: String): CollectionCatalogPage = CollectionCatalogPage(
        collectionId = "gid://shopify/Collection/$handle",
        handle = handle,
        title = handle,
        products = emptyList(),
        productTypeFilter = null,
        endCursor = null,
        hasNextPage = false
    )

    private class FakeCatalogGateway : StorefrontCatalogGateway {
        var discoveryResult: StorefrontResult<CatalogDiscoveryMenu?> = StorefrontResult.Success(null)
        val discoveryRequests = mutableListOf<CatalogDiscoveryRequest>()
        var pageResult: StorefrontResult<CollectionCatalogPage?> = StorefrontResult.Success(null)
        var lastPageRequest: CollectionCatalogPageRequest? = null

        override suspend fun loadCatalogDiscovery(
            request: CatalogDiscoveryRequest
        ): StorefrontResult<CatalogDiscoveryMenu?> {
            discoveryRequests += request
            return discoveryResult
        }

        override suspend fun loadCollectionCatalogPage(
            request: CollectionCatalogPageRequest
        ): StorefrontResult<CollectionCatalogPage?> {
            lastPageRequest = request
            return pageResult
        }
    }
}
