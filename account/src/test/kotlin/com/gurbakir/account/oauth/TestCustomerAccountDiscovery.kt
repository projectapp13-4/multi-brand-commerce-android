package com.gurbakir.account.oauth

internal fun testDiscovery(): CustomerAccountDiscoveredConfiguration = CustomerAccountDiscoveredConfiguration(
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
