@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.gurbakir.mobile.search

import androidx.lifecycle.SavedStateHandle
import com.gurbakir.storefront.CatalogProductSummary
import com.gurbakir.storefront.Cursor
import com.gurbakir.storefront.ProductSearchPage
import com.gurbakir.storefront.StorefrontMoney
import java.math.BigDecimal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
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

class SearchViewModelTest {
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
    fun `debounce cancels stale request and only newest query updates results`() = runTest(dispatcher) {
        val repository = CancellingSearchRepository()
        val viewModel = SearchViewModel(repository, FakeHistoryRepository(), SavedStateHandle())
        runCurrent()

        viewModel.onQueryChanged("bakır")
        advanceTimeBy(400)
        runCurrent()
        viewModel.onQueryChanged("cezve")
        advanceTimeBy(400)
        advanceUntilIdle()

        assertTrue(repository.firstRequestCancelled)
        assertEquals(listOf("cezve"), viewModel.state.value.products.map { it.handle })
        assertEquals("cezve", viewModel.state.value.query)
    }

    @Test
    fun `explicit submit records one normalized local history entry`() = runTest(dispatcher) {
        val history = FakeHistoryRepository()
        val viewModel =
            SearchViewModel(
                QueueSearchRepository(ArrayDeque(listOf(SearchPageLoad.Content(page("tava"))))),
                history,
                SavedStateHandle()
            )
        runCurrent()

        viewModel.onQueryChanged("  Bakır   Tava ")
        viewModel.submit()
        advanceUntilIdle()

        assertEquals(listOf("Bakır Tava"), history.recorded)
        assertEquals("Bakır Tava", viewModel.state.value.history.single().display)
    }

    @Test
    fun `pagination deduplicates products and retains loaded rows`() = runTest(dispatcher) {
        val repository =
            QueueSearchRepository(
                ArrayDeque(
                    listOf(
                        SearchPageLoad.Content(
                            page("one", "two", cursor = Cursor("page-2"), hasNextPage = true)
                        ),
                        SearchPageLoad.Content(page("two", "three"))
                    )
                )
            )
        val viewModel = SearchViewModel(repository, FakeHistoryRepository(), SavedStateHandle())
        runCurrent()

        viewModel.onQueryChanged("bakır")
        viewModel.submit()
        advanceUntilIdle()
        viewModel.loadNextPage()
        advanceUntilIdle()

        assertEquals(listOf("one", "two", "three"), viewModel.state.value.products.map { it.handle })
        assertEquals(listOf(null, "page-2"), repository.cursors)
    }

    private class FakeHistoryRepository : SearchHistoryRepository {
        val recorded = mutableListOf<String>()
        private var state = SearchHistoryState(enabled = true, entries = emptyList())

        override suspend fun load(): SearchHistoryState = state

        override suspend fun record(displayQuery: String): SearchHistoryState {
            val display = displayQuery.toDisplaySearchQuery()
            recorded += display
            state =
                state.copy(
                    entries =
                        listOf(
                            StoredSearchQuery(
                                SearchHistoryNormalizationPolicy("tr-TR").normalize(display),
                                display,
                                1L
                            )
                        )
                )
            return state
        }

        override suspend fun remove(normalizedQuery: String): SearchHistoryState {
            state = state.copy(entries = state.entries.filterNot { it.normalized == normalizedQuery })
            return state
        }

        override suspend fun clear(): SearchHistoryState {
            state = state.copy(entries = emptyList())
            return state
        }

        override suspend fun setEnabled(enabled: Boolean): SearchHistoryState {
            state = SearchHistoryState(enabled, emptyList())
            return state
        }
    }

    private class QueueSearchRepository(private val pages: ArrayDeque<SearchPageLoad>) : ProductSearchRepository {
        val cursors = mutableListOf<String?>()

        override suspend fun search(query: String, after: Cursor?): SearchPageLoad {
            cursors += after?.value
            return pages.removeFirst()
        }
    }

    private class CancellingSearchRepository : ProductSearchRepository {
        var calls = 0
        var firstRequestCancelled = false

        override suspend fun search(query: String, after: Cursor?): SearchPageLoad {
            calls += 1
            if (calls == 1) {
                try {
                    awaitCancellation()
                } finally {
                    firstRequestCancelled = true
                }
            }
            return SearchPageLoad.Content(page(query))
        }
    }

    private companion object {
        fun page(vararg handles: String, cursor: Cursor? = null, hasNextPage: Boolean = false): ProductSearchPage =
            ProductSearchPage(
                query = "bakır",
                products = handles.map(::product),
                totalCount = handles.size,
                endCursor = cursor,
                hasNextPage = hasNextPage
            )

        fun product(handle: String): CatalogProductSummary = CatalogProductSummary(
            id = "gid://shopify/Product/$handle",
            handle = handle,
            title = handle,
            availableForSale = true,
            media = null,
            minimumPrice = StorefrontMoney(BigDecimal("10.00"), "TRY"),
            maximumPrice = StorefrontMoney(BigDecimal("10.00"), "TRY")
        )
    }
}
