package com.gurbakir.mobile

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.gurbakir.firebase.FirebaseRuntimeInspector
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FirebaseRuntimeTest {
    @Test
    fun variantConfigurationMatchesFirebaseEnablementWithoutPreConsentMessagingRegistration() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val status = FirebaseRuntimeInspector(context).inspect()

        if (BuildConfig.FIREBASE_CONFIGURED) {
            assertTrue(status.defaultAppInitialized)
            assertTrue(status.singleFirebaseApp)
            assertTrue(status.applicationIdConfigured)
            assertTrue(status.projectIdConfigured)
        } else {
            assertFalse(status.defaultAppInitialized)
            assertFalse(status.singleFirebaseApp)
            assertFalse(status.applicationIdConfigured)
            assertFalse(status.projectIdConfigured)
        }
        assertFalse(status.messagingAutoInitEnabled)
    }
}
