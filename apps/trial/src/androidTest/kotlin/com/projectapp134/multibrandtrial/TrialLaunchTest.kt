package com.projectapp134.multibrandtrial

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TrialLaunchTest {
    @get:Rule val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun developmentApplicationLaunchesWithTrialOwnedIdentity() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.projectapp134.multibrandtrial.dev.debug", context.packageName)
        assertTrue(context.applicationContext is TrialApplication)
        assertEquals(context.packageName, composeRule.activity.packageName)
        composeRule.onNodeWithText("Multi Brand Trial").assertIsDisplayed()
        composeRule.onNodeWithText("Gürbakır").assertDoesNotExist()
    }
}
