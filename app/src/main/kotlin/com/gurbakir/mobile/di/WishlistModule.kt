package com.gurbakir.mobile.di

import com.gurbakir.foundation.config.AppConfiguration
import com.gurbakir.mobile.wishlist.WishlistPartition
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object WishlistModule {
    @Provides
    fun provideWishlistPartition(configuration: AppConfiguration): WishlistPartition =
        WishlistPartition(configuration.environment.name, configuration.market.id)
}
