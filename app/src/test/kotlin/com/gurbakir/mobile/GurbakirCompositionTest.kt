package com.gurbakir.mobile

import com.gurbakir.foundation.config.ApplicationCapability
import com.gurbakir.foundation.config.MarketConfiguration
import com.gurbakir.foundation.config.PrimaryNavigationDestination
import com.gurbakir.mobile.address.GurbakirAddressTerritoryPolicy
import com.gurbakir.mobile.address.PostalCodeInputMode
import com.gurbakir.mobile.brand.GurbakirBrand
import com.gurbakir.mobile.catalog.CatalogConfiguration
import com.gurbakir.mobile.catalog.GurbakirCatalogConfiguration
import com.gurbakir.mobile.di.ApplicationModule
import com.gurbakir.mobile.di.SearchModule
import com.gurbakir.mobile.di.UpdatePolicyModule
import com.gurbakir.mobile.di.WishlistModule
import com.gurbakir.mobile.home.GurbakirHomeConfiguration
import com.gurbakir.mobile.home.HomeCollectionSource
import com.gurbakir.mobile.home.HomeConfiguration
import com.gurbakir.mobile.home.HomeFeaturedProductConfiguration
import com.gurbakir.mobile.home.HomeProductRangeConfiguration
import com.gurbakir.mobile.navigation.GurbakirDeepLinkConfiguration
import com.gurbakir.mobile.navigation.MobileDeepLinkConfiguration
import com.gurbakir.mobile.order.GurbakirTrackingUrlPolicy
import java.util.Locale
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GurbakirCompositionTest {
    @Test
    fun `app selects concrete gurbakir configuration objects`() {
        val app = ApplicationModule.provideAppConfiguration()
        assertTrue(ApplicationCapability.entries.all(app.applicationComposition.capabilities::isEnabled))
        assertEquals(
            listOf(
                PrimaryNavigationDestination.HOME,
                PrimaryNavigationDestination.CATEGORIES,
                PrimaryNavigationDestination.SEARCH,
                PrimaryNavigationDestination.WISHLIST,
                PrimaryNavigationDestination.ACCOUNT
            ),
            app.applicationComposition.primaryNavigation.destinations
        )

        assertEquals("gurbakir", GurbakirBrand.configuration.key)
        assertEquals("Gürbakır", GurbakirBrand.configuration.displayName)
        assertEquals(listOf("tr", "en"), app.localization.supportedLocaleTags)
        assertEquals("tr", app.localization.defaultLocaleTag)
        assertEquals(MarketConfiguration("TR", "TR", "TRY"), app.market)
        assertSame(GurbakirHomeConfiguration.value, ApplicationModule.provideHomeConfiguration())
        assertSame(GurbakirCatalogConfiguration.value, ApplicationModule.provideCatalogConfiguration())
        assertSame(GurbakirAddressTerritoryPolicy, ApplicationModule.provideAddressTerritoryPolicy())
    }

    @Test
    fun `app supplies exact home market handles ordering resources and featured product`() {
        assertEquals(
            HomeConfiguration(
                HomeProductRangeConfiguration(
                    "HOME_PRODUCT_RANGE",
                    R.string.home_product_range_title,
                    5,
                    listOf(
                        HomeCollectionSource("HOME_RANGE_DRINKWARE", "bardaklar", R.string.home_collection_drinkware),
                        HomeCollectionSource(
                            "HOME_RANGE_COFFEE_POTS",
                            "cezveler",
                            R.string.home_collection_coffee_pots
                        ),
                        HomeCollectionSource("HOME_RANGE_PANS", "tavalar-sahanlar", R.string.home_collection_pans),
                        HomeCollectionSource("HOME_RANGE_POTS", "tencereler", R.string.home_collection_pots),
                        HomeCollectionSource("HOME_RANGE_SPECIAL", "ozel-urunlerimiz", R.string.home_collection_special)
                    )
                ),
                HomeFeaturedProductConfiguration(
                    "HOME_FEATURED_PRODUCT",
                    R.string.home_featured_product_title,
                    "bakir-tava-ve-sahan-el-dovmesi-cift-pirinc-kulplu"
                )
            ),
            ApplicationModule.provideHomeConfiguration()
        )
    }

    @Test
    fun `app supplies configured catalog menu selector`() {
        assertEquals(
            CatalogConfiguration(BuildConfig.CATALOG_MENU_HANDLE),
            ApplicationModule.provideCatalogConfiguration()
        )
    }

    @Test
    fun `app supplies exact deep links address market and installed version`() {
        val configuration = ApplicationModule.provideAppConfiguration()
        assertEquals(
            MobileDeepLinkConfiguration(
                "https://gurbakir.com/collections",
                "https://gurbakir.com/apps/mobile/products"
            ),
            GurbakirDeepLinkConfiguration
        )
        val territory = ApplicationModule.provideAddressTerritoryPolicy()
        assertTrue(territory.supports("TR"))
        assertFalse(territory.supports("tr"))
        assertFalse(territory.supports("US"))
        assertFalse(territory.supports(null))
        assertEquals(configuration.market.countryCode, territory.supportedTerritoryCode)
        assertEquals(PostalCodeInputMode.NUMERIC, territory.postalCodeInputMode)
        assertTrue(territory.acceptsPostalCode("34000"))
        assertFalse(territory.acceptsPostalCode("34"))
        assertFalse(territory.acceptsPostalCode("AB-1234"))
        assertEquals(
            "TR",
            SearchModule.provideSearchHistoryPartition(configuration).marketId
        )
        val originalLocale = Locale.getDefault()
        try {
            for (foregroundLocale in listOf("tr-TR", "en-US", "en-GB", "fr-FR", "en-XA", "ar-XB")) {
                Locale.setDefault(Locale.forLanguageTag(foregroundLocale))
                assertEquals(
                    "ı bakır",
                    SearchModule.provideSearchHistoryNormalizationPolicy().normalize("I BAKIR"),
                    "Search keys must retain tr-TR normalization under foreground locale $foregroundLocale"
                )
            }
        } finally {
            Locale.setDefault(originalLocale)
        }
        assertEquals(
            "TR",
            WishlistModule.provideWishlistPartition(configuration).marketId
        )
        assertEquals(BuildConfig.VERSION_CODE, UpdatePolicyModule.provideCurrentAppVersionCode().value)
    }

    @Test
    fun `app preserves exact carrier allowlist including subdomains and rejects lookalikes`() {
        for (host in listOf("ptt.gov.tr", "yurticikargo.com", "araskargo.com.tr", "suratkargo.com.tr")) {
            assertTrue(GurbakirTrackingUrlPolicy.isAllowed("https://$host/tracking?code=SYNTHETIC"))
            assertTrue(GurbakirTrackingUrlPolicy.isAllowed("https://track.$host/tracking"))
            assertFalse(GurbakirTrackingUrlPolicy.isAllowed("https://$host.evil.example/tracking"))
        }
        assertFalse(GurbakirTrackingUrlPolicy.isAllowed("https://tracking.example/unknown"))
    }
}
