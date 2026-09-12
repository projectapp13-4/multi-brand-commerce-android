package com.gurbakir.mobile.di

import com.gurbakir.firebase.RemoteFeatureFlags
import com.gurbakir.mobile.BuildConfig
import com.gurbakir.mobile.update.CurrentAppVersionCode
import com.gurbakir.mobile.update.FirebaseUpdatePolicyRemoteGateway
import com.gurbakir.mobile.update.UpdatePolicyRemoteGateway
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Provider

@Module
@InstallIn(SingletonComponent::class)
object UpdatePolicyModule {
    @Provides
    fun provideCurrentAppVersionCode(): CurrentAppVersionCode = CurrentAppVersionCode(BuildConfig.VERSION_CODE)

    @Provides
    fun provideUpdatePolicyRemoteGateway(remoteFeatureFlags: Provider<RemoteFeatureFlags>): UpdatePolicyRemoteGateway =
        selectUpdatePolicyRemoteGateway(BuildConfig.FIREBASE_CONFIGURED, remoteFeatureFlags::get)
}

internal fun selectUpdatePolicyRemoteGateway(
    firebaseConfigured: Boolean,
    remoteFeatureFlagsProvider: () -> RemoteFeatureFlags
): UpdatePolicyRemoteGateway = if (firebaseConfigured) {
    FirebaseUpdatePolicyRemoteGateway(remoteFeatureFlagsProvider())
} else {
    UpdatePolicyRemoteGateway { com.gurbakir.mobile.update.UpdatePolicyRefreshResult.LocalDefaults }
}
