@file:Suppress("MagicNumber")

package com.example.gate2synthetic

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.gate2synthetic.brand.Gate2SyntheticBrand
import com.gurbakir.foundation.ui.CommerceTheme
import com.gurbakir.foundation.ui.LocalBrandMotion
import com.gurbakir.foundation.ui.LocalBrandSpacing
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Gate2SyntheticResourceAndThemeTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun defaultEnglishAndNonEnglishFallbackOwnTheSyntheticHomeMarker() {
        assertEquals("Synthetic Lab", context.getString(R.string.home_title))
        assertEquals("Synthetic Lab", localizedContext("en-CA").getString(R.string.home_title))
        assertEquals("Synthetic Lab", localizedContext("fr-CA").getString(R.string.home_title))
    }

    @Test
    fun approvedTokensReachMaterialThemeAndCompositionLocalsAtRuntime() {
        var primary = Color.Unspecified
        var titleSize = 0.sp
        var titleWeight: FontWeight? = null
        var spacing = 0
        var motion = 0

        composeRule.setContent {
            CommerceTheme(Gate2SyntheticBrand.configuration.designTokens, darkTheme = false) {
                val currentPrimary = MaterialTheme.colorScheme.primary
                val currentTitleSize = MaterialTheme.typography.titleLarge.fontSize
                val currentTitleWeight = MaterialTheme.typography.titleLarge.fontWeight
                val currentSpacing = LocalBrandSpacing.current.sectionDp
                val currentMotion = LocalBrandMotion.current.containerTransitionMs
                SideEffect {
                    primary = currentPrimary
                    titleSize = currentTitleSize
                    titleWeight = currentTitleWeight
                    spacing = currentSpacing
                    motion = currentMotion
                }
            }
        }
        composeRule.waitForIdle()

        assertEquals(Color(0xFF4F378B), primary)
        assertEquals(20.sp, titleSize)
        assertEquals(FontWeight.SemiBold, titleWeight)
        assertEquals(32, spacing)
        assertEquals(420, motion)
    }

    private fun localizedContext(languageTag: String): Context {
        val configuration = Configuration(context.resources.configuration)
        val locale = Locale.forLanguageTag(languageTag)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            configuration.setLocales(LocaleList(locale))
        } else {
            @Suppress("DEPRECATION")
            configuration.setLocale(locale)
        }
        return context.createConfigurationContext(configuration)
    }
}
