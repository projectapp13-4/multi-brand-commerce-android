package com.gurbakir.mobile.config

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
import com.gurbakir.mobile.BuildConfig
import com.gurbakir.mobile.brand.GurbakirBrand

object BuildConfigurationSource {
    private val environment = resolveEnvironmentId(BuildConfig.ENVIRONMENT_ID)

    val current: AppConfiguration = AppConfiguration(
        brand = GurbakirBrand.configuration,
        environment = environment,
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
        protectedPersistence = projectedProtectedPersistence(),
        storefront =
            StorefrontConfiguration(
                domain = BuildConfig.STOREFRONT_DOMAIN,
                apiVersion = BuildConfig.STOREFRONT_API_VERSION,
                publicToken = ControlledPublicToken.from(BuildConfig.STOREFRONT_PUBLIC_TOKEN)
            ),
        applicationComposition = gurbakirComposition(
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
}

internal fun gurbakirComposition(configuration: CustomerAccountConfiguration): ApplicationComposition {
    val capabilities = ApplicationCapabilities(
        CapabilityState.ENABLED,
        CapabilityState.ENABLED,
        CustomerAccountCapability.Enabled(configuration)
    )
    return ApplicationComposition(
        capabilities,
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
}

internal fun resolveEnvironmentId(rawValue: String): EnvironmentId = when (rawValue) {
    "DEVELOPMENT" -> EnvironmentId.DEVELOPMENT
    "STAGING" -> EnvironmentId.STAGING
    else -> error("Unsupported application environment.")
}

internal fun projectedProtectedPersistence(): ProtectedPersistenceConfiguration = ProtectedPersistenceConfiguration(
    cart =
        ProtectedStoreIdentity(
            preferencesName = BuildConfig.CART_PREFERENCES,
            keyAlias = BuildConfig.CART_KEY_ALIAS
        ),
    customerSession =
        ProtectedStoreIdentity(
            preferencesName = BuildConfig.CUSTOMER_PREFERENCES,
            keyAlias = BuildConfig.CUSTOMER_KEY_ALIAS
        )
)
