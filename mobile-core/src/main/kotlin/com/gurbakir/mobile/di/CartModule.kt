package com.gurbakir.mobile.di

import com.gurbakir.mobile.cart.CartOperations
import com.gurbakir.mobile.cart.CartRepository
import com.gurbakir.mobile.cart.CoordinatedCartOperations
import com.gurbakir.mobile.cart.DefaultCartRepository
import com.gurbakir.mobile.checkout.CheckoutCartCompleter
import com.gurbakir.mobile.checkout.CoordinatedCheckoutCartCompleter
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal abstract class CartModule {
    @Binds
    @Singleton
    abstract fun bindCartOperations(implementation: CoordinatedCartOperations): CartOperations

    @Binds
    @Singleton
    abstract fun bindCartRepository(implementation: DefaultCartRepository): CartRepository

    @Binds
    @Singleton
    abstract fun bindCheckoutCartCompleter(implementation: CoordinatedCheckoutCartCompleter): CheckoutCartCompleter
}
