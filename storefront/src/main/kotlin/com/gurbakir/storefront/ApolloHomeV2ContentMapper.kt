@file:Suppress("TooManyFunctions") // Generated GraphQL shapes require explicit closed-type adapters.

package com.gurbakir.storefront

import com.gurbakir.storefront.graphql.HomeContentV2MetaobjectQuery
import com.gurbakir.storefront.graphql.HomeV2ResourcesQuery
import java.net.URI

internal fun HomeContentV2MetaobjectQuery.Metaobject.toHomeDocumentObservation(
    mediaPolicy: StorefrontMediaPolicy
): HomeDocumentObservation = HomeDocumentObservation(
    rootGid = id,
    rootHandle = handle,
    rootType = type,
    rootUpdatedAt = updatedAt,
    schemaVersion = schemaVersion?.let { HomeFieldObservation(it.type, it.value, it.key) },
    declaredSectionCount = declaredSectionCount?.let { HomeFieldObservation(it.type, it.value, it.key) },
    sections =
        sections?.let { field ->
            HomeSectionsFieldObservation(
                type = field.type,
                value = field.value,
                references =
                    field.references?.let { references ->
                        HomeSectionReferencesObservation(
                            references.nodes.map { it.toHomeSectionNodeObservation(mediaPolicy) },
                            references.pageInfo.hasNextPage
                        )
                    },
                key = field.key
            )
        }
)

private fun HomeContentV2MetaobjectQuery.Node.toHomeSectionNodeObservation(
    mediaPolicy: StorefrontMediaPolicy
): HomeSectionNodeObservation = HomeSectionNodeObservation(
    runtimeType = __typename,
    section = onMetaobject?.toHomeSectionObservation(mediaPolicy)
)

private fun HomeContentV2MetaobjectQuery.OnMetaobject.toHomeSectionObservation(
    mediaPolicy: StorefrontMediaPolicy
): HomeSectionObservation = HomeSectionObservation(
    sectionGid = id,
    handle = handle,
    type = type,
    title = title?.let { HomeFieldObservation(it.type, it.value, it.key) },
    collections = collections?.toObservation(mediaPolicy),
    product = product?.toObservation(mediaPolicy),
    updatedAt = updatedAt,
    presentation = presentation?.let { HomeFieldObservation(it.type, it.value, it.key) },
    media = media?.toObservation(mediaPolicy),
    poster = poster?.toObservation(mediaPolicy),
    altText = altText?.let { HomeFieldObservation(it.type, it.value, it.key) },
    caption = caption?.let { HomeFieldObservation(it.type, it.value, it.key) },
    productTarget = productTarget?.toObservation(),
    collectionTarget = collectionTarget?.toObservation()
)

private fun HomeContentV2MetaobjectQuery.Collections.toObservation(
    mediaPolicy: StorefrontMediaPolicy
): HomeCollectionsFieldObservation = HomeCollectionsFieldObservation(
    type = type,
    value = value,
    references =
        references?.let { resolved ->
            HomeCollectionReferencesObservation(
                resolved.nodes.map { node ->
                    HomeResourceNodeObservation(
                        runtimeType = node.__typename,
                        resource = node.onCollection?.toHomeCollectionResource(mediaPolicy)
                    )
                },
                resolved.pageInfo.hasNextPage
            )
        },
    key = key
)

private fun HomeContentV2MetaobjectQuery.Product.toObservation(
    mediaPolicy: StorefrontMediaPolicy
): HomeProductFieldObservation = HomeProductFieldObservation(
    type = type,
    value = value,
    reference =
        reference?.let { resolved ->
            HomeResourceNodeObservation(
                runtimeType = resolved.__typename,
                resource = resolved.onProduct?.toHomeProductResource(mediaPolicy)
            )
        },
    key = key
)

private fun HomeContentV2MetaobjectQuery.Media.toObservation(
    mediaPolicy: StorefrontMediaPolicy
): HomeMediaFieldObservation = HomeMediaFieldObservation(
    type = type,
    value = value,
    reference = reference?.toObservation(value, mediaPolicy),
    key = key
)

private fun HomeContentV2MetaobjectQuery.Reference1.toObservation(
    declaredGid: String?,
    mediaPolicy: StorefrontMediaPolicy
): HomeResourceNodeObservation = HomeResourceNodeObservation(
    runtimeType = __typename,
    resource =
        when {
            onMediaImage != null -> onMediaImage.toHomeMediaImageResource(mediaPolicy)
            onVideo != null -> onVideo.toHomeVideoResource(mediaPolicy)
            else -> null
        }?.takeIf { it.key.gid == declaredGid }
)

