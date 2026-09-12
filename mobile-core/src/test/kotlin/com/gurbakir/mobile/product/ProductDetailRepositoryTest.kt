package com.gurbakir.mobile.product

import com.gurbakir.mobile.catalog.CatalogLoadFailureCategory
import com.gurbakir.storefront.ProductDetailRequest
import com.gurbakir.storefront.StorefrontFailure
import com.gurbakir.storefront.StorefrontProductDetail
import com.gurbakir.storefront.StorefrontProductGateway
import com.gurbakir.storefront.StorefrontResult
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ProductDetailRepositoryTest {
    @Test
    fun `content and not found remain distinct`() = runBlocking<Unit> {
        val gateway = FakeProductGateway(StorefrontResult.Success(productFixture()))
        val repository = DefaultProductDetailRepository(gateway)

        assertInstanceOf(ProductDetailLoad.Content::class.java, repository.load(productFixture().id))
        gateway.result = StorefrontResult.Success(null)
        assertInstanceOf(ProductDetailLoad.NotFound::class.java, repository.load(productFixture().id))
    }

    @Test
    fun `retryable transport error remains retryable connection failure`() = runBlocking {
        val repository =
            DefaultProductDetailRepository(
                FakeProductGateway(StorefrontResult.Failure(StorefrontFailure.Transport(true)))
            )

        val error = assertInstanceOf(ProductDetailLoad.Error::class.java, repository.load(productFixture().id))

        assertEquals(CatalogLoadFailureCategory.CONNECTION, error.failure.category)
        assertTrue(error.failure.retryable)
    }
}

private class FakeProductGateway(var result: StorefrontResult<StorefrontProductDetail?>) : StorefrontProductGateway {
    override suspend fun loadProductDetail(request: ProductDetailRequest): StorefrontResult<StorefrontProductDetail?> =
        result
}
