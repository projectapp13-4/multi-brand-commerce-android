package com.gurbakir.storefront

import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.api.Optional
import com.gurbakir.storefront.graphql.ProductDetailPageQuery

private const val DEFAULT_VARIANT_PAGE_SIZE = 250
private const val DEFAULT_MEDIA_PAGE_SIZE = 50
private const val MAX_DETAIL_PAGE_COUNT = 16

class ApolloStorefrontProductGateway(
    private val client: ApolloClient,
    private val mediaPolicy: StorefrontMediaPolicy,
    private val variantPageSize: Int = DEFAULT_VARIANT_PAGE_SIZE,
    private val mediaPageSize: Int = DEFAULT_MEDIA_PAGE_SIZE,
    private val maxPageCount: Int = MAX_DETAIL_PAGE_COUNT,
    requestTimeoutMillis: Long = DEFAULT_REQUEST_TIMEOUT_MILLIS
) : StorefrontProductGateway {
    private val callExecutor = ApolloCallExecutor(requestTimeoutMillis)

    init {
        require(variantPageSize in 1..DEFAULT_VARIANT_PAGE_SIZE)
        require(mediaPageSize in 1..DEFAULT_MEDIA_PAGE_SIZE)
        require(maxPageCount > 0)
        require(requestTimeoutMillis > 0)
    }

    override suspend fun loadProductDetail(request: ProductDetailRequest): StorefrontResult<StorefrontProductDetail?> {
        if (!request.isValid()) {
            return StorefrontResult.Failure(StorefrontFailure.Configuration(setOf("product.request")))
        }
        return loadAllPages(request)
    }

    private suspend fun loadAllPages(request: ProductDetailRequest): StorefrontResult<StorefrontProductDetail?> {
        val accumulator = ProductAccumulator()
        var outcome: StorefrontResult<StorefrontProductDetail?>? = null
        var pageCount = 0
        while (outcome == null && pageCount < maxPageCount) {
            val query =
                ProductDetailPageQuery(
                    id = request.productId,
                    variantFirst = variantPageSize,
                    variantAfter = accumulator.variantAfter.toOptional(),
                    mediaFirst = mediaPageSize,
                    mediaAfter = accumulator.mediaAfter.toOptional()
                )
            outcome =
                resolvePage(callExecutor.execute(client.query(query)), request.productId, accumulator, mediaPolicy)
            pageCount += 1
        }
        return outcome ?: graphQlFailure("PRODUCT_DETAIL_PAGE_LIMIT")
    }
}

private fun resolvePage(
    result: StorefrontResult<ProductDetailPageQuery.Data>,
    requestedId: String,
    accumulator: ProductAccumulator,
    mediaPolicy: StorefrontMediaPolicy
): StorefrontResult<StorefrontProductDetail?>? = when (result) {
    is StorefrontResult.Failure -> result

    is StorefrontResult.Success ->
        when (val product = result.value.product) {
            null -> StorefrontResult.Success(null)
            else -> resolveProduct(product, requestedId, accumulator, mediaPolicy)
        }
}

private fun resolveProduct(
    product: ProductDetailPageQuery.Product,
    requestedId: String,
    accumulator: ProductAccumulator,
    mediaPolicy: StorefrontMediaPolicy
): StorefrontResult<StorefrontProductDetail?>? = when (
    val mapped = product.toMappedPage(requestedId, mediaPolicy)
) {
    is StorefrontResult.Failure -> mapped

    is StorefrontResult.Success ->
        try {
            accumulator.accept(mapped.value)?.let { StorefrontResult.Success(it) }
        } catch (failure: ProductMappingFailure) {
            graphQlFailure(failure.code)
        }
}

private fun Cursor?.toOptional(): Optional<String> = this?.let { Optional.present(it.value) } ?: Optional.Absent