private fun HomeContentV2MetaobjectQuery.Poster.toObservation(
    mediaPolicy: StorefrontMediaPolicy
): HomeMediaFieldObservation = HomeMediaFieldObservation(
    type = type,
    value = value,
    reference =
        reference?.let { resolved ->
            HomeResourceNodeObservation(
                runtimeType = resolved.__typename,
                resource =
                    resolved.onMediaImage
                        ?.toHomeMediaImageResource(mediaPolicy)
                        ?.takeIf { it.key.gid == value }
            )
        },
    key = key
)

private fun HomeContentV2MetaobjectQuery.ProductTarget.toObservation(): HomeTargetFieldObservation =
    HomeTargetFieldObservation(
        type = type,
        value = value,
        reference =
            reference?.let { resolved ->
                HomeTargetNodeObservation(
                    runtimeType = resolved.__typename,
                    target =
                        resolved.onProduct?.let { product ->
                            StorefrontHomeTarget(
                                HomeResourceKey(HomeResourceKind.PRODUCT, product.id),
                                product.handle,
                                product.title
                            )
                        }?.takeIf { it.key.gid == value }
                )
            },
        key = key
    )

private fun HomeContentV2MetaobjectQuery.CollectionTarget.toObservation(): HomeTargetFieldObservation =
    HomeTargetFieldObservation(
        type = type,
        value = value,
        reference =
            reference?.let { resolved ->
                HomeTargetNodeObservation(
                    runtimeType = resolved.__typename,
                    target =
                        resolved.onCollection?.let { collection ->
                            StorefrontHomeTarget(
                                HomeResourceKey(HomeResourceKind.COLLECTION, collection.id),
                                collection.handle,
                                collection.title
                            )
                        }?.takeIf { it.key.gid == value }
                )
            },
        key = key
    )

private fun HomeContentV2MetaobjectQuery.OnCollection.toHomeCollectionResource(
    mediaPolicy: StorefrontMediaPolicy
): StorefrontHomeResource.Collection = StorefrontHomeResource.Collection(
    key = HomeResourceKey(HomeResourceKind.COLLECTION, id),
    handle = handle,
    title = title,
    hasProducts = products.nodes.isNotEmpty(),
    media = image?.toHomeMediaObservation(mediaPolicy)
        ?: products.nodes.firstOrNull()?.featuredImage.toHomeMediaObservation(mediaPolicy)
)

private fun HomeContentV2MetaobjectQuery.OnProduct.toHomeProductResource(
    mediaPolicy: StorefrontMediaPolicy
): StorefrontHomeResource.Product = StorefrontHomeResource.Product(
    key = HomeResourceKey(HomeResourceKind.PRODUCT, id),
    handle = handle,
    title = title,
    availableForSale = availableForSale,
    media = featuredImage.toHomeMediaObservation(mediaPolicy),
    money =
        priceRange.minVariantPrice.amount.toBigDecimalOrNull()?.let { amount ->
            HomeMoneyObservation.Accepted(
                StorefrontMoney(amount, priceRange.minVariantPrice.currencyCode.rawValue)
            )
        } ?: HomeMoneyObservation.Malformed
)

private fun HomeContentV2MetaobjectQuery.OnMediaImage.toHomeMediaImageResource(
    mediaPolicy: StorefrontMediaPolicy
): StorefrontHomeResource.MediaImage = StorefrontHomeResource.MediaImage(
    key = HomeResourceKey(HomeResourceKind.MEDIA_IMAGE, id),
    contentType = mediaContentType.rawValue,
    media = image.toHomeMediaObservation(mediaPolicy, alt)
)

private fun HomeContentV2MetaobjectQuery.OnMediaImage1.toHomeMediaImageResource(
    mediaPolicy: StorefrontMediaPolicy
): StorefrontHomeResource.MediaImage = StorefrontHomeResource.MediaImage(
    key = HomeResourceKey(HomeResourceKind.MEDIA_IMAGE, id),
    contentType = mediaContentType.rawValue,
    media = image.toHomeMediaObservation(mediaPolicy, alt)
)

private fun HomeContentV2MetaobjectQuery.OnVideo.toHomeVideoResource(
    mediaPolicy: StorefrontMediaPolicy
): StorefrontHomeResource.Video = StorefrontHomeResource.Video(
    key = HomeResourceKey(HomeResourceKind.VIDEO, id),
    contentType = mediaContentType.rawValue,
    sources = sources.mapNotNull { it.toVideoSource(mediaPolicy) },
    observedSourceCount = sources.size,
    preview = previewImage.toHomeMediaObservation(mediaPolicy, alt)
)

