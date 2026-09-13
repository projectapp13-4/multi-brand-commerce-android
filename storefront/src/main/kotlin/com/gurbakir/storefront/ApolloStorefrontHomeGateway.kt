package com.gurbakir.storefront

import com.apollographql.apollo.ApolloClient
import com.gurbakir.storefront.graphql.HomeCollectionQuery
import com.gurbakir.storefront.graphql.HomeContentMetaobjectQuery
import com.gurbakir.storefront.graphql.HomeProductQuery
import com.gurbakir.storefront.graphql.HomeResourcesQuery
import com.gurbakir.storefront.graphql.type.MetaobjectHandleInput

private const val MAX_HOME_RESOURCE_KEYS = 7
private val METAOBJECT_TYPE_PATTERN = Regex("^[a-z0-9][a-z0-9_-]{0,254}$")
private val STOREFRONT_HANDLE_PATTERN = Regex("^[a-z0-9][a-z0-9-]{0,254}$")

internal class ApolloStorefrontHomeGateway(
    private val client: ApolloClient,
    private val mediaPolicy: StorefrontMediaPolicy,
    requestTimeoutMillis: Long = DEFAULT_REQUEST_TIMEOUT_MILLIS
) : StorefrontHomeGateway {
    private val callExecutor = ApolloCallExecutor(requestTimeoutMillis)

    init {
        require(requestTimeoutMillis > 0) { "requestTimeoutMillis must be positive" }
    }

    override suspend fun loadHomeCollection(handle: String): StorefrontResult<HomeCollectionSummary?> =
        if (!handle.isValidStorefrontHandle()) {
            StorefrontResult.Failure(StorefrontFailure.Configuration(setOf("home.collectionHandle")))
        } else {
            when (val result = callExecutor.execute(client.query(HomeCollectionQuery(handle)))) {
                is StorefrontResult.Failure -> result
                is StorefrontResult.Success -> result.value.toHomeCollectionSummary(mediaPolicy)
            }
        }

    override suspend fun loadHomeProduct(handle: String): StorefrontResult<HomeProductSummary?> =
        if (!handle.isValidStorefrontHandle()) {
            StorefrontResult.Failure(StorefrontFailure.Configuration(setOf("home.productHandle")))
        } else {
            when (val result = callExecutor.execute(client.query(HomeProductQuery(handle)))) {
                is StorefrontResult.Failure -> result
                is StorefrontResult.Success -> result.value.toHomeProductSummary(mediaPolicy)
            }
        }

    override suspend fun loadHomeDocument(selector: HomeDocumentSelector): StorefrontResult<HomeDocumentObservation?> =
        if (!selector.type.isValidMetaobjectType() || !selector.handle.isValidStorefrontHandle()) {
            StorefrontResult.Failure(StorefrontFailure.Configuration(setOf("home.documentSelector")))
        } else {
            val query = HomeContentMetaobjectQuery(MetaobjectHandleInput(selector.handle, selector.type))
            when (val result = callExecutor.execute(client.query(query))) {
                is StorefrontResult.Failure -> result

                is StorefrontResult.Success -> {
                    val root = result.value.metaobject
                    when {
                        root == null -> StorefrontResult.Success(null)

                        root.handle != selector.handle || root.type != selector.type ->
                            graphQlFailure("HOME_ROOT_IDENTITY_MISMATCH")

                        else -> StorefrontResult.Success(root.toHomeDocumentObservation(mediaPolicy))
                    }
                }
            }
        }

    override suspend fun loadHomeResources(keys: List<HomeResourceKey>): StorefrontResult<HomeResourceBatch> = when {
        !keys.areValidHomeResourceKeys() ->
            StorefrontResult.Failure(StorefrontFailure.Configuration(setOf("home.resourceKeys")))

        keys.isEmpty() -> StorefrontResult.Success(HomeResourceBatch(emptyList()))

        else ->
            when (val result = callExecutor.execute(client.query(HomeResourcesQuery(keys.map { it.gid })))) {
                is StorefrontResult.Failure -> result
                is StorefrontResult.Success -> result.value.toHomeResourceBatch(keys, mediaPolicy)
            }
    }
}

private fun HomeCollectionQuery.Data.toHomeCollectionSummary(
    mediaPolicy: StorefrontMediaPolicy
): StorefrontResult<HomeCollectionSummary?> {
    val firstProduct = collection?.products?.nodes?.firstOrNull()
    val media =
        when {
            collection == null || firstProduct == null -> null
            collection.image == null -> firstProduct.featuredImage?.homeImageFields?.toStorefrontMedia(mediaPolicy)
            else -> collection.image.homeImageFields.toStorefrontMedia(mediaPolicy)
        }
    return StorefrontResult.Success(
        if (collection == null || media == null) {
            null
        } else {
            HomeCollectionSummary(collection.id, collection.handle, collection.title, media)
        }
    )
}

private fun HomeProductQuery.Data.toHomeProductSummary(
    mediaPolicy: StorefrontMediaPolicy
): StorefrontResult<HomeProductSummary?> {
    val amount = product?.priceRange?.minVariantPrice?.amount?.toBigDecimalOrNull()
    return when {
        product == null -> StorefrontResult.Success(null)

        amount == null -> graphQlFailure("INVALID_MONEY_AMOUNT")

        else ->
            StorefrontResult.Success(
                HomeProductSummary(
                    id = product.id,
                    handle = product.handle,
                    title = product.title,
                    availableForSale = product.availableForSale,
                    media = product.featuredImage?.homeImageFields?.toStorefrontMedia(mediaPolicy),
                    price = StorefrontMoney(amount, product.priceRange.minVariantPrice.currencyCode.rawValue)
                )
            )
    }
}

private fun String.isValidStorefrontHandle(): Boolean = STOREFRONT_HANDLE_PATTERN.matches(this)

private fun String.isValidMetaobjectType(): Boolean = METAOBJECT_TYPE_PATTERN.matches(this)

private fun List<HomeResourceKey>.areValidHomeResourceKeys(): Boolean =
    size <= MAX_HOME_RESOURCE_KEYS && distinct().size == size && all(HomeResourceKey::hasValidTypedGid)

private fun HomeResourceKey.hasValidTypedGid(): Boolean {
    val prefix = when (kind) {
        HomeResourceKind.COLLECTION -> "gid://shopify/Collection/"
        HomeResourceKind.PRODUCT -> "gid://shopify/Product/"
    }
    val suffix = gid.removePrefix(prefix)
    return suffix != gid && suffix.isNotBlank() && suffix.none { it.isWhitespace() || it.isISOControl() }
}
