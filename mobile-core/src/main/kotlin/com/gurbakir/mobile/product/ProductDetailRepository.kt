package com.gurbakir.mobile.product

import com.gurbakir.mobile.catalog.CatalogLoadFailure
import com.gurbakir.mobile.catalog.CatalogLoadFailureCategory
import com.gurbakir.storefront.ProductDetailRequest
import com.gurbakir.storefront.StorefrontFailure
import com.gurbakir.storefront.StorefrontProductDetail
import com.gurbakir.storefront.StorefrontProductGateway
import com.gurbakir.storefront.StorefrontResult
import javax.inject.Inject

interface ProductDetailRepository {
    suspend fun load(productId: String): ProductDetailLoad
}

class DefaultProductDetailRepository
@Inject
constructor(private val gateway: StorefrontProductGateway) :
    ProductDetailRepository {
    override suspend fun load(productId: String): ProductDetailLoad =
        when (val result = gateway.loadProductDetail(ProductDetailRequest(productId))) {
            is StorefrontResult.Success ->
                result.value?.let(ProductDetailLoad::Content) ?: ProductDetailLoad.NotFound

            is StorefrontResult.Failure -> ProductDetailLoad.Error(result.error.toLoadFailure())
        }
}

sealed interface ProductDetailLoad {
    data class Content(val product: StorefrontProductDetail) : ProductDetailLoad

    data class Error(val failure: CatalogLoadFailure) : ProductDetailLoad

    data object NotFound : ProductDetailLoad
}

private fun StorefrontFailure.toLoadFailure(): CatalogLoadFailure = CatalogLoadFailure(
    category =
        when (this) {
            is StorefrontFailure.Configuration -> CatalogLoadFailureCategory.CONFIGURATION
            is StorefrontFailure.Transport -> CatalogLoadFailureCategory.CONNECTION
            else -> CatalogLoadFailureCategory.SERVICE
        },
    retryable = this is StorefrontFailure.Transport && retryable
)
