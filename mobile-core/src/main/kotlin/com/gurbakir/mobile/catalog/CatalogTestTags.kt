package com.gurbakir.mobile.catalog

object CatalogTestTags {
    const val CATEGORIES_ROOT = "categories-root"
    const val CATEGORIES_EMPTY = "categories-empty"
    const val CATEGORIES_ERROR = "categories-error"
    const val CATEGORIES_PARTIAL_ERROR = "categories-partial-error"
    const val COLLECTION_ROOT = "collection-root"
    const val COLLECTION_GRID = "collection-grid"
    const val COLLECTION_LOADING = "collection-loading"
    const val COLLECTION_EMPTY = "collection-empty"
    const val COLLECTION_ERROR = "collection-error"
    const val COLLECTION_NOT_FOUND = "collection-not-found"
    const val COLLECTION_RESULT_COUNT = "collection-result-count"
    const val COLLECTION_SORT = "collection-sort"
    const val COLLECTION_LOAD_MORE = "collection-load-more"
    const val COLLECTION_NEXT_LOADING = "collection-next-loading"
    const val COLLECTION_NEXT_ERROR = "collection-next-error"

    fun category(handle: String): String = "category-$handle"

    fun categoryLabel(handle: String): String = "category-label-$handle"

    fun product(handle: String): String = "catalog-product-$handle"

    fun productMedia(handle: String): String = "catalog-product-media-$handle"

    fun productTitle(handle: String): String = "catalog-product-title-$handle"

    fun productPrice(handle: String): String = "catalog-product-price-$handle"

    fun productAvailability(handle: String): String = "catalog-product-availability-$handle"

    fun productUnavailable(handle: String): String = productAvailability(handle)

    fun filter(value: String): String = "catalog-filter-${value.lowercase().replace(' ', '-')}"
}
