package com.gurbakir.account.oauth

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CustomerAccountDiscoveryParserTest {
    private val parser = CustomerAccountDiscoveryParser()
    private val issuer = "https://shopify.com/authentication/123456"

    @Test
    fun `valid Shopify discovery documents produce a typed endpoint snapshot`() {
        val result = parser.parse(issuer, validOpenIdDocument(), VALID_API_DOCUMENT)

        val success = assertInstanceOf(CustomerAccountDiscoveryResult.Success::class.java, result)
        assertEquals(issuer, success.configuration.issuer)
        assertEquals("https://shop.example/authentication/oauth/token", success.configuration.tokenEndpoint)
        assertEquals("https://shop.example/customer/api/2026-07/graphql", success.configuration.graphqlEndpoint)
        assertTrue(!success.configuration.toString().contains("jwks.json"))
    }

    @Test
    fun `issuer mismatch fails closed before any endpoint can be used`() {
        val result =
            parser.parse(
                "https://shopify.com/authentication/other-shop",
                validOpenIdDocument(),
                VALID_API_DOCUMENT
            )

        assertEquals(
            CustomerAccountDiscoveryResult.Failure(CustomerAccountDiscoveryFailure.ISSUER_MISMATCH),
            result
        )
    }

    @Test
    fun `missing S256 or non-HTTPS endpoint is rejected`() {
        val missingS256 = validOpenIdDocument().replace("\"S256\"", "\"plain\"")
        val insecureApi = VALID_API_DOCUMENT.replace("https://", "http://")

        assertEquals(
            CustomerAccountDiscoveryResult.Failure(
                CustomerAccountDiscoveryFailure.UNSUPPORTED_SECURITY_CAPABILITIES
            ),
            parser.parse(issuer, missingS256, VALID_API_DOCUMENT)
        )
        assertEquals(
            CustomerAccountDiscoveryResult.Failure(CustomerAccountDiscoveryFailure.INVALID_DOCUMENT),
            parser.parse(issuer, validOpenIdDocument(), insecureApi)
        )
    }

    private fun validOpenIdDocument() =
        """
        {
          "issuer": "$issuer",
          "authorization_endpoint": "https://shop.example/authentication/oauth/authorize",
          "token_endpoint": "https://shop.example/authentication/oauth/token",
          "end_session_endpoint": "https://shop.example/authentication/logout",
          "jwks_uri": "https://shop.example/authentication/.well-known/jwks.json",
          "code_challenge_methods_supported": ["S256"],
          "grant_types_supported": ["authorization_code", "refresh_token"],
          "id_token_signing_alg_values_supported": ["RS256"]
        }
        """.trimIndent()

    private companion object {
        const val VALID_API_DOCUMENT =
            """{"graphql_api":"https://shop.example/customer/api/2026-07/graphql"}"""
    }
}
