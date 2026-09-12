@file:Suppress("MagicNumber")

package com.example.gate2synthetic

import com.example.gate2synthetic.brand.Gate2SyntheticBrand
import com.example.gate2synthetic.config.Gate2SyntheticConfiguration
import com.gurbakir.foundation.config.ApplicationCapability
import com.gurbakir.foundation.config.ArgbColor
import com.gurbakir.foundation.config.BrandColorSchemeTokens
import com.gurbakir.foundation.config.BrandColorTokens
import com.gurbakir.foundation.config.BrandFontWeight
import com.gurbakir.foundation.config.BrandMotionTokens
import com.gurbakir.foundation.config.BrandShapeTokens
import com.gurbakir.foundation.config.BrandSpacingTokens
import com.gurbakir.foundation.config.BrandTextStyleTokens
import com.gurbakir.foundation.config.BrandTypographyTokens
import com.gurbakir.foundation.config.ConfigurationIssue
import com.gurbakir.foundation.config.CustomerAccountCapability
import com.gurbakir.foundation.config.EnvironmentId
import com.gurbakir.foundation.config.MarketConfiguration
import com.gurbakir.foundation.config.PrimaryNavigationDestination
import com.gurbakir.foundation.config.ProtectedPersistenceConfiguration
import com.gurbakir.foundation.config.ProtectedStoreIdentity
import com.gurbakir.mobile.address.PostalCodeInputMode
import com.gurbakir.mobile.update.UpdatePolicyRefreshResult
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class Gate2SyntheticConfigurationTest {
    @Test
    fun `brand exposes the complete approved synthetic identity and design tokens`() {
        val brand = Gate2SyntheticBrand.configuration

        assertEquals("gate2-synthetic", brand.key)
        assertEquals("Gate 2 Synthetic", brand.displayName)
        assertEquals("gate2_synthetic", brand.analyticsEventNamespace)
        assertEquals("ic_gate2_synthetic_launcher", brand.assets.appIconResourceName)
        assertEquals(null, brand.assets.logoResourceName)
        assertEquals(null, brand.assets.heroImageResourceName)
        assertEquals("https://legal.gate2.invalid/privacy", brand.legalLinks.privacyPolicyUrl)
        assertEquals("https://legal.gate2.invalid/terms", brand.legalLinks.termsUrl)
        assertEquals("https://legal.gate2.invalid/support", brand.legalLinks.supportUrl)
        assertTrue(brand.validationIssues().isEmpty())

        assertEquals(expectedColors(), brand.designTokens.colors)
        assertEquals(expectedTypography(), brand.designTokens.typography)
        assertEquals(BrandShapeTokens(4, 16, 28), brand.designTokens.shapes)
        assertEquals(BrandSpacingTokens(6, 12, 20, 32), brand.designTokens.spacing)
        assertEquals(BrandMotionTokens(100, 240, 420), brand.designTokens.motion)
    }

    @Test
    fun `application configuration is structurally valid except for deliberate network credentials`() {
        val configuration = Gate2SyntheticConfiguration.app

        assertEquals(EnvironmentId.DEVELOPMENT, configuration.environment)
        assertEquals("en-CA", configuration.localization.defaultLocaleTag)
        assertEquals(listOf("en-CA"), configuration.localization.supportedLocaleTags)
        assertEquals(MarketConfiguration("ZZ", "ZZ", "XTS"), configuration.market)
        assertEquals(
            ProtectedPersistenceConfiguration(
                cart =
                    ProtectedStoreIdentity(
                        "gate2_synthetic_secure_cart_development",
                        "gate2.synthetic.cart.development.v1"
                    ),
                customerSession =
                    ProtectedStoreIdentity(
                        "gate2_synthetic_secure_customer_session_development",
                        "gate2.synthetic.customer.session.development.v1"
                    )
            ),
            configuration.protectedPersistence
        )
        assertEquals("storefront.gate2.invalid", configuration.storefront.domain)
        assertEquals("2026-07", configuration.storefront.apiVersion)
        assertFalse(configuration.storefront.publicToken.isConfigured)
        assertEquals(
            CustomerAccountCapability.Disabled,
            configuration.applicationComposition.capabilities.customerAccount
        )
        assertTrue(configuration.applicationComposition.capabilities.isEnabled(ApplicationCapability.SEARCH))
        assertFalse(configuration.applicationComposition.capabilities.isEnabled(ApplicationCapability.WISHLIST))
        assertEquals(
            listOf(
                PrimaryNavigationDestination.SEARCH,
                PrimaryNavigationDestination.HOME,
                PrimaryNavigationDestination.CATEGORIES
            ),
            configuration.applicationComposition.primaryNavigation.destinations
        )
        assertEquals(
            setOf(
                ConfigurationIssue.STOREFRONT_PUBLIC_TOKEN
            ),
            configuration.validationIssues
        )
    }

    @Test
    fun `home catalog market and local partitions use only synthetic fixtures`() {
        val home = Gate2SyntheticConfiguration.home
        val catalog = Gate2SyntheticConfiguration.catalog

        assertEquals("SYNTHETIC_HOME_RANGE", home.productRange.stableId)
        assertEquals(3, home.productRange.itemLimit)
        assertEquals(
            listOf("synthetic-alpha", "synthetic-beta"),
            home.productRange.sources.map { it.handle }
        )
        assertEquals("synthetic-featured-product", home.featuredProduct.handle)
        assertEquals("synthetic-catalog-menu", catalog.menuHandle)
        assertEquals("DEVELOPMENT", Gate2SyntheticConfiguration.searchPartition.environmentId)
        assertEquals("ZZ", Gate2SyntheticConfiguration.searchPartition.marketId)
        assertEquals("i copper", Gate2SyntheticConfiguration.searchNormalizationPolicy.normalize("I COPPER"))
        assertEquals("DEVELOPMENT", Gate2SyntheticConfiguration.wishlistPartition.environmentId)
        assertEquals("ZZ", Gate2SyntheticConfiguration.wishlistPartition.marketId)
    }

    @Test
    fun `app-owned policies accept only the approved synthetic boundaries`() {
        val deepLinks = Gate2SyntheticConfiguration.deepLinks

        assertEquals("https://links.gate2.invalid/collections", deepLinks.collectionBasePath)
        assertEquals("https://links.gate2.invalid/apps/mobile/products", deepLinks.productBasePath)
        assertTrue(Gate2SyntheticConfiguration.addressPolicy.supports("ZZ"))
        assertFalse(Gate2SyntheticConfiguration.addressPolicy.supports("TR"))
        assertEquals(
            Gate2SyntheticConfiguration.app.market.countryCode,
            Gate2SyntheticConfiguration.addressPolicy.supportedTerritoryCode
        )
        assertEquals(PostalCodeInputMode.TEXT, Gate2SyntheticConfiguration.addressPolicy.postalCodeInputMode)
        assertTrue(Gate2SyntheticConfiguration.addressPolicy.acceptsPostalCode("AB-1234"))
        assertFalse(Gate2SyntheticConfiguration.addressPolicy.acceptsPostalCode("34000"))
    }

    @Test
    fun `update policy remains on local defaults without a provider`() = runTest {
        assertEquals(
            UpdatePolicyRefreshResult.LocalDefaults,
            Gate2SyntheticConfiguration.updatePolicyGateway.refresh()
        )
    }
}

