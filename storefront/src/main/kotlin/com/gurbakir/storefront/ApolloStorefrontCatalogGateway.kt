package com.gurbakir.storefront

import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.api.Optional
import com.gurbakir.storefront.graphql.CatalogDiscoveryMenuQuery
import com.gurbakir.storefront.graphql.CollectionCatalogPageQuery
import com.gurbakir.storefront.graphql.type.ProductCollectionSortKeys
import com.gurbakir.storefront.graphql.type.ProductFilter

class ApolloStorefrontCatalogGateway(
    private val client: ApolloClient,
    private val mediaPolicy: StorefrontMediaPolicy,
    private val pageSize: Int = DEFAULT_PAGE_SIZE,
    requestTimeoutMillis: Long = DEFAULT_REQUEST_TIMEOUT_MILLIS
) : StorefrontCatalogGateway {
    private val callExecutor = ApolloCallExecutor(requestTimeoutMillis)

    init {
        require(pageSize in 1..MAX_PAGE_SIZE) { "pageSize must be between 1 and $MAX_PAGE_SIZE" }
        require(requestTimeoutMillis > 0) { "requestTimeoutMillis must be positive" }
    }

    override suspend fun loadCatalogDiscovery(
        request: CatalogDiscoveryRequest
    ): StorefrontResult<CatalogDiscoveryMenu?> {
        if (!request.isValid()) {
            return catalogDiscoveryConfigurationFailure("catalog.discovery.request")
        }
        return when (val result = callExecutor.execute(client.query(CatalogDiscoveryMenuQuery(request.menuHandle)))) {
            is StorefrontResult.Failure -> result
            is StorefrontResult.Success -> result.value.toCatalogDiscoveryResult(request, mediaPolicy)
        }
    }

    override suspend fun loadCollectionCatalogPage(
        request: CollectionCatalogPageRequest
    ): StorefrontResult<CollectionCatalogPage?> {
        if (!request.isValid()) {
            return StorefrontResult.Failure(StorefrontFailure.Configuration(setOf("catalog.request")))
        }
        return when (val result = callExecutor.execute(client.query(request.toQuery(pageSize)))) {
            is StorefrontResult.Failure -> result
            is StorefrontResult.Success -> result.value.toCatalogResult(mediaPolicy)
        }
    }
}

private fun CollectionCatalogPageRequest.toQuery(pageSize: Int): CollectionCatalogPageQuery {
    val productTypeFilters =
        productTypes.sorted().map { productType ->
            ProductFilter(productType = Optional.present(productType))
        }
    return CollectionCatalogPageQuery(
        handle = handle,
        first = pageSize,
        after = after?.let { Optional.present(it.value) } ?: Optional.Absent,
        sortKey = sort.toApolloSortKey(),
        reverse = sort.reversed,
        filters =
            if (productTypeFilters.isEmpty()) {
                Optional.Absent
            } else {
                Optional.present(productTypeFilters)
            }
    )
}

private fun CollectionCatalogPageQuery.Data.toCatalogResult(
    mediaPolicy: StorefrontMediaPolicy
): StorefrontResult<CollectionCatalogPage?> = collection?.toCatalogResult(mediaPolicy) ?: StorefrontResult.Success(null)

private fun CollectionCatalogPageQuery.Collection.toCatalogResult(
    mediaPolicy: StorefrontMediaPolicy
): StorefrontResult<CollectionCatalogPage?> {
    val productResults = products.nodes.map { product -> product.toCatalogProduct(mediaPolicy) }
    val failure = productResults.filterIsInstance<StorefrontResult.Failure>().firstOrNull()
    return if (failure != null) {
        failure
    } else {
        @Suppress("UNCHECKED_CAST")
        val mappedProducts = productResults.map { (it as StorefrontResult.Success<CatalogProductSummary>).value }
        StorefrontResult.Success(
            CollectionCatalogPage(
                collectionId = id,
                handle = handle,
                title = title,
                products = mappedProducts,
                productTypeFilter = products.toProductTypeFilter(),
                endCursor = products.pageInfo.endCursor?.let(::Cursor),
                hasNextPage = products.pageInfo.hasNextPage
            )
        )
    }
}

private fun CollectionCatalogPageQuery.Node.toCatalogProduct(
    mediaPolicy: StorefrontMediaPolicy
): StorefrontResult<CatalogProductSummary> {
    val minimumAmount = priceRange.minVariantPrice.amount.toBigDecimalOrNull()
    val maximumAmount = priceRange.maxVariantPrice.amount.toBigDecimalOrNull()
    val minimumCurrency = priceRange.minVariantPrice.currencyCode.rawValue
    val maximumCurrency = priceRange.maxVariantPrice.currencyCode.rawValue
    return when {
        minimumAmount == null || maximumAmount == null -> graphQlFailure("INVALID_CATALOG_MONEY_AMOUNT")

        minimumCurrency != maximumCurrency -> graphQlFailure("INCONSISTENT_CATALOG_CURRENCY")

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

private val CollectionCatalogSort.reversed: Boolean
    get() =
        this == CollectionCatalogSort.PRICE_HIGH_TO_LOW ||
            this == CollectionCatalogSort.TITLE_Z_TO_A

private fun CollectionCatalogSort.toApolloSortKey(): ProductCollectionSortKeys = when (this) {
    CollectionCatalogSort.COLLECTION_DEFAULT -> ProductCollectionSortKeys.COLLECTION_DEFAULT

    CollectionCatalogSort.BEST_SELLING -> ProductCollectionSortKeys.BEST_SELLING

    CollectionCatalogSort.NEWEST -> ProductCollectionSortKeys.CREATED

    CollectionCatalogSort.PRICE_LOW_TO_HIGH,
    CollectionCatalogSort.PRICE_HIGH_TO_LOW -> ProductCollectionSortKeys.PRICE

    CollectionCatalogSort.TITLE_A_TO_Z,
    CollectionCatalogSort.TITLE_Z_TO_A -> ProductCollectionSortKeys.TITLE
}

private fun CollectionCatalogPageQuery.Products.toProductTypeFilter(): CatalogProductTypeFilter? {
    val filter = filters.firstOrNull { it.id == "filter.p.product_type" } ?: return null
    val values =
        filter.values
            .filter { it.count > 0 && it.label.isNotBlank() }
            .distinctBy { it.label }
            .map { value -> CatalogProductTypeValue(value.label, value.label, value.count) }
    return if (values.size > 1) CatalogProductTypeFilter(filter.label, values) else null
}
