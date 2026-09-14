package com.gurbakir.mobile.di

import android.content.Context
import com.gurbakir.checkout.CheckoutAdapter
import com.gurbakir.checkout.CheckoutUrlPolicy
import com.gurbakir.checkout.ShopifyCheckoutAdapter
import com.gurbakir.foundation.config.AppConfiguration
import com.gurbakir.mobile.home.AndroidHomeContentStore
import com.gurbakir.mobile.home.DefaultHomeContentRepository
import com.gurbakir.mobile.home.HomeConfiguration
import com.gurbakir.mobile.home.HomeContentAcceptanceCoordinator
import com.gurbakir.mobile.home.HomeContentIo
import com.gurbakir.mobile.home.HomeContentPartition
import com.gurbakir.mobile.home.HomeContentRepository
import com.gurbakir.mobile.home.HomeContentStore
import com.gurbakir.mobile.home.HomeContentValidator
import com.gurbakir.mobile.home.HomeEditorialClock
import com.gurbakir.mobile.home.HomeRemoteSource
import com.gurbakir.mobile.home.SystemHomeEditorialClock
import com.gurbakir.storefront.CartCoordinator
import com.gurbakir.storefront.CartSessionStore
import com.gurbakir.storefront.StorefrontGateway
import com.gurbakir.storefront.StorefrontHomeGateway
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
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
    @Singleton
    @Suppress("LongParameterList")
    fun provideHomeContentRepository(
        gateway: StorefrontHomeGateway,
        configuration: HomeConfiguration,
        validator: HomeContentValidator,
        store: HomeContentStore,
        coordinator: HomeContentAcceptanceCoordinator,
        clock: HomeEditorialClock,
        partition: HomeContentPartition
    ): HomeContentRepository = DefaultHomeContentRepository(
        gateway,
        configuration,
        validator,
        store,
        coordinator,
        clock,
        partition
    )

    @Provides
    @Singleton
    fun provideHomeContentValidator(): HomeContentValidator = HomeContentValidator()

    @Provides
    @Singleton
    fun provideHomeContentPartition(
        @ApplicationContext context: Context,
        appConfiguration: AppConfiguration,
        homeConfiguration: HomeConfiguration
    ): HomeContentPartition {
        val source = homeConfiguration.remoteSource as? HomeRemoteSource.ShopifyMetaobject
        return HomeContentPartition(
            applicationId = context.packageName,
            environmentId = appConfiguration.environment.name.lowercase(),
            storefrontDomain = appConfiguration.storefront.domain.lowercase(),
            rootType = source?.selector?.type ?: "mobile_home",
            rootHandle = source?.selector?.handle ?: "disabled"
        )
    }

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
