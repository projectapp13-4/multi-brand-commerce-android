package com.gurbakir.account.oauth

import com.gurbakir.account.session.SensitiveToken
import com.gurbakir.foundation.config.CustomerAccountConfiguration
import com.gurbakir.foundation.config.REQUIRED_CUSTOMER_ACCOUNT_SCOPES
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CustomerAccountTokenClientTest {
    @Test
    fun `exchange and refresh use the application-owned user agent`() {
        val configuration = configuration()
        val discovery = discovery()
        val exchangePlan =
            CustomerTokenExchangePlan(
                clientId = configuration.clientId,
                discovery = discovery,
                redirectUri = configuration.redirectUri,
                authorizationCode = SensitiveAuthorizationCode.from("fixture-code"),
                codeVerifier = SensitiveCodeVerifier.from("fixture-verifier"),
                expectedNonce = SensitiveNonce.from("fixture-nonce")
            )
        val refreshPlan =
            CustomerTokenRefreshPlan(
                clientId = configuration.clientId,
                discovery = discovery,
                refreshToken = SensitiveToken.from("fixture-refresh")
            )

        val exchange = ShopifyCustomerAccountTokenRequestFactory.exchange(configuration, exchangePlan)
        val refresh = ShopifyCustomerAccountTokenRequestFactory.refresh(configuration, refreshPlan)

        assertEquals("Fixture-Android", exchange.header("User-Agent"))
        assertEquals("Fixture-Android", refresh.header("User-Agent"))
        assertEquals(null, exchange.header("Authorization"))
        assertEquals(null, refresh.header("Authorization"))
    }

    private fun configuration() = CustomerAccountConfiguration(
        clientId = "fixture-public-client",
        issuer = "https://shopify.com/authentication/123456",
        authorizationEndpoint = "https://shop.example/authentication/oauth/authorize",
        tokenEndpoint = "https://shop.example/authentication/oauth/token",
        logoutEndpoint = "https://shop.example/authentication/logout",
        graphqlEndpoint = "https://shop.example/customer/api/2026-07/graphql",
        redirectUri = "shop.123456.fixture://oauth/callback",
        userAgent = "Fixture-Android",
        scopes = REQUIRED_CUSTOMER_ACCOUNT_SCOPES
    )

    private fun discovery() = CustomerAccountDiscoveredConfiguration(
        issuer = "https://shopify.com/authentication/123456",
        authorizationEndpoint = "https://shop.example/authentication/oauth/authorize",
        tokenEndpoint = "https://shop.example/authentication/oauth/token",
        logoutEndpoint = "https://shop.example/authentication/logout",
        graphqlEndpoint = "https://shop.example/customer/api/2026-07/graphql",
        openIdConfigurationJson =
            """
                {
                  "issuer": "https://shopify.com/authentication/123456",
                  "authorization_endpoint": "https://shop.example/authentication/oauth/authorize",
                  "token_endpoint": "https://shop.example/authentication/oauth/token",
                  "end_session_endpoint": "https://shop.example/authentication/logout",
                  "jwks_uri": "https://shop.example/authentication/.well-known/jwks.json"
                }
            """.trimIndent()
    )
}
