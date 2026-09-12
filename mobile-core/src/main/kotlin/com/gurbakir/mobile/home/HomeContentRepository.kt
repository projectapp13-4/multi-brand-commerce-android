package com.gurbakir.mobile.home

import com.gurbakir.storefront.HomeCollectionSummary
import com.gurbakir.storefront.HomeProductSummary
import com.gurbakir.storefront.StorefrontFailure
import com.gurbakir.storefront.StorefrontHomeGateway
import com.gurbakir.storefront.StorefrontResult
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

interface HomeContentRepository {
    suspend fun loadProductRange(): HomeSectionLoad<List<HomeCollectionItem>>

    suspend fun loadFeaturedProduct(): HomeSectionLoad<HomeFeaturedItem>
}

class DefaultHomeContentRepository
@Inject
constructor(
    private val gateway: StorefrontHomeGateway,
    private val configuration: HomeConfiguration
) : HomeContentRepository {
    override suspend fun loadProductRange(): HomeSectionLoad<List<HomeCollectionItem>> = coroutineScope {
        val outcomes =
            configuration.productRange.sources
                .take(configuration.productRange.itemLimit)
                .map { source ->
                    async { source to gateway.loadHomeCollection(source.handle) }
                }.awaitAll()
        val items =
            outcomes.mapNotNull { (source, result) ->
                (result as? StorefrontResult.Success)?.value?.let { summary ->
                    HomeCollectionItem(source = source, summary = summary)
                }
            }
        val failures = outcomes.mapNotNull { (_, result) -> (result as? StorefrontResult.Failure)?.error }
        when {
            items.isNotEmpty() -> HomeSectionLoad.Content(items, failures.toHomeLoadFailureOrNull())
            failures.isNotEmpty() -> HomeSectionLoad.Error(failures.toHomeLoadFailure())
            else -> HomeSectionLoad.Empty
        }
    }

    override suspend fun loadFeaturedProduct(): HomeSectionLoad<HomeFeaturedItem> =
        when (val result = gateway.loadHomeProduct(configuration.featuredProduct.handle)) {
            is StorefrontResult.Failure -> HomeSectionLoad.Error(listOf(result.error).toHomeLoadFailure())

            is StorefrontResult.Success -> {
                val product = result.value
                if (product == null || !product.availableForSale || product.media == null) {
                    HomeSectionLoad.Empty
                } else {
                    HomeSectionLoad.Content(
                        HomeFeaturedItem(configuration.featuredProduct, product),
                        partialFailure = null
                    )
                }
            }
        }
}

data class HomeCollectionItem(val source: HomeCollectionSource, val summary: HomeCollectionSummary)

data class HomeFeaturedItem(val source: HomeFeaturedProductConfiguration, val summary: HomeProductSummary)

sealed interface HomeSectionLoad<out T> {
    data class Content<T>(val value: T, val partialFailure: HomeLoadFailure?) : HomeSectionLoad<T>

    data class Error(val failure: HomeLoadFailure) : HomeSectionLoad<Nothing>

    data object Empty : HomeSectionLoad<Nothing>
}

data class HomeLoadFailure(val category: HomeLoadFailureCategory, val retryable: Boolean)

enum class HomeLoadFailureCategory {
    CONNECTION,
    CONFIGURATION,
    SERVICE
}

private fun List<StorefrontFailure>.toHomeLoadFailureOrNull(): HomeLoadFailure? =
    if (isEmpty()) null else toHomeLoadFailure()

private fun List<StorefrontFailure>.toHomeLoadFailure(): HomeLoadFailure {
    val category =
        when {
            any { it is StorefrontFailure.Configuration } -> HomeLoadFailureCategory.CONFIGURATION
            all { it is StorefrontFailure.Transport } -> HomeLoadFailureCategory.CONNECTION
            else -> HomeLoadFailureCategory.SERVICE
        }
    val retryable = isNotEmpty() && all { it is StorefrontFailure.Transport && it.retryable }
    return HomeLoadFailure(category = category, retryable = retryable)
}
