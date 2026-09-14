package com.gurbakir.foundation.config

import java.net.URI

val REQUIRED_CUSTOMER_ACCOUNT_SCOPES: Set<String> =
    setOf("openid", "email", "customer-account-api:full")

private const val MINIMUM_SHOPIFY_CALLBACK_SCHEME_SEGMENTS = 3
private val LOCALE_TAG_PATTERN = Regex("^[a-z]{2,3}(-[A-Z]{2})?$")
private val MARKET_ID_PATTERN = Regex("^[A-Z][A-Z0-9_-]{1,31}$")
private val COUNTRY_CODE_PATTERN = Regex("^[A-Z]{2}$")
private val CURRENCY_CODE_PATTERN = Regex("^[A-Z]{3}$")
private val PREFERENCES_NAME_PATTERN = Regex("^[a-z][a-z0-9_]{1,79}$")
private val KEY_ALIAS_PATTERN = Regex("^[a-z][a-z0-9.-]{1,127}$")

enum class EnvironmentId {
    DEVELOPMENT,
    STAGING
}

class ControlledPublicToken private constructor(private val rawValue: String) {
    val isConfigured: Boolean
        get() = rawValue.isNotBlank()

    fun <T> use(block: (String) -> T): T = block(rawValue)

    override fun toString(): String = "<redacted>"

    override fun equals(other: Any?): Boolean = other is ControlledPublicToken && rawValue == other.rawValue

    override fun hashCode(): Int = rawValue.hashCode()

    companion object {
        fun from(rawValue: String): ControlledPublicToken = ControlledPublicToken(rawValue.trim())
    }
}

data class StorefrontConfiguration(val domain: String, val apiVersion: String, val publicToken: ControlledPublicToken) {
    fun validationIssues(): Set<ConfigurationIssue> = buildSet {
        if (!domain.isValidHttpsHost()) add(ConfigurationIssue.STOREFRONT_DOMAIN)
        if (!apiVersion.matches(Regex("^20[0-9]{2}-(01|04|07|10)$"))) {
            add(ConfigurationIssue.STOREFRONT_API_VERSION)
        }
        if (!publicToken.isConfigured) add(ConfigurationIssue.STOREFRONT_PUBLIC_TOKEN)
    }
}

data class CustomerAccountConfiguration(
    val clientId: String,
    val issuer: String,
    val authorizationEndpoint: String,
    val tokenEndpoint: String,
    val logoutEndpoint: String,
    val graphqlEndpoint: String,
    val redirectUri: String,
    val userAgent: String,
    val scopes: Set<String>
) {
    fun validationIssues(): Set<ConfigurationIssue> = buildSet {
        if (clientId.isBlank()) add(ConfigurationIssue.CUSTOMER_ACCOUNT_CLIENT_ID)
        if (!issuer.isValidHttpsUri()) add(ConfigurationIssue.CUSTOMER_ACCOUNT_ISSUER)
        if (!authorizationEndpoint.isValidHttpsUri()) add(ConfigurationIssue.CUSTOMER_ACCOUNT_AUTH_ENDPOINT)
        if (!tokenEndpoint.isValidHttpsUri()) add(ConfigurationIssue.CUSTOMER_ACCOUNT_TOKEN_ENDPOINT)
        if (!logoutEndpoint.isValidHttpsUri()) add(ConfigurationIssue.CUSTOMER_ACCOUNT_LOGOUT_ENDPOINT)
        if (!graphqlEndpoint.isValidHttpsUri()) add(ConfigurationIssue.CUSTOMER_ACCOUNT_GRAPHQL_ENDPOINT)
        if (!redirectUri.isValidShopifyMobileRedirectUri()) {
            add(ConfigurationIssue.CUSTOMER_ACCOUNT_REDIRECT_URI)
        }
        if (!userAgent.isValidHttpUserAgent()) add(ConfigurationIssue.CUSTOMER_ACCOUNT_USER_AGENT)
        if (scopes != REQUIRED_CUSTOMER_ACCOUNT_SCOPES) add(ConfigurationIssue.CUSTOMER_ACCOUNT_SCOPES)
    }

    override fun toString(): String =
        "CustomerAccountConfiguration(clientId=<redacted>, issuer=$issuer, scopes=$scopes)"
}

data class LocalizationPolicy(val defaultLocaleTag: String, val supportedLocaleTags: List<String>) {
    fun validationIssues(): Set<ConfigurationIssue> = buildSet {
        if (supportedLocaleTags.isEmpty()) add(ConfigurationIssue.LOCALIZATION)
        if (defaultLocaleTag !in supportedLocaleTags) add(ConfigurationIssue.LOCALIZATION)
        if (supportedLocaleTags.distinct().size != supportedLocaleTags.size) add(ConfigurationIssue.LOCALIZATION)
        if (supportedLocaleTags.any { !LOCALE_TAG_PATTERN.matches(it) }) add(ConfigurationIssue.LOCALIZATION)
    }
}

