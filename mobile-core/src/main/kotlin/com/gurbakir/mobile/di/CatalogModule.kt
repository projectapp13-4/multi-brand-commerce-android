package com.gurbakir.mobile.di

import com.gurbakir.mobile.catalog.CatalogConfiguration
import com.gurbakir.mobile.catalog.CatalogRepository
import com.gurbakir.mobile.catalog.DefaultCatalogRepository
import com.gurbakir.storefront.StorefrontCatalogGateway
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal object CatalogModule {
    @Provides
    fun provideCatalogRepository(
        catalogGateway: StorefrontCatalogGateway,
        configuration: CatalogConfiguration
    ): CatalogRepository = DefaultCatalogRepository(catalogGateway, configuration)
}
