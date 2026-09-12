@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
@file:Suppress("FunctionNaming")

package com.gurbakir.mobile

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.waitUntilExactlyOneExists
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gurbakir.mobile.CoreTestTheme
import com.gurbakir.mobile.navigation.CollectionRoute
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProductionAppShellTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun fivePrimaryRootsSelectInPlaceAndSecondaryRoutesHidePrimaryNavigation() {
        lateinit var navController: NavHostController
        composeRule.setContent {
            ProductionShellTestContent { navController = it }
        }

        assertSelectedDestination(ProductionTestTags.PRIMARY_HOME, TEST_HOME)
        selectDestination(ProductionTestTags.PRIMARY_CATEGORIES, TEST_CATEGORIES)
        selectDestination(ProductionTestTags.PRIMARY_SEARCH, TEST_SEARCH)
        selectDestination(ProductionTestTags.PRIMARY_WISHLIST, TEST_WISHLIST)
        selectDestination(ProductionTestTags.PRIMARY_ACCOUNT, TEST_ACCOUNT)

        composeRule.runOnIdle { navController.navigate(CollectionRoute("cp-03")) }
        composeRule.onNodeWithTag(TEST_COLLECTION).assertIsDisplayed()
        composeRule.onNodeWithTag(ProductionTestTags.PRIMARY_NAVIGATION).assertDoesNotExist()

        composeRule.runOnIdle { navController.popBackStack() }
        assertSelectedDestination(ProductionTestTags.PRIMARY_ACCOUNT, TEST_ACCOUNT)
    }

    private fun selectDestination(itemTag: String, contentTag: String) {
        composeRule.onNodeWithTag(itemTag).performClick()
        assertSelectedDestination(itemTag, contentTag)
    }

    private fun assertSelectedDestination(itemTag: String, contentTag: String) {
        composeRule.waitUntilExactlyOneExists(hasTestTag(contentTag), timeoutMillis = 5_000)
        composeRule.onNodeWithTag(ProductionTestTags.PRIMARY_NAVIGATION).assertIsDisplayed()
        composeRule.onNodeWithTag(itemTag).assertIsDisplayed().assertIsSelected()
        composeRule.onNodeWithTag(contentTag).assertIsDisplayed()
    }

    companion object {
        const val TEST_HOME = "shell-home"
        const val TEST_CATEGORIES = "shell-categories"
        const val TEST_COLLECTION = "shell-collection"
        const val TEST_SEARCH = "shell-search"
        const val TEST_WISHLIST = "shell-wishlist"
        const val TEST_ACCOUNT = "shell-account"
    }
}

@Composable
private fun ProductionShellTestContent(onController: (NavHostController) -> Unit) {
    CoreTestTheme(
        darkTheme = false
    ) {
        val navController = rememberNavController()
        val destination = navController.currentBackStackEntryAsState().value?.destination
        SideEffect { onController(navController) }
        ProductionAppShell(navController, destination, applicationComposition = capabilityComposition()) {
            ProductionNavHost(
                applicationComposition = capabilityComposition(),
                customerAccountBindings = com.gurbakir.mobile.navigation.CustomerAccountFeatureBindings(
                    "https://shop.example/apps/mobile/orders",
                    com.gurbakir.mobile.order.TrackingUrlPolicy(setOf("tracking.example")),
                    { _, _ -> com.gurbakir.mobile.accountdeletion.DeletionPageLaunchResult.REJECTED }
                ),
                deepLinks = com.gurbakir.mobile.navigation.MobileDeepLinkConfiguration(
                    "https://shop.example/collections",
                    "https://shop.example/apps/mobile/products"
                ),
                navController = navController,
                content =
                    ProductionDestinationContent(
                        home = { TestDestination("Home", ProductionAppShellTest.TEST_HOME) },
                        categories = {
                            TestDestination("Categories", ProductionAppShellTest.TEST_CATEGORIES)
                        },
                        collection = {
                            TestDestination("Collection", ProductionAppShellTest.TEST_COLLECTION)
                        },
                        search = { TestDestination("Search", ProductionAppShellTest.TEST_SEARCH) },
                        wishlist = {
                            TestDestination("Wishlist", ProductionAppShellTest.TEST_WISHLIST)
                        },
                        account = { TestDestination("Account", ProductionAppShellTest.TEST_ACCOUNT) }
                    )
            )
        }
    }
}

@Composable
private fun TestDestination(label: String, testTag: String) {
    Text(label, Modifier.testTag(testTag))
}
