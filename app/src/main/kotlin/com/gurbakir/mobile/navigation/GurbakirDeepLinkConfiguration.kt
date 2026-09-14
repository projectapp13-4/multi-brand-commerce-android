package com.gurbakir.mobile.navigation

import com.gurbakir.mobile.BuildConfig

internal val GurbakirDeepLinkConfiguration = MobileDeepLinkConfiguration(
    collectionBasePath =
        BuildConfig.COLLECTION_APP_LINK_ORIGIN + BuildConfig.COLLECTION_APP_LINK_PATH_PREFIX.dropLast(1),
    productBasePath = BuildConfig.PRODUCT_APP_LINK_ORIGIN + BuildConfig.PRODUCT_APP_LINK_PATH_PREFIX.dropLast(1)
)
