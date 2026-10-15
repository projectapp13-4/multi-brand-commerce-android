package com.tumiart.mobile

import com.gurbakir.foundation.config.ApplicationCapability
import com.gurbakir.foundation.config.ControlledPublicToken
import com.gurbakir.foundation.config.CustomerAccountCapability
import com.gurbakir.foundation.config.CustomerAccountConfiguration
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
    fun `only the real development environment is supported`() {
        assertThrows(IllegalStateException::class.java) { resolveEnvironment("PRODUCTION") }
        assertThrows(IllegalStateException::class.java) { resolveEnvironment("STAGING") }
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
        redirectUri = "shop.80183034101.tumiart://oauth/callback",
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
    fun `development identity locale market and protected stores are independent`() {
        val app = TumiArtConfiguration.app
        assertEquals("com.tumiart.mobile.development.debug", BuildConfig.APPLICATION_ID)
        assertEquals("tumiart", app.brand.key)
        assertEquals("TumiArt", app.brand.displayName)
        assertEquals("tr", app.localization.defaultLocaleTag)
        assertEquals(listOf("tr", "en"), app.localization.supportedLocaleTags)
        assertEquals("TR", app.market.countryCode)
        assertEquals("TRY", app.market.currencyCode)
        assertEquals("tumiart-development-local.db", BuildConfig.DATABASE_NAME)
        assertEquals("tumiart_secure_cart_development", app.protectedPersistence.cart.preferencesName)
        assertEquals("tumiart.cart.development.v1", app.protectedPersistence.cart.keyAlias)
        assertEquals(
            "tumiart_secure_customer_session_development",
            app.protectedPersistence.customerSession.preferencesName
        )
        assertEquals("tumiart.customer.session.development.v1", app.protectedPersistence.customerSession.keyAlias)
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
    fun `Home selects bounded v2 root without selecting unapproved artwork`() {
        val home = TumiArtConfiguration.home
        val remote = assertInstanceOf(HomeRemoteSource.ShopifyMetaobject::class.java, home.remoteSource)
        assertEquals("mobile_home_v2", remote.selector.type)
        assertEquals("primary", remote.selector.handle)
        assertEquals(HomeContentContractId.PILOT_MEDIA_V2, remote.contractId)
        assertTrue(home.packagedFallback.productRange.sources.isEmpty())
        assertEquals("", home.packagedFallback.featuredProduct.handle)
        assertEquals("tum-koleksiyonlar", TumiArtConfiguration.catalog.menuHandle)
    }
}
