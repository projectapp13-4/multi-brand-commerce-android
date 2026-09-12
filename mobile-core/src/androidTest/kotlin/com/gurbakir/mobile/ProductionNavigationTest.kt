@file:Suppress("FunctionNaming")

package com.gurbakir.mobile

import android.content.Intent
import android.net.Uri
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gurbakir.mobile.CoreTestTheme
import com.gurbakir.mobile.home.HomeTestTags
import com.gurbakir.mobile.navigation.AccountDeletionRoute
import com.gurbakir.mobile.navigation.AccountRoute
import com.gurbakir.mobile.navigation.AddressFormRoute
import com.gurbakir.mobile.navigation.AddressListRoute
import com.gurbakir.mobile.navigation.CartRoute
import com.gurbakir.mobile.navigation.CategoriesRoute
import com.gurbakir.mobile.navigation.CollectionRoute
import com.gurbakir.mobile.navigation.LegalSupportRoute
import com.gurbakir.mobile.navigation.OrderDetailRoute
import com.gurbakir.mobile.navigation.OrderListRoute
import com.gurbakir.mobile.navigation.ProductRoute
import com.gurbakir.mobile.navigation.ProfileRoute
import com.gurbakir.mobile.navigation.RouteRecoveryReason
import com.gurbakir.mobile.navigation.RouteRecoveryRoute
import com.gurbakir.mobile.navigation.SearchRoute
import com.gurbakir.mobile.navigation.WishlistRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProductionNavigationTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun unavailableTypedRouteRecoversToTheOnlyFunctionalHomeDestination() {
        lateinit var navController: NavHostController
        setProductionNavigation { navController = it }
        composeRule.onNodeWithTag(HomeTestTags.ROOT).assertIsDisplayed()

        composeRule.runOnIdle {
            navController.navigate(RouteRecoveryRoute(RouteRecoveryReason.UNAVAILABLE_DESTINATION))
        }
        composeRule.onNodeWithTag(ProductionTestTags.ROUTE_RECOVERY).assertIsDisplayed()
        composeRule.onNodeWithTag(ProductionTestTags.ROUTE_RECOVERY_HOME).performDeterministicClick()

        composeRule.onNodeWithTag(HomeTestTags.ROOT).assertIsDisplayed()
    }

    @Test
    fun routeRecoverySurvivesSavedInstanceStateRestoration() {
        val restorationTester = StateRestorationTester(composeRule)
        lateinit var navController: NavHostController
        restorationTester.setContent {
            ProductionNavigationTestContent(onController = { navController = it })
        }
        composeRule.runOnIdle {
            navController.navigate(RouteRecoveryRoute(RouteRecoveryReason.UNAVAILABLE_DESTINATION))
        }
        composeRule.onNodeWithTag(ProductionTestTags.ROUTE_RECOVERY).assertIsDisplayed()

        restorationTester.emulateSavedInstanceStateRestore()

        composeRule.onNodeWithTag(ProductionTestTags.ROUTE_RECOVERY).assertIsDisplayed()
    }

    @Test
    fun routeRecoveryKeepsPublicLegalSupportReachable() {
        lateinit var navController: NavHostController
        composeRule.setContent {
            ProductionNavigationTestContent(
                onController = { navController = it },
                legalSupportContent = { Text("Legal", Modifier.testTag(TEST_LEGAL_SUPPORT)) }
            )
        }
        composeRule.runOnIdle {
            navController.navigate(RouteRecoveryRoute(RouteRecoveryReason.UNAVAILABLE_DESTINATION))
        }
        composeRule.onNodeWithTag(ProductionTestTags.ROUTE_RECOVERY).assertIsDisplayed()
        composeRule.onNodeWithTag(ProductionTestTags.ROUTE_RECOVERY_LEGAL_SUPPORT).performDeterministicClick()

        composeRule.onNodeWithTag(TEST_LEGAL_SUPPORT).assertIsDisplayed()
    }

    @Test
    fun allImplementedCommerceAndAccountTypedRoutesAreFunctional() {
        lateinit var navController: NavHostController
        composeRule.setContent {
            ProductionNavigationTestContent(
                onController = { navController = it },
                categoriesContent = { Text("Categories", Modifier.testTag(TEST_CATEGORIES)) },
                collectionContent = { route ->
                    Text(route.handle, Modifier.testTag(TEST_COLLECTION))
                },
                searchContent = { Text("Search", Modifier.testTag(TEST_SEARCH)) },
                wishlistContent = { Text("Wishlist", Modifier.testTag(TEST_WISHLIST)) },
                accountContent = { Text("Account", Modifier.testTag(TEST_ACCOUNT)) },
                accountDeletionContent = {
                    Text("Account deletion", Modifier.testTag(TEST_ACCOUNT_DELETION))
                },
                profileContent = { Text("Profile", Modifier.testTag(TEST_PROFILE)) },
                addressListContent = { Text("Addresses", Modifier.testTag(TEST_ADDRESS_LIST)) },
                addressFormContent = { route ->
                    Text(route.addressId.orEmpty(), Modifier.testTag(TEST_ADDRESS_FORM))
                },
                orderListContent = { Text("Orders", Modifier.testTag(TEST_ORDER_LIST)) },
                orderDetailContent = { route ->
                    Text(route.orderId, Modifier.testTag(TEST_ORDER_DETAIL))
                },
                cartContent = { Text("Cart", Modifier.testTag(TEST_CART)) },
                legalSupportContent = { Text("Legal", Modifier.testTag(TEST_LEGAL_SUPPORT)) },
                productContent = { route ->
                    Text(route.productId, Modifier.testTag(TEST_PRODUCT))
                }
            )
        }

        composeRule.runOnIdle { navController.navigate(CategoriesRoute) }
        composeRule.onNodeWithTag(TEST_CATEGORIES).assertIsDisplayed()
        composeRule.runOnIdle { navController.navigate(CollectionRoute("bardaklar")) }
        composeRule.onNodeWithTag(TEST_COLLECTION).assertIsDisplayed()
        composeRule.onNodeWithText("bardaklar").assertIsDisplayed()
        composeRule.runOnIdle { navController.navigate(SearchRoute) }
        composeRule.onNodeWithTag(TEST_SEARCH).assertIsDisplayed()
        composeRule.runOnIdle { navController.navigate(WishlistRoute) }
        composeRule.onNodeWithTag(TEST_WISHLIST).assertIsDisplayed()
        composeRule.runOnIdle { navController.navigate(AccountRoute) }
        composeRule.onNodeWithTag(TEST_ACCOUNT).assertIsDisplayed()
        composeRule.runOnIdle { navController.navigate(AccountDeletionRoute) }
        composeRule.onNodeWithTag(TEST_ACCOUNT_DELETION).assertIsDisplayed()
        composeRule.runOnIdle { navController.navigate(ProfileRoute) }
        composeRule.onNodeWithTag(TEST_PROFILE).assertIsDisplayed()
        composeRule.runOnIdle { navController.navigate(AddressListRoute) }
        composeRule.onNodeWithTag(TEST_ADDRESS_LIST).assertIsDisplayed()
        composeRule.runOnIdle {
            navController.navigate(AddressFormRoute("gid://shopify/CustomerAddress/synthetic"))
        }
        composeRule.onNodeWithTag(TEST_ADDRESS_FORM).assertIsDisplayed()
        composeRule.onNodeWithText("gid://shopify/CustomerAddress/synthetic").assertIsDisplayed()
        composeRule.runOnIdle { navController.navigate(OrderListRoute) }
        composeRule.onNodeWithTag(TEST_ORDER_LIST).assertIsDisplayed()
        composeRule.runOnIdle { navController.navigate(OrderDetailRoute("1001")) }
        composeRule.onNodeWithTag(TEST_ORDER_DETAIL).assertIsDisplayed()
        composeRule.onNodeWithText("1001").assertIsDisplayed()
        composeRule.runOnIdle { navController.navigate(CartRoute) }
        composeRule.onNodeWithTag(TEST_CART).assertIsDisplayed()
        composeRule.runOnIdle { navController.navigate(LegalSupportRoute) }
        composeRule.onNodeWithTag(TEST_LEGAL_SUPPORT).assertIsDisplayed()
        composeRule.runOnIdle { navController.navigate(ProductRoute("123", "456")) }
        composeRule.onNodeWithTag(TEST_PRODUCT).assertIsDisplayed()
        composeRule.onNodeWithText("123").assertIsDisplayed()
    }

    @Test
    fun terminalProfileSessionResetReplacesTheStaleAccountBackStackEntry() {
        lateinit var navController: NavHostController
        composeRule.setContent {
            ProductionNavigationTestContent(
                onController = { navController = it },
                accountContent = { Text("Account", Modifier.testTag(TEST_ACCOUNT)) },
                profileContent = { Text("Profile", Modifier.testTag(TEST_PROFILE)) }
            )
        }

        composeRule.runOnIdle { navController.navigate(AccountRoute) }
        composeRule.onNodeWithTag(TEST_ACCOUNT).assertIsDisplayed()
        val originalAccountEntryId = navController.currentBackStackEntry?.id
        composeRule.runOnIdle { navController.navigate(ProfileRoute) }
        composeRule.onNodeWithTag(TEST_PROFILE).assertIsDisplayed()

        composeRule.runOnIdle { navController.resetToAccountAfterSessionExpiry(capabilityComposition()) }

        composeRule.onNodeWithTag(TEST_ACCOUNT).assertIsDisplayed()
        composeRule.runOnIdle {
            assertTrue(originalAccountEntryId != navController.currentBackStackEntry?.id)
        }
    }

    @Test
    fun terminalOrderSessionResetRemovesDeepLinkedOrderBeforeAccountBack() {
        lateinit var navController: NavHostController
        composeRule.setContent {
            ProductionNavigationTestContent(
                onController = { navController = it },
                accountContent = { Text("Account", Modifier.testTag(TEST_ACCOUNT)) },
                orderDetailContent = { Text("Order", Modifier.testTag(TEST_ORDER_DETAIL)) }
            )
        }

        composeRule.runOnIdle {
            assertTrue(
                navController.handleDeepLink(
                    Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("https://shop.example/apps/mobile/orders/1001")
                    )
                )
            )
        }
        composeRule.onNodeWithTag(TEST_ORDER_DETAIL).assertIsDisplayed()

        composeRule.runOnIdle { navController.resetToAccountAfterSessionExpiry(capabilityComposition()) }
        composeRule.onNodeWithTag(TEST_ACCOUNT).assertIsDisplayed()
        composeRule.runOnIdle {
            assertTrue(
                "Terminal direct-link stack should not expose an earlier destination",
                !navController.popBackStack()
            )
        }
        composeRule.onNodeWithTag(TEST_ACCOUNT).assertIsDisplayed()
    }

    @Test
    fun ownedCollectionHttpsDeepLinkResolvesToTypedHandle() {
        lateinit var navController: NavHostController
        composeRule.setContent {
            ProductionNavigationTestContent(
                onController = { navController = it },
                collectionContent = { route ->
                    Text(route.handle, Modifier.testTag(TEST_COLLECTION))
                }
            )
        }

        composeRule.runOnIdle {
            assertTrue(
                navController.handleDeepLink(
                    Intent(Intent.ACTION_VIEW, Uri.parse("https://shop.example/collections/tencereler"))
                )
            )
        }

        composeRule.onNodeWithTag(TEST_COLLECTION).assertIsDisplayed()
        composeRule.onNodeWithText("tencereler").assertIsDisplayed()
    }

    @Test
    fun ownedProductHttpsDeepLinkResolvesToTypedProductAndVariantIds() {
        lateinit var navController: NavHostController
        var resolved: ProductRoute? = null
        composeRule.setContent {
            ProductionNavigationTestContent(
                onController = { navController = it },
                productContent = { route ->
                    resolved = route
                    Text(route.productId, Modifier.testTag(TEST_PRODUCT))
                }
            )
        }

        composeRule.runOnIdle {
            assertTrue(
                navController.handleDeepLink(
                    Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("https://shop.example/apps/mobile/products/123?variantId=456")
                    )
                )
            )
        }

        composeRule.onNodeWithTag(TEST_PRODUCT).assertIsDisplayed()
        composeRule.runOnIdle { assertTrue(resolved == ProductRoute("123", "456")) }
    }

    @Test
    fun ownedOrderHttpsDeepLinkResolvesOnlyTheBoundedOrderId() {
        lateinit var navController: NavHostController
        var resolved: OrderDetailRoute? = null
        composeRule.setContent {
            ProductionNavigationTestContent(
                onController = { navController = it },
                orderDetailContent = { route ->
                    resolved = route
                    Text(route.orderId, Modifier.testTag(TEST_ORDER_DETAIL))
                }
            )
        }

        composeRule.runOnIdle {
            assertTrue(
                navController.handleDeepLink(
                    Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("https://shop.example/apps/mobile/orders/1001")
                    )
                )
            )
        }

        composeRule.onNodeWithTag(TEST_ORDER_DETAIL).assertIsDisplayed()
        composeRule.runOnIdle {
            assertTrue(resolved == OrderDetailRoute("1001"))
            assertTrue(!resolved.toString().contains("1001"))
        }
    }

    @Test
    fun crossDomainAndMalformedRouteShapesAreRejectedWithoutChangingTheBackStack() {
        lateinit var navController: NavHostController
        setProductionNavigation { navController = it }
        composeRule.onNodeWithTag(HomeTestTags.ROOT).assertIsDisplayed()
        composeRule.runOnIdle {
            val originalEntry = navController.currentBackStackEntry?.id
            for (url in listOf(
                "https://other.example/collections/example-collection",
                "https://shop.example.evil.example/apps/mobile/products/123",
                "https://other.example/apps/mobile/orders/1001",
                "https://shop.example/collections",
                "https://shop.example/apps/mobile/products/123/unexpected",
                "https://shop.example/apps/mobile/orders/1001/unexpected",
                "https://shop.example/unmatched/path"
            )) {
                assertFalse(navController.handleDeepLink(Intent(Intent.ACTION_VIEW, Uri.parse(url))))
                assertEquals(originalEntry, navController.currentBackStackEntry?.id)
            }
        }
        composeRule.onNodeWithTag(HomeTestTags.ROOT).assertIsDisplayed()
    }

    @Test
    fun invalidProductGidUsesExistingRouteRecoveryAndCanReturnHome() {
        lateinit var navController: NavHostController
        setProductionNavigation { navController = it }
        composeRule.runOnIdle { navController.navigateProduct("gid://shopify/Collection/123") }
        composeRule.onNodeWithTag(ProductionTestTags.ROUTE_RECOVERY).assertIsDisplayed()
        composeRule.onNodeWithTag(ProductionTestTags.ROUTE_RECOVERY_HOME).performDeterministicClick()
        composeRule.onNodeWithTag(HomeTestTags.ROOT).assertIsDisplayed()
    }

    private fun setProductionNavigation(onController: (NavHostController) -> Unit) {
        composeRule.setContent { ProductionNavigationTestContent(onController) }
    }

    private companion object {
        const val TEST_CATEGORIES = "test-categories"
        const val TEST_COLLECTION = "test-collection"
        const val TEST_SEARCH = "test-search"
        const val TEST_WISHLIST = "test-wishlist"
        const val TEST_ACCOUNT = "test-account"
        const val TEST_ACCOUNT_DELETION = "test-account-deletion"
        const val TEST_PROFILE = "test-profile"
        const val TEST_ADDRESS_LIST = "test-address-list"
        const val TEST_ADDRESS_FORM = "test-address-form"
        const val TEST_ORDER_LIST = "test-order-list"
        const val TEST_ORDER_DETAIL = "test-order-detail"
        const val TEST_CART = "test-cart"
        const val TEST_LEGAL_SUPPORT = "test-legal-support"
        const val TEST_PRODUCT = "test-product"
    }
}

