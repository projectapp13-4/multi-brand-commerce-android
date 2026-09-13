package com.gurbakir.mobile.home

import com.gurbakir.storefront.HomeCollectionSummary
import com.gurbakir.storefront.HomeProductSummary
import com.gurbakir.storefront.StorefrontFailure
import com.gurbakir.storefront.StorefrontHomeGateway
import com.gurbakir.storefront.StorefrontMedia
import com.gurbakir.storefront.StorefrontMoney
import com.gurbakir.storefront.StorefrontResult
import java.math.BigDecimal
import java.net.URI
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class HomeContentRepositoryTest {
    @Test
    fun `alternate configuration bounds collection requests and supplies featured handle`() = runTest {
        val gateway = FakeHomeGateway()
        val configuration = homeTestConfiguration.copy(
            productRange = homeTestConfiguration.productRange.copy(itemLimit = 2),
            featuredProduct = homeTestConfiguration.featuredProduct.copy(handle = "other-featured")
        )
        val repository = DefaultHomeContentRepository(gateway, configuration)
        repository.loadProductRange()
        repository.loadFeaturedProduct()
        assertEquals(listOf("alpha", "beta"), gateway.collectionRequests)
        assertEquals("other-featured", gateway.productRequest)
    }

    @Test
    fun `product range preserves configured order and reports a retryable partial failure`() = runTest {
        val gateway = FakeHomeGateway()
        homeTestConfiguration.productRange.sources.forEach { source ->
            gateway.collections[source.handle] = StorefrontResult.Success(collection(source.handle))
        }
        gateway.collections["beta"] = StorefrontResult.Failure(StorefrontFailure.Transport(retryable = true))

        val result = DefaultHomeContentRepository(gateway, homeTestConfiguration).loadProductRange()

        val content = assertInstanceOf(HomeSectionLoad.Content::class.java, result)

        @Suppress("UNCHECKED_CAST")
        val items = content.value as List<HomeCollectionItem>
        assertEquals(
            listOf("alpha", "gamma", "delta", "epsilon"),
            items.map { it.source.handle }
        )
        assertEquals(HomeLoadFailureCategory.CONNECTION, content.partialFailure?.category)
        assertEquals(true, content.partialFailure?.retryable)
    }

    @Test
    fun `all missing collections become an honest empty section`() = runTest {
        val result = DefaultHomeContentRepository(FakeHomeGateway(), homeTestConfiguration).loadProductRange()

        assertInstanceOf(HomeSectionLoad.Empty::class.java, result)
    }

    @Test
    fun `configuration failures become a non retryable section error`() = runTest {
        val gateway = FakeHomeGateway(defaultCollectionResult = configurationFailure())

        val result = DefaultHomeContentRepository(gateway, homeTestConfiguration).loadProductRange()

        val error = assertInstanceOf(HomeSectionLoad.Error::class.java, result)
        assertEquals(HomeLoadFailureCategory.CONFIGURATION, error.failure.category)
        assertEquals(false, error.failure.retryable)
    }

    @Test
    fun `featured product keeps zero money but hides unusable media`() = runTest {
        val gateway = FakeHomeGateway()
        gateway.productResult = StorefrontResult.Success(product(media = media(), amount = "0.00"))
        val repository = DefaultHomeContentRepository(gateway, homeTestConfiguration)

        val visible = assertInstanceOf(HomeSectionLoad.Content::class.java, repository.loadFeaturedProduct())
        val item = visible.value as HomeFeaturedItem
        assertEquals(BigDecimal("0.00"), item.summary.price.amount)
        assertNull(visible.partialFailure)

        gateway.productResult = StorefrontResult.Success(product(media = null, amount = "10.00"))
        assertInstanceOf(HomeSectionLoad.Empty::class.java, repository.loadFeaturedProduct())
    }

    private fun collection(handle: String): HomeCollectionSummary = HomeCollectionSummary(
        id = "gid://shopify/Collection/$handle",
        handle = handle,
        sourceTitle = handle,
        media = media()
    )

    private fun product(media: StorefrontMedia?, amount: String): HomeProductSummary = HomeProductSummary(
        id = "gid://shopify/Product/featured",
        handle = homeTestConfiguration.featuredProduct.handle,
        title = "Bakır Tava",
        availableForSale = true,
        media = media,
        price = StorefrontMoney(BigDecimal(amount), "TRY")
    )

    private fun media(): StorefrontMedia = StorefrontMedia(
        uri = URI("https://cdn.shopify.com/s/files/1/test.jpg"),
        altText = "Bakır ürün",
        width = 300,
        height = 400
    )

    private fun configurationFailure(): StorefrontResult.Failure =
        StorefrontResult.Failure(StorefrontFailure.Configuration(setOf("shopify.storefrontDomain")))

    private class FakeHomeGateway(
        private val defaultCollectionResult: StorefrontResult<HomeCollectionSummary?> = StorefrontResult.Success(null)
    ) : StorefrontHomeGateway {
        val collections = mutableMapOf<String, StorefrontResult<HomeCollectionSummary?>>()
        var productResult: StorefrontResult<HomeProductSummary?> = StorefrontResult.Success(null)
        val collectionRequests = mutableListOf<String>()
        var productRequest: String? = null

        override suspend fun loadHomeCollection(handle: String): StorefrontResult<HomeCollectionSummary?> {
            collectionRequests += handle
            return collections[handle] ?: defaultCollectionResult
        }

        override suspend fun loadHomeProduct(handle: String): StorefrontResult<HomeProductSummary?> {
            productRequest = handle
            return productResult
        }

        override suspend fun loadHomeDocument(
            selector: com.gurbakir.storefront.HomeDocumentSelector
        ): StorefrontResult<com.gurbakir.storefront.HomeDocumentObservation?> =
            StorefrontResult.Failure(StorefrontFailure.GraphQl(setOf("HOME_CONTENT_NOT_IMPLEMENTED")))

        override suspend fun loadHomeResources(
            keys: List<com.gurbakir.storefront.HomeResourceKey>
        ): StorefrontResult<com.gurbakir.storefront.HomeResourceBatch> =
            StorefrontResult.Failure(StorefrontFailure.GraphQl(setOf("HOME_RESOURCES_NOT_IMPLEMENTED")))
    }
}
