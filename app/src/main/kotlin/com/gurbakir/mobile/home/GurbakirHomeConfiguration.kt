package com.gurbakir.mobile.home

import com.gurbakir.mobile.R

internal object GurbakirHomeConfiguration {
    val value =
        HomeConfiguration(
            productRange =
                HomeProductRangeConfiguration(
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
            featuredProduct =
                HomeFeaturedProductConfiguration(
                    stableId = "HOME_FEATURED_PRODUCT",
                    titleResourceId = R.string.home_featured_product_title,
                    handle = "bakir-tava-ve-sahan-el-dovmesi-cift-pirinc-kulplu"
                )
        )
}
