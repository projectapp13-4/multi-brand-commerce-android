package com.gurbakir.storefront

private const val MAX_PRODUCT_TYPE_FILTERS = 25
private const val MAX_PRODUCT_TYPE_LENGTH = 255

interface StorefrontCatalogGateway {
    suspend fun loadCatalogDiscovery(request: CatalogDiscoveryRequest): StorefrontResult<CatalogDiscoveryMenu?>

    suspend fun loadCollectionCatalogPage(
        request: CollectionCatalogPageRequest
    ): StorefrontResult<CollectionCatalogPage?>
}

data class StorefrontGatewaySet(
    val api: StorefrontApi,
    val catalog: StorefrontCatalogGateway,
    val search: StorefrontSearchGateway,
    val product: StorefrontProductGateway
)

data class CollectionCatalogPageRequest(
    val handle: String,
    val after: Cursor? = null,
    val sort: CollectionCatalogSort = CollectionCatalogSort.COLLECTION_DEFAULT,
    val productTypes: Set<String> = emptySet()
) {
    fun isValid(): Boolean = handle.matches(Regex("^[a-z0-9][a-z0-9-]{0,254}$")) &&
        productTypes.size <= MAX_PRODUCT_TYPE_FILTERS &&
        productTypes.all { value ->
            value.isNotBlank() &&
                value.length <= MAX_PRODUCT_TYPE_LENGTH &&
                value.none(Char::isISOControl)
        }
}

enum class CollectionCatalogSort {
    COLLECTION_DEFAULT,
    BEST_SELLING,
    NEWEST,
    PRICE_LOW_TO_HIGH,
    PRICE_HIGH_TO_LOW,
    TITLE_A_TO_Z,
    TITLE_Z_TO_A
}

data class CollectionCatalogPage(
    val collectionId: String,
    val handle: String,
    val title: String,
    val products: List<CatalogProductSummary>,
    val productTypeFilter: CatalogProductTypeFilter?,
    val endCursor: Cursor?,
    val hasNextPage: Boolean
)

data class CatalogProductSummary(
    val id: String,
    val handle: String,
    val title: String,
    val availableForSale: Boolean,
    val media: StorefrontMedia?,
    val minimumPrice: StorefrontMoney,
    val maximumPrice: StorefrontMoney
) {
    val hasPriceRange: Boolean
        get() =
            minimumPrice.currencyCode != maximumPrice.currencyCode ||
                minimumPrice.amount.compareTo(maximumPrice.amount) != 0
}

data class CatalogProductTypeFilter(val label: String, val values: List<CatalogProductTypeValue>)

data class CatalogProductTypeValue(val value: String, val label: String, val count: Int)
