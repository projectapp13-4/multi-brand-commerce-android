@file:Suppress("MagicNumber")

package com.gurbakir.foundation.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class AppConfigurationTest {
    @Test
    fun `disabled account has no configuration issues while invalid enabled preserves every issue`() {
        val disabled =
            ApplicationCapabilities(
                CapabilityState.DISABLED,
                CapabilityState.DISABLED,
                CustomerAccountCapability.Disabled
            )
        val enabled = disabled.copy(
            customerAccount = CustomerAccountCapability.Enabled(
                CustomerAccountConfiguration("", "", "", "", "", "", "", "", emptySet())
            )
        )
        val app = AppConfiguration(
            brand = validBrand(),
            environment = EnvironmentId.DEVELOPMENT,
            localization = LocalizationPolicy("en", listOf("en")),
            market = MarketConfiguration("ZZ", "ZZ", "XTS"),
            protectedPersistence = ProtectedPersistenceConfiguration(
                ProtectedStoreIdentity("test_cart", "test.cart"),
                ProtectedStoreIdentity("test_session", "test.session")
            ),
            storefront = StorefrontConfiguration("shop.example", "2026-07", ControlledPublicToken.from("test-public")),
            applicationComposition = ApplicationComposition(
                disabled,
                PrimaryNavigationSpec.create(
                    listOf(PrimaryNavigationDestination.HOME, PrimaryNavigationDestination.CATEGORIES),
                    disabled
                )
            )
        )
        assertEquals(emptySet<ConfigurationIssue>(), app.validationIssues)
        assertEquals(
            setOf(
                ConfigurationIssue.CUSTOMER_ACCOUNT_CLIENT_ID,
                ConfigurationIssue.CUSTOMER_ACCOUNT_ISSUER,
                ConfigurationIssue.CUSTOMER_ACCOUNT_AUTH_ENDPOINT,
                ConfigurationIssue.CUSTOMER_ACCOUNT_TOKEN_ENDPOINT,
                ConfigurationIssue.CUSTOMER_ACCOUNT_LOGOUT_ENDPOINT,
                ConfigurationIssue.CUSTOMER_ACCOUNT_GRAPHQL_ENDPOINT,
                ConfigurationIssue.CUSTOMER_ACCOUNT_REDIRECT_URI,
                ConfigurationIssue.CUSTOMER_ACCOUNT_USER_AGENT,
                ConfigurationIssue.CUSTOMER_ACCOUNT_SCOPES
            ),
            app.copy(
                applicationComposition = ApplicationComposition(
                    enabled,
                    PrimaryNavigationSpec.create(
                        listOf(
                            PrimaryNavigationDestination.HOME,
                            PrimaryNavigationDestination.CATEGORIES,
                            PrimaryNavigationDestination.ACCOUNT
                        ),
                        enabled
                    )
                )
            ).validationIssues
        )
    }

    @Test
    fun `brand configuration accepts typed design without owning localization`() {
        val brand = validBrand()

        assertTrue(brand.validationIssues().isEmpty())
    }

    @Test
    fun `brand configuration rejects unsafe identity links and resource names`() {
        val valid = validBrand()
        val issues =
            valid.copy(
                key = "Gür Bakır",
                displayName = "",
                assets = valid.assets.copy(logoResourceName = "../logo"),
                legalLinks = valid.legalLinks.copy(supportUrl = "http://example.com/support"),
                analyticsEventNamespace = "invalid.namespace"
            ).validationIssues()

        assertEquals(
            setOf(
                ConfigurationIssue.BRAND_KEY,
                ConfigurationIssue.BRAND_DISPLAY_NAME,
                ConfigurationIssue.BRAND_ASSETS,
                ConfigurationIssue.BRAND_LEGAL_LINKS,
                ConfigurationIssue.BRAND_ANALYTICS_NAMESPACE
            ),
            issues
        )
    }

    @Test
    fun `localization policy preserves ordered supported locales and rejects duplicates`() {
        val valid = LocalizationPolicy(defaultLocaleTag = "tr", supportedLocaleTags = listOf("tr", "en"))

        assertTrue(valid.validationIssues().isEmpty())
        assertEquals(listOf("tr", "en"), valid.supportedLocaleTags)
        assertEquals(
            setOf(ConfigurationIssue.LOCALIZATION),
            valid.copy(supportedLocaleTags = listOf("tr", "en", "en")).validationIssues()
        )
        assertEquals(
            setOf(ConfigurationIssue.LOCALIZATION),
            valid.copy(defaultLocaleTag = "fr").validationIssues()
        )
        assertEquals(
            setOf(ConfigurationIssue.LOCALIZATION),
            LocalizationPolicy("en-us", listOf("en-us")).validationIssues()
        )
    }

    @Test
    fun `market configuration accepts exact finite identifiers and rejects malformed values`() {
        val valid = MarketConfiguration(id = "TR", countryCode = "TR", currencyCode = "TRY")

        assertTrue(valid.validationIssues().isEmpty())
        assertEquals(
            setOf(ConfigurationIssue.MARKET),
            valid.copy(id = "tr", countryCode = "tr", currencyCode = "try").validationIssues()
        )
    }

    @Test
    fun `protected persistence rejects invalid identities and cross-store collisions`() {
        val valid =
            ProtectedPersistenceConfiguration(
                cart = ProtectedStoreIdentity("merchant_secure_cart_development", "merchant.cart.development.v1"),
                customerSession =
                    ProtectedStoreIdentity(
                        "merchant_secure_customer_session_development",
                        "merchant.customer.session.development.v1"
                    )
            )

        assertTrue(valid.validationIssues().isEmpty())
        assertEquals(
            setOf(ConfigurationIssue.PROTECTED_PERSISTENCE),
            valid.copy(customerSession = valid.cart).validationIssues()
        )
        assertEquals(
            setOf(ConfigurationIssue.PROTECTED_PERSISTENCE),
            valid.copy(cart = ProtectedStoreIdentity("UPPER", "unsafe_alias")).validationIssues()
        )
    }

    @Test
    fun `dimension tokens reject unordered values`() {
        assertThrows<IllegalArgumentException> {
            BrandSpacingTokens(compactDp = 8, normalDp = 4, generousDp = 16, sectionDp = 24)
        }
    }

    @Test
    fun `controlled public token never renders its raw value`() {
        val token = ControlledPublicToken.from("controlled-value")

        assertEquals("<redacted>", token.toString())
        assertTrue(token.isConfigured)
    }

    @Test
    fun `storefront validation rejects blank domain token and unversioned api`() {
        val issues =
            StorefrontConfiguration(
                domain = "",
                apiVersion = "latest",
                publicToken = ControlledPublicToken.from("")
            ).validationIssues()

        assertEquals(
            setOf(
                ConfigurationIssue.STOREFRONT_DOMAIN,
                ConfigurationIssue.STOREFRONT_API_VERSION,
                ConfigurationIssue.STOREFRONT_PUBLIC_TOKEN
            ),
            issues
        )
    }

    @Test
    fun `storefront validation accepts an owned https host and quarterly version`() {
        val issues =
            StorefrontConfiguration(
                domain = "example.myshopify.com",
                apiVersion = "2026-07",
                publicToken = ControlledPublicToken.from("test-public-token")
            ).validationIssues()

        assertTrue(issues.isEmpty())
        assertFalse(ControlledPublicToken.from("").isConfigured)
    }

    @Test
    fun `customer account validation accepts the Shopify mobile public client contract`() {
        val configuration =
            CustomerAccountConfiguration(
                clientId = "public-client-id",
                issuer = "https://shopify.com/authentication/123456",
                authorizationEndpoint = "https://shop.example/authentication/oauth/authorize",
                tokenEndpoint = "https://shop.example/authentication/oauth/token",
                logoutEndpoint = "https://shop.example/authentication/logout",
                graphqlEndpoint = "https://shop.example/customer/api/2026-07/graphql",
                redirectUri = "shop.123456.gurbakir://oauth/callback",
                userAgent = "Fixture-Android",
                scopes = REQUIRED_CUSTOMER_ACCOUNT_SCOPES
            )

        assertTrue(configuration.validationIssues().isEmpty())
        assertFalse(configuration.toString().contains("public-client-id"))
    }

    @Test
    fun `customer account rejects unsafe user agent input`() {
        val valid =
            CustomerAccountConfiguration(
                clientId = "public-client-id",
                issuer = "https://shopify.com/authentication/123456",
                authorizationEndpoint = "https://shop.example/authentication/oauth/authorize",
                tokenEndpoint = "https://shop.example/authentication/oauth/token",
                logoutEndpoint = "https://shop.example/authentication/logout",
                graphqlEndpoint = "https://shop.example/customer/api/2026-07/graphql",
                redirectUri = "shop.123456.fixture://oauth/callback",
                userAgent = "Fixture-Android",
                scopes = REQUIRED_CUSTOMER_ACCOUNT_SCOPES
            )

        assertEquals(
            setOf(ConfigurationIssue.CUSTOMER_ACCOUNT_USER_AGENT),
            valid.copy(userAgent = "unsafe\r\nheader").validationIssues()
        )
    }

    @Test
    fun `customer account validation rejects an unscoped generic redirect`() {
        val issues =
            CustomerAccountConfiguration(
                clientId = "public-client-id",
                issuer = "https://shopify.com/authentication/123456",
                authorizationEndpoint = "https://shop.example/authentication/oauth/authorize",
                tokenEndpoint = "https://shop.example/authentication/oauth/token",
                logoutEndpoint = "https://shop.example/authentication/logout",
                graphqlEndpoint = "https://shop.example/customer/api/2026-07/graphql",
                redirectUri = "gurbakir://oauth/callback",
                userAgent = "Test-Android",
                scopes = setOf("openid")
            ).validationIssues()

        assertTrue(ConfigurationIssue.CUSTOMER_ACCOUNT_REDIRECT_URI in issues)
        assertTrue(ConfigurationIssue.CUSTOMER_ACCOUNT_SCOPES in issues)
    }

    private fun validBrand(): BrandConfiguration = BrandConfiguration(
        key = "gurbakir",
        displayName = "Gürbakır",
        designTokens =
            BrandDesignTokens(
                colors = BrandColorTokens(light = colorScheme(), dark = colorScheme()),
                typography = typography(),
                shapes = BrandShapeTokens(8, 12, 20),
                spacing = BrandSpacingTokens(4, 8, 16, 24),
                motion = BrandMotionTokens(150, 220, 300)
            ),
        assets = BrandAssetReferences("ic_launcher_foreground", null, null),
        legalLinks = BrandLegalLinks(null, null, "https://example.com/support"),
        analyticsEventNamespace = "gurbakir"
    )

    private fun colorScheme(): BrandColorSchemeTokens = BrandColorSchemeTokens(
        primary = ArgbColor(0xFF000000),
        onPrimary = ArgbColor(0xFFFFFFFF),
        primaryContainer = ArgbColor(0xFF111111),
        onPrimaryContainer = ArgbColor(0xFFFFFFFF),
        secondary = ArgbColor(0xFF222222),
        onSecondary = ArgbColor(0xFFFFFFFF),
        secondaryContainer = ArgbColor(0xFF333333),
        onSecondaryContainer = ArgbColor(0xFFFFFFFF),
        tertiary = ArgbColor(0xFF444444),
        onTertiary = ArgbColor(0xFFFFFFFF),
        tertiaryContainer = ArgbColor(0xFF555555),
        onTertiaryContainer = ArgbColor(0xFFFFFFFF),
        background = ArgbColor(0xFFFFFFFF),
        onBackground = ArgbColor(0xFF000000),
        surface = ArgbColor(0xFFFFFFFF),
        onSurface = ArgbColor(0xFF000000),
        surfaceVariant = ArgbColor(0xFFEEEEEE),
        onSurfaceVariant = ArgbColor(0xFF222222),
        surfaceContainerLow = ArgbColor(0xFFF8F8F8),
        surfaceContainer = ArgbColor(0xFFF0F0F0),
        surfaceContainerHigh = ArgbColor(0xFFE8E8E8),
        outline = ArgbColor(0xFF555555),
        outlineVariant = ArgbColor(0xFFCCCCCC),
        error = ArgbColor(0xFFBA1A1A),
        onError = ArgbColor(0xFFFFFFFF),
        errorContainer = ArgbColor(0xFFFFDAD6),
        onErrorContainer = ArgbColor(0xFF410002),
        inverseSurface = ArgbColor(0xFF222222),
        inverseOnSurface = ArgbColor(0xFFFFFFFF),
        inversePrimary = ArgbColor(0xFFBBBBBB),
        scrim = ArgbColor(0xFF000000)
    )

    private fun typography(): BrandTypographyTokens = BrandTypographyTokens(
        fontFamilyResourceName = null,
        headlineLarge = BrandTextStyleTokens(32, 40, BrandFontWeight.REGULAR),
        headlineMedium = BrandTextStyleTokens(28, 36, BrandFontWeight.REGULAR),
        headlineSmall = BrandTextStyleTokens(24, 32, BrandFontWeight.REGULAR),
        titleLarge = BrandTextStyleTokens(22, 28, BrandFontWeight.SEMIBOLD),
        titleMedium = BrandTextStyleTokens(16, 24, BrandFontWeight.SEMIBOLD),
        titleSmall = BrandTextStyleTokens(14, 20, BrandFontWeight.SEMIBOLD),
        bodyLarge = BrandTextStyleTokens(16, 24, BrandFontWeight.REGULAR),
        bodyMedium = BrandTextStyleTokens(14, 20, BrandFontWeight.REGULAR),
        bodySmall = BrandTextStyleTokens(12, 16, BrandFontWeight.REGULAR),
        labelLarge = BrandTextStyleTokens(14, 20, BrandFontWeight.MEDIUM),
        labelMedium = BrandTextStyleTokens(12, 16, BrandFontWeight.MEDIUM),
        labelSmall = BrandTextStyleTokens(11, 16, BrandFontWeight.MEDIUM)
    )
}