private fun expectedColors(): BrandColorTokens =
    BrandColorTokens(light = expectedLightColors(), dark = expectedDarkColors())

private fun expectedLightColors(): BrandColorSchemeTokens = colors(
    0xFF4F378B,
    0xFFFFFFFF,
    0xFFEADDFF,
    0xFF21005D,
    0xFF625B71,
    0xFFFFFFFF,
    0xFFE8DEF8,
    0xFF1D192B,
    0xFF7D5260,
    0xFFFFFFFF,
    0xFFFFD8E4,
    0xFF31111D,
    0xFFFFF7FF,
    0xFF1D1B20,
    0xFFFFF7FF,
    0xFF1D1B20,
    0xFFE7E0EC,
    0xFF49454F,
    0xFFF7F2FA,
    0xFFF3EDF7,
    0xFFECE6F0,
    0xFF79747E,
    0xFFCAC4D0,
    0xFFB3261E,
    0xFFFFFFFF,
    0xFFF9DEDC,
    0xFF410E0B,
    0xFF322F35,
    0xFFF5EFF7,
    0xFFD0BCFF,
    0xFF000000
)

private fun expectedDarkColors(): BrandColorSchemeTokens = colors(
    0xFFD0BCFF,
    0xFF381E72,
    0xFF4F378B,
    0xFFEADDFF,
    0xFFCCC2DC,
    0xFF332D41,
    0xFF4A4458,
    0xFFE8DEF8,
    0xFFEFB8C8,
    0xFF492532,
    0xFF633B48,
    0xFFFFD8E4,
    0xFF141218,
    0xFFE6E0E9,
    0xFF141218,
    0xFFE6E0E9,
    0xFF49454F,
    0xFFCAC4D0,
    0xFF1D1B20,
    0xFF211F26,
    0xFF2B2930,
    0xFF938F99,
    0xFF49454F,
    0xFFF2B8B5,
    0xFF601410,
    0xFF8C1D18,
    0xFFF9DEDC,
    0xFFE6E0E9,
    0xFF322F35,
    0xFF6750A4,
    0xFF000000
)