internal fun HomeV2ResourcesQuery.Data.toHomeV2ResourceBatch(
    keys: List<HomeResourceKey>,
    mediaPolicy: StorefrontMediaPolicy
): StorefrontResult<HomeResourceBatch> {
    var failureCode = if (nodes.size != keys.size) "HOME_RESOURCE_COUNT_MISMATCH" else null
    val returnedIds = nodes.mapNotNull { it?.id }
    if (failureCode == null && returnedIds.distinct().size != returnedIds.size) {
        failureCode = "DUPLICATE_HOME_RESOURCE"
    }
    val resolutions = mutableListOf<HomeResourceResolution>()
    nodes.forEachIndexed { index, node ->
        if (failureCode != null) return@forEachIndexed
        val requested = keys[index]
        if (node == null) {
            resolutions += HomeResourceResolution(requested, null)
            return@forEachIndexed
        }
        if (node.id != requested.gid) {
            failureCode = "HOME_RESOURCE_ID_MISMATCH"
        } else {
            val resource = node.toRequestedResource(requested, mediaPolicy)
            if (resource == null) {
                failureCode = "HOME_RESOURCE_KIND_MISMATCH"
            } else {
                resolutions += HomeResourceResolution(requested, resource)
            }
        }
    }
    return failureCode?.let { graphQlFailure(it) }
        ?: StorefrontResult.Success(HomeResourceBatch(resolutions))
}

private fun HomeV2ResourcesQuery.Node.toRequestedResource(
    key: HomeResourceKey,
    mediaPolicy: StorefrontMediaPolicy
): StorefrontHomeResource? = when (key.kind) {
    HomeResourceKind.COLLECTION -> onCollection?.toHomeCollectionResource(key, mediaPolicy)
    HomeResourceKind.PRODUCT -> onProduct?.toHomeProductResource(key, mediaPolicy)
    HomeResourceKind.MEDIA_IMAGE -> onMediaImage?.toHomeMediaImageResource(key, mediaPolicy)
    HomeResourceKind.VIDEO -> onVideo?.toHomeVideoResource(key, mediaPolicy)
}

private fun HomeV2ResourcesQuery.OnCollection.toHomeCollectionResource(
    key: HomeResourceKey,
    mediaPolicy: StorefrontMediaPolicy
): StorefrontHomeResource.Collection = StorefrontHomeResource.Collection(
    key = key,
    handle = handle,
    title = title,
    hasProducts = products.nodes.isNotEmpty(),
    media = image?.toHomeMediaObservation(mediaPolicy)
        ?: products.nodes.firstOrNull()?.featuredImage.toHomeMediaObservation(mediaPolicy)
)

private fun HomeV2ResourcesQuery.OnProduct.toHomeProductResource(
    key: HomeResourceKey,
    mediaPolicy: StorefrontMediaPolicy
): StorefrontHomeResource.Product = StorefrontHomeResource.Product(
    key = key,
    handle = handle,
    title = title,
    availableForSale = availableForSale,
    media = featuredImage.toHomeMediaObservation(mediaPolicy),
    money =
        priceRange.minVariantPrice.amount.toBigDecimalOrNull()?.let { amount ->
            HomeMoneyObservation.Accepted(
                StorefrontMoney(amount, priceRange.minVariantPrice.currencyCode.rawValue)
            )
        } ?: HomeMoneyObservation.Malformed
)

private fun HomeV2ResourcesQuery.OnMediaImage.toHomeMediaImageResource(
    key: HomeResourceKey,
    mediaPolicy: StorefrontMediaPolicy
): StorefrontHomeResource.MediaImage = StorefrontHomeResource.MediaImage(
    key = key,
    contentType = mediaContentType.rawValue,
    media = image.toHomeMediaObservation(mediaPolicy, alt)
)

private fun HomeV2ResourcesQuery.OnVideo.toHomeVideoResource(
    key: HomeResourceKey,
    mediaPolicy: StorefrontMediaPolicy
): StorefrontHomeResource.Video = StorefrontHomeResource.Video(
    key = key,
    contentType = mediaContentType.rawValue,
    sources = sources.mapNotNull { it.toVideoSource(mediaPolicy) },
    observedSourceCount = sources.size,
    preview = previewImage.toHomeMediaObservation(mediaPolicy, alt)
)

private data class MediaObservationInput(
    val url: String?,
    val altText: String?,
    val fallbackAlt: String?,
    val width: Int?,
    val height: Int?
)

private fun mediaObservation(input: MediaObservationInput, mediaPolicy: StorefrontMediaPolicy): HomeMediaObservation =
    when {
        input.url == null -> HomeMediaObservation.Absent

        else -> {
            val uri = runCatching { URI(input.url) }.getOrNull()?.takeIf(mediaPolicy::accepts)
            if (uri == null) {
                HomeMediaObservation.Rejected
            } else {
                HomeMediaObservation.Accepted(
                    StorefrontMedia(
                        uri,
                        input.altText?.trim()?.takeIf(String::isNotEmpty)
                            ?: input.fallbackAlt?.trim()?.takeIf(String::isNotEmpty),
                        input.width?.takeIf { it > 0 },
                        input.height?.takeIf { it > 0 }
                    )
                )
            }
        }
    }

