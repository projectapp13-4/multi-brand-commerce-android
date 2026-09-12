package com.gurbakir.mobile

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import coil3.SingletonImageLoader
import com.gurbakir.mobile.config.BuildConfigurationSource
import com.gurbakir.mobile.localization.effectiveForegroundLocale
import com.gurbakir.mobile.localization.resolveAppLocales
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityLocaleTest {
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
