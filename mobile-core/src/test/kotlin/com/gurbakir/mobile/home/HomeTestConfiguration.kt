package com.gurbakir.mobile.home

internal val homeTestPackagedFallback = HomePackagedFallback(
    productRange = HomeProductRangeConfiguration(
        stableId = "TEST_RANGE",
        titleResourceId = 101,
        itemLimit = 5,
        sources = listOf("alpha", "beta", "gamma", "delta", "epsilon").mapIndexed { index, handle ->
            HomeCollectionSource("TEST_$index", handle, 102 + index)
        }
    ),
    featuredProduct = HomeFeaturedProductConfiguration("TEST_FEATURED", 110, "featured-fixture")
)

internal val homeTestConfiguration = HomeConfiguration(
    remoteSource = HomeRemoteSource.Disabled,
    packagedFallback = homeTestPackagedFallback
)