private fun HomeContentV2MetaobjectQuery.Image?.toHomeMediaObservation(
    mediaPolicy: StorefrontMediaPolicy
): HomeMediaObservation =
    mediaObservation(MediaObservationInput(this?.url, this?.altText, null, this?.width, this?.height), mediaPolicy)

private fun HomeContentV2MetaobjectQuery.FeaturedImage?.toHomeMediaObservation(
    mediaPolicy: StorefrontMediaPolicy
): HomeMediaObservation =
    mediaObservation(MediaObservationInput(this?.url, this?.altText, null, this?.width, this?.height), mediaPolicy)

private fun HomeContentV2MetaobjectQuery.FeaturedImage1?.toHomeMediaObservation(
    mediaPolicy: StorefrontMediaPolicy
): HomeMediaObservation =
    mediaObservation(MediaObservationInput(this?.url, this?.altText, null, this?.width, this?.height), mediaPolicy)

private fun HomeContentV2MetaobjectQuery.Image1?.toHomeMediaObservation(
    mediaPolicy: StorefrontMediaPolicy,
    fallbackAlt: String?
): HomeMediaObservation = mediaObservation(
    MediaObservationInput(this?.url, this?.altText, fallbackAlt, this?.width, this?.height),
    mediaPolicy
)

private fun HomeContentV2MetaobjectQuery.Image2?.toHomeMediaObservation(
    mediaPolicy: StorefrontMediaPolicy,
    fallbackAlt: String?
): HomeMediaObservation = mediaObservation(
    MediaObservationInput(this?.url, this?.altText, fallbackAlt, this?.width, this?.height),
    mediaPolicy
)

private fun HomeContentV2MetaobjectQuery.PreviewImage?.toHomeMediaObservation(
    mediaPolicy: StorefrontMediaPolicy,
    fallbackAlt: String?
): HomeMediaObservation = mediaObservation(
    MediaObservationInput(this?.url, this?.altText, fallbackAlt, this?.width, this?.height),
    mediaPolicy
)

private fun HomeContentV2MetaobjectQuery.Source.toVideoSource(
    mediaPolicy: StorefrontMediaPolicy
): StorefrontVideoSource? = runCatching { URI(url) }.getOrNull()
    ?.takeIf(mediaPolicy::accepts)
    ?.let { uri -> StorefrontVideoSource(uri, mimeType, format, width, height) }

private fun HomeV2ResourcesQuery.Image?.toHomeMediaObservation(
    mediaPolicy: StorefrontMediaPolicy
): HomeMediaObservation =
    mediaObservation(MediaObservationInput(this?.url, this?.altText, null, this?.width, this?.height), mediaPolicy)

private fun HomeV2ResourcesQuery.FeaturedImage?.toHomeMediaObservation(
    mediaPolicy: StorefrontMediaPolicy
): HomeMediaObservation =
    mediaObservation(MediaObservationInput(this?.url, this?.altText, null, this?.width, this?.height), mediaPolicy)

private fun HomeV2ResourcesQuery.FeaturedImage1?.toHomeMediaObservation(
    mediaPolicy: StorefrontMediaPolicy
): HomeMediaObservation =
    mediaObservation(MediaObservationInput(this?.url, this?.altText, null, this?.width, this?.height), mediaPolicy)

private fun HomeV2ResourcesQuery.Image1?.toHomeMediaObservation(
    mediaPolicy: StorefrontMediaPolicy,
    fallbackAlt: String?
): HomeMediaObservation = mediaObservation(
    MediaObservationInput(this?.url, this?.altText, fallbackAlt, this?.width, this?.height),
    mediaPolicy
)

private fun HomeV2ResourcesQuery.PreviewImage?.toHomeMediaObservation(
    mediaPolicy: StorefrontMediaPolicy,
    fallbackAlt: String?
): HomeMediaObservation = mediaObservation(
    MediaObservationInput(this?.url, this?.altText, fallbackAlt, this?.width, this?.height),
    mediaPolicy
)

private fun HomeV2ResourcesQuery.Source.toVideoSource(mediaPolicy: StorefrontMediaPolicy): StorefrontVideoSource? =
    runCatching { URI(url) }.getOrNull()
        ?.takeIf(mediaPolicy::accepts)
        ?.let { uri -> StorefrontVideoSource(uri, mimeType, format, width, height) }
