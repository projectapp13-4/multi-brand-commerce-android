package com.tumiart.mobile.di

import android.content.Context
import androidx.room.Room
import com.gurbakir.account.session.AndroidKeystoreCustomerSessionStore
import com.gurbakir.account.session.CustomerSessionStore
import com.gurbakir.foundation.config.AppConfiguration
import com.gurbakir.mobile.accountdeletion.DeletionPageSource
import com.gurbakir.mobile.address.AddressTerritoryPolicy
import com.gurbakir.mobile.catalog.CatalogConfiguration
import com.gurbakir.mobile.home.HomeConfiguration
import com.gurbakir.mobile.search.LocalCommerceDatabase
import com.gurbakir.mobile.search.SearchHistoryNormalizationPolicy
import com.gurbakir.mobile.search.SearchHistoryPartition
import com.gurbakir.mobile.update.CurrentAppVersionCode
import com.gurbakir.mobile.update.UpdatePolicyRefreshResult
import com.gurbakir.mobile.update.UpdatePolicyRemoteGateway
import com.gurbakir.mobile.wishlist.WISHLIST_MIGRATION_1_2
import com.gurbakir.mobile.wishlist.WishlistPartition
import com.gurbakir.storefront.AndroidKeystoreCartSessionStore
import com.gurbakir.storefront.CartSessionStore
import com.tumiart.mobile.BuildConfig
import com.tumiart.mobile.config.TumiArtConfiguration
import com.tumiart.mobile.legal.TumiArtLegalPages
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object TumiArtModule {
    @Provides
    @Singleton
    fun provideAppConfiguration(): AppConfiguration = TumiArtConfiguration.app

    @Provides
    fun provideHomeConfiguration(): HomeConfiguration = TumiArtConfiguration.home

    @Provides
    fun provideCatalogConfiguration(): CatalogConfiguration = TumiArtConfiguration.catalog

    @Provides
    fun provideAddressTerritoryPolicy(): AddressTerritoryPolicy = TumiArtConfiguration.addressPolicy

    @Provides
    fun provideSearchHistoryPartition(): SearchHistoryPartition = TumiArtConfiguration.searchPartition

    @Provides
    fun provideSearchHistoryNormalizationPolicy(): SearchHistoryNormalizationPolicy =
        TumiArtConfiguration.searchNormalizationPolicy

    @Provides
    fun provideWishlistPartition(): WishlistPartition = TumiArtConfiguration.wishlistPartition

    @Provides
    fun provideDeletionPageSource(): DeletionPageSource = TumiArtLegalPages()

    @Provides
    fun provideCurrentAppVersionCode(): CurrentAppVersionCode = CurrentAppVersionCode(BuildConfig.VERSION_CODE)

    @Provides
    fun provideUpdatePolicyRemoteGateway(): UpdatePolicyRemoteGateway =
        UpdatePolicyRemoteGateway { UpdatePolicyRefreshResult.LocalDefaults }
}

@Module
@InstallIn(SingletonComponent::class)
object TumiArtPersistenceModule {
    @Provides
    @Singleton
    fun provideLocalCommerceDatabase(@ApplicationContext context: Context): LocalCommerceDatabase =
        Room.databaseBuilder(context, LocalCommerceDatabase::class.java, BuildConfig.DATABASE_NAME)
            .addMigrations(WISHLIST_MIGRATION_1_2).build()

    @Provides
    @Singleton
    fun provideCartSessionStore(
        @ApplicationContext context: Context,
        configuration: AppConfiguration
    ): CartSessionStore = AndroidKeystoreCartSessionStore(context, configuration.protectedPersistence.cart)

    @Provides
    @Singleton
    fun provideCustomerSessionStore(
        @ApplicationContext context: Context,
        configuration: AppConfiguration
    ): CustomerSessionStore =
        AndroidKeystoreCustomerSessionStore(context, configuration.protectedPersistence.customerSession)
}
