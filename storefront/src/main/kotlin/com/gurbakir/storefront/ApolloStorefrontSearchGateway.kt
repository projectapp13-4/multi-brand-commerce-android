package com.gurbakir.storefront

import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.api.Optional
import com.gurbakir.storefront.graphql.ProductSearchPageQuery

class ApolloStorefrontSearchGateway(
    private val client: ApolloClient,
    private val mediaPolicy: StorefrontMediaPolicy,
    private val pageSize: Int = DEFAULT_PAGE_SIZE,
    requestTimeoutMillis: Long = DEFAULT_REQUEST_TIMEOUT_MILLIS
) : StorefrontSearchGateway {
    private val callExecutor = ApolloCallExecutor(requestTimeoutMillis)

    init {
        require(pageSize in 1..MAX_PAGE_SIZE) { "pageSize must be between 1 and $MAX_PAGE_SIZE" }
        require(requestTimeoutMillis > 0) { "requestTimeoutMillis must be positive" }
    }

    override suspend fun searchProducts(request: ProductSearchPageRequest): StorefrontResult<ProductSearchPage> {
        if (!request.isValid()) {
            return StorefrontResult.Failure(StorefrontFailure.Configuration(setOf("search.request")))
        }
        val query = request.normalizedQuery()
        val operation =
            ProductSearchPageQuery(
                query = query,
                first = pageSize,
                after = request.after?.let { Optional.present(it.value) } ?: Optional.Absent
            )
        return when (val result = callExecutor.execute(client.query(operation))) {
            is StorefrontResult.Failure -> result
            is StorefrontResult.Success -> result.value.toSearchResult(query, mediaPolicy)
        }
    }
}

private fun ProductSearchPageQuery.Data.toSearchResult(
    query: String,
    mediaPolicy: StorefrontMediaPolicy
): StorefrontResult<ProductSearchPage> {
    val productResults = search.nodes.mapNotNull { it.onProduct?.toCatalogProduct(mediaPolicy) }
    val failure = productResults.filterIsInstance<StorefrontResult.Failure>().firstOrNull()
    if (failure != null) return failure

    @Suppress("UNCHECKED_CAST")
    val products = productResults.map { (it as StorefrontResult.Success<CatalogProductSummary>).value }
    return StorefrontResult.Success(
        ProductSearchPage(
            query = query,
            products = products,
            totalCount = search.totalCount,
            endCursor = search.pageInfo.endCursor?.let(::Cursor),
            hasNextPage = search.pageInfo.hasNextPage
        )
    )
}

private fun ProductSearchPageQuery.OnProduct.toCatalogProduct(
    mediaPolicy: StorefrontMediaPolicy
): StorefrontResult<CatalogProductSummary> {
    val minimumAmount = priceRange.minVariantPrice.amount.toBigDecimalOrNull()
    val maximumAmount = priceRange.maxVariantPrice.amount.toBigDecimalOrNull()
    val minimumCurrency = priceRange.minVariantPrice.currencyCode.rawValue
    val maximumCurrency = priceRange.maxVariantPrice.currencyCode.rawValue
    return when {
        minimumAmount == null || maximumAmount == null -> graphQlFailure("INVALID_SEARCH_MONEY_AMOUNT")

        minimumCurrency != maximumCurrency -> graphQlFailure("INCONSISTENT_SEARCH_CURRENCY")

        else ->
            StorefrontResult.Success(
                CatalogProductSummary(
                    id = id,
                    handle = handle,
                    title = title,
                    availableForSale = availableForSale,
                    media = featuredImage?.homeImageFields?.toStorefrontMedia(mediaPolicy),
                    minimumPrice = StorefrontMoney(minimumAmount, minimumCurrency),
                    maximumPrice = StorefrontMoney(maximumAmount, maximumCurrency)
                )
            )
    }
}
