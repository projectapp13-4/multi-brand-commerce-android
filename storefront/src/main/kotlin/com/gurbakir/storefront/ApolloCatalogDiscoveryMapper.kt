@file:Suppress("TooManyFunctions")

package com.gurbakir.storefront

import com.gurbakir.storefront.graphql.CatalogDiscoveryMenuQuery
import com.gurbakir.storefront.graphql.fragment.CatalogDiscoveryItemFields
import com.gurbakir.storefront.graphql.fragment.CatalogDiscoveryLevel1
import com.gurbakir.storefront.graphql.fragment.CatalogDiscoveryLevel2
import com.gurbakir.storefront.graphql.fragment.CatalogDiscoveryLevel3
import com.gurbakir.storefront.graphql.type.MenuItemType

internal fun CatalogDiscoveryMenuQuery.Data.toCatalogDiscoveryResult(
    request: CatalogDiscoveryRequest,
    mediaPolicy: StorefrontMediaPolicy
): StorefrontResult<CatalogDiscoveryMenu?> = menu?.toCatalogDiscoveryResult(request, mediaPolicy)
    ?: StorefrontResult.Success(null)

private fun CatalogDiscoveryMenuQuery.Menu.toCatalogDiscoveryResult(
    request: CatalogDiscoveryRequest,
    mediaPolicy: StorefrontMediaPolicy
): StorefrontResult<CatalogDiscoveryMenu?> {
    val issue = when {
        !id.isValidCatalogIdentity() || handle != request.menuHandle -> "catalog.discovery.root"
        else -> CatalogDiscoveryShapeInspector().inspect(items)
    }
    return if (issue != null) {
        catalogDiscoveryConfigurationFailure(issue)
    } else {
        StorefrontResult.Success(
            CatalogDiscoveryMenu(
                id = id,
                handle = handle,
                items = items.map { item -> item.catalogDiscoveryLevel1.toDiscoveryNode(mediaPolicy) }
            )
        )
    }
}

private class CatalogDiscoveryShapeInspector {
    private var nodeCount = 0

    fun inspect(nodes: List<CatalogDiscoveryMenuQuery.Item>): String? = inspectNodes(nodes) { first ->
        inspectLevel2(first.catalogDiscoveryLevel1.items)
    }

    private fun inspectLevel2(nodes: List<CatalogDiscoveryLevel1.Item>): String? = inspectNodes(nodes) { second ->
        inspectLevel3(second.catalogDiscoveryLevel2.items)
    }

    private fun inspectLevel3(nodes: List<CatalogDiscoveryLevel2.Item>): String? = inspectNodes(nodes) { third ->
        if (third.catalogDiscoveryLevel3.items.isEmpty()) null else "catalog.discovery.depth"
    }

    @Suppress("ReturnCount")
    private inline fun <T> inspectNodes(nodes: List<T>, inspectChildren: (T) -> String?): String? {
        for (node in nodes) {
            nodeCount += 1
            if (nodeCount > CatalogDiscoveryBounds.MAX_NODES) return "catalog.discovery.nodes"
            val issue = inspectChildren(node)
            if (issue != null) return issue
        }
        return null
    }
}

private fun CatalogDiscoveryLevel1.toDiscoveryNode(mediaPolicy: StorefrontMediaPolicy): CatalogDiscoveryNode =
    catalogDiscoveryItemFields.toDiscoveryNode(
        mediaPolicy,
        items.map { child -> child.catalogDiscoveryLevel2.toDiscoveryNode(mediaPolicy) }
    )

private fun CatalogDiscoveryLevel2.toDiscoveryNode(mediaPolicy: StorefrontMediaPolicy): CatalogDiscoveryNode =
    catalogDiscoveryItemFields.toDiscoveryNode(
        mediaPolicy,
        items.map { child -> child.catalogDiscoveryLevel3.toDiscoveryNode(mediaPolicy) }
    )

private fun CatalogDiscoveryLevel3.toDiscoveryNode(mediaPolicy: StorefrontMediaPolicy): CatalogDiscoveryNode =
    catalogDiscoveryItemFields.toDiscoveryNode(mediaPolicy, emptyList())

private fun CatalogDiscoveryItemFields.toDiscoveryNode(
    mediaPolicy: StorefrontMediaPolicy,
    children: List<CatalogDiscoveryNode>
): CatalogDiscoveryNode = CatalogDiscoveryNode(
    id = id,
    title = title,
    hasCollectionFilters = tags.isNotEmpty(),
    target = toDiscoveryTarget(mediaPolicy),
    children = children
)

private fun CatalogDiscoveryItemFields.toDiscoveryTarget(mediaPolicy: StorefrontMediaPolicy): CatalogDiscoveryTarget {
    val collection = resource?.onCollection
    return when {
        type == MenuItemType.UNKNOWN__ -> CatalogDiscoveryTarget.Unsupported
        type == MenuItemType.COLLECTION && resource == null -> CatalogDiscoveryTarget.UnavailableCollection
        type == MenuItemType.COLLECTION && collection == null -> CatalogDiscoveryTarget.Malformed
        type == MenuItemType.COLLECTION -> checkNotNull(collection).toDiscoveryTarget(mediaPolicy)
        collection != null -> CatalogDiscoveryTarget.Malformed
        else -> CatalogDiscoveryTarget.Unsupported
    }
}

private fun CatalogDiscoveryItemFields.OnCollection.toDiscoveryTarget(
    mediaPolicy: StorefrontMediaPolicy
): CatalogDiscoveryTarget = when {
    !id.isValidCatalogIdentity() || !CatalogDiscoveryRequest(handle).isValid() -> CatalogDiscoveryTarget.Malformed
    products.nodes.isEmpty() -> CatalogDiscoveryTarget.UnavailableCollection
    else -> toAvailableDiscoveryTarget(products.nodes.first(), mediaPolicy)
}

private fun CatalogDiscoveryItemFields.OnCollection.toAvailableDiscoveryTarget(
    firstProduct: CatalogDiscoveryItemFields.Node,
    mediaPolicy: StorefrontMediaPolicy
): CatalogDiscoveryTarget {
    val media = if (image == null) {
        firstProduct.featuredImage?.homeImageFields?.toStorefrontMedia(mediaPolicy)
    } else {
        image.homeImageFields.toStorefrontMedia(mediaPolicy)
    }
    return media?.let {
        CatalogDiscoveryTarget.Collection(
            CatalogDiscoveryCollection(
                id = id,
                handle = handle,
                sourceTitle = title,
                media = it
            )
        )
    } ?: CatalogDiscoveryTarget.UnavailableCollection
}

private fun String.isValidCatalogIdentity(): Boolean = isNotBlank() &&
    length <= MAX_CATALOG_IDENTITY_LENGTH &&
    this == trim() &&
    none(Char::isISOControl)

internal fun catalogDiscoveryConfigurationFailure(key: String): StorefrontResult.Failure =
    StorefrontResult.Failure(StorefrontFailure.Configuration(setOf(key)))

private const val MAX_CATALOG_IDENTITY_LENGTH = 255
