package com.gurbakir.mobile.cart

import com.apollographql.apollo.ApolloClient
import com.gurbakir.account.oauth.CustomerAccountAuthorizationGrant
import com.gurbakir.account.oauth.CustomerAccountTokenClient
import com.gurbakir.account.oauth.CustomerTokenResult
import com.gurbakir.account.session.CustomerAccountSessionCoordinator
import com.gurbakir.account.session.CustomerSession
import com.gurbakir.account.session.CustomerSessionStore
import com.gurbakir.account.session.SensitiveToken
import com.gurbakir.foundation.config.CustomerAccountConfiguration
import com.gurbakir.foundation.config.REQUIRED_CUSTOMER_ACCOUNT_SCOPES
import java.time.Clock

internal fun outcomeApolloClient(endpoint: String): ApolloClient {
    val factory = Class.forName("com.gurbakir.storefront.StorefrontApolloClientFactory")
    return factory.getMethod("createClientForEndpoint", String::class.java, String::class.java)
        .invoke(factory.getField("INSTANCE").get(null), endpoint, "synthetic-only-token") as ApolloClient
}

internal fun authenticatedOutcomeSessions(clock: Clock): CustomerAccountSessionCoordinator {
    val session = CustomerSession(
        SensitiveToken.from("synthetic-current-access"),
        SensitiveToken.from("synthetic-refresh"),
        SensitiveToken.from("synthetic-id"),
        clock.instant().plusSeconds(3600)
    )
    return CustomerAccountSessionCoordinator(
        CustomerAccountConfiguration(
            "synthetic-public-client", "https://shopify.com/authentication/123456",
            "https://shop.example/authentication/oauth/authorize",
            "https://shop.example/authentication/oauth/token",
            "https://shop.example/authentication/logout", "https://shop.example/customer/api/2026-07/graphql",
            "shop.123456.synthetic://oauth/callback", "Test-Android", REQUIRED_CUSTOMER_ACCOUNT_SCOPES
        ),
        object : CustomerAccountTokenClient {
            override suspend fun exchange(grant: CustomerAccountAuthorizationGrant): CustomerTokenResult =
                error("no external exchange")
            override suspend fun refresh(refreshToken: SensitiveToken): CustomerTokenResult = error("no refresh")
        },
        object : CustomerSessionStore {
            override suspend fun read(): CustomerSession = session
            override suspend fun write(session: CustomerSession) = error("no replacement")
            override suspend fun clear() = error("no logout")
        },
        clock = clock
    )
}
