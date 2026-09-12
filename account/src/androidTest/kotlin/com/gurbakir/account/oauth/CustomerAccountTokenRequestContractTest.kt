package com.gurbakir.account.oauth

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gurbakir.foundation.config.CustomerAccountConfiguration
import com.gurbakir.foundation.config.REQUIRED_CUSTOMER_ACCOUNT_SCOPES
import okhttp3.FormBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CustomerAccountTokenRequestContractTest {
    @Test
    fun publicMobileTokenRequestFollowsShopifyPkceContractWithoutNonceParameter() {
        val configuration = configuration()
        val grant =
            CustomerAccountAuthorizationGrant(
                code = SensitiveAuthorizationCode.from("synthetic-code"),
                codeVerifier = SensitiveCodeVerifier.from("synthetic-verifier"),
                expectedNonce = SensitiveNonce.from("synthetic-nonce"),
                redirectUri = configuration.redirectUri,
                discovery = discovery()
            )
        val request =
            ShopifyCustomerAccountTokenRequestFactory.exchange(
                configuration,
                CustomerAccountTokenRequestPlanner(configuration).exchange(grant)
            )
        val body = request.body as FormBody
        val fields = (0 until body.size).associate { body.name(it) to body.value(it) }

        assertEquals("POST", request.method)
        assertEquals(discovery().tokenEndpoint, request.url.toString())
        assertEquals(
            setOf("grant_type", "client_id", "redirect_uri", "code", "code_verifier"),
            fields.keys
        )
        assertEquals("authorization_code", fields["grant_type"])
        assertEquals(configuration.clientId, fields["client_id"])
        assertEquals(configuration.redirectUri, fields["redirect_uri"])
        assertEquals("synthetic-code", fields["code"])
        assertEquals("synthetic-verifier", fields["code_verifier"])
        assertFalse(fields.containsKey("nonce"))
        assertFalse(fields.containsKey("client_secret"))
        assertEquals(null, request.header("Authorization"))
    }

    private fun configuration() = CustomerAccountConfiguration(
        clientId = "public-client-id",
        issuer = "https://shopify.com/authentication/123456",
        authorizationEndpoint = "https://shop.example/authentication/oauth/authorize",
        tokenEndpoint = "https://shop.example/authentication/oauth/token",
        logoutEndpoint = "https://shop.example/authentication/logout",
        graphqlEndpoint = "https://shop.example/customer/api/2026-07/graphql",
        redirectUri = "shop.123456.gurbakir://oauth/callback",
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
