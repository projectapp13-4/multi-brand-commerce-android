package com.gurbakir.mobile.di

import com.gurbakir.checkout.CheckoutAdapter
import com.gurbakir.checkout.CheckoutUrlPolicy
import com.gurbakir.checkout.ShopifyCheckoutAdapter
import com.gurbakir.foundation.config.AppConfiguration
import com.gurbakir.mobile.home.DefaultHomeContentRepository
import com.gurbakir.mobile.home.HomeConfiguration
import com.gurbakir.mobile.home.HomeContentRepository
import com.gurbakir.storefront.CartCoordinator
import com.gurbakir.storefront.CartSessionStore
import com.gurbakir.storefront.StorefrontGateway
import com.gurbakir.storefront.StorefrontHomeGateway
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

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
    fun provideCartCoordinator(gateway: StorefrontGateway, store: CartSessionStore): CartCoordinator =
        CartCoordinator(gateway, store)
}
