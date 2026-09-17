package com.projectapp134.multibrandtrial

import com.gurbakir.foundation.config.ApplicationCapability
import com.gurbakir.foundation.config.CustomerAccountCapability
import com.gurbakir.foundation.config.MarketConfiguration
import com.gurbakir.foundation.config.PrimaryNavigationDestination
import com.gurbakir.mobile.home.HomeRemoteSource
import com.gurbakir.storefront.HomeContentContractId
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
    fun `Trial selects Home v2 and owns only pilot fallbacks`() {
        val home = TrialConfiguration.home
        val remote = assertInstanceOf(HomeRemoteSource.ShopifyMetaobject::class.java, home.remoteSource)

        assertEquals("mobile_home_v2", remote.selector.type)
        assertEquals(HomeContentContractId.PILOT_MEDIA_V2, remote.contractId)
        assertEquals(listOf("pilot-koleksiyonu"), home.packagedFallback.productRange.sources.map { it.handle })
        assertEquals("pilot-urun", home.packagedFallback.featuredProduct.handle)
        assertEquals("main-menu", TrialConfiguration.catalog.menuHandle)
    }

    @Test
    fun `provider verified Trial legal and deletion links stay provisional and brand isolated`() {
        assertEquals(TrialLegalRole.entries.toSet(), TrialConfiguration.legal.entries.map { it.role }.toSet())
        assertTrue(TrialConfiguration.legal.entries.all { it.state == TrialLegalState.DEVELOPMENT_VERIFIED })
        assertEquals(
            setOf(
                "https://multi-brand-trial-store.myshopify.com/pages/trial-destek",
                "https://multi-brand-trial-store.myshopify.com/pages/trial-gizlilik",
                "https://multi-brand-trial-store.myshopify.com/pages/trial-kullanim-kosullari",
                "https://multi-brand-trial-store.myshopify.com/pages/trial-kargo",
                "https://multi-brand-trial-store.myshopify.com/pages/trial-iade",
                "https://multi-brand-trial-store.myshopify.com/pages/trial-yasal-bildirim"
            ),
            TrialConfiguration.legal.entries.map { it.intendedUri }.toSet()
        )
        assertEquals(TrialLegalState.DEVELOPMENT_VERIFIED, TrialConfiguration.deletion.state)
        assertEquals(TrialLegalRole.PRIVACY, TrialConfiguration.deletion.privacy.role)
        assertEquals(TrialLegalRole.SUPPORT, TrialConfiguration.deletion.request.role)
        assertEquals(
            "https://multi-brand-trial-store.myshopify.com/pages/trial-gizlilik",
            TrialConfiguration.app.brand.legalLinks.privacyPolicyUrl
        )
        assertEquals(
            "https://multi-brand-trial-store.myshopify.com/pages/trial-kullanim-kosullari",
            TrialConfiguration.app.brand.legalLinks.termsUrl
        )
        assertEquals(
            "https://multi-brand-trial-store.myshopify.com/pages/trial-destek",
            TrialConfiguration.app.brand.legalLinks.supportUrl
        )
        assertFalse(TrialTrackingUrlPolicy.isAllowed("https://ptt.gov.tr/tracking/AA123"))
        assertFalse(TrialTrackingUrlPolicy.isAllowed("https://carrier.example/tracking/AA123"))
    }
}
