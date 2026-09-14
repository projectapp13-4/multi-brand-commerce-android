package com.example.gate2synthetic.config

import com.example.gate2synthetic.R
import com.example.gate2synthetic.brand.Gate2SyntheticBrand
import com.gurbakir.foundation.config.AppConfiguration
import com.gurbakir.foundation.config.ApplicationCapabilities
import com.gurbakir.foundation.config.ApplicationComposition
import com.gurbakir.foundation.config.CapabilityState
import com.gurbakir.foundation.config.ControlledPublicToken
import com.gurbakir.foundation.config.CustomerAccountCapability
import com.gurbakir.foundation.config.EnvironmentId
import com.gurbakir.foundation.config.LocalizationPolicy
import com.gurbakir.foundation.config.MarketConfiguration
import com.gurbakir.foundation.config.PrimaryNavigationDestination
import com.gurbakir.foundation.config.PrimaryNavigationSpec
import com.gurbakir.foundation.config.ProtectedPersistenceConfiguration
import com.gurbakir.foundation.config.ProtectedStoreIdentity
import com.gurbakir.foundation.config.StorefrontConfiguration
import com.gurbakir.mobile.address.AddressPostalCodePolicy
import com.gurbakir.mobile.address.AddressTerritoryPolicy
import com.gurbakir.mobile.address.PostalCodeInputMode
import com.gurbakir.mobile.catalog.CatalogConfiguration
import com.gurbakir.mobile.home.HomeCollectionSource
import com.gurbakir.mobile.home.HomeConfiguration
import com.gurbakir.mobile.home.HomeFeaturedProductConfiguration
import com.gurbakir.mobile.home.HomePackagedFallback
import com.gurbakir.mobile.home.HomeProductRangeConfiguration
import com.gurbakir.mobile.home.HomeRemoteSource
import com.gurbakir.mobile.navigation.MobileDeepLinkConfiguration
import com.gurbakir.mobile.search.SearchHistoryNormalizationPolicy
import com.gurbakir.mobile.search.SearchHistoryPartition
import com.gurbakir.mobile.update.UpdatePolicyRefreshResult
import com.gurbakir.mobile.update.UpdatePolicyRemoteGateway
import com.gurbakir.mobile.wishlist.WishlistPartition

internal object Gate2SyntheticConfiguration {
    private val capabilities = ApplicationCapabilities(
        CapabilityState.ENABLED,
        CapabilityState.DISABLED,
        CustomerAccountCapability.Disabled
    )
    val app: AppConfiguration =
        AppConfiguration(
            brand = Gate2SyntheticBrand.configuration,
            environment = EnvironmentId.DEVELOPMENT,
            localization = LocalizationPolicy(defaultLocaleTag = "en-CA", supportedLocaleTags = listOf("en-CA")),
            market = MarketConfiguration(id = "ZZ", countryCode = "ZZ", currencyCode = "XTS"),
            protectedPersistence =
                ProtectedPersistenceConfiguration(
                    cart =
                        ProtectedStoreIdentity(
                            preferencesName = "gate2_synthetic_secure_cart_development",
                            keyAlias = "gate2.synthetic.cart.development.v1"
                        ),
                    customerSession =
                        ProtectedStoreIdentity(
                            preferencesName = "gate2_synthetic_secure_customer_session_development",
                            keyAlias = "gate2.synthetic.customer.session.development.v1"
                        )
                ),
            storefront =
                StorefrontConfiguration(
                    domain = "storefront.gate2.invalid",
                    apiVersion = "2026-07",
                    publicToken = ControlledPublicToken.from("")
                ),
            applicationComposition = ApplicationComposition(
                capabilities,
                PrimaryNavigationSpec.create(
                    listOf(
                        PrimaryNavigationDestination.SEARCH,
                        PrimaryNavigationDestination.HOME,
                        PrimaryNavigationDestination.CATEGORIES
                    ),
                    capabilities
                )
            )
        )

    val home: HomeConfiguration =
        HomeConfiguration(
            remoteSource = HomeRemoteSource.Disabled,
            packagedFallback =
                HomePackagedFallback(
                    productRange = HomeProductRangeConfiguration(
                        stableId = "SYNTHETIC_HOME_RANGE",
                        titleResourceId = R.string.home_product_range_title,
                        itemLimit = 3,
                        sources =
                            listOf(
                                HomeCollectionSource(
                                    stableId = "SYNTHETIC_HOME_ALPHA",
                                    handle = "synthetic-alpha",
                                    labelResourceId = R.string.synthetic_alpha
                                ),
                                HomeCollectionSource(
                                    stableId = "SYNTHETIC_HOME_BETA",
                                    handle = "synthetic-beta",
                                    labelResourceId = R.string.synthetic_beta
                                )
                            )
                    ),
                    featuredProduct = HomeFeaturedProductConfiguration(
                        stableId = "SYNTHETIC_HOME_FEATURED",
                        titleResourceId = R.string.synthetic_featured,
                        handle = "synthetic-featured-product"
                    )
                )
        )

    val catalog: CatalogConfiguration =
        CatalogConfiguration(
            menuHandle = "synthetic-catalog-menu"
        )

    val addressPolicy: AddressTerritoryPolicy =
        AddressTerritoryPolicy(
            supportedTerritoryCode = "ZZ",
            postalCodeInputMode = PostalCodeInputMode.TEXT,
            postalCodePolicy = AddressPostalCodePolicy(Regex("^AB-[0-9]{4}$")::matches)
        )

    val deepLinks: MobileDeepLinkConfiguration =
        MobileDeepLinkConfiguration(
            collectionBasePath = "https://links.gate2.invalid/collections",
            productBasePath = "https://links.gate2.invalid/apps/mobile/products"
        )

    val searchPartition: SearchHistoryPartition = SearchHistoryPartition(app.environment.name, app.market.id)

    val searchNormalizationPolicy: SearchHistoryNormalizationPolicy = SearchHistoryNormalizationPolicy("en-CA")

    val wishlistPartition: WishlistPartition = WishlistPartition(app.environment.name, app.market.id)

    val updatePolicyGateway: UpdatePolicyRemoteGateway =
        UpdatePolicyRemoteGateway { UpdatePolicyRefreshResult.LocalDefaults }
}
