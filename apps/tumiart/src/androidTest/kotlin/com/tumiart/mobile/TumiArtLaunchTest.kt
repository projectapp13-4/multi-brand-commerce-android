package com.tumiart.mobile

import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tumiart.mobile.config.TumiArtConfiguration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TumiArtLaunchTest {
    @get:Rule val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun independentDevelopmentApplicationLaunchesAndMissingConfigurationFailsClosed() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.tumiart.mobile.development.debug", context.packageName)
        assertTrue(context.applicationContext is TumiArtApplication)
        assertTrue(context.filesDir.absolutePath.contains(context.packageName))
        composeRule.onNodeWithText("TumiArt").assertIsDisplayed()
        composeRule.onNodeWithText("Gürbakır").assertDoesNotExist()
        if (!TumiArtConfiguration.isLaunchReady()) {
            composeRule.onNodeWithText(context.getString(R.string.tumiart_setup_required)).assertIsDisplayed()
            composeRule.onNodeWithText("Hesap").assertDoesNotExist()
        }
    }

    @Test
    fun installedManifestAcceptsOnlyTheExactMobileCallbackAndOwnedWebRoutes() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        fun handlers(raw: String): List<String> {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(raw))
                .addCategory(Intent.CATEGORY_BROWSABLE).setPackage(context.packageName)
            @Suppress("DEPRECATION")
            return context.packageManager.queryIntentActivities(intent, 0).map { it.activityInfo.name }
        }
        assertEquals(
            listOf("net.openid.appauth.RedirectUriReceiverActivity"),
            handlers("shop.80183034101.tumiart://oauth/callback")
        )
        listOf(
            "shop.80183034101.tumiart://other/callback",
            "shop.80183034101.tumiart://oauth/callback/extra",
            "shop.61252272257.multibrandtrial://oauth/callback",
            "https://tumiart.com.attacker.invalid/products/example",
            "https://tumiart.com/pages/iletisim"
        ).forEach { assertTrue(handlers(it).isEmpty()) }
        assertEquals(listOf("com.tumiart.mobile.MainActivity"), handlers("https://tumiart.com/collections/example"))
        assertEquals(listOf("com.tumiart.mobile.MainActivity"), handlers("https://tumiart.com/products/example"))
        assertFalse(context.packageManager.getLaunchIntentForPackage(context.packageName) == null)
    }
}
