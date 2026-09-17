package com.projectapp134.multibrandtrial.di

import android.content.Context
import androidx.room.Room
import com.gurbakir.account.session.AndroidKeystoreCustomerSessionStore
import com.gurbakir.account.session.CustomerSessionStore
import com.gurbakir.firebase.LocalDefaultFeatureFlags
import com.gurbakir.firebase.RemoteConfigResult
import com.gurbakir.firebase.RemoteFeatureFlags
import com.gurbakir.firebase.createFirebaseRemoteFeatureFlags
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
import com.gurbakir.mobile.update.UpdatePolicySnapshot
import com.gurbakir.mobile.wishlist.WISHLIST_MIGRATION_1_2
import com.gurbakir.mobile.wishlist.WishlistPartition
import com.gurbakir.storefront.AndroidKeystoreCartSessionStore
import com.gurbakir.storefront.CartSessionStore
import com.projectapp134.multibrandtrial.BuildConfig
import com.projectapp134.multibrandtrial.accountdeletion.TrialDeletionPages
import com.projectapp134.multibrandtrial.config.TrialConfiguration
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Provider
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object TrialModule {
    @Provides
    @Singleton
    fun provideAppConfiguration(): AppConfiguration = TrialConfiguration.app

    @Provides
    fun provideHomeConfiguration(): HomeConfiguration = TrialConfiguration.home

    @Provides
    fun provideCatalogConfiguration(): CatalogConfiguration = TrialConfiguration.catalog

    @Provides
    fun provideAddressTerritoryPolicy(): AddressTerritoryPolicy = TrialConfiguration.addressPolicy

    @Provides
    fun provideSearchHistoryPartition(): SearchHistoryPartition = TrialConfiguration.searchPartition

    @Provides
    fun provideSearchHistoryNormalizationPolicy(): SearchHistoryNormalizationPolicy =
        TrialConfiguration.searchNormalizationPolicy

    @Provides
    fun provideWishlistPartition(): WishlistPartition = TrialConfiguration.wishlistPartition
}

@Module
@InstallIn(SingletonComponent::class)
object TrialRuntimeModule {
    @Provides
    fun provideDeletionPageSource(): DeletionPageSource = TrialDeletionPages(TrialConfiguration.legal)

    @Provides
    fun provideCurrentAppVersionCode(): CurrentAppVersionCode = CurrentAppVersionCode(BuildConfig.VERSION_CODE)

    @Provides
    @Singleton
    fun provideRemoteFeatureFlags(): RemoteFeatureFlags =
        selectRemoteFeatureFlags(BuildConfig.FIREBASE_CONFIGURED, ::createFirebaseRemoteFeatureFlags)

    @Provides
    fun provideUpdatePolicyRemoteGateway(remoteFeatureFlags: Provider<RemoteFeatureFlags>): UpdatePolicyRemoteGateway =
        if (BuildConfig.FIREBASE_CONFIGURED) {
            TrialFirebaseUpdatePolicyRemoteGateway(remoteFeatureFlags.get())
        } else {
            UpdatePolicyRemoteGateway { UpdatePolicyRefreshResult.LocalDefaults }
        }
}

@Module
@InstallIn(SingletonComponent::class)
object TrialPersistenceModule {
    @Provides
    @Singleton
    fun provideLocalCommerceDatabase(@ApplicationContext context: Context): LocalCommerceDatabase =
        Room.databaseBuilder(context, LocalCommerceDatabase::class.java, BuildConfig.DATABASE_NAME)
            .addMigrations(WISHLIST_MIGRATION_1_2)
            .build()

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

internal fun selectRemoteFeatureFlags(
    firebaseConfigured: Boolean,
    firebaseFactory: () -> RemoteFeatureFlags
): RemoteFeatureFlags = if (firebaseConfigured) firebaseFactory() else LocalDefaultFeatureFlags()

private class TrialFirebaseUpdatePolicyRemoteGateway(private val flags: RemoteFeatureFlags) :
    UpdatePolicyRemoteGateway {
    override suspend fun refresh(): UpdatePolicyRefreshResult = when (val result = flags.refresh()) {
        RemoteConfigResult.LocalDefaults -> UpdatePolicyRefreshResult.LocalDefaults

        is RemoteConfigResult.Fetched -> {
            val snapshot = flags.policySnapshot()
            UpdatePolicyRefreshResult.Fetched(
                snapshot =
                    UpdatePolicySnapshot(
                        maintenanceMessageEnabled = snapshot.maintenanceMessageEnabled,
                        checkoutPreloadEnabled = snapshot.checkoutPreloadEnabled,
                        optionalUpdateMessageEnabled = snapshot.optionalUpdateMessageEnabled,
                        recommendedVersionCode = snapshot.recommendedVersionCode,
                        policyRevision = snapshot.policyRevision
                    ),
                fetchedAtEpochMillis = result.fetchedAtEpochMillis
            )
        }
    }
}
