package com.gurbakir.mobile.home

import com.gurbakir.storefront.HomeCollectionSummary
import com.gurbakir.storefront.HomeProductSummary
import com.gurbakir.storefront.StorefrontMedia
import com.gurbakir.storefront.StorefrontMoney
import java.math.BigDecimal
import java.net.URI
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    @Test
    fun `initial sections load independently and product retry retains valid content`() = runTest {
        withMainDispatcher(StandardTestDispatcher(testScheduler)) {
            val first = collectionItem("first")
            val second = collectionItem("second")
            val featured = featuredItem()
            val repository =
                FakeHomeContentRepository(
                    productRangeResult = HomeSectionLoad.Content(listOf(first), partialFailure = null),
                    featuredProductResult = HomeSectionLoad.Content(featured, partialFailure = null)
                )
            val viewModel = HomeViewModel(repository, HomeLoadingClock())
            advanceUntilIdle()

            assertEquals(listOf(first), viewModel.state.value.productRange.contentValue())
            assertEquals(featured, viewModel.state.value.featuredProduct.contentValue())

            repository.productRangeResult = HomeSectionLoad.Content(listOf(second), partialFailure = null)
            viewModel.retryProductRange()

            val refreshing = viewModel.state.value.productRange as HomeSectionUiState.Content
            assertTrue(refreshing.refreshing)
            assertEquals(listOf(first), refreshing.value)
            assertEquals(featured, viewModel.state.value.featuredProduct.contentValue())

            advanceUntilIdle()
            assertEquals(listOf(second), viewModel.state.value.productRange.contentValue())
        }
    }

    @Test
    fun `featured retry does not replace a successful product range`() = runTest {
        withMainDispatcher(StandardTestDispatcher(testScheduler)) {
            val range = listOf(collectionItem("range"))
            val repository =
                FakeHomeContentRepository(
                    productRangeResult = HomeSectionLoad.Content(range, partialFailure = null),
                    featuredProductResult = HomeSectionLoad.Empty
                )
            val viewModel = HomeViewModel(repository, HomeLoadingClock())
            advanceUntilIdle()

            repository.featuredProductResult =
                HomeSectionLoad.Error(HomeLoadFailure(HomeLoadFailureCategory.CONNECTION, retryable = true))
            viewModel.retryFeaturedProduct()
            advanceUntilIdle()

            assertEquals(range, viewModel.state.value.productRange.contentValue())
            assertTrue(viewModel.state.value.featuredProduct is HomeSectionUiState.Error)
        }
    }

    @Test
    fun `pending initial load becomes recoverable after eight seconds and retry cancels it`() = runTest {
        withMainDispatcher(StandardTestDispatcher(testScheduler)) {
            val repository = RetryablePendingRepository()
            val viewModel = HomeViewModel(repository, HomeLoadingClock())
            runCurrent()

            advanceTimeBy(7_999)
            assertTrue(viewModel.state.value.productRange is HomeSectionUiState.Loading)

            advanceTimeBy(1)
            runCurrent()
            assertTrue(viewModel.state.value.productRange is HomeSectionUiState.SlowLoading)

            viewModel.retryProductRange()
            assertTrue(viewModel.state.value.productRange is HomeSectionUiState.Loading)
            runCurrent()

            assertTrue(repository.firstRequestCancelled)
            assertEquals(2, repository.productRangeCalls)
            assertTrue(viewModel.state.value.productRange is HomeSectionUiState.Empty)
        }
    }

    @Test
    fun `slow retry retains valid content until replacement arrives`() = runTest {
        withMainDispatcher(StandardTestDispatcher(testScheduler)) {
            val first = collectionItem("retained")
            val replacement = collectionItem("replacement")
            val nextResult = CompletableDeferred<HomeSectionLoad<List<HomeCollectionItem>>>()
            val repository = RetainedContentRepository(first, nextResult)
            val viewModel = HomeViewModel(repository, HomeLoadingClock())
            runCurrent()

            viewModel.retryProductRange()
            val refreshing = viewModel.state.value.productRange as HomeSectionUiState.Content
            assertEquals(listOf(first), refreshing.value)
            assertTrue(refreshing.refreshing)

            advanceTimeBy(8_000)
            runCurrent()
            val slow = viewModel.state.value.productRange as HomeSectionUiState.Content
            assertEquals(listOf(first), slow.value)
            assertTrue(slow.refreshing)
            assertTrue(slow.slowLoading)

            nextResult.complete(HomeSectionLoad.Content(listOf(replacement), partialFailure = null))
            runCurrent()
            val completed = viewModel.state.value.productRange as HomeSectionUiState.Content
            assertEquals(listOf(replacement), completed.value)
            assertTrue(!completed.refreshing)
            assertTrue(!completed.slowLoading)
        }
    }

    private suspend fun withMainDispatcher(dispatcher: TestDispatcher, block: suspend () -> Unit) {
        Dispatchers.setMain(dispatcher)
        try {
            block()
        } finally {
            Dispatchers.resetMain()
        }
    }

    private fun collectionItem(id: String): HomeCollectionItem {
        val source = homeTestConfiguration.productRange.sources.first().copy(stableId = id, handle = id)
        return HomeCollectionItem(
            source = source,
            summary = HomeCollectionSummary("gid://shopify/Collection/$id", id, id, media())
        )
    }

    private fun featuredItem(): HomeFeaturedItem = HomeFeaturedItem(
        source = homeTestConfiguration.featuredProduct,
        summary =
            HomeProductSummary(
                id = "gid://shopify/Product/featured",
                handle = homeTestConfiguration.featuredProduct.handle,
                title = "Bakır Tava",
                availableForSale = true,
                media = media(),
                price = StorefrontMoney(BigDecimal("0.00"), "TRY")
            )
    )

    private fun media(): StorefrontMedia =
        StorefrontMedia(URI("https://cdn.shopify.com/s/files/1/test.jpg"), "Bakır ürün", 300, 400)

    @Suppress("UNCHECKED_CAST")
    private fun <T> HomeSectionUiState<T>.contentValue(): T = (this as HomeSectionUiState.Content<T>).value

    private class FakeHomeContentRepository(
        var productRangeResult: HomeSectionLoad<List<HomeCollectionItem>>,
        var featuredProductResult: HomeSectionLoad<HomeFeaturedItem>
    ) : HomeContentRepository {
        override suspend fun loadProductRange(): HomeSectionLoad<List<HomeCollectionItem>> = productRangeResult

        override suspend fun loadFeaturedProduct(): HomeSectionLoad<HomeFeaturedItem> = featuredProductResult
    }

    private class RetryablePendingRepository : HomeContentRepository {
        var productRangeCalls = 0
        var firstRequestCancelled = false

        override suspend fun loadProductRange(): HomeSectionLoad<List<HomeCollectionItem>> {
            productRangeCalls += 1
            return if (productRangeCalls == 1) {
                try {
                    awaitCancellation()
                } finally {
                    firstRequestCancelled = true
                }
            } else {
                HomeSectionLoad.Empty
            }
        }

        override suspend fun loadFeaturedProduct(): HomeSectionLoad<HomeFeaturedItem> = HomeSectionLoad.Empty
    }

    private class RetainedContentRepository(
        private val first: HomeCollectionItem,
        private val nextResult: CompletableDeferred<HomeSectionLoad<List<HomeCollectionItem>>>
    ) : HomeContentRepository {
        private var productRangeCalls = 0

        override suspend fun loadProductRange(): HomeSectionLoad<List<HomeCollectionItem>> {
            productRangeCalls += 1
            return if (productRangeCalls == 1) {
                HomeSectionLoad.Content(listOf(first), partialFailure = null)
            } else {
                nextResult.await()
            }
        }

        override suspend fun loadFeaturedProduct(): HomeSectionLoad<HomeFeaturedItem> = HomeSectionLoad.Empty
    }
}