data class MarketConfiguration(val id: String, val countryCode: String, val currencyCode: String) {
    fun validationIssues(): Set<ConfigurationIssue> = buildSet {
        if (
            !MARKET_ID_PATTERN.matches(id) ||
            !COUNTRY_CODE_PATTERN.matches(countryCode) ||
            !CURRENCY_CODE_PATTERN.matches(currencyCode)
        ) {
            add(ConfigurationIssue.MARKET)
        }
    }
}

data class ProtectedStoreIdentity(val preferencesName: String, val keyAlias: String) {
    internal fun isValid(): Boolean =
        PREFERENCES_NAME_PATTERN.matches(preferencesName) && KEY_ALIAS_PATTERN.matches(keyAlias)
}

data class ProtectedPersistenceConfiguration(
    val cart: ProtectedStoreIdentity,
    val customerSession: ProtectedStoreIdentity
) {
    fun validationIssues(): Set<ConfigurationIssue> = buildSet {
        if (!cart.isValid()) add(ConfigurationIssue.PROTECTED_PERSISTENCE)
        if (!customerSession.isValid()) add(ConfigurationIssue.PROTECTED_PERSISTENCE)
        if (cart.preferencesName == customerSession.preferencesName) add(ConfigurationIssue.PROTECTED_PERSISTENCE)
        if (cart.keyAlias == customerSession.keyAlias) add(ConfigurationIssue.PROTECTED_PERSISTENCE)
    }
}

data class AppConfiguration(
    val brand: BrandConfiguration,
    val environment: EnvironmentId,
    val localization: LocalizationPolicy,
    val market: MarketConfiguration,
    val protectedPersistence: ProtectedPersistenceConfiguration,
    val storefront: StorefrontConfiguration,
    val applicationComposition: ApplicationComposition
) {
    val validationIssues: Set<ConfigurationIssue>
        get() =
            brand.validationIssues() +
                localization.validationIssues() +
                market.validationIssues() +
                protectedPersistence.validationIssues() +
                storefront.validationIssues() +
                when (val account = applicationComposition.capabilities.customerAccount) {
                    CustomerAccountCapability.Disabled -> emptySet()
                    is CustomerAccountCapability.Enabled -> account.configuration.validationIssues()
                }
}

enum class ConfigurationIssue {
    BRAND_KEY,
    BRAND_DISPLAY_NAME,
    BRAND_ASSETS,
    BRAND_LEGAL_LINKS,
    BRAND_ANALYTICS_NAMESPACE,
    LOCALIZATION,
    MARKET,
    PROTECTED_PERSISTENCE,
    STOREFRONT_DOMAIN,
    STOREFRONT_API_VERSION,
    STOREFRONT_PUBLIC_TOKEN,
    CUSTOMER_ACCOUNT_CLIENT_ID,
    CUSTOMER_ACCOUNT_ISSUER,
    CUSTOMER_ACCOUNT_AUTH_ENDPOINT,
    CUSTOMER_ACCOUNT_TOKEN_ENDPOINT,
    CUSTOMER_ACCOUNT_LOGOUT_ENDPOINT,
    CUSTOMER_ACCOUNT_GRAPHQL_ENDPOINT,
    CUSTOMER_ACCOUNT_REDIRECT_URI,
    CUSTOMER_ACCOUNT_USER_AGENT,
    CUSTOMER_ACCOUNT_SCOPES
}

private fun String.isValidHttpsHost(): Boolean = runCatching {
    val uri = URI("https://$this")
    isNotBlank() && uri.scheme == "https" && uri.host == this && uri.userInfo == null && uri.port == -1
}.getOrDefault(false)

private fun String.isValidHttpsUri(): Boolean = runCatching {
    val uri = URI(this)
    uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null && uri.fragment == null
}.getOrDefault(false)

private fun String.isValidShopifyMobileRedirectUri(): Boolean = runCatching {
    val uri = URI(this)
    val scheme = uri.scheme.orEmpty()
    val schemeSegments = scheme.split('.')
    schemeSegments.size >= MINIMUM_SHOPIFY_CALLBACK_SCHEME_SEGMENTS &&
        schemeSegments[0] == "shop" &&
        schemeSegments[1].matches(Regex("^[0-9]+$")) &&
        schemeSegments.drop(2).all { it.matches(Regex("^[a-z0-9][a-z0-9-]*$")) } &&
        !uri.host.isNullOrBlank() &&
        uri.path.isNotBlank() &&
        uri.userInfo == null &&
        uri.port == -1 &&
        uri.query == null &&
        uri.fragment == null
}.getOrDefault(false)

private fun String.isValidHttpUserAgent(): Boolean = length in 1..128 && this == trim() && all { it.code in 0x20..0x7e }
