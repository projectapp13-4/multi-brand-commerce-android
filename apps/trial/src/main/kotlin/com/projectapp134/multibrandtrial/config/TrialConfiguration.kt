package com.projectapp134.multibrandtrial.config

import com.gurbakir.foundation.config.AppConfiguration
import com.gurbakir.foundation.config.ApplicationCapabilities
import com.gurbakir.foundation.config.ApplicationComposition
import com.gurbakir.foundation.config.CapabilityState
import com.gurbakir.foundation.config.ControlledPublicToken
import com.gurbakir.foundation.config.CustomerAccountCapability
import com.gurbakir.foundation.config.CustomerAccountConfiguration
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
import com.gurbakir.mobile.wishlist.WishlistPartition
import com.gurbakir.storefront.HomeDocumentSelector
import com.projectapp134.multibrandtrial.BuildConfig
import com.projectapp134.multibrandtrial.R
import com.projectapp134.multibrandtrial.brand.TrialBrand
import com.projectapp134.multibrandtrial.legal.TrialDeletionContract
import com.projectapp134.multibrandtrial.legal.TrialLegalContract
import com.projectapp134.multibrandtrial.legal.TrialLegalRole

private val TRIAL_POSTAL_CODE = Regex("^[0-9]{5}$")

internal object TrialConfiguration {
    private val capabilities =
        ApplicationCapabilities(
            search = CapabilityState.ENABLED,
            wishlist = CapabilityState.ENABLED,
            customerAccount =
                CustomerAccountCapability.Enabled(
                    CustomerAccountConfiguration(
                        clientId = BuildConfig.CUSTOMER_ACCOUNT_CLIENT_ID,
                        issuer = BuildConfig.CUSTOMER_ACCOUNT_ISSUER,
                        authorizationEndpoint = BuildConfig.CUSTOMER_ACCOUNT_AUTH_ENDPOINT,
                        tokenEndpoint = BuildConfig.CUSTOMER_ACCOUNT_TOKEN_ENDPOINT,
                        logoutEndpoint = BuildConfig.CUSTOMER_ACCOUNT_LOGOUT_ENDPOINT,
                        graphqlEndpoint = BuildConfig.CUSTOMER_ACCOUNT_GRAPHQL_ENDPOINT,
                        redirectUri = BuildConfig.CUSTOMER_ACCOUNT_REDIRECT_URI,
                        userAgent = BuildConfig.CUSTOMER_ACCOUNT_USER_AGENT,
                        scopes =
                            BuildConfig.CUSTOMER_ACCOUNT_SCOPES
                                .split(' ')
                                .filter(String::isNotBlank)
                                .toSet()
                    )
                )
        )

    val app: AppConfiguration =
        AppConfiguration(
            brand = TrialBrand.configuration,
            environment = resolveEnvironment(BuildConfig.ENVIRONMENT_ID),
            localization =
                LocalizationPolicy(
                    defaultLocaleTag = BuildConfig.DEFAULT_LOCALE,
                    supportedLocaleTags = BuildConfig.SUPPORTED_LOCALES.split(',')
                ),
            market =
                MarketConfiguration(
                    id = BuildConfig.MARKET_ID,
                    countryCode = BuildConfig.MARKET_COUNTRY_CODE,
                    currencyCode = BuildConfig.MARKET_CURRENCY_CODE
                ),
            protectedPersistence =
                ProtectedPersistenceConfiguration(
                    cart = ProtectedStoreIdentity(BuildConfig.CART_PREFERENCES, BuildConfig.CART_KEY_ALIAS),
                    customerSession =
                        ProtectedStoreIdentity(BuildConfig.CUSTOMER_PREFERENCES, BuildConfig.CUSTOMER_KEY_ALIAS)
                ),
            storefront =
                StorefrontConfiguration(
                    domain = BuildConfig.STOREFRONT_DOMAIN,
                    apiVersion = BuildConfig.STOREFRONT_API_VERSION,
                    publicToken = ControlledPublicToken.from(BuildConfig.STOREFRONT_PUBLIC_TOKEN)
                ),
            applicationComposition =
                ApplicationComposition(
                    capabilities = capabilities,
                    primaryNavigation =
                        PrimaryNavigationSpec.create(
                            listOf(
                                PrimaryNavigationDestination.HOME,
                                PrimaryNavigationDestination.CATEGORIES,
                                PrimaryNavigationDestination.SEARCH,
                                PrimaryNavigationDestination.WISHLIST,
                                PrimaryNavigationDestination.ACCOUNT
                            ),
                            capabilities
                        )
                )
        )

