package com.gurbakir.mobile.search

import com.gurbakir.mobile.catalog.CatalogLoadFailure
import com.gurbakir.mobile.catalog.CatalogLoadFailureCategory
import com.gurbakir.storefront.Cursor
import com.gurbakir.storefront.ProductSearchPage
import com.gurbakir.storefront.ProductSearchPageRequest
import com.gurbakir.storefront.StorefrontFailure
import com.gurbakir.storefront.StorefrontResult
import com.gurbakir.storefront.StorefrontSearchGateway

internal const val SEARCH_MINIMUM_LENGTH = 2
internal const val SEARCH_MAXIMUM_LENGTH = 100

interface ProductSearchRepository {
    suspend fun search(query: String, after: Cursor? = null): SearchPageLoad
}

class DefaultProductSearchRepository(private val gateway: StorefrontSearchGateway) : ProductSearchRepository {
    override suspend fun search(query: String, after: Cursor?): SearchPageLoad =
        when (val result = gateway.searchProducts(ProductSearchPageRequest(query, after))) {
            is StorefrontResult.Success -> SearchPageLoad.Content(result.value)
            is StorefrontResult.Failure -> SearchPageLoad.Error(result.error.toSearchFailure())
        }
}

sealed interface SearchPageLoad {
    data class Content(val page: ProductSearchPage) : SearchPageLoad

    data class Error(val failure: CatalogLoadFailure) : SearchPageLoad
}

internal fun String.isSearchableQuery(): Boolean {
    val normalized = toDisplaySearchQuery()
    return normalized.length in SEARCH_MINIMUM_LENGTH..SEARCH_MAXIMUM_LENGTH &&
        normalized.none(Char::isISOControl)
}

private fun StorefrontFailure.toSearchFailure(): CatalogLoadFailure = when (this) {
    is StorefrontFailure.Configuration ->
        CatalogLoadFailure(CatalogLoadFailureCategory.CONFIGURATION, retryable = false)

    is StorefrontFailure.Transport ->
        CatalogLoadFailure(CatalogLoadFailureCategory.CONNECTION, retryable = retryable)

    is StorefrontFailure.GraphQl,
    is StorefrontFailure.UserErrors,
    is StorefrontFailure.InvalidCart,
    StorefrontFailure.SecurePersistence ->
        CatalogLoadFailure(CatalogLoadFailureCategory.SERVICE, retryable = false)
}
