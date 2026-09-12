package com.gurbakir.mobile.home

import androidx.annotation.StringRes

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

data class HomeConfiguration(
    val productRange: HomeProductRangeConfiguration,
    val featuredProduct: HomeFeaturedProductConfiguration
)
