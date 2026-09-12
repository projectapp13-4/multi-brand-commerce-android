package com.gurbakir.mobile.di

import com.gurbakir.firebase.LocalDefaultFeatureFlags
import com.gurbakir.firebase.RemoteFeatureFlags
import com.gurbakir.firebase.createFirebaseRemoteFeatureFlags
import com.gurbakir.mobile.BuildConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object FirebaseModule {
    @Provides
    @Singleton
    fun provideRemoteFeatureFlags(): RemoteFeatureFlags =
        selectRemoteFeatureFlags(BuildConfig.FIREBASE_CONFIGURED, ::createFirebaseRemoteFeatureFlags)
}

internal fun selectRemoteFeatureFlags(
    firebaseConfigured: Boolean,
    firebaseFactory: () -> RemoteFeatureFlags
): RemoteFeatureFlags = if (firebaseConfigured) firebaseFactory() else LocalDefaultFeatureFlags()
