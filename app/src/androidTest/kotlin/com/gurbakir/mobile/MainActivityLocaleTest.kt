package com.gurbakir.mobile

import android.content.res.Configuration
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import coil3.SingletonImageLoader
import com.gurbakir.mobile.config.BuildConfigurationSource
import com.gurbakir.mobile.localization.effectiveForegroundLocale
import com.gurbakir.mobile.localization.resolveAppLocales
import com.gurbakir.mobile.localization.withAppLocale
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityLocaleTest {
    @Test
    fun productionTurkishShellDoesNotFollowAnUnrelatedEnglishDeviceLocale() {
        assumeTrue("This acceptance contract is scoped to the production profile", BuildConfig.FLAVOR == "production")
        val policy = BuildConfigurationSource.current.localization
        assertEquals("tr", policy.defaultLocaleTag)
        assertEquals(setOf("tr"), policy.supportedLocaleTags.toSet())
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val englishConfiguration = Configuration(context.resources.configuration).apply { setLocale(Locale.US) }
        val englishContext = context.createConfigurationContext(englishConfiguration)
        val localized = englishContext.withAppLocale(policy, allowPseudoLocales = false)
        assertEquals("tr", effectiveForegroundLocale(localized.resources.configuration).language)
        assertEquals("Geri", localized.getString(com.gurbakir.mobile.core.R.string.back))
    }

    @Test
    fun launchedMainActivityAppliesTheConfiguredForegroundLocalePolicy() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val applicationConfiguration = activity.application.resources.configuration
                val requested =
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                        (0 until applicationConfiguration.locales.size()).map(applicationConfiguration.locales::get)
                    } else {
                        @Suppress("DEPRECATION")
                        listOf(applicationConfiguration.locale)
                    }
                val expected =
                    resolveAppLocales(
                        policy = BuildConfigurationSource.current.localization,
                        requestedLocales = requested,
                        allowPseudoLocales = BuildConfig.DEBUG
                    ).first()

                assertEquals(
                    expected.toLanguageTag(),
                    effectiveForegroundLocale(activity.resources.configuration).toLanguageTag()
                )
                val factory = activity.application as SingletonImageLoader.Factory
                factory.newImageLoader(activity)
            }
        }
    }
}
