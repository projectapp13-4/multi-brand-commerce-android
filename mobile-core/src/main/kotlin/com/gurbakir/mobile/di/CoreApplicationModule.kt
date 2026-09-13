package com.gurbakir.mobile.di

import com.gurbakir.checkout.CheckoutAdapter
import com.gurbakir.checkout.CheckoutUrlPolicy
import com.gurbakir.checkout.ShopifyCheckoutAdapter
import com.gurbakir.foundation.config.AppConfiguration
import com.gurbakir.mobile.home.AndroidHomeContentStore
import com.gurbakir.mobile.home.DefaultHomeContentRepository
import com.gurbakir.mobile.home.HomeConfiguration
import com.gurbakir.mobile.home.HomeContentIo
import com.gurbakir.mobile.home.HomeContentRepository
import com.gurbakir.mobile.home.HomeContentStore
import com.gurbakir.mobile.home.HomeEditorialClock
import com.gurbakir.mobile.home.SystemHomeEditorialClock
import com.gurbakir.storefront.CartCoordinator
import com.gurbakir.storefront.CartSessionStore
import com.gurbakir.storefront.StorefrontGateway
import com.gurbakir.storefront.StorefrontHomeGateway
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

@Module
@InstallIn(SingletonComponent::class)
internal object CoreApplicationModule {
    @Provides
    @Singleton
    fun provideCheckoutAdapter(configuration: AppConfiguration): CheckoutAdapter = ShopifyCheckoutAdapter(
        checkoutUrlPolicy = CheckoutUrlPolicy(setOf(configuration.storefront.domain))
    )

    @Provides
    fun provideHomeContentRepository(
        gateway: StorefrontHomeGateway,
        configuration: HomeConfiguration
    ): HomeContentRepository = DefaultHomeContentRepository(gateway, configuration)

    @Provides
    @Singleton
    fun provideHomeContentStore(store: AndroidHomeContentStore): HomeContentStore = store

    @Provides
    fun provideHomeEditorialClock(clock: SystemHomeEditorialClock): HomeEditorialClock = clock

    @Provides
    @HomeContentIo
    fun provideHomeContentIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides
    @Singleton
    fun provideCartCoordinator(gateway: StorefrontGateway, store: CartSessionStore): CartCoordinator =
        CartCoordinator(gateway, store)
}
