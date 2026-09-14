package com.gurbakir.storefront

import java.net.URI

interface StorefrontGateway {
    suspend fun loadShopSummary(): StorefrontResult<ShopSummary>

    suspend fun loadCatalogPage(after: Cursor?): StorefrontResult<CatalogPage>

    suspend fun createCart(
        lines: List<CartLineInput>,
        buyerAccessToken: SensitiveBuyerAccessToken? = null
    ): StorefrontResult<CartReference>

    suspend fun loadCart(cartId: SensitiveCartId): StorefrontResult<CartReference>

    suspend fun addCartLines(cartId: SensitiveCartId, lines: List<CartLineInput>): StorefrontResult<CartReference>

    suspend fun updateCartLines(cartId: SensitiveCartId, lines: List<CartLineUpdate>): StorefrontResult<CartReference>

    suspend fun removeCartLines(
        cartId: SensitiveCartId,
        lineIds: List<SensitiveCartLineId>
    ): StorefrontResult<CartReference>

    suspend fun updateBuyerIdentity(
        cartId: SensitiveCartId,
        buyerAccessToken: SensitiveBuyerAccessToken?
    ): StorefrontResult<CartReference>
}

sealed interface StorefrontResult<out T> {
    data class Success<T>(val value: T) : StorefrontResult<T>

    data class Failure(val error: StorefrontFailure) : StorefrontResult<Nothing>
}

sealed interface StorefrontFailure {
    data class Configuration(val missingKeys: Set<String>) : StorefrontFailure

    data class Transport(val retryable: Boolean) : StorefrontFailure

    data class GraphQl(val errorCodes: Set<String>) : StorefrontFailure

    data class UserErrors(val errors: List<ShopifyUserError>) : StorefrontFailure

    data class InvalidCart(val reason: InvalidCartReason) : StorefrontFailure

    data object SecurePersistence : StorefrontFailure
}

enum class InvalidCartReason {
    EXPIRED,
    COMPLETED,
    NOT_FOUND,
    MERCHANDISE_UNAVAILABLE
}

data class ShopifyUserError(val code: String?, val fieldPath: List<String>)

@JvmInline
value class Cursor(val value: String)

data class ShopSummary(val name: String, val primaryDomain: String)

data class CatalogPage(val products: List<ProductSummary>, val endCursor: Cursor?, val hasNextPage: Boolean)

data class ProductSummary(
    val id: String,
    val handle: String,
    val title: String,
    val primaryImage: URI?,
    val variants: List<ProductVariantSummary> = emptyList()
)

data class ProductVariantSummary(val id: String, val title: String, val availableForSale: Boolean)

data class CartLineInput(val merchandiseId: String, val quantity: Int)

data class CartLineUpdate(val lineId: SensitiveCartLineId, val quantity: Int)

data class CartQuantityRule(val minimum: Int, val maximum: Int?, val increment: Int)

data class CartLineSummary(
    val id: SensitiveCartLineId,
    val merchandiseId: String,
    val quantity: Int,
    val productId: String = "",
    val productTitle: String = "",
    val variantTitle: String = "",
    val availableForSale: Boolean = true,
    val currentlyNotInStock: Boolean = false,
    val quantityRule: CartQuantityRule = CartQuantityRule(minimum = 1, maximum = null, increment = 1),
    val canRemove: Boolean = true,
    val canUpdateQuantity: Boolean = true,
    val image: StorefrontMedia? = null,
    val unitPrice: StorefrontMoney? = null,
    val totalPrice: StorefrontMoney? = null
)

data class CartReference(
    val id: SensitiveCartId,
    val checkoutUrl: SensitiveCheckoutUrl,
    val totalQuantity: Int,
    val lines: List<CartLineSummary>,
    val hasMoreLines: Boolean,
    val warningCodes: Set<String>,
    val subtotal: StorefrontMoney? = null,
    val total: StorefrontMoney? = null,
    val customerAssociated: Boolean = false
) {
    override fun toString(): String =
        "CartReference(id=<redacted>, checkoutUrl=<redacted>, totalQuantity=$totalQuantity, " +
            "lineCount=${lines.size}, hasMoreLines=$hasMoreLines, warningCodes=$warningCodes)"
}

class UnconfiguredStorefrontGateway :
    StorefrontApi,
    StorefrontHomeGateway by UnconfiguredStorefrontHomeGateway {
    override suspend fun loadShopSummary(): StorefrontResult<ShopSummary> = missingConfiguration()

    override suspend fun loadCatalogPage(after: Cursor?): StorefrontResult<CatalogPage> = missingConfiguration()

    override suspend fun createCart(
        lines: List<CartLineInput>,
        buyerAccessToken: SensitiveBuyerAccessToken?
    ): StorefrontResult<CartReference> = missingConfiguration()

    override suspend fun loadCart(cartId: SensitiveCartId): StorefrontResult<CartReference> = missingConfiguration()

    override suspend fun addCartLines(
        cartId: SensitiveCartId,
        lines: List<CartLineInput>
    ): StorefrontResult<CartReference> = missingConfiguration()

    override suspend fun updateCartLines(
        cartId: SensitiveCartId,
        lines: List<CartLineUpdate>
    ): StorefrontResult<CartReference> = missingConfiguration()

    override suspend fun removeCartLines(
        cartId: SensitiveCartId,
        lineIds: List<SensitiveCartLineId>
    ): StorefrontResult<CartReference> = missingConfiguration()

    override suspend fun updateBuyerIdentity(
        cartId: SensitiveCartId,
        buyerAccessToken: SensitiveBuyerAccessToken?
    ): StorefrontResult<CartReference> = missingConfiguration()
}

private object UnconfiguredStorefrontHomeGateway : StorefrontHomeGateway {
    override suspend fun loadHomeCollection(handle: String): StorefrontResult<HomeCollectionSummary?> =
        missingConfiguration()

    override suspend fun loadHomeProduct(handle: String): StorefrontResult<HomeProductSummary?> = missingConfiguration()

    override suspend fun loadHomeDocument(selector: HomeDocumentSelector): StorefrontResult<HomeDocumentObservation?> =
        missingConfiguration()

    override suspend fun loadHomeResources(keys: List<HomeResourceKey>): StorefrontResult<HomeResourceBatch> =
        missingConfiguration()
}

class UnconfiguredStorefrontCatalogGateway : StorefrontCatalogGateway {
    override suspend fun loadCatalogDiscovery(
        request: CatalogDiscoveryRequest
    ): StorefrontResult<CatalogDiscoveryMenu?> = missingConfiguration()

    override suspend fun loadCollectionCatalogPage(
        request: CollectionCatalogPageRequest
    ): StorefrontResult<CollectionCatalogPage?> = missingConfiguration()
}

private fun missingConfiguration(): StorefrontResult.Failure = StorefrontResult.Failure(
    StorefrontFailure.Configuration(
        missingKeys = setOf("shopify.storefrontDomain", "shopify.storefrontPublicToken")
    )
)
