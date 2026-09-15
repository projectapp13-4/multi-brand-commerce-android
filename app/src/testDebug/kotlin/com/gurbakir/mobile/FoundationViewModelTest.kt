package com.gurbakir.mobile

import com.gurbakir.foundation.config.AppConfiguration
import com.gurbakir.foundation.config.ControlledPublicToken
import com.gurbakir.foundation.config.CustomerAccountConfiguration
import com.gurbakir.foundation.config.EnvironmentId
import com.gurbakir.foundation.config.LocalizationPolicy
import com.gurbakir.foundation.config.MarketConfiguration
import com.gurbakir.foundation.config.ProtectedPersistenceConfiguration
import com.gurbakir.foundation.config.ProtectedStoreIdentity
import com.gurbakir.foundation.config.StorefrontConfiguration
import com.gurbakir.foundation.logging.LogEvent
import com.gurbakir.foundation.logging.ProjectLogger
import com.gurbakir.foundation.logging.SafeAttribute
import com.gurbakir.mobile.brand.GurbakirBrand
import com.gurbakir.mobile.config.gurbakirComposition
import com.gurbakir.mobile.navigation.IntegrationId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FoundationViewModelTest {
    @Test
    fun `Firebase readiness is supplied separately from generic application configuration`() {
        val configuration = unconfigured()

        val unconfiguredFirebase = FoundationUiState.from(configuration, firebaseConfigured = false)
        val configuredFirebase = FoundationUiState.from(configuration, firebaseConfigured = true)

        assertFalse(unconfiguredFirebase.integrations.single { it.id == IntegrationId.FIREBASE }.ready)
        assertTrue(configuredFirebase.integrations.single { it.id == IntegrationId.FIREBASE }.ready)
        assertEquals(
            unconfiguredFirebase.integrations.filterNot { it.id == IntegrationId.FIREBASE },
            configuredFirebase.integrations.filterNot { it.id == IntegrationId.FIREBASE }
        )
    }

    @Test
    fun `incomplete configuration remains visible instead of pretending integrations passed`() {
        val logger = RecordingLogger()
        val viewModel = FoundationViewModel(unconfigured(), logger)

        assertTrue(viewModel.state.value.configurationIssues.isNotEmpty())
        assertTrue(
            viewModel.state.value.integrations
                .filterNot { it.id == IntegrationId.FIREBASE }
                .none { it.ready }
        )
        assertEquals(
            BuildConfig.FIREBASE_CONFIGURED,
            viewModel.state.value.integrations.single { it.id == IntegrationId.FIREBASE }.ready
        )
        assertEquals("Gürbakır", viewModel.state.value.brandDisplayName)
        assertEquals(EnvironmentId.DEVELOPMENT, viewModel.state.value.environment)
        assertEquals("foundation.started", logger.events.single().value)
    }

    private fun unconfigured(): AppConfiguration = AppConfiguration(
        brand = GurbakirBrand.configuration,
        environment = EnvironmentId.DEVELOPMENT,
        localization = LocalizationPolicy("tr", listOf("tr", "en")),
        market = MarketConfiguration("TR", "TR", "TRY"),
        protectedPersistence =
            ProtectedPersistenceConfiguration(
                ProtectedStoreIdentity("gurbakir_secure_cart_development", "gurbakir.cart.development.v1"),
                ProtectedStoreIdentity(
                    "gurbakir_secure_customer_session_development",
                    "gurbakir.customer.session.development.v1"
                )
            ),
        storefront =
            StorefrontConfiguration(
                domain = "",
                apiVersion = "2026-07",
                publicToken = ControlledPublicToken.from("")
            ),
        applicationComposition = gurbakirComposition(
            CustomerAccountConfiguration(
                clientId = "",
                issuer = "",
                authorizationEndpoint = "",
                tokenEndpoint = "",
                logoutEndpoint = "",
                graphqlEndpoint = "",
                redirectUri = "",
                userAgent = "Test-Android",
                scopes = emptySet()
            )
        )
    )

    private class RecordingLogger : ProjectLogger {
        val events = mutableListOf<LogEvent>()

        override fun info(event: LogEvent, attributes: Map<SafeAttribute, String>) {
            events += event
        }

        override fun warn(event: LogEvent, attributes: Map<SafeAttribute, String>) {
            events += event
        }
    }
}
