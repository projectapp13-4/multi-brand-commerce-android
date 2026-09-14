package com.gurbakir.account.oauth

import com.gurbakir.account.session.SensitiveToken
import com.gurbakir.foundation.config.CustomerAccountConfiguration
import com.gurbakir.foundation.config.REQUIRED_CUSTOMER_ACCOUNT_SCOPES
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class CustomerAccountTokenRequestPlannerTest {
    @Test
    fun `exchange plan preserves PKCE nonce and redirect without rendering sensitive values`() {
        val planner = CustomerAccountTokenRequestPlanner(configuration())
        val grant = grant()

        val plan = planner.exchange(grant)

        assertEquals("synthetic-code", plan.authorizationCode.use { it })
        assertEquals("synthetic-verifier", plan.codeVerifier.use { it })
        assertEquals("synthetic-nonce", plan.expectedNonce.use { it })
        assertFalse(plan.toString().contains("synthetic-code"))
        assertFalse(plan.toString().contains("synthetic-verifier"))
    }

    @Test
    fun `redirect mismatch is rejected before token request construction`() {
        val planner = CustomerAccountTokenRequestPlanner(configuration())
        val mismatched = grant().copy(redirectUri = "shop.123456.attacker://oauth/callback")

        assertThrows(IllegalArgumentException::class.java) { planner.exchange(mismatched) }
    }

    @Test
    fun `refresh plan never renders the refresh token`() {
        val planner = CustomerAccountTokenRequestPlanner(configuration())
        val plan = planner.refresh(SensitiveToken.from("synthetic-refresh"), testDiscovery())

        assertEquals("synthetic-refresh", plan.refreshToken.use { it })
        assertFalse(plan.toString().contains("synthetic-refresh"))
    }

    @Test
    fun `mobile end-session plan carries the required id token without rendering it`() {
        val plan =
            CustomerAccountTokenRequestPlanner(configuration()).logout(
                SensitiveToken.from("synthetic-id-token"),
                testDiscovery()
            )

        assertEquals("https://shop.example/authentication/logout", plan.discovery.logoutEndpoint)
        assertEquals("synthetic-id-token", plan.idToken.use { it })
        assertFalse(plan.toString().contains("synthetic-id-token"))
    }

    private fun grant() = CustomerAccountAuthorizationGrant(
        code = SensitiveAuthorizationCode.from("synthetic-code"),
        codeVerifier = SensitiveCodeVerifier.from("synthetic-verifier"),
        expectedNonce = SensitiveNonce.from("synthetic-nonce"),
        redirectUri = "shop.123456.gurbakir://oauth/callback",
        discovery = testDiscovery()
    )

    private fun configuration() = CustomerAccountConfiguration(
        clientId = "public-client-id",
        issuer = "https://shopify.com/authentication/123456",
        authorizationEndpoint = "https://shop.example/authentication/oauth/authorize",
        tokenEndpoint = "https://shop.example/authentication/oauth/token",
        logoutEndpoint = "https://shop.example/authentication/logout",
        graphqlEndpoint = "https://shop.example/customer/api/2026-07/graphql",
        redirectUri = "shop.123456.gurbakir://oauth/callback",
        userAgent = "Test-Android",
        scopes = REQUIRED_CUSTOMER_ACCOUNT_SCOPES
    )
}
