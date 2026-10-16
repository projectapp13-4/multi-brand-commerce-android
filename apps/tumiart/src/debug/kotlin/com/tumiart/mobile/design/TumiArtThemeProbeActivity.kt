package com.tumiart.mobile.design

import android.content.res.Configuration
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.core.view.WindowCompat
import com.gurbakir.foundation.ui.CommerceTheme
import com.tumiart.mobile.brand.TumiArtBrand
import com.tumiart.mobile.brand.TumiArtFontFamilies

/** Local visual proof only. Not exported, not a navigation destination, absent from release. */
class TumiArtThemeProbeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
                WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN
        )
        applyProbeAppearance(
            resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        )
        setContent { TumiArtThemeProbe() }
    }

    internal fun applyProbeAppearance(dark: Boolean) {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
    }
}

@Composable
internal fun TumiArtThemeProbe(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit = { TumiArtThemeSpecimen() }
) {
    CommerceTheme(TumiArtBrand.configuration.designTokens, darkTheme, TumiArtFontFamilies) {
        content()
    }
}