@Composable
private fun ProductionNavigationTestContent(
    onController: (NavHostController) -> Unit,
    categoriesContent: @Composable () -> Unit = {},
    collectionContent: @Composable (CollectionRoute) -> Unit = {},
    searchContent: @Composable () -> Unit = {},
    wishlistContent: @Composable () -> Unit = {},
    accountContent: @Composable () -> Unit = {},
    accountDeletionContent: @Composable () -> Unit = {},
    profileContent: @Composable () -> Unit = {},
    addressListContent: @Composable () -> Unit = {},
    addressFormContent: @Composable (AddressFormRoute) -> Unit = {},
    orderListContent: @Composable () -> Unit = {},
    orderDetailContent: @Composable (OrderDetailRoute) -> Unit = {},
    cartContent: @Composable () -> Unit = {},
    legalSupportContent: @Composable () -> Unit = {},
    productContent: @Composable (ProductRoute) -> Unit = {}
) {
    CoreTestTheme(
        darkTheme = false
    ) {
        val navController = rememberNavController()
        SideEffect { onController(navController) }
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
                    home = { Text("Home", modifier = Modifier.testTag(HomeTestTags.ROOT)) },
                    categories = categoriesContent,
                    collection = collectionContent,
                    search = searchContent,
                    wishlist = wishlistContent,
                    account = accountContent,
                    accountDeletion = accountDeletionContent,
                    profile = profileContent,
                    addressList = addressListContent,
                    addressForm = addressFormContent,
                    orderList = orderListContent,
                    orderDetail = orderDetailContent,
                    cart = cartContent,
                    legalSupport = legalSupportContent,
                    product = productContent
                )
        )
    }
}
