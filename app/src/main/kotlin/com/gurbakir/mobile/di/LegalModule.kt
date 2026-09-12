package com.gurbakir.mobile.di

import com.gurbakir.mobile.accountdeletion.DeletionPageSource
import com.gurbakir.mobile.accountdeletion.GurbakirDeletionPages
import com.gurbakir.mobile.legal.LegalSupportRepository
import com.gurbakir.mobile.legal.PackagedLegalSupportRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class LegalModule {
    @Binds
    @Singleton
    internal abstract fun bindDeletionPageSource(adapter: GurbakirDeletionPages): DeletionPageSource

    @Binds
    @Singleton
    abstract fun bindLegalSupportRepository(repository: PackagedLegalSupportRepository): LegalSupportRepository
}
