package com.gurbakir.mobile.catalog

import com.gurbakir.storefront.CatalogDiscoveryBounds
import com.gurbakir.storefront.CatalogDiscoveryCollection
import com.gurbakir.storefront.CatalogDiscoveryMenu
import com.gurbakir.storefront.CatalogDiscoveryNode
import com.gurbakir.storefront.CatalogDiscoveryRequest
import com.gurbakir.storefront.CatalogDiscoveryTarget
import com.gurbakir.storefront.CollectionCatalogPage
import com.gurbakir.storefront.CollectionCatalogPageRequest
import com.gurbakir.storefront.CollectionCatalogSort
import com.gurbakir.storefront.Cursor
import com.gurbakir.storefront.StorefrontCatalogGateway
import com.gurbakir.storefront.StorefrontFailure
import com.gurbakir.storefront.StorefrontResult
import javax.inject.Inject

interface CatalogRepository {
    suspend fun loadCategories(): CatalogCategoryLoad

    suspend fun loadCollectionPage(
        handle: String,
        after: Cursor?,
        sort: CollectionCatalogSort,
        productTypes: Set<String>
    ): CatalogPageLoad
}

class DefaultCatalogRepository
@Inject
constructor(
    private val catalogGateway: StorefrontCatalogGateway,
    private val configuration: CatalogConfiguration
) : CatalogRepository {
    override suspend fun loadCategories(): CatalogCategoryLoad = when (
        val result =
            catalogGateway.loadCatalogDiscovery(
                CatalogDiscoveryRequest(configuration.menuHandle)
            )
    ) {
        is StorefrontResult.Success ->
            result.value?.toCatalogCategoryLoad() ?: catalogConfigurationError()

        is StorefrontResult.Failure ->
            CatalogCategoryLoad.Error(listOf(result.error).toCatalogFailure())
    }

    override suspend fun loadCollectionPage(
        handle: String,
        after: Cursor?,
        sort: CollectionCatalogSort,
        productTypes: Set<String>
    ): CatalogPageLoad = when (
        val result =
            catalogGateway.loadCollectionCatalogPage(
                CollectionCatalogPageRequest(
                    handle = handle,
                    after = after,
                    sort = sort,
                    productTypes = productTypes
                )
            )
    ) {
        is StorefrontResult.Success ->
            result.value?.let(CatalogPageLoad::Content) ?: CatalogPageLoad.NotFound

        is StorefrontResult.Failure -> CatalogPageLoad.Error(listOf(result.error).toCatalogFailure())
    }
}

private fun CatalogDiscoveryMenu.toCatalogCategoryLoad(): CatalogCategoryLoad = if (!items.haveValidCatalogShape()) {
    catalogConfigurationError()
} else {
    CatalogDiscoveryProjection().also { projection -> items.forEach(projection::visit) }.toCategoryLoad()
}

private fun CatalogDiscoveryProjection.toCategoryLoad(): CatalogCategoryLoad {
    if (outputLimitExceeded) return catalogConfigurationError()
    val partialFailure =
        if (hasConfigurationProblem) {
            CatalogLoadFailure(CatalogLoadFailureCategory.CONFIGURATION, retryable = false)
        } else {
            null
        }
    return when {
        items.isNotEmpty() -> CatalogCategoryLoad.Content(items, partialFailure)
        partialFailure != null -> CatalogCategoryLoad.Error(partialFailure)
        else -> CatalogCategoryLoad.Empty
    }
}

private fun List<CatalogDiscoveryNode>.haveValidCatalogShape(): Boolean {
    var count = 0

    fun validate(nodes: List<CatalogDiscoveryNode>, depth: Int): Boolean {
        if (nodes.isNotEmpty() && depth > CatalogDiscoveryBounds.MAX_DEPTH) return false
        return nodes.all { node ->
            count += 1
            count <= CatalogDiscoveryBounds.MAX_NODES && validate(node.children, depth + 1)
        }
    }

    return validate(this, depth = 1)
}

private class CatalogDiscoveryProjection {
    val items = mutableListOf<CatalogCategoryItem>()
    var hasConfigurationProblem = false
        private set
    var outputLimitExceeded = false
        private set

    private val seenItemIds = mutableSetOf<String>()
    private val acceptedHandleByCollectionId = mutableMapOf<String, String>()
    private val acceptedCollectionIdByHandle = mutableMapOf<String, String>()

