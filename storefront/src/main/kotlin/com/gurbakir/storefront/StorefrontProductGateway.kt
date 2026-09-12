package com.gurbakir.storefront

private val PRODUCT_GID = Regex("^gid://shopify/Product/([1-9][0-9]*)$")
private val VARIANT_GID = Regex("^gid://shopify/ProductVariant/([1-9][0-9]*)$")
private val ROUTE_ID = Regex("^[1-9][0-9]*$")

interface StorefrontProductGateway {
    suspend fun loadProductDetail(request: ProductDetailRequest): StorefrontResult<StorefrontProductDetail?>
}

class UnconfiguredStorefrontProductGateway : StorefrontProductGateway {
    override suspend fun loadProductDetail(request: ProductDetailRequest): StorefrontResult<StorefrontProductDetail?> =
        StorefrontResult.Failure(StorefrontFailure.Configuration(setOf("storefront")))
}

data class ProductDetailRequest(val productId: String) {
    fun isValid(): Boolean = PRODUCT_GID.matches(productId)
}

data class StorefrontProductDetail(
    val id: String,
    val handle: String,
    val title: String,
    val description: String,
    val availableForSale: Boolean,
    val options: List<StorefrontProductOption>,
    val variants: List<StorefrontProductVariant>,
    val media: List<StorefrontProductMedia>
)

data class StorefrontProductOption(val id: String, val name: String, val values: List<StorefrontProductOptionValue>)

data class StorefrontProductOptionValue(val id: String, val name: String)

data class StorefrontSelectedOption(val name: String, val value: String)

data class StorefrontProductVariant(
    val id: String,
    val title: String,
    val availableForSale: Boolean,
    val currentlyNotInStock: Boolean,
    val price: StorefrontMoney,
    val compareAtPrice: StorefrontMoney?,
    val image: StorefrontMedia?,
    val selectedOptions: List<StorefrontSelectedOption>
)

data class StorefrontProductMedia(val id: String, val image: StorefrontMedia)

object StorefrontProductIds {
    fun productGidFromRoute(routeValue: String): String? =
        routeValue.takeIf(ROUTE_ID::matches)?.let { "gid://shopify/Product/$it" }

    fun productRouteFromGid(gid: String): String? = PRODUCT_GID.matchEntire(gid)?.groupValues?.get(1)

    fun variantGidFromRoute(routeValue: String): String? =
        routeValue.takeIf(ROUTE_ID::matches)?.let { "gid://shopify/ProductVariant/$it" }

    fun variantRouteFromGid(gid: String): String? = VARIANT_GID.matchEntire(gid)?.groupValues?.get(1)

    fun isProductGid(value: String): Boolean = PRODUCT_GID.matches(value)

    fun isVariantGid(value: String): Boolean = VARIANT_GID.matches(value)
}
