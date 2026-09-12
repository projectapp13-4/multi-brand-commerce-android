package com.gurbakir.storefront

private const val MIN_SEARCH_QUERY_LENGTH = 2
private const val MAX_SEARCH_QUERY_LENGTH = 100

interface StorefrontSearchGateway {
    suspend fun searchProducts(request: ProductSearchPageRequest): StorefrontResult<ProductSearchPage>
}

class UnconfiguredStorefrontSearchGateway : StorefrontSearchGateway {
    override suspend fun searchProducts(request: ProductSearchPageRequest): StorefrontResult<ProductSearchPage> =
        StorefrontResult.Failure(StorefrontFailure.Configuration(setOf("storefront")))
}

data class ProductSearchPageRequest(val query: String, val after: Cursor? = null) {
    fun normalizedQuery(): String = query.trim().replace(Regex("\\s+"), " ")

    fun isValid(): Boolean {
        val normalized = normalizedQuery()
        return normalized.length in MIN_SEARCH_QUERY_LENGTH..MAX_SEARCH_QUERY_LENGTH &&
            normalized.none(Char::isISOControl)
    }
}

data class ProductSearchPage(
    val query: String,
    val products: List<CatalogProductSummary>,
    val totalCount: Int,
    val endCursor: Cursor?,
    val hasNextPage: Boolean
)