    val home: HomeConfiguration =
        HomeConfiguration(
            remoteSource =
                HomeRemoteSource.ShopifyMetaobject(
                    selector = HomeDocumentSelector("mobile_home", BuildConfig.HOME_CONTENT_ROOT_HANDLE),
                    supportedContentVersion = 1
                ),
            packagedFallback =
                HomePackagedFallback(
                    productRange =
                        HomeProductRangeConfiguration(
                            stableId = "TRIAL_HOME_PRODUCT_RANGE",
                            titleResourceId = R.string.home_product_range_title,
                            itemLimit = 5,
                            sources =
                                listOf(
                                    HomeCollectionSource(
                                        stableId = "TRIAL_HOME_PILOT_COLLECTION",
                                        handle = "pilot-koleksiyonu",
                                        labelResourceId = R.string.trial_home_pilot_collection
                                    )
                                )
                        ),
                    featuredProduct =
                        HomeFeaturedProductConfiguration(
                            stableId = "TRIAL_HOME_PILOT_PRODUCT",
                            titleResourceId = R.string.trial_home_featured_product,
                            handle = "pilot-urun"
                        )
                )
        )

    val catalog: CatalogConfiguration = CatalogConfiguration(BuildConfig.CATALOG_MENU_HANDLE)

    val addressPolicy: AddressTerritoryPolicy =
        AddressTerritoryPolicy(
            supportedTerritoryCode = BuildConfig.SUPPORTED_TERRITORY,
            postalCodeInputMode = PostalCodeInputMode.NUMERIC,
            postalCodePolicy = AddressPostalCodePolicy(TRIAL_POSTAL_CODE::matches)
        )

    val deepLinks: MobileDeepLinkConfiguration =
        MobileDeepLinkConfiguration(
            collectionBasePath =
                BuildConfig.COLLECTION_APP_LINK_ORIGIN + BuildConfig.COLLECTION_APP_LINK_PATH_PREFIX.dropLast(1),
            productBasePath =
                BuildConfig.PRODUCT_APP_LINK_ORIGIN + BuildConfig.PRODUCT_APP_LINK_PATH_PREFIX.dropLast(1)
        )

    val searchPartition: SearchHistoryPartition = SearchHistoryPartition(app.environment.name, app.market.id)
    val searchNormalizationPolicy: SearchHistoryNormalizationPolicy =
        SearchHistoryNormalizationPolicy(BuildConfig.SEARCH_NORMALIZATION_LOCALE)
    val wishlistPartition: WishlistPartition = WishlistPartition(app.environment.name, app.market.id)
    val legal: TrialLegalContract =
        TrialLegalContract.developmentVerified(
            origin = BuildConfig.LEGAL_SUPPORT_ORIGIN,
            paths =
                mapOf(
                    TrialLegalRole.SUPPORT to BuildConfig.LEGAL_SUPPORT_PATH_SUPPORT,
                    TrialLegalRole.PRIVACY to BuildConfig.LEGAL_SUPPORT_PATH_PRIVACY,
                    TrialLegalRole.TERMS to BuildConfig.LEGAL_SUPPORT_PATH_TERMS,
                    TrialLegalRole.SHIPPING to BuildConfig.LEGAL_SUPPORT_PATH_SHIPPING,
                    TrialLegalRole.RETURNS to BuildConfig.LEGAL_SUPPORT_PATH_RETURNS,
                    TrialLegalRole.LEGAL_NOTICE to BuildConfig.LEGAL_SUPPORT_PATH_LEGAL_NOTICE
                )
        )
    val deletion: TrialDeletionContract = TrialDeletionContract.developmentVerified(legal)
}

internal fun resolveEnvironment(rawValue: String): EnvironmentId = when (rawValue) {
    "DEVELOPMENT" -> EnvironmentId.DEVELOPMENT
    else -> error("Unsupported Trial application environment.")
}
