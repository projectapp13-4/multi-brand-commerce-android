package com.gurbakir.mobile.di

import com.gurbakir.mobile.search.DefaultProductSearchRepository
import com.gurbakir.mobile.search.DefaultSearchHistoryRepository
import com.gurbakir.mobile.search.LocalCommerceDatabase
import com.gurbakir.mobile.search.ProductSearchRepository
import com.gurbakir.mobile.search.RoomSearchHistoryStore
import com.gurbakir.mobile.search.SearchHistoryClock
import com.gurbakir.mobile.search.SearchHistoryNormalizationPolicy
import com.gurbakir.mobile.search.SearchHistoryPartition
import com.gurbakir.mobile.search.SearchHistoryRepository
import com.gurbakir.mobile.search.SearchHistoryStore
import com.gurbakir.storefront.StorefrontSearchGateway
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal object CoreSearchModule {
    @Provides
    internal fun provideProductSearchRepository(gateway: StorefrontSearchGateway): ProductSearchRepository =
        DefaultProductSearchRepository(gateway)

    @Provides
    internal fun provideSearchHistoryStore(database: LocalCommerceDatabase): SearchHistoryStore =
        RoomSearchHistoryStore(database)

    @Provides
    internal fun provideSearchHistoryClock(): SearchHistoryClock = SearchHistoryClock(System::currentTimeMillis)

    @Provides
    @Singleton
    internal fun provideSearchHistoryRepository(
        store: SearchHistoryStore,
        partition: SearchHistoryPartition,
        normalizationPolicy: SearchHistoryNormalizationPolicy,
        clock: SearchHistoryClock
    ): SearchHistoryRepository = DefaultSearchHistoryRepository(store, partition, normalizationPolicy, clock)
}
