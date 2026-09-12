package com.gurbakir.mobile.di

import android.content.Context
import com.gurbakir.account.CustomerAccountGateway
import com.gurbakir.account.oauth.CustomerAccountAuthorizationCoordinator
import com.gurbakir.account.session.CustomerAccountSessionCoordinator
import com.gurbakir.checkout.CheckoutAdapter
import com.gurbakir.firebase.PushRegistrationCoordinator
import com.gurbakir.firebase.RemoteFeatureFlags
import com.gurbakir.firebase.createFirebasePushRegistrationCoordinator
import com.gurbakir.mobile.BuildConfig
import com.gurbakir.mobile.BuildVariantFirebaseProofTargetRecorder
import com.gurbakir.mobile.CommerceProofController
import com.gurbakir.mobile.CustomerAccountProofController
import com.gurbakir.mobile.DefaultCommerceProofController
import com.gurbakir.mobile.DefaultCustomerAccountProofController
import com.gurbakir.mobile.DefaultFirebaseProofController
import com.gurbakir.mobile.FirebaseProofController
import com.gurbakir.mobile.FirebaseProofTargetRecorder
import com.gurbakir.storefront.CartCoordinator
import com.gurbakir.storefront.StorefrontGateway
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ProofModule {
    @Provides
    @Singleton
    fun providePushRegistrationCoordinator(@ApplicationContext context: Context): PushRegistrationCoordinator =
        createConfiguredPushRegistrationCoordinator(BuildConfig.FIREBASE_CONFIGURED) {
            createFirebasePushRegistrationCoordinator(context)
        }

    @Provides
    @Singleton
    fun provideFirebaseProofTargetRecorder(@ApplicationContext context: Context): FirebaseProofTargetRecorder =
        BuildVariantFirebaseProofTargetRecorder(context)

    @Provides
    fun provideFirebaseProofController(
        remoteFeatureFlags: RemoteFeatureFlags,
        pushRegistrationCoordinator: PushRegistrationCoordinator,
        targetRecorder: FirebaseProofTargetRecorder
    ): FirebaseProofController = DefaultFirebaseProofController(
        remoteFeatureFlags = remoteFeatureFlags,
        pushRegistrationCoordinator = pushRegistrationCoordinator,
        proofTargetRecorder = targetRecorder
    )

    @Provides
    fun provideCommerceProofController(
        gateway: StorefrontGateway,
        cartCoordinator: CartCoordinator,
        customerSessionCoordinator: CustomerAccountSessionCoordinator,
        checkoutAdapter: CheckoutAdapter
    ): CommerceProofController = DefaultCommerceProofController(
        storefrontGateway = gateway,
        cartCoordinator = cartCoordinator,
        customerSessionCoordinator = customerSessionCoordinator,
        checkoutAdapter = checkoutAdapter
    )

    @Provides
    @Singleton
    fun provideCustomerAccountProofController(
        authorizationCoordinator: CustomerAccountAuthorizationCoordinator,
        sessionCoordinator: CustomerAccountSessionCoordinator,
        gateway: CustomerAccountGateway
    ): CustomerAccountProofController = DefaultCustomerAccountProofController(
        authorizationCoordinator = authorizationCoordinator,
        sessionCoordinator = sessionCoordinator,
        gateway = gateway
    )
}

internal fun createConfiguredPushRegistrationCoordinator(
    firebaseConfigured: Boolean,
    factory: () -> PushRegistrationCoordinator
): PushRegistrationCoordinator {
    check(firebaseConfigured) { "Firebase proof is unavailable for an unconfigured build." }
    return factory()
}
