package com.gurbakir.mobile

import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gurbakir.mobile.navigation.IntegrationDetail
import com.gurbakir.mobile.navigation.IntegrationId
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FirebaseProofHiltTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun configuredFirebaseRouteConstructsTheHiltProofGraphWithoutRegistration() {
        assumeTrue("Requires the complete ignored Firebase configuration set.", BuildConfig.FIREBASE_CONFIGURED)
        composeRule.activity.setContent { FoundationProofApp(firebaseConfigured = true) }

        composeRule
            .onNodeWithTag(FoundationTestTags.integrationAction(com.gurbakir.mobile.navigation.IntegrationId.FIREBASE))
            .performClick()

        composeRule.onNodeWithTag(FirebaseProofTestTags.ROOT).assertIsDisplayed()
        composeRule.onNodeWithTag(FirebaseProofTestTags.STATUS).assertIsDisplayed()
        composeRule.onNodeWithTag(FirebaseProofTestTags.ENABLE_PUSH).assertHasClickAction()
    }

    @Test
    fun unconfiguredDirectFirebaseRouteRendersUnavailableBeforeHiltProofConstruction() {
        composeRule.activity.setContent {
            val navController = rememberNavController()
            FoundationProofApp(
                navController = navController,
                firebaseConfigured = false
            )
            LaunchedEffect(navController) {
                navController.navigate(IntegrationDetail(IntegrationId.FIREBASE))
            }
        }

        composeRule.onNodeWithTag(FirebaseProofUnavailableTestTags.ROOT).assertIsDisplayed()
        composeRule.onNodeWithTag(FirebaseProofUnavailableTestTags.STATUS).assertIsDisplayed()
        composeRule.onNodeWithTag(FirebaseProofUnavailableTestTags.BACK).assertHasClickAction()
    }
}
