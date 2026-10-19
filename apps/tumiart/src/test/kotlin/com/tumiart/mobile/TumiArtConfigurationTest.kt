package com.tumiart.mobile

import com.gurbakir.foundation.config.ApplicationCapability
import com.gurbakir.foundation.config.ControlledPublicToken
import com.gurbakir.foundation.config.CustomerAccountCapability
import com.gurbakir.foundation.config.CustomerAccountConfiguration
import com.gurbakir.foundation.config.EnvironmentId
import com.gurbakir.foundation.config.PrimaryNavigationDestination
import com.gurbakir.mobile.home.HomeRemoteSource
import com.gurbakir.mobile.update.UpdatePolicyRefreshResult
import com.gurbakir.storefront.HomeContentContractId
import com.tumiart.mobile.config.TumiArtConfiguration
import com.tumiart.mobile.config.resolveEnvironment
import com.tumiart.mobile.di.TumiArtModule
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TumiArtConfigurationTest {
    @Test
    fun `production and development environments map while staging fails closed`() {
        assertEquals(EnvironmentId.DEVELOPMENT, resolveEnvironment("DEVELOPMENT"))
        assertEquals(EnvironmentId.PRODUCTION, resolveEnvironment("PRODUCTION"))
        assertThrows(IllegalStateException::class.java) { resolveEnvironment("STAGING") }
        assertThrows(IllegalStateException::class.java) { resolveEnvironment("UNKNOWN") }
    }

    @Test
    fun `update policy comes from local defaults without constructing Firebase`() = runTest {
        assertEquals(
            UpdatePolicyRefreshResult.LocalDefaults,
            TumiArtModule.provideUpdatePolicyRemoteGateway().refresh()
        )
    }

    private val account = CustomerAccountConfiguration(
        clientId = "test-mobile-client",
        issuer = "https://shopify.com/authentication/80183034101",
        authorizationEndpoint = "https://hesabim.tumiart.com/authentication/oauth/authorize",
        tokenEndpoint = "https://hesabim.tumiart.com/authentication/oauth/token",
        logoutEndpoint = "https://hesabim.tumiart.com/authentication/logout",
        graphqlEndpoint = "https://hesabim.tumiart.com/customer/api/2026-07/graphql",
        redirectUri = if (BuildConfig.FLAVOR == "production") {
            "shop.80183034101.tumiart.production://oauth/callback"
        } else {
            "shop.80183034101.tumiart://oauth/callback"
        },
        userAgent = "TumiArt-Android",
        scopes = setOf("openid", "email", "customer-account-api:full")
    )

    private fun configured(accountConfiguration: CustomerAccountConfiguration = account) =
        TumiArtConfiguration.app.let { app ->
            app.copy(
                storefront = app.storefront.copy(publicToken = ControlledPublicToken.from("test-public-client")),
                applicationComposition = app.applicationComposition.copy(
                    capabilities = app.applicationComposition.capabilities.copy(
                        customerAccount = CustomerAccountCapability.Enabled(accountConfiguration)
                    )
                )
            )
        }

    @Test
    fun `each environment accepts only its registered callback`() {
        val production = configured(account.copy(redirectUri = "shop.80183034101.tumiart.production://oauth/callback"))
            .copy(environment = EnvironmentId.PRODUCTION)
        assertTrue(TumiArtConfiguration.isLaunchReady(production))
        val development = configured(account.copy(redirectUri = "shop.80183034101.tumiart://oauth/callback"))
            .copy(environment = EnvironmentId.DEVELOPMENT)
        assertTrue(TumiArtConfiguration.isLaunchReady(development))
        assertFalse(TumiArtConfiguration.isLaunchReady(development.copy(environment = EnvironmentId.PRODUCTION)))
        assertFalse(TumiArtConfiguration.isLaunchReady(production.copy(environment = EnvironmentId.DEVELOPMENT)))
    }

    @Test
    fun `profile identity locale market and protected stores are independent`() {
        val app = TumiArtConfiguration.app
        val production = BuildConfig.FLAVOR == "production"
        val profile = if (production) "production" else "development"
        assertEquals(
            if (production) "com.tumiart.mobile.debug" else "com.tumiart.mobile.development.debug",
            BuildConfig.APPLICATION_ID
        )
        assertEquals("tumiart", app.brand.key)
        assertEquals("TumiArt", app.brand.displayName)
        assertEquals("tr", app.localization.defaultLocaleTag)
        assertEquals(listOf("tr", "en"), app.localization.supportedLocaleTags)
        assertEquals("TR", app.market.countryCode)
        assertEquals("TRY", app.market.currencyCode)
        assertEquals("tumiart-$profile-local.db", BuildConfig.DATABASE_NAME)
        assertEquals("tumiart_secure_cart_$profile", app.protectedPersistence.cart.preferencesName)
        assertEquals("tumiart.cart.$profile.v1", app.protectedPersistence.cart.keyAlias)
        assertEquals(
            "tumiart_secure_customer_session_$profile",
            app.protectedPersistence.customerSession.preferencesName
        )
        assertEquals("tumiart.customer.session.$profile.v1", app.protectedPersistence.customerSession.keyAlias)
        assertTrue(app.protectedPersistence.validationIssues().isEmpty())
    }

    @Test
    fun `all five native destinations and exact registered callback remain declared without credentials`() {
        val composition = TumiArtConfiguration.app.applicationComposition
        assertTrue(ApplicationCapability.entries.all(composition.capabilities::isEnabled))
        assertEquals(PrimaryNavigationDestination.entries.toSet(), composition.primaryNavigation.destinations.toSet())
        assertEquals(
            listOf(
                PrimaryNavigationDestination.HOME,
                PrimaryNavigationDestination.CATEGORIES,
                PrimaryNavigationDestination.SEARCH,
                PrimaryNavigationDestination.WISHLIST,
                PrimaryNavigationDestination.ACCOUNT
            ),
            composition.primaryNavigation.destinations
        )
        val enabled =
            assertInstanceOf(CustomerAccountCapability.Enabled::class.java, composition.capabilities.customerAccount)
        assertEquals(account.redirectUri, enabled.configuration.redirectUri)
    }

    @Test
    fun `complete bound configuration opens launch gate but missing client inputs fail closed`() {
        assertTrue(TumiArtConfiguration.isLaunchReady(configured()))
        assertFalse(
            TumiArtConfiguration.isLaunchReady(
                configured().copy(
                    storefront = configured().storefront.copy(publicToken = ControlledPublicToken.from(""))
                )
            )
        )
        assertFalse(TumiArtConfiguration.isLaunchReady(configured(account.copy(clientId = ""))))
        assertFalse(TumiArtConfiguration.isLaunchReady(configured(account.copy(tokenEndpoint = ""))))
    }

    @Test
    fun `valid HTTPS foreign origins and callback near matches fail closed`() {
        val rejected = listOf(
            account.copy(redirectUri = account.redirectUri + "/extra"),
            account.copy(redirectUri = account.redirectUri + "?foreign=true"),
            account.copy(redirectUri = account.redirectUri.replace("://oauth/", "://other/")),
            account.copy(issuer = "https://shopify.com/authentication/61252272257"),
            account.copy(authorizationEndpoint = "https://accounts.shopify.com/authentication/oauth/authorize"),
            account.copy(tokenEndpoint = "https://hesabim.tumiart.com.attacker.invalid/authentication/oauth/token"),
            account.copy(logoutEndpoint = "https://hesabim.tumiart.com/authentication/logout?redirect=foreign"),
            account.copy(graphqlEndpoint = "https://hesabim.tumiart.com/customer/api/2026-10/graphql"),
            account.copy(redirectUri = "shop.80183034101.tumiart://oauth/callback/extra"),
            account.copy(redirectUri = "shop.80183034101.tumiart://other/callback"),
            account.copy(redirectUri = "shop.80183034101.tumiart://oauth/callback?foreign=true")
        )
        rejected.forEach { assertFalse(TumiArtConfiguration.isLaunchReady(configured(it))) }
        assertFalse(
            TumiArtConfiguration.isLaunchReady(
                configured().copy(
                    storefront = configured().storefront.copy(domain = "multi-brand-trial-store.myshopify.com")
                )
            )
        )
    }

    @Test
    fun `Home selects bounded v2 root with verified collection and artwork fallbacks`() {
        val home = TumiArtConfiguration.home
        val remote = assertInstanceOf(HomeRemoteSource.ShopifyMetaobject::class.java, home.remoteSource)
        assertEquals("mobile_home_v2", remote.selector.type)
        assertEquals("primary", remote.selector.handle)
        assertEquals(HomeContentContractId.PILOT_MEDIA_V2, remote.contractId)
        val sources = home.packagedFallback.productRange.sources
        assertEquals(
            listOf("ustalar", "tumi-art-originals", "modern-forms", "zen-botanicals", "cocuk-odasi-posterleri"),
            sources.map { it.handle }
        )
        assertEquals(5, home.packagedFallback.productRange.itemLimit)
        assertEquals(sources.size, sources.map { it.stableId }.distinct().size)
        assertEquals("henri-matisse-bitkiler-1952", home.packagedFallback.featuredProduct.handle)
        assertEquals("tum-koleksiyonlar", TumiArtConfiguration.catalog.menuHandle)
    }
}
