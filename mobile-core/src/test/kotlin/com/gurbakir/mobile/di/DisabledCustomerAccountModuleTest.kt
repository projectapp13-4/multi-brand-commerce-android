@file:Suppress("MagicNumber")

package com.gurbakir.mobile.di

import com.gurbakir.account.UnconfiguredCustomerAccountGateway
import com.gurbakir.account.UnconfiguredCustomerAddressGateway
import com.gurbakir.account.UnconfiguredCustomerOrderGateway
import com.gurbakir.account.UnconfiguredCustomerProfileGateway
import com.gurbakir.account.oauth.UnconfiguredCustomerAccountDiscoveryClient
import com.gurbakir.account.oauth.UnconfiguredCustomerAccountLogoutClient
import com.gurbakir.account.oauth.UnconfiguredCustomerAccountTokenClient
import com.gurbakir.account.session.CustomerSessionResolution
import com.gurbakir.foundation.config.AppConfiguration
import com.gurbakir.foundation.config.ApplicationCapabilities
import com.gurbakir.foundation.config.ApplicationComposition
import com.gurbakir.foundation.config.ArgbColor
import com.gurbakir.foundation.config.BrandAssetReferences
import com.gurbakir.foundation.config.BrandColorSchemeTokens
import com.gurbakir.foundation.config.BrandColorTokens
import com.gurbakir.foundation.config.BrandConfiguration
import com.gurbakir.foundation.config.BrandDesignTokens
import com.gurbakir.foundation.config.BrandFontWeight
import com.gurbakir.foundation.config.BrandLegalLinks
import com.gurbakir.foundation.config.BrandMotionTokens
import com.gurbakir.foundation.config.BrandShapeTokens
import com.gurbakir.foundation.config.BrandSpacingTokens
import com.gurbakir.foundation.config.BrandTextStyleTokens
import com.gurbakir.foundation.config.BrandTypographyTokens
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
import javax.inject.Provider
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test

class DisabledCustomerAccountModuleTest {
    @Test
    fun `disabled composition never requests discovery token logout or secure store providers`() = runTest {
        val app = configuration()
        val session = CoreCustomerAccountModule.provideCustomerAccountSessionCoordinator(
            app,
            Provider { error("token provider requested") },
            Provider { error("logout provider requested") },
            Provider { error("encrypted store provider requested") }
        )
        assertEquals(CustomerSessionResolution.SignedOut, session.restore())
        assertEquals(CustomerSessionResolution.SignedOut, session.refresh())
        session.clearForLogout()
        val discovery = Provider<com.gurbakir.account.oauth.CustomerAccountDiscoveryClient> {
            error("network discovery provider requested")
        }
        assertInstanceOf(
            UnconfiguredCustomerAccountDiscoveryClient::class.java,
            CoreCustomerAccountModule.provideCustomerAccountDiscoveryClient(app)
        )
        assertInstanceOf(
            UnconfiguredCustomerAccountTokenClient::class.java,
            CoreCustomerAccountModule.provideCustomerAccountTokenClient(app, discovery)
        )
        assertInstanceOf(
            UnconfiguredCustomerAccountLogoutClient::class.java,
            CoreCustomerAccountModule.provideCustomerAccountLogoutClient(app, discovery)
        )
        assertInstanceOf(
            UnconfiguredCustomerAccountGateway::class.java,
            CoreCustomerAccountModule.provideCustomerAccountGateway(app, discovery, session)
        )
        assertInstanceOf(
            UnconfiguredCustomerProfileGateway::class.java,
            CoreCustomerAccountModule.provideCustomerProfileGateway(app, discovery, session)
        )
        assertInstanceOf(
            UnconfiguredCustomerAddressGateway::class.java,
            CoreCustomerAccountModule.provideCustomerAddressGateway(app, discovery, session)
        )
        assertInstanceOf(
            UnconfiguredCustomerOrderGateway::class.java,
            CustomerOrderModule.provideCustomerOrderGateway(app, discovery, session)
        )
        CoreCustomerAccountModule.provideCustomerAccountAuthorizationCoordinator(app, discovery).prepare()
    }

    private fun configuration(): AppConfiguration {
        val capabilities =
            ApplicationCapabilities(
                CapabilityState.DISABLED,
                CapabilityState.DISABLED,
                CustomerAccountCapability.Disabled
            )
        return AppConfiguration(
            validBrand(),
            EnvironmentId.DEVELOPMENT,
            LocalizationPolicy("en", listOf("en")),
            MarketConfiguration("ZZ", "ZZ", "XTS"),
            ProtectedPersistenceConfiguration(
                ProtectedStoreIdentity("test_cart", "test.cart"),
                ProtectedStoreIdentity("test_session", "test.session")
            ),
            StorefrontConfiguration("shop.example", "2026-07", ControlledPublicToken.from("synthetic-public")),
            ApplicationComposition(
                capabilities,
                PrimaryNavigationSpec.create(
                    listOf(PrimaryNavigationDestination.HOME, PrimaryNavigationDestination.CATEGORIES),
                    capabilities
                )
            )
        )
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