@Suppress("LongParameterList")
private fun colors(
    primary: Long,
    onPrimary: Long,
    primaryContainer: Long,
    onPrimaryContainer: Long,
    secondary: Long,
    onSecondary: Long,
    secondaryContainer: Long,
    onSecondaryContainer: Long,
    tertiary: Long,
    onTertiary: Long,
    tertiaryContainer: Long,
    onTertiaryContainer: Long,
    background: Long,
    onBackground: Long,
    surface: Long,
    onSurface: Long,
    surfaceVariant: Long,
    onSurfaceVariant: Long,
    surfaceContainerLow: Long,
    surfaceContainer: Long,
    surfaceContainerHigh: Long,
    outline: Long,
    outlineVariant: Long,
    error: Long,
    onError: Long,
    errorContainer: Long,
    onErrorContainer: Long,
    inverseSurface: Long,
    inverseOnSurface: Long,
    inversePrimary: Long,
    scrim: Long
): BrandColorSchemeTokens = BrandColorSchemeTokens(
    primary = ArgbColor(primary),
    onPrimary = ArgbColor(onPrimary),
    primaryContainer = ArgbColor(primaryContainer),
    onPrimaryContainer = ArgbColor(onPrimaryContainer),
    secondary = ArgbColor(secondary),
    onSecondary = ArgbColor(onSecondary),
    secondaryContainer = ArgbColor(secondaryContainer),
    onSecondaryContainer = ArgbColor(onSecondaryContainer),
    tertiary = ArgbColor(tertiary),
    onTertiary = ArgbColor(onTertiary),
    tertiaryContainer = ArgbColor(tertiaryContainer),
    onTertiaryContainer = ArgbColor(onTertiaryContainer),
    background = ArgbColor(background),
    onBackground = ArgbColor(onBackground),
    surface = ArgbColor(surface),
    onSurface = ArgbColor(onSurface),
    surfaceVariant = ArgbColor(surfaceVariant),
    onSurfaceVariant = ArgbColor(onSurfaceVariant),
    surfaceContainerLow = ArgbColor(surfaceContainerLow),
    surfaceContainer = ArgbColor(surfaceContainer),
    surfaceContainerHigh = ArgbColor(surfaceContainerHigh),
    outline = ArgbColor(outline),
    outlineVariant = ArgbColor(outlineVariant),
    error = ArgbColor(error),
    onError = ArgbColor(onError),
    errorContainer = ArgbColor(errorContainer),
    onErrorContainer = ArgbColor(onErrorContainer),
    inverseSurface = ArgbColor(inverseSurface),
    inverseOnSurface = ArgbColor(inverseOnSurface),
    inversePrimary = ArgbColor(inversePrimary),
    scrim = ArgbColor(scrim)
)

private fun expectedTypography(): BrandTypographyTokens = BrandTypographyTokens(
    fontFamilyResourceName = null,
    headlineLarge = BrandTextStyleTokens(34, 42, BrandFontWeight.SEMIBOLD),
    headlineMedium = BrandTextStyleTokens(30, 38, BrandFontWeight.SEMIBOLD),
    headlineSmall = BrandTextStyleTokens(26, 34, BrandFontWeight.MEDIUM),
    titleLarge = BrandTextStyleTokens(20, 28, BrandFontWeight.SEMIBOLD),
    titleMedium = BrandTextStyleTokens(17, 24, BrandFontWeight.MEDIUM),
    titleSmall = BrandTextStyleTokens(15, 22, BrandFontWeight.MEDIUM),
    bodyLarge = BrandTextStyleTokens(18, 26, BrandFontWeight.REGULAR),
    bodyMedium = BrandTextStyleTokens(15, 22, BrandFontWeight.REGULAR),
    bodySmall = BrandTextStyleTokens(13, 18, BrandFontWeight.REGULAR),
    labelLarge = BrandTextStyleTokens(15, 20, BrandFontWeight.SEMIBOLD),
    labelMedium = BrandTextStyleTokens(13, 18, BrandFontWeight.MEDIUM),
    labelSmall = BrandTextStyleTokens(12, 16, BrandFontWeight.MEDIUM)
)
