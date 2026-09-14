package com.gurbakir.mobile.di

import android.content.Context
import androidx.room.Room
import com.gurbakir.foundation.config.AppConfiguration
import com.gurbakir.mobile.BuildConfig
import com.gurbakir.mobile.search.LocalCommerceDatabase
import com.gurbakir.mobile.search.SearchHistoryNormalizationPolicy
import com.gurbakir.mobile.search.SearchHistoryPartition
import com.gurbakir.mobile.wishlist.WISHLIST_MIGRATION_1_2
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SearchModule {
    @Provides
    @Singleton
    fun provideLocalCommerceDatabase(@ApplicationContext context: Context): LocalCommerceDatabase =
        Room.databaseBuilder(context, LocalCommerceDatabase::class.java, BuildConfig.DATABASE_NAME)
            .addMigrations(WISHLIST_MIGRATION_1_2)
            .build()

    @Provides
    fun provideSearchHistoryPartition(configuration: AppConfiguration): SearchHistoryPartition =
        SearchHistoryPartition(configuration.environment.name, configuration.market.id)

    @Provides
    fun provideSearchHistoryNormalizationPolicy(): SearchHistoryNormalizationPolicy =
        SearchHistoryNormalizationPolicy(BuildConfig.SEARCH_NORMALIZATION_LOCALE)
}
