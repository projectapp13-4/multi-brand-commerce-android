package com.gurbakir.mobile.di

import com.gurbakir.mobile.search.LocalCommerceDatabase
import com.gurbakir.mobile.wishlist.DefaultWishlistRepository
import com.gurbakir.mobile.wishlist.RoomWishlistStore
import com.gurbakir.mobile.wishlist.WishlistClock
import com.gurbakir.mobile.wishlist.WishlistPartition
import com.gurbakir.mobile.wishlist.WishlistRepository
import com.gurbakir.mobile.wishlist.WishlistStore
import com.gurbakir.storefront.StorefrontProductGateway
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal object CoreWishlistModule {
    @Provides
    internal fun provideWishlistStore(database: LocalCommerceDatabase): WishlistStore = RoomWishlistStore(database)

    @Provides
    internal fun provideWishlistClock(): WishlistClock = WishlistClock(System::currentTimeMillis)

    @Provides
    @Singleton
    internal fun provideWishlistRepository(
        store: WishlistStore,
        gateway: StorefrontProductGateway,
        partition: WishlistPartition,
        clock: WishlistClock
    ): WishlistRepository = DefaultWishlistRepository(store, gateway, partition, clock)
}
