package com.gurbakir.mobile.di

import com.gurbakir.foundation.config.AppConfiguration
import com.gurbakir.storefront.StorefrontApi
import com.gurbakir.storefront.StorefrontApolloClientFactory
import com.gurbakir.storefront.StorefrontCatalogGateway
import com.gurbakir.storefront.StorefrontGateway
import com.gurbakir.storefront.StorefrontGatewaySet
import com.gurbakir.storefront.StorefrontHomeGateway
import com.gurbakir.storefront.StorefrontMediaPolicy
import com.gurbakir.storefront.StorefrontProductGateway
import com.gurbakir.storefront.StorefrontSearchGateway
import com.gurbakir.storefront.UnconfiguredStorefrontCatalogGateway
import com.gurbakir.storefront.UnconfiguredStorefrontGateway
import com.gurbakir.storefront.UnconfiguredStorefrontProductGateway
import com.gurbakir.storefront.UnconfiguredStorefrontSearchGateway
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal object StorefrontModule {
    @Provides
    @Singleton
    fun provideStorefrontMediaPolicy(configuration: AppConfiguration): StorefrontMediaPolicy =
        StorefrontMediaPolicy(configuration.storefront.domain)

    @Provides
    @Singleton
    fun provideStorefrontGatewaySet(
        configuration: AppConfiguration,
        mediaPolicy: StorefrontMediaPolicy
    ): StorefrontGatewaySet = if (configuration.storefront.validationIssues().isEmpty()) {
        StorefrontApolloClientFactory.createGateways(configuration.storefront, mediaPolicy)
    } else {
        StorefrontGatewaySet(
            api = UnconfiguredStorefrontGateway(),
            catalog = UnconfiguredStorefrontCatalogGateway(),
            search = UnconfiguredStorefrontSearchGateway(),
            product = UnconfiguredStorefrontProductGateway()
        )
    }

    @Provides
    fun provideStorefrontApi(gateways: StorefrontGatewaySet): StorefrontApi = gateways.api

    @Provides
    fun provideStorefrontGateway(storefrontApi: StorefrontApi): StorefrontGateway = storefrontApi

    @Provides
    fun provideStorefrontHomeGateway(storefrontApi: StorefrontApi): StorefrontHomeGateway = storefrontApi

    @Provides
    fun provideStorefrontCatalogGateway(gateways: StorefrontGatewaySet): StorefrontCatalogGateway = gateways.catalog

    @Provides
    fun provideStorefrontSearchGateway(gateways: StorefrontGatewaySet): StorefrontSearchGateway = gateways.search

    @Provides
    fun provideStorefrontProductGateway(gateways: StorefrontGatewaySet): StorefrontProductGateway = gateways.product
}