    fun visit(node: CatalogDiscoveryNode) {
        val validItemId = node.id.isValidCatalogIdentity()
        val firstItemOccurrence = validItemId && seenItemIds.add(node.id)
        if (!validItemId || !firstItemOccurrence) {
            hasConfigurationProblem = true
        } else {
            projectAction(node)
        }
        node.children.forEach(::visit)
    }

    private fun projectAction(node: CatalogDiscoveryNode) {
        when (val target = node.target) {
            CatalogDiscoveryTarget.Malformed -> hasConfigurationProblem = true

            CatalogDiscoveryTarget.UnavailableCollection,
            CatalogDiscoveryTarget.Unsupported -> Unit

            is CatalogDiscoveryTarget.Collection -> projectCollection(node, target.collection)
        }
    }

    private fun projectCollection(node: CatalogDiscoveryNode, collection: CatalogDiscoveryCollection) {
        val title = node.title.trim()
        when {
            node.hasCollectionFilters -> hasConfigurationProblem = true

            !title.isValidCatalogTitle() ||
                !collection.id.isValidCatalogIdentity() ||
                !CatalogDiscoveryRequest(collection.handle).isValid() -> hasConfigurationProblem = true

            else -> projectEligibleCollection(node, title, collection)
        }
    }

    private fun projectEligibleCollection(
        node: CatalogDiscoveryNode,
        title: String,
        collection: CatalogDiscoveryCollection
    ) {
        val existingHandle = acceptedHandleByCollectionId[collection.id]
        val existingId = acceptedCollectionIdByHandle[collection.handle]
        when {
            existingHandle != null || existingId != null ->
                hasConfigurationProblem = hasConfigurationProblem ||
                    hasIdentityConflict(existingHandle, existingId, collection)

            items.size >= MAX_ACCEPTED_CATEGORIES -> outputLimitExceeded = true

            else -> accept(node, title, collection)
        }
    }

    private fun accept(node: CatalogDiscoveryNode, title: String, collection: CatalogDiscoveryCollection) {
        acceptedHandleByCollectionId[collection.id] = collection.handle
        acceptedCollectionIdByHandle[collection.handle] = collection.id
        items += CatalogCategoryItem(node.id, title, collection)
    }

    private fun hasIdentityConflict(
        existingHandle: String?,
        existingId: String?,
        collection: CatalogDiscoveryCollection
    ): Boolean = (existingHandle != null && existingHandle != collection.handle) ||
        (existingId != null && existingId != collection.id)
}

private fun String.isValidCatalogIdentity(): Boolean = isNotBlank() &&
    length <= MAX_CATALOG_TEXT_LENGTH &&
    this == trim() &&
    none(Char::isISOControl)

private fun String.isValidCatalogTitle(): Boolean =
    isNotBlank() && length <= MAX_CATALOG_TEXT_LENGTH && none(Char::isISOControl)

private fun catalogConfigurationError(): CatalogCategoryLoad.Error = CatalogCategoryLoad.Error(
    CatalogLoadFailure(CatalogLoadFailureCategory.CONFIGURATION, retryable = false)
)

internal const val MAX_ACCEPTED_CATEGORIES = 24
private const val MAX_CATALOG_TEXT_LENGTH = 255

data class CatalogCategoryItem(val stableId: String, val title: String, val collection: CatalogDiscoveryCollection)

sealed interface CatalogCategoryLoad {
    data class Content(val items: List<CatalogCategoryItem>, val partialFailure: CatalogLoadFailure?) :
        CatalogCategoryLoad

    data class Error(val failure: CatalogLoadFailure) : CatalogCategoryLoad

    data object Empty : CatalogCategoryLoad
}

sealed interface CatalogPageLoad {
    data class Content(val page: CollectionCatalogPage) : CatalogPageLoad

    data class Error(val failure: CatalogLoadFailure) : CatalogPageLoad

    data object NotFound : CatalogPageLoad
}

data class CatalogLoadFailure(val category: CatalogLoadFailureCategory, val retryable: Boolean)

enum class CatalogLoadFailureCategory {
    CONNECTION,
    CONFIGURATION,
    SERVICE
}

private fun List<StorefrontFailure>.toCatalogFailure(): CatalogLoadFailure {
    val category =
        when {
            any { it is StorefrontFailure.Configuration } -> CatalogLoadFailureCategory.CONFIGURATION
            all { it is StorefrontFailure.Transport } -> CatalogLoadFailureCategory.CONNECTION
            else -> CatalogLoadFailureCategory.SERVICE
        }
    return CatalogLoadFailure(
        category = category,
        retryable = isNotEmpty() && all { it is StorefrontFailure.Transport && it.retryable }
    )
}
