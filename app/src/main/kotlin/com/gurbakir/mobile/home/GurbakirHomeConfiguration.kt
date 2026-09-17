package com.gurbakir.mobile.home

import com.gurbakir.mobile.BuildConfig
import com.gurbakir.mobile.R
import com.gurbakir.storefront.HomeContentContractId
import com.gurbakir.storefront.HomeDocumentSelector

internal object GurbakirHomeConfiguration {
    val value =
        HomeConfiguration(
            remoteSource =
                BuildConfig.HOME_CONTENT_ROOT_HANDLE.takeIf(String::isNotBlank)?.let { handle ->
                    val contractId =
                        HomeContentContractId.fromTuple(
                            BuildConfig.HOME_CONTENT_ROOT_TYPE,
                            BuildConfig.HOME_CONTENT_SCHEMA_VERSION,
                            BuildConfig.HOME_DEFINITION_CONTRACT
                        )
                    HomeRemoteSource.ShopifyMetaobject(
                        HomeDocumentSelector(BuildConfig.HOME_CONTENT_ROOT_TYPE, handle),
                        contractId
                    )
                } ?: HomeRemoteSource.Disabled,
            packagedFallback =
                HomePackagedFallback(
                    productRange = HomeProductRangeConfiguration(
                        stableId = "HOME_PRODUCT_RANGE",
                        titleResourceId = R.string.home_product_range_title,
                        itemLimit = 5,
                        sources =
                            listOf(
                                HomeCollectionSource(
                                    "HOME_RANGE_DRINKWARE",
                                    "bardaklar",
                                    R.string.home_collection_drinkware
                                ),
                                HomeCollectionSource(
                                    "HOME_RANGE_COFFEE_POTS",
                                    "cezveler",
                                    R.string.home_collection_coffee_pots
                                ),
                                HomeCollectionSource(
                                    "HOME_RANGE_PANS",
                                    "tavalar-sahanlar",
                                    R.string.home_collection_pans
                                ),
                                HomeCollectionSource("HOME_RANGE_POTS", "tencereler", R.string.home_collection_pots),
                                HomeCollectionSource(
                                    "HOME_RANGE_SPECIAL",
                                    "ozel-urunlerimiz",
                                    R.string.home_collection_special
                                )
                            )
                    ),
                    featuredProduct = HomeFeaturedProductConfiguration(
                        stableId = "HOME_FEATURED_PRODUCT",
                        titleResourceId = R.string.home_featured_product_title,
                        handle = "bakir-tava-ve-sahan-el-dovmesi-cift-pirinc-kulplu"
                    )
                )
        )
}
