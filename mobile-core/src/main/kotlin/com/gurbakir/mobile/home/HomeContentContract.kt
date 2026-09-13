package com.gurbakir.mobile.home

import com.gurbakir.storefront.HomeResourceKey

sealed interface RemoteHomeSection {
    val sectionGid: String
    val type: String
    val handle: String
    val title: String

    data class CollectionGrid(
        override val sectionGid: String,
        override val type: String,
        override val handle: String,
        override val title: String,
        val collections: List<HomeResourceKey>
    ) : RemoteHomeSection

    data class FeaturedProduct(
        override val sectionGid: String,
        override val type: String,
        override val handle: String,
        override val title: String,
        val product: HomeResourceKey
    ) : RemoteHomeSection
}

data class RemoteHomeSnapshot(
    val rootGid: String,
    val rootType: String,
    val rootHandle: String,
    val rootUpdatedAt: String,
    val contentVersion: Int,
    val sections: List<RemoteHomeSection>
)
