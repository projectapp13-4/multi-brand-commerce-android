package com.gurbakir.mobile.di

import android.content.Context
import com.gurbakir.foundation.config.AppConfiguration
import com.gurbakir.mobile.address.AddressTerritoryPolicy
import com.gurbakir.mobile.address.GurbakirAddressTerritoryPolicy
import com.gurbakir.mobile.catalog.CatalogConfiguration
import com.gurbakir.mobile.catalog.GurbakirCatalogConfiguration
import com.gurbakir.mobile.config.BuildConfigurationSource
import com.gurbakir.mobile.home.GurbakirHomeConfiguration
import com.gurbakir.mobile.home.HomeConfiguration
import com.gurbakir.storefront.AndroidKeystoreCartSessionStore
import com.gurbakir.storefront.CartSessionStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ApplicationModule {
    @Provides
    @Singleton
    fun provideAppConfiguration(): AppConfiguration = BuildConfigurationSource.current

    @Provides
    fun provideHomeConfiguration(): HomeConfiguration = GurbakirHomeConfiguration.value

    @Provides
    fun provideCatalogConfiguration(): CatalogConfiguration = GurbakirCatalogConfiguration.value

    @Provides
    fun provideAddressTerritoryPolicy(): AddressTerritoryPolicy = GurbakirAddressTerritoryPolicy

    @Provides
    @Singleton
    fun provideCartSessionStore(
        @ApplicationContext context: Context,
        configuration: AppConfiguration
    ): CartSessionStore = AndroidKeystoreCartSessionStore(
        context = context,
        identity = configuration.protectedPersistence.cart
    )
}
