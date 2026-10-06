package com.gurbakir.storefront

import com.apollographql.apollo.ApolloClient
import com.gurbakir.foundation.config.StorefrontConfiguration

object StorefrontApolloClientFactory {
    fun createGateways(
        configuration: StorefrontConfiguration,
        mediaPolicy: StorefrontMediaPolicy
    ): StorefrontGatewaySet {
        val client = createClient(configuration)
        return StorefrontGatewaySet(
            api = ApolloStorefrontGateway(client, mediaPolicy),
            catalog = ApolloStorefrontCatalogGateway(client, mediaPolicy),
            search = ApolloStorefrontSearchGateway(client, mediaPolicy),
            product = ApolloStorefrontProductGateway(client, mediaPolicy)
        )
    }

    internal fun createClient(configuration: StorefrontConfiguration): ApolloClient {
        require(configuration.validationIssues().isEmpty()) {
            "Storefront configuration must be valid before creating a network client."
        }
        val endpoint = "https://${configuration.domain}/api/${configuration.apiVersion}/graphql.json"
        return configuration.publicToken.use { token -> createClientForEndpoint(endpoint, token) }
    }

    @JvmName("createClientForEndpoint")
    internal fun createClientForEndpoint(endpoint: String, publicToken: String): ApolloClient = ApolloClient.Builder()
        .networkTransport(StorefrontNetworkTransport(endpoint))
        .retryOnError(false)
        .addHttpHeader("X-Shopify-Storefront-Access-Token", publicToken)
        .build()
}
