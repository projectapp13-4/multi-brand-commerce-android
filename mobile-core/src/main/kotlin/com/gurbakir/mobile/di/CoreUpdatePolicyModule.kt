package com.gurbakir.mobile.di

import android.content.Context
import com.gurbakir.mobile.update.AndroidUpdatePolicyStore
import com.gurbakir.mobile.update.CurrentAppVersionCode
import com.gurbakir.mobile.update.DefaultUpdatePolicyController
import com.gurbakir.mobile.update.UpdatePolicyClock
import com.gurbakir.mobile.update.UpdatePolicyController
import com.gurbakir.mobile.update.UpdatePolicyRemoteGateway
import com.gurbakir.mobile.update.UpdatePolicyStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal object CoreUpdatePolicyModule {
    @Provides
    @Singleton
    internal fun provideUpdatePolicyStore(@ApplicationContext context: Context): UpdatePolicyStore =
        AndroidUpdatePolicyStore(context)

    @Provides
    internal fun provideUpdatePolicyClock(): UpdatePolicyClock = UpdatePolicyClock(System::currentTimeMillis)

    @Provides
    @Singleton
    internal fun provideUpdatePolicyController(
        remoteGateway: UpdatePolicyRemoteGateway,
        store: UpdatePolicyStore,
        currentVersionCode: CurrentAppVersionCode,
        clock: UpdatePolicyClock
    ): UpdatePolicyController = DefaultUpdatePolicyController(remoteGateway, store, currentVersionCode, clock)
}
