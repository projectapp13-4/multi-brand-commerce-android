package com.projectapp134.multibrandtrial

import com.gurbakir.firebase.LocalDefaultFeatureFlags
import com.gurbakir.firebase.RemoteFeatureFlags
import com.projectapp134.multibrandtrial.di.selectRemoteFeatureFlags
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TrialProviderSelectionTest {
    @Test
    fun `unconfigured Trial Firebase fails closed to local defaults`() {
        var factoryCalled = false

        val selected = selectRemoteFeatureFlags(firebaseConfigured = false) {
            factoryCalled = true
            error("Firebase factory must not run for an unconfigured Trial build")
        }

        assertTrue(selected is LocalDefaultFeatureFlags)
        assertTrue(!factoryCalled)
    }

    @Test
    fun `configured Trial Firebase uses its selected provider`() {
        val expected = object : RemoteFeatureFlags by LocalDefaultFeatureFlags() {}

        assertSame(expected, selectRemoteFeatureFlags(firebaseConfigured = true) { expected })
    }
}
