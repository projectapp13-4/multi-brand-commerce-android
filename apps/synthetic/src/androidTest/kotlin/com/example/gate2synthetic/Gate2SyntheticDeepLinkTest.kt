@file:Suppress("DEPRECATION", "FunctionNaming")

package com.example.gate2synthetic

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.compose.material3.Text
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.gate2synthetic.brand.Gate2SyntheticBrand
import com.example.gate2synthetic.config.Gate2SyntheticConfiguration
import com.gurbakir.foundation.ui.CommerceTheme
import com.gurbakir.mobile.ProductionDestinationContent
import com.gurbakir.mobile.ProductionNavHost
import com.gurbakir.mobile.navigation.CollectionRoute
import com.gurbakir.mobile.navigation.OrderDetailRoute
import com.gurbakir.mobile.navigation.ProductRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Gate2SyntheticDeepLinkTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun onlyEnabledSyntheticRouteFamiliesResolveInsideTheInstalledPackage() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        for (url in listOf(COLLECTION_LINK, PRODUCT_LINK)) {
            val resolved = context.packageManager.resolveActivity(appLink(url), PackageManager.MATCH_DEFAULT_ONLY)
            assertEquals(context.packageName, resolved?.activityInfo?.packageName)
            assertEquals(MainActivity::class.java.name, resolved?.activityInfo?.name)
        }
        assertEquals(
            null,
            context.packageManager.resolveActivity(appLink(ORDER_LINK), PackageManager.MATCH_DEFAULT_ONLY)
        )
        assertEquals(
            null,
            context.packageManager.resolveActivity(appLink(CUSTOM_SCHEME_LINK), PackageManager.MATCH_DEFAULT_ONLY)
        )
    }

    @Test
    fun appOwnedConfigurationMapsCommonLinksAndRejectsDisabledOrderLink() {
        lateinit var navController: NavHostController
        composeRule.setContent {
            CommerceTheme(Gate2SyntheticBrand.configuration.designTokens, darkTheme = false) {
                val controller = rememberNavController()
                SideEffect { navController = controller }
                ProductionNavHost(
                    applicationComposition = Gate2SyntheticConfiguration.app.applicationComposition,
                    navController = controller,
                    deepLinks = Gate2SyntheticConfiguration.deepLinks,
                    content =
                        ProductionDestinationContent(
                            home = { Text("Inert home") },
                            collection = { Text("Collection: ${it.handle}") },
                            product = { Text("Product: ${it.productId}; variant: ${it.variantId}") },
                            orderDetail = { Text("Order: ${it.orderId}") }
                        )
                )
            }
        }

        composeRule.runOnIdle { assertTrue(navController.handleDeepLink(appLink(COLLECTION_LINK))) }
        composeRule.onNodeWithText("Collection: synthetic-alpha").assertIsDisplayed()
        composeRule.runOnIdle {
            assertEquals(
                CollectionRoute("synthetic-alpha"),
                navController.currentBackStackEntry?.toRoute<CollectionRoute>()
            )
        }

        composeRule.runOnIdle { assertTrue(navController.handleDeepLink(appLink(PRODUCT_LINK))) }
        composeRule.onNodeWithText("Product: fixture-1; variant: fixture-2").assertIsDisplayed()
        composeRule.runOnIdle {
            assertEquals(
                ProductRoute("fixture-1", "fixture-2"),
                navController.currentBackStackEntry?.toRoute<ProductRoute>()
            )
        }

        composeRule.runOnIdle {
            val before = navController.currentBackStackEntry?.id
            assertFalse(navController.handleDeepLink(appLink(ORDER_LINK)))
            assertEquals(before, navController.currentBackStackEntry?.id)
        }
    }

    private fun appLink(url: String): Intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
        addCategory(Intent.CATEGORY_BROWSABLE)
        setPackage(InstrumentationRegistry.getInstrumentation().targetContext.packageName)
    }

    private companion object {
        const val COLLECTION_LINK = "https://links.gate2.invalid/collections/synthetic-alpha"
        const val PRODUCT_LINK =
            "https://links.gate2.invalid/apps/mobile/products/fixture-1?variantId=fixture-2"
        const val ORDER_LINK = "https://links.gate2.invalid/apps/mobile/orders/fixture-order"
        const val CUSTOM_SCHEME_LINK = "shop.0.gate2synthetic:/oauth/callback"
    }
}
