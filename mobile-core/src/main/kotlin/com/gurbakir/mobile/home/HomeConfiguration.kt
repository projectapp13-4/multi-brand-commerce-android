package com.gurbakir.mobile.home

import androidx.annotation.StringRes
import com.gurbakir.storefront.HomeDocumentSelector

const val HOME_EDITORIAL_TTL_MILLIS = 86_400_000L

data class HomeCollectionSource(val stableId: String, val handle: String, @param:StringRes val labelResourceId: Int)

data class HomeProductRangeConfiguration(
    val stableId: String,
    @param:StringRes val titleResourceId: Int,
    val itemLimit: Int,
    val sources: List<HomeCollectionSource>
)

data class HomeFeaturedProductConfiguration(
    val stableId: String,
    @param:StringRes val titleResourceId: Int,
    val handle: String
)

sealed interface HomeRemoteSource {
    data object Disabled : HomeRemoteSource

    data class ShopifyMetaobject(
        val selector: HomeDocumentSelector,
        val supportedContentVersion: Int = 1,
        val ttlMillis: Long = HOME_EDITORIAL_TTL_MILLIS
    ) : HomeRemoteSource
}

data class HomePackagedFallback(
    val productRange: HomeProductRangeConfiguration,
    val featuredProduct: HomeFeaturedProductConfiguration
)

data class HomeConfiguration(val remoteSource: HomeRemoteSource, val packagedFallback: HomePackagedFallback)

sealed interface HomeText {
    data class Remote(val value: String) : HomeText

    data class Packaged(@param:StringRes val resourceId: Int) : HomeText
}
