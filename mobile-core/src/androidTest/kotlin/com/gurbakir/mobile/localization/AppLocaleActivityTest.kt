package com.gurbakir.mobile.localization

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gurbakir.foundation.config.LocalizationPolicy
import com.gurbakir.mobile.core.R
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppLocaleActivityTest {
    @Test
    fun launchedActivityUsesTheGurbakirCompatibilityMatrix() {
        assertLaunchedLocale("tr-TR", expectedTag = "tr-TR", expectedTitle = "Ana Sayfa")
        assertLaunchedLocale("en-US", expectedTag = "en-US", expectedTitle = "Home")
        assertLaunchedLocale("en-GB", expectedTag = "en-GB", expectedTitle = "Home")
        assertLaunchedLocale("fr-FR", expectedTag = "tr", expectedTitle = "Ana Sayfa")
    }

    @Test
    fun launchedDebugActivityPreservesExpansionAndRtlPseudoLocales() {
        assertLaunchedLocale("en-XA", expectedTag = "en-XA", allowPseudoLocales = true)
        assertLaunchedLocale("ar-XB", expectedTag = "ar-XB", allowPseudoLocales = true)
    }

    @Test
    fun launchedSyntheticPolicyFallsBackToEnglishCanadianOnTurkishDeviceLocale() {
        assertLaunchedLocale(
            requestedTag = "tr-TR",
            expectedTag = "en-CA",
            expectedTitle = "Home",
            policy = LocalizationPolicy(defaultLocaleTag = "en-CA", supportedLocaleTags = listOf("en-CA"))
        )
    }

    private fun assertLaunchedLocale(
        requestedTag: String,
        expectedTag: String,
        expectedTitle: String? = null,
        allowPseudoLocales: Boolean = false,
        policy: LocalizationPolicy = GURBAKIR_POLICY
    ) {
        AppLocaleProbeActivity.requestedTag = requestedTag
        AppLocaleProbeActivity.policy = policy
        AppLocaleProbeActivity.allowPseudoLocales = allowPseudoLocales
        ActivityScenario.launch(AppLocaleProbeActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertEquals(expectedTag, effectiveForegroundLocale(activity.resources.configuration).toLanguageTag())
                expectedTitle?.let { assertEquals(it, activity.getString(R.string.home_title)) }
            }
        }
    }

    private companion object {
        val GURBAKIR_POLICY =
            LocalizationPolicy(defaultLocaleTag = "tr", supportedLocaleTags = listOf("tr", "en"))
    }
}

class AppLocaleProbeActivity : Activity() {
    override fun attachBaseContext(newBase: Context) {
        val requested = Configuration(newBase.resources.configuration)
        val locale = Locale.forLanguageTag(requestedTag)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            requested.setLocales(LocaleList(locale))
        } else {
            @Suppress("DEPRECATION")
            requested.setLocale(locale)
        }
        val requestedContext = newBase.createConfigurationContext(requested)
        super.attachBaseContext(requestedContext.withAppLocale(policy, allowPseudoLocales))
    }

    companion object {
        lateinit var requestedTag: String
        lateinit var policy: LocalizationPolicy
        var allowPseudoLocales: Boolean = false
    }
}
