package com.projectapp134.multibrandtrial

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.gurbakir.firebase.FirebaseRuntimeInspector
import com.gurbakir.firebase.PushRegistrationResult
import com.gurbakir.firebase.RemoteConfigResult
import com.gurbakir.firebase.createFirebasePushRegistrationCoordinator
import com.gurbakir.firebase.createFirebaseRemoteFeatureFlags
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TrialFirebaseRuntimeProofTest {
    private val context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun configuredTrialFetchesRemoteConfigFromItsDefaultFirebaseApp() = runBlocking {
        assertTrue(BuildConfig.FIREBASE_CONFIGURED)

        val runtime = FirebaseRuntimeInspector(context).inspect()
        assertTrue(runtime.defaultAppInitialized)
        assertTrue(runtime.singleFirebaseApp)
        assertTrue(runtime.applicationIdConfigured)
        assertTrue(runtime.projectIdConfigured)
        assertFalse(runtime.messagingAutoInitEnabled)

        assertTrue(createFirebaseRemoteFeatureFlags().refresh() is RemoteConfigResult.Fetched)
    }

    @Test
    fun pushRegistrationRequiresTheExplicitProofActionAndCleansUp() = runBlocking {
        assertTrue(BuildConfig.FIREBASE_CONFIGURED)
        val coordinator = createFirebasePushRegistrationCoordinator(context)

        assertFalse(coordinator.hasStoredConsent())
        try {
            assertTrue(coordinator.registerAfterConsent() is PushRegistrationResult.Registered)
            assertTrue(coordinator.hasStoredConsent())
        } finally {
            assertTrue(coordinator.unregister())
        }
        assertFalse(coordinator.hasStoredConsent())
    }
}
