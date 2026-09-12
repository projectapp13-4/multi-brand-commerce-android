package com.gurbakir.mobile.di

import com.gurbakir.foundation.logging.AndroidProjectLogger
import com.gurbakir.foundation.logging.ProjectLogger
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DebugLoggingModule {
    @Provides
    @Singleton
    fun provideProjectLogger(): ProjectLogger = AndroidProjectLogger()
}
