package com.projectapp134.multibrandtrial

import com.gurbakir.foundation.config.ApplicationCapability
import com.gurbakir.foundation.config.CustomerAccountCapability
import com.gurbakir.foundation.config.MarketConfiguration
import com.gurbakir.foundation.config.PrimaryNavigationDestination
import com.gurbakir.mobile.home.HomeRemoteSource
import com.projectapp134.multibrandtrial.config.TrialConfiguration
import com.projectapp134.multibrandtrial.legal.TrialLegalRole
import com.projectapp134.multibrandtrial.legal.TrialLegalState
import com.projectapp134.multibrandtrial.order.TrialTrackingUrlPolicy
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TrialConfigurationTest {
    @Test
    fun `Trial owns exact identity locale market and persistence partitions`() {
        val app = TrialConfiguration.app

        assertEquals("multi-brand-trial", app.brand.key)
        assertEquals("Multi Brand Trial", app.brand.displayName)
        assertEquals("multi-brand-trial-store.myshopify.com", app.storefront.domain)
        assertEquals("2026-07", app.storefront.apiVersion)
        assertEquals(listOf("tr", "en"), app.localization.supportedLocaleTags)
        assertEquals("tr", app.localization.defaultLocaleTag)
        assertEquals(MarketConfiguration("TR", "TR", "TRY"), app.market)
        assertEquals("trial_secure_cart_development", app.protectedPersistence.cart.preferencesName)
        assertEquals("trial.cart.development.v1", app.protectedPersistence.cart.keyAlias)
        assertEquals(
            "trial_secure_customer_session_development",
            app.protectedPersistence.customerSession.preferencesName
        )
        assertEquals("trial.customer.session.development.v1", app.protectedPersistence.customerSession.keyAlias)
        assertTrue(
            setOf(
                app.protectedPersistence.cart.preferencesName,
                app.protectedPersistence.cart.keyAlias,
                app.protectedPersistence.customerSession.preferencesName,
                app.protectedPersistence.customerSession.keyAlias
            ).none { it.startsWith("gurbakir") }
        )
    }

    @Test
    fun `Trial enables the full five destination composition and exact mobile callback`() {
        val composition = TrialConfiguration.app.applicationComposition
        assertTrue(ApplicationCapability.entries.all(composition.capabilities::isEnabled))
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
        val account = assertInstanceOf(
            CustomerAccountCapability.Enabled::class.java,
            composition.capabilities.customerAccount
        )
        assertEquals("shop.61252272257.multibrandtrial://oauth/callback", account.configuration.redirectUri)
        assertEquals(setOf("openid", "email", "customer-account-api:full"), account.configuration.scopes)
    }

    @Test
    fun `Trial keeps Home v1 and owns only pilot fallbacks`() {
        val home = TrialConfiguration.home
        val remote = assertInstanceOf(HomeRemoteSource.ShopifyMetaobject::class.java, home.remoteSource)

        assertEquals("mobile_home", remote.selector.type)
        assertEquals(1, remote.supportedContentVersion)
        assertEquals(listOf("pilot-koleksiyonu"), home.packagedFallback.productRange.sources.map { it.handle })
        assertEquals("pilot-urun", home.packagedFallback.featuredProduct.handle)
        assertEquals("main-menu", TrialConfiguration.catalog.menuHandle)
    }

    @Test
    fun `unverified legal and carrier links fail closed without borrowing another brand`() {
        assertEquals(TrialLegalRole.entries.toSet(), TrialConfiguration.legal.entries.map { it.role }.toSet())
        assertTrue(TrialConfiguration.legal.entries.all { it.state == TrialLegalState.SETUP_REQUIRED })
        assertTrue(
            TrialConfiguration.legal.entries.all {
                it.intendedUri.startsWith("https://multi-brand-trial-store.myshopify.com/setup-required/")
            }
        )
        assertEquals(TrialLegalState.SETUP_REQUIRED, TrialConfiguration.deletion.state)
        assertEquals(TrialLegalRole.PRIVACY, TrialConfiguration.deletion.privacy.role)
        assertEquals(TrialLegalRole.SUPPORT, TrialConfiguration.deletion.request.role)
        assertFalse(TrialTrackingUrlPolicy.isAllowed("https://ptt.gov.tr/tracking/AA123"))
        assertFalse(TrialTrackingUrlPolicy.isAllowed("https://carrier.example/tracking/AA123"))
    }
}
