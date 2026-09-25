package com.example.gate2synthetic.di

import android.content.Context
import androidx.room.Room
import com.example.gate2synthetic.BuildConfig
import com.example.gate2synthetic.R
import com.example.gate2synthetic.config.Gate2SyntheticConfiguration
import com.gurbakir.account.session.AndroidKeystoreCustomerSessionStore
import com.gurbakir.account.session.CustomerSessionStore
import com.gurbakir.foundation.config.AppConfiguration
import com.gurbakir.mobile.accountdeletion.DeletionPageDescriptor
import com.gurbakir.mobile.accountdeletion.DeletionPageId
import com.gurbakir.mobile.accountdeletion.DeletionPageSource
import com.gurbakir.mobile.address.AddressTerritoryPolicy
import com.gurbakir.mobile.catalog.CatalogConfiguration
import com.gurbakir.mobile.home.HomeConfiguration
import com.gurbakir.mobile.search.LocalCommerceDatabase
import com.gurbakir.mobile.search.SearchHistoryNormalizationPolicy
import com.gurbakir.mobile.search.SearchHistoryPartition
import com.gurbakir.mobile.update.CurrentAppVersionCode
import com.gurbakir.mobile.update.UpdatePolicyRemoteGateway
import com.gurbakir.mobile.wishlist.WishlistPartition
import com.gurbakir.storefront.AndroidKeystoreCartSessionStore
import com.gurbakir.storefront.CartSessionStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

internal const val SYNTHETIC_DATABASE_NAME = "gate2-synthetic-local.db"

@Module
@InstallIn(SingletonComponent::class)
object Gate2SyntheticModule {
    @Provides
    @Singleton
    fun provideAppConfiguration(): AppConfiguration = Gate2SyntheticConfiguration.app

    @Provides
    fun provideHomeConfiguration(): HomeConfiguration = Gate2SyntheticConfiguration.home

    @Provides
    fun provideCatalogConfiguration(): CatalogConfiguration = Gate2SyntheticConfiguration.catalog

    @Provides
    fun provideAddressTerritoryPolicy(): AddressTerritoryPolicy = Gate2SyntheticConfiguration.addressPolicy

    @Provides
    fun provideSearchHistoryPartition(): SearchHistoryPartition = Gate2SyntheticConfiguration.searchPartition

    @Provides
    fun provideSearchHistoryNormalizationPolicy(): SearchHistoryNormalizationPolicy =
        Gate2SyntheticConfiguration.searchNormalizationPolicy

    @Provides
    fun provideWishlistPartition(): WishlistPartition = Gate2SyntheticConfiguration.wishlistPartition

    @Provides
    fun provideDeletionPageSource(): DeletionPageSource = DeletionPageSource {
        listOf(DeletionPageDescriptor(DeletionPageId.PRIVACY, R.string.synthetic_privacy))
    }

    @Provides
    fun provideCurrentAppVersionCode(): CurrentAppVersionCode = CurrentAppVersionCode(BuildConfig.VERSION_CODE)

    @Provides
    fun provideUpdatePolicyRemoteGateway(): UpdatePolicyRemoteGateway = Gate2SyntheticConfiguration.updatePolicyGateway
}

@Module
@InstallIn(SingletonComponent::class)
object Gate2SyntheticPersistenceModule {
    @Provides
    @Singleton
    fun provideLocalCommerceDatabase(@ApplicationContext context: Context): LocalCommerceDatabase =
        Room.databaseBuilder(context, LocalCommerceDatabase::class.java, SYNTHETIC_DATABASE_NAME).build()

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
