package com.gurbakir.storefront

import com.apollographql.apollo.ApolloClient
import com.gurbakir.storefront.graphql.HomeCollectionQuery
import com.gurbakir.storefront.graphql.HomeContentMetaobjectQuery
import com.gurbakir.storefront.graphql.HomeContentV2MetaobjectQuery
import com.gurbakir.storefront.graphql.HomeProductQuery
import com.gurbakir.storefront.graphql.HomeResourcesQuery
import com.gurbakir.storefront.graphql.HomeV2ResourcesQuery
import com.gurbakir.storefront.graphql.type.MetaobjectHandleInput

private const val MAX_HOME_V1_RESOURCE_KEYS = 7
private const val MAX_HOME_V2_RESOURCE_KEYS = 14
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

    override suspend fun loadHomeDocument(selector: HomeDocumentSelector): StorefrontResult<HomeDocumentObservation?> {
        if (!selector.isSupported()) {
            return StorefrontResult.Failure(StorefrontFailure.Configuration(setOf("home.documentSelector")))
        }
        return when (selector.type) {
            HomeContentContractId.GATE7_V1.rootType -> loadHomeV1Document(selector)
            HomeContentContractId.PILOT_MEDIA_V2.rootType -> loadHomeV2Document(selector)
            else -> error("Selector support must remain closed")
        }
    }

    override suspend fun loadHomeResources(keys: List<HomeResourceKey>): StorefrontResult<HomeResourceBatch> = when {
        !keys.areValidHomeResourceKeys() ->
            StorefrontResult.Failure(StorefrontFailure.Configuration(setOf("home.resourceKeys")))

        keys.isEmpty() -> StorefrontResult.Success(HomeResourceBatch(emptyList()))

        keys.requiresV2Query() ->
            when (val result = callExecutor.execute(client.query(HomeV2ResourcesQuery(keys.map { it.gid })))) {
                is StorefrontResult.Failure -> result
                is StorefrontResult.Success -> result.value.toHomeV2ResourceBatch(keys, mediaPolicy)
            }

        else ->
            when (val result = callExecutor.execute(client.query(HomeResourcesQuery(keys.map { it.gid })))) {
                is StorefrontResult.Failure -> result
                is StorefrontResult.Success -> result.value.toHomeResourceBatch(keys, mediaPolicy)
            }
    }

    private suspend fun loadHomeV1Document(selector: HomeDocumentSelector): StorefrontResult<HomeDocumentObservation?> {
        val query = HomeContentMetaobjectQuery(MetaobjectHandleInput(selector.handle, selector.type))
        return when (val result = callExecutor.execute(client.query(query))) {
            is StorefrontResult.Failure -> result
            is StorefrontResult.Success -> result.value.metaobject.toObservedDocument(selector, mediaPolicy)
        }
    }

    private suspend fun loadHomeV2Document(selector: HomeDocumentSelector): StorefrontResult<HomeDocumentObservation?> {
        val query = HomeContentV2MetaobjectQuery(MetaobjectHandleInput(selector.handle, selector.type))
        return when (val result = callExecutor.execute(client.query(query))) {
            is StorefrontResult.Failure -> result
            is StorefrontResult.Success -> result.value.metaobject.toObservedV2Document(selector, mediaPolicy)
        }
    }
}

private fun HomeContentMetaobjectQuery.Metaobject?.toObservedDocument(
    selector: HomeDocumentSelector,
    mediaPolicy: StorefrontMediaPolicy
): StorefrontResult<HomeDocumentObservation?> = when {
    this == null -> StorefrontResult.Success(null)
    handle != selector.handle || type != selector.type -> graphQlFailure("HOME_ROOT_IDENTITY_MISMATCH")
    else -> StorefrontResult.Success(toHomeDocumentObservation(mediaPolicy))
}

private fun HomeContentV2MetaobjectQuery.Metaobject?.toObservedV2Document(
    selector: HomeDocumentSelector,
    mediaPolicy: StorefrontMediaPolicy
): StorefrontResult<HomeDocumentObservation?> = when {
    this == null -> StorefrontResult.Success(null)
    handle != selector.handle || type != selector.type -> graphQlFailure("HOME_ROOT_IDENTITY_MISMATCH")
    else -> StorefrontResult.Success(toHomeDocumentObservation(mediaPolicy))
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
    size <= MAX_HOME_V2_RESOURCE_KEYS && distinct().size == size && all(HomeResourceKey::hasValidTypedGid)

private fun List<HomeResourceKey>.requiresV2Query(): Boolean = size > MAX_HOME_V1_RESOURCE_KEYS ||
    any { it.kind == HomeResourceKind.MEDIA_IMAGE || it.kind == HomeResourceKind.VIDEO }

private fun HomeDocumentSelector.isSupported(): Boolean = type.isValidMetaobjectType() &&
    handle.isValidStorefrontHandle() &&
    type in HomeContentContractId.entries.map(HomeContentContractId::rootType)

private fun HomeResourceKey.hasValidTypedGid(): Boolean {
    val prefix = when (kind) {
        HomeResourceKind.COLLECTION -> "gid://shopify/Collection/"
        HomeResourceKind.PRODUCT -> "gid://shopify/Product/"
        HomeResourceKind.MEDIA_IMAGE -> "gid://shopify/MediaImage/"
        HomeResourceKind.VIDEO -> "gid://shopify/Video/"
    }
    val suffix = gid.removePrefix(prefix)
    return suffix != gid && suffix.isNotBlank() && suffix.none { it.isWhitespace() || it.isISOControl() }
}
