@file:Suppress("DEPRECATION", "FunctionNaming")

package com.gurbakir.mobile

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
import com.gurbakir.foundation.ui.CommerceTheme
import com.gurbakir.mobile.brand.GurbakirBrand
import com.gurbakir.mobile.navigation.CollectionRoute
import com.gurbakir.mobile.navigation.GurbakirDeepLinkConfiguration
import com.gurbakir.mobile.navigation.OrderDetailRoute
import com.gurbakir.mobile.navigation.ProductRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GurbakirDeepLinkIntegrationTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun concreteRouteFamiliesResolveToMainActivityWithinTheInstalledAppPackage() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        for (url in listOf(COLLECTION_LINK, PRODUCT_LINK, ORDER_LINK)) {
            val resolved = context.packageManager.resolveActivity(appLink(url), PackageManager.MATCH_DEFAULT_ONLY)
            assertEquals(context.packageName, resolved?.activityInfo?.packageName)
            assertEquals(MainActivity::class.java.name, resolved?.activityInfo?.name)
        }
        val callback = Uri.parse(BuildConfig.CUSTOMER_ACCOUNT_REDIRECT_URI)
            .scheme
            ?.takeIf { it.startsWith("shop.") }
            ?.let { "$it:/oauth/callback" }
            ?: "shop.unconfigured.gurbakir:/oauth/callback"
        val callbackReceiver = context.packageManager.resolveActivity(
            appLink(callback),
            PackageManager.MATCH_DEFAULT_ONLY
        )
        assertEquals(context.packageName, callbackReceiver?.activityInfo?.packageName)
        assertEquals("net.openid.appauth.RedirectUriReceiverActivity", callbackReceiver?.activityInfo?.name)
    }

    @Test
    fun concreteConfigurationResolvesAllThreeTypedRoutesWithInertContent() {
        lateinit var navController: NavHostController
        composeRule.setContent {
            CommerceTheme(GurbakirBrand.configuration.designTokens, darkTheme = false) {
                val controller = rememberNavController()
                SideEffect { navController = controller }
                ProductionNavHost(
                    customerAccountBindings = com.gurbakir.mobile.navigation.CustomerAccountFeatureBindings(
                        "https://gurbakir.com/apps/mobile/orders",
                        com.gurbakir.mobile.order.GurbakirTrackingUrlPolicy,
                        { _, _ -> com.gurbakir.mobile.accountdeletion.DeletionPageLaunchResult.REJECTED }
                    ),
                    applicationComposition =
                        com.gurbakir.mobile.config.BuildConfigurationSource.current.applicationComposition,
                    navController = controller,
                    deepLinks = GurbakirDeepLinkConfiguration,
                    content = ProductionDestinationContent(
                        home = { Text("Inert home") },
                        collection = { Text("Collection: ${it.handle}") },
                        product = { Text("Product: ${it.productId}; variant: ${it.variantId}") },
                        orderDetail = { Text("Order: ${it.orderId}") }
                    )
                )
            }
        }

        composeRule.runOnIdle { assertTrue(navController.handleDeepLink(appLink(COLLECTION_LINK))) }
        composeRule.onNodeWithText("Collection: tencereler").assertIsDisplayed()
        composeRule.runOnIdle {
            assertEquals(CollectionRoute("tencereler"), navController.currentBackStackEntry?.toRoute<CollectionRoute>())
        }

        composeRule.runOnIdle { assertTrue(navController.handleDeepLink(appLink(PRODUCT_LINK))) }
        composeRule.onNodeWithText("Product: 123; variant: 456").assertIsDisplayed()
        composeRule.runOnIdle {
            assertEquals(ProductRoute("123", "456"), navController.currentBackStackEntry?.toRoute<ProductRoute>())
        }

        composeRule.runOnIdle { assertTrue(navController.handleDeepLink(appLink(ORDER_LINK))) }
        composeRule.onNodeWithText("Order: 1001").assertIsDisplayed()
        composeRule.runOnIdle {
            assertEquals(OrderDetailRoute("1001"), navController.currentBackStackEntry?.toRoute<OrderDetailRoute>())
        }
    }

    private fun appLink(url: String): Intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
        addCategory(Intent.CATEGORY_BROWSABLE)
        setPackage(InstrumentationRegistry.getInstrumentation().targetContext.packageName)
    }

    private companion object {
        const val COLLECTION_LINK = "https://gurbakir.com/collections/tencereler"
        const val PRODUCT_LINK = "https://gurbakir.com/apps/mobile/products/123?variantId=456"
        const val ORDER_LINK = "https://gurbakir.com/apps/mobile/orders/1001"
    }
}
