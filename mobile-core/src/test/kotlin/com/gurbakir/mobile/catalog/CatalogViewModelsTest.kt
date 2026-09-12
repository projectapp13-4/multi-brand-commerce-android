@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.gurbakir.mobile.catalog

import androidx.lifecycle.SavedStateHandle
import com.gurbakir.storefront.CatalogDiscoveryCollection
import com.gurbakir.storefront.CatalogProductSummary
import com.gurbakir.storefront.CollectionCatalogPage
import com.gurbakir.storefront.CollectionCatalogSort
import com.gurbakir.storefront.Cursor
import com.gurbakir.storefront.StorefrontMedia
import com.gurbakir.storefront.StorefrontMoney
import java.math.BigDecimal
import java.net.URI
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class CatalogViewModelsTest {
    private val dispatcher = StandardTestDispatcher()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `categories retain visible content during refresh and replace it with terminal empty`() = runTest(dispatcher) {
        val repository = RefreshingCategoriesRepository()
        val viewModel = CategoriesViewModel(repository)
        advanceUntilIdle()

        viewModel.retry()
        runCurrent()

        assertEquals(
            CategoriesUiState.Content(
                listOf(category("existing")),
                partialFailure = null,
                refreshing = true
            ),
            viewModel.state.value
        )

        repository.refresh.complete(CatalogCategoryLoad.Empty)
        advanceUntilIdle()

        assertEquals(CategoriesUiState.Empty, viewModel.state.value)
    }

    @Test
    fun `categories superseded request cannot overwrite newer content`() = runTest(dispatcher) {
        val repository = CancellingCategoriesRepository()
        val viewModel = CategoriesViewModel(repository)
        runCurrent()

        viewModel.retry()
        advanceUntilIdle()

        assertTrue(repository.firstRequestCancelled)
        val content = viewModel.state.value as CategoriesUiState.Content
        assertEquals(listOf("fresh"), content.items.map { it.collection.handle })
        assertEquals(false, content.refreshing)
    }

    @Test
    fun `pagination deduplicates products and retains cursor order`() = runTest(dispatcher) {
        val repository = QueueCatalogRepository(
            ArrayDeque(
                listOf(
                    CatalogPageLoad.Content(
                        page(
                            products = listOf(product("one"), product("two")),
                            cursor = Cursor("page-2"),
                            hasNextPage = true
                        )
                    ),
                    CatalogPageLoad.Content(
                        page(
                            products = listOf(product("two"), product("three")),
                            cursor = null,
                            hasNextPage = false
                        )
                    )
                )
            )
        )
        val viewModel = CollectionViewModel(repository, SavedStateHandle())

        viewModel.start("bardaklar")
        advanceUntilIdle()
        viewModel.loadNextPage()
        advanceUntilIdle()

        assertEquals(listOf("one", "two", "three"), viewModel.state.value.products.map { it.handle })
        assertEquals(false, viewModel.state.value.hasNextPage)
        assertEquals(listOf(null, "page-2"), repository.requests.map { it.after?.value })
    }

    @Test
    fun `new sort cancels stale request and persists bounded selection`() = runTest(dispatcher) {
        val repository = CancellingCatalogRepository()
        val savedState = SavedStateHandle()
        val viewModel = CollectionViewModel(repository, savedState)

        viewModel.start("bardaklar")
        runCurrent()
        viewModel.selectSort(CollectionCatalogSort.NEWEST)
        advanceUntilIdle()

        assertTrue(repository.firstRequestCancelled)
        assertEquals(CollectionCatalogSort.NEWEST, viewModel.state.value.sort)
        assertEquals("NEWEST", savedState.get<String>("catalog.sort"))
        assertEquals(listOf("fresh"), viewModel.state.value.products.map { it.handle })
    }

    private fun page(
        products: List<CatalogProductSummary>,
        cursor: Cursor?,
        hasNextPage: Boolean
    ): CollectionCatalogPage = CollectionCatalogPage(
        collectionId = "gid://shopify/Collection/1",
        handle = "bardaklar",
        title = "Bardaklar",
        products = products,
        productTypeFilter = null,
        endCursor = cursor,
        hasNextPage = hasNextPage
    )

    private fun product(handle: String): CatalogProductSummary = CatalogProductSummary(
        id = "gid://shopify/Product/$handle",
        handle = handle,
        title = handle,
        availableForSale = true,
        media = null,
        minimumPrice = StorefrontMoney(BigDecimal("10.00"), "TRY"),
        maximumPrice = StorefrontMoney(BigDecimal("10.00"), "TRY")
    )

    private fun category(handle: String): CatalogCategoryItem = CatalogCategoryItem(
        stableId = "menu-item-$handle",
        title = "Menu $handle",
        collection =
            CatalogDiscoveryCollection(
                id = "gid://shopify/Collection/$handle",
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

    private inner class RefreshingCategoriesRepository : CatalogRepository {
        var calls = 0
        val refresh = CompletableDeferred<CatalogCategoryLoad>()

        override suspend fun loadCategories(): CatalogCategoryLoad {
            calls += 1
            return if (calls == 1) {
                CatalogCategoryLoad.Content(listOf(category("existing")), partialFailure = null)
            } else {
                refresh.await()
            }
        }

        override suspend fun loadCollectionPage(
            handle: String,
            after: Cursor?,
            sort: CollectionCatalogSort,
            productTypes: Set<String>
        ): CatalogPageLoad = CatalogPageLoad.NotFound
    }

    private inner class CancellingCategoriesRepository : CatalogRepository {
        var calls = 0
        var firstRequestCancelled = false

        override suspend fun loadCategories(): CatalogCategoryLoad {
            calls += 1
            if (calls == 1) {
                try {
                    awaitCancellation()
                } finally {
                    firstRequestCancelled = true
                }
            }
            return CatalogCategoryLoad.Content(listOf(category("fresh")), partialFailure = null)
        }

        override suspend fun loadCollectionPage(
            handle: String,
            after: Cursor?,
            sort: CollectionCatalogSort,
            productTypes: Set<String>
        ): CatalogPageLoad = CatalogPageLoad.NotFound
    }

    private inner class QueueCatalogRepository(private val pages: ArrayDeque<CatalogPageLoad>) : CatalogRepository {
        val requests = mutableListOf<Request>()

        override suspend fun loadCategories(): CatalogCategoryLoad = CatalogCategoryLoad.Empty

        override suspend fun loadCollectionPage(
            handle: String,
            after: Cursor?,
            sort: CollectionCatalogSort,
            productTypes: Set<String>
        ): CatalogPageLoad {
            requests += Request(after)
            return pages.removeFirst()
        }
    }

    private inner class CancellingCatalogRepository : CatalogRepository {
        var calls = 0
        var firstRequestCancelled = false

        override suspend fun loadCategories(): CatalogCategoryLoad = CatalogCategoryLoad.Empty

        override suspend fun loadCollectionPage(
            handle: String,
            after: Cursor?,
            sort: CollectionCatalogSort,
            productTypes: Set<String>
        ): CatalogPageLoad {
            calls += 1
            if (calls == 1) {
                try {
                    awaitCancellation()
                } finally {
                    firstRequestCancelled = true
                }
            }
            return CatalogPageLoad.Content(
                page(listOf(product("fresh")), cursor = null, hasNextPage = false)
            )
        }
    }

    private data class Request(val after: Cursor?)
}
