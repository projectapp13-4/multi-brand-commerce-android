package com.gurbakir.storefront

import com.gurbakir.storefront.graphql.HomeContentMetaobjectQuery
import com.gurbakir.storefront.graphql.HomeResourcesQuery
import com.gurbakir.storefront.graphql.fragment.HomeImageFields

internal fun HomeContentMetaobjectQuery.Metaobject.toHomeDocumentObservation(
    mediaPolicy: StorefrontMediaPolicy
): HomeDocumentObservation = HomeDocumentObservation(
    rootGid = id,
    rootHandle = handle,
    rootType = type,
    rootUpdatedAt = updatedAt,
    schemaVersion = schemaVersion?.let { HomeFieldObservation(it.type, it.value) },
    declaredSectionCount = declaredSectionCount?.let { HomeFieldObservation(it.type, it.value) },
    sections =
        sections?.let { sectionField ->
            HomeSectionsFieldObservation(
                type = sectionField.type,
                value = sectionField.value,
                references =
                    sectionField.references?.let { references ->
                        HomeSectionReferencesObservation(
                            nodes = references.nodes.map { it.toHomeSectionNodeObservation(mediaPolicy) },
                            hasNextPage = references.pageInfo.hasNextPage
                        )
                    }
            )
        }
)

private fun HomeContentMetaobjectQuery.Node.toHomeSectionNodeObservation(
    mediaPolicy: StorefrontMediaPolicy
): HomeSectionNodeObservation = HomeSectionNodeObservation(
    runtimeType = __typename,
    section = onMetaobject?.toHomeSectionObservation(mediaPolicy)
)

private fun HomeContentMetaobjectQuery.OnMetaobject.toHomeSectionObservation(
    mediaPolicy: StorefrontMediaPolicy
): HomeSectionObservation = HomeSectionObservation(
    sectionGid = id,
    handle = handle,
    type = type,
    title = title?.let { HomeFieldObservation(it.type, it.value) },
    collections =
        collections?.let { field ->
            HomeCollectionsFieldObservation(
                type = field.type,
                value = field.value,
                references =
                    field.references?.let { references ->
                        HomeCollectionReferencesObservation(
                            nodes =
                                references.nodes.map { node ->
                                    HomeResourceNodeObservation(
                                        runtimeType = node.__typename,
                                        resource = node.onCollection?.toHomeCollectionResource(mediaPolicy)
                                    )
                                },
                            hasNextPage = references.pageInfo.hasNextPage
                        )
                    }
            )
        },
    product =
        product?.let { field ->
            HomeProductFieldObservation(
                type = field.type,
                value = field.value,
                reference =
                    field.reference?.let { reference ->
                        HomeResourceNodeObservation(
                            runtimeType = reference.__typename,
                            resource = reference.onProduct?.toHomeProductResource(mediaPolicy)
                        )
                    }
            )
        }
)

private fun HomeContentMetaobjectQuery.OnCollection.toHomeCollectionResource(
    mediaPolicy: StorefrontMediaPolicy
): StorefrontHomeResource.Collection = StorefrontHomeResource.Collection(
    key = HomeResourceKey(HomeResourceKind.COLLECTION, id),
    handle = handle,
    title = title,
    hasProducts = products.nodes.isNotEmpty(),
    media =
        if (image != null) {
            image.homeImageFields.toHomeMediaObservation(mediaPolicy)
        } else {
            products.nodes.firstOrNull()?.featuredImage?.homeImageFields.toHomeMediaObservation(mediaPolicy)
        }
)

private fun HomeContentMetaobjectQuery.OnProduct.toHomeProductResource(
    mediaPolicy: StorefrontMediaPolicy
): StorefrontHomeResource.Product = StorefrontHomeResource.Product(
    key = HomeResourceKey(HomeResourceKind.PRODUCT, id),
    handle = handle,
    title = title,
    availableForSale = availableForSale,
    media = featuredImage?.homeImageFields.toHomeMediaObservation(mediaPolicy),
    money =
        priceRange.minVariantPrice.amount.toBigDecimalOrNull()?.let {
            HomeMoneyObservation.Accepted(StorefrontMoney(it, priceRange.minVariantPrice.currencyCode.rawValue))
        } ?: HomeMoneyObservation.Malformed
)

