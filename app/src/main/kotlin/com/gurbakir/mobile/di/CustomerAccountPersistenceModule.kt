package com.gurbakir.mobile.di

import android.content.Context
import com.gurbakir.account.session.AndroidKeystoreCustomerSessionStore
import com.gurbakir.account.session.CustomerSessionStore
import com.gurbakir.foundation.config.AppConfiguration
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object CustomerAccountPersistenceModule {
    @Provides
    @Singleton
    fun provideCustomerSessionStore(
        @ApplicationContext context: Context,
        configuration: AppConfiguration
    ): CustomerSessionStore = AndroidKeystoreCustomerSessionStore(
        context = context,
        identity = configuration.protectedPersistence.customerSession
    )
}
