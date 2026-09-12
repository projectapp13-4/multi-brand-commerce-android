package com.gurbakir.storefront

private val CATALOG_DISCOVERY_HANDLE_PATTERN = Regex("^[a-z0-9][a-z0-9-]{0,254}$")

data class CatalogDiscoveryRequest(val menuHandle: String) {
    fun isValid(): Boolean = CATALOG_DISCOVERY_HANDLE_PATTERN.matches(menuHandle)
}

object CatalogDiscoveryBounds {
    const val MAX_DEPTH = 3
    const val MAX_NODES = 64
}

data class CatalogDiscoveryMenu(val id: String, val handle: String, val items: List<CatalogDiscoveryNode>)

data class CatalogDiscoveryNode(
    val id: String,
    val title: String,
    val hasCollectionFilters: Boolean,
    val target: CatalogDiscoveryTarget,
    val children: List<CatalogDiscoveryNode>
)

sealed interface CatalogDiscoveryTarget {
    data class Collection(val collection: CatalogDiscoveryCollection) : CatalogDiscoveryTarget

    data object UnavailableCollection : CatalogDiscoveryTarget

    data object Unsupported : CatalogDiscoveryTarget

    data object Malformed : CatalogDiscoveryTarget
}

data class CatalogDiscoveryCollection(
    val id: String,
    val handle: String,
    val sourceTitle: String,
    val media: StorefrontMedia
)