internal fun HomeResourcesQuery.Data.toHomeResourceBatch(
    keys: List<HomeResourceKey>,
    mediaPolicy: StorefrontMediaPolicy
): StorefrontResult<HomeResourceBatch> {
    val returnedIds = nodes.mapNotNull { it?.id }
    return when {
        nodes.size != keys.size -> graphQlFailure("HOME_RESOURCE_COUNT_MISMATCH")

        returnedIds.distinct().size != returnedIds.size -> graphQlFailure("DUPLICATE_HOME_RESOURCE")

        else -> {
            val mappings = nodes.mapIndexed { index, node -> node.toHomeResourceMapping(keys[index], mediaPolicy) }
            val failure = mappings.filterIsInstance<HomeResourceMapping.Failure>().firstOrNull()
            if (failure != null) {
                graphQlFailure(failure.code)
            } else {
                val resolutions = mappings.filterIsInstance<HomeResourceMapping.Accepted>().map { it.resolution }
                StorefrontResult.Success(HomeResourceBatch(resolutions))
            }
        }
    }
}

private sealed interface HomeResourceMapping {
    data class Accepted(val resolution: HomeResourceResolution) : HomeResourceMapping
    data class Failure(val code: String) : HomeResourceMapping
}

private fun HomeResourcesQuery.Node?.toHomeResourceMapping(
    requested: HomeResourceKey,
    mediaPolicy: StorefrontMediaPolicy
): HomeResourceMapping = when {
    this == null -> HomeResourceMapping.Accepted(HomeResourceResolution(requested, null))

    id != requested.gid -> HomeResourceMapping.Failure("HOME_RESOURCE_ID_MISMATCH")

    else -> {
        val resource = when (requested.kind) {
            HomeResourceKind.COLLECTION -> onCollection?.toHomeCollectionResource(requested, mediaPolicy)
            HomeResourceKind.PRODUCT -> onProduct?.toHomeProductResource(requested, mediaPolicy)
            HomeResourceKind.MEDIA_IMAGE, HomeResourceKind.VIDEO -> null
        }
        resource?.let { HomeResourceMapping.Accepted(HomeResourceResolution(requested, it)) }
            ?: HomeResourceMapping.Failure("HOME_RESOURCE_KIND_MISMATCH")
    }
}

private fun HomeResourcesQuery.OnCollection.toHomeCollectionResource(
    key: HomeResourceKey,
    mediaPolicy: StorefrontMediaPolicy
): StorefrontHomeResource.Collection = StorefrontHomeResource.Collection(
    key = key,
    handle = handle,
    title = title,
    hasProducts = products.nodes.isNotEmpty(),
    media =
        if (image != null) {
            image.homeImageFields.toHomeMediaObservation(mediaPolicy)
        } else {
            products.nodes.firstOrNull()?.featuredImage?.homeImageFields.toHomeMediaObservation(mediaPolicy)
        }
)

private fun HomeResourcesQuery.OnProduct.toHomeProductResource(
    key: HomeResourceKey,
    mediaPolicy: StorefrontMediaPolicy
): StorefrontHomeResource.Product = StorefrontHomeResource.Product(
    key = key,
    handle = handle,
    title = title,
    availableForSale = availableForSale,
    media = featuredImage?.homeImageFields.toHomeMediaObservation(mediaPolicy),
    money =
        priceRange.minVariantPrice.amount.toBigDecimalOrNull()?.let {
            HomeMoneyObservation.Accepted(StorefrontMoney(it, priceRange.minVariantPrice.currencyCode.rawValue))
        } ?: HomeMoneyObservation.Malformed
)

private fun HomeImageFields?.toHomeMediaObservation(mediaPolicy: StorefrontMediaPolicy): HomeMediaObservation =
    when (this) {
        null -> HomeMediaObservation.Absent
        else -> toStorefrontMedia(mediaPolicy)?.let(HomeMediaObservation::Accepted) ?: HomeMediaObservation.Rejected
    }
