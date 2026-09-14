@file:Suppress("FunctionNaming")

package com.gurbakir.mobile

import android.content.Intent
import android.net.Uri
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.gurbakir.foundation.config.ApplicationCapabilities
import com.gurbakir.foundation.config.ApplicationComposition
import com.gurbakir.foundation.config.CapabilityState
import com.gurbakir.foundation.config.CustomerAccountCapability
import com.gurbakir.foundation.config.CustomerAccountConfiguration
import com.gurbakir.foundation.config.PrimaryNavigationDestination
import com.gurbakir.foundation.config.PrimaryNavigationDestination.ACCOUNT
import com.gurbakir.foundation.config.PrimaryNavigationDestination.CATEGORIES
import com.gurbakir.foundation.config.PrimaryNavigationDestination.HOME
import com.gurbakir.foundation.config.PrimaryNavigationDestination.SEARCH
import com.gurbakir.foundation.config.PrimaryNavigationDestination.WISHLIST
import com.gurbakir.foundation.config.PrimaryNavigationSpec
import com.gurbakir.mobile.accountdeletion.DeletionPageLaunchResult
import com.gurbakir.mobile.navigation.AccountDeletionRoute
import com.gurbakir.mobile.navigation.AccountRoute
import com.gurbakir.mobile.navigation.AddressFormRoute
import com.gurbakir.mobile.navigation.AddressListRoute
import com.gurbakir.mobile.navigation.CustomerAccountFeatureBindings
import com.gurbakir.mobile.navigation.MobileDeepLinkConfiguration
import com.gurbakir.mobile.navigation.OrderDetailRoute
import com.gurbakir.mobile.navigation.OrderListRoute
import com.gurbakir.mobile.navigation.ProductRoute
import com.gurbakir.mobile.navigation.ProfileRoute
import com.gurbakir.mobile.navigation.SearchRoute
import com.gurbakir.mobile.navigation.WishlistRoute
import com.gurbakir.mobile.order.TrackingUrlPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class CapabilityNavigationTest {
    @get:Rule val rule = createComposeRule()
    private lateinit var controller: NavHostController

    @Test fun mixedOrderStartsAtSecondHomeAndSecondaryHidesNavigation() {
        show(capabilityComposition(true, false, false, listOf(SEARCH, HOME, CATEGORIES)))
        assertItems(
            listOf(
                ProductionTestTags.PRIMARY_SEARCH,
                ProductionTestTags.PRIMARY_HOME,
                ProductionTestTags.PRIMARY_CATEGORIES
            )
        )
        rule.onNodeWithTag(ProductionTestTags.PRIMARY_HOME).assertIsSelected()
        rule.runOnIdle { controller.navigate(ProductRoute("123")) }
        rule.onNodeWithTag("product").assertIsDisplayed()
        rule.onNodeWithTag(ProductionTestTags.PRIMARY_NAVIGATION).assertDoesNotExist()
    }

    @Test fun allOffHasOnlyHomeAndCategories() {
        show(capabilityComposition(false, false, false))
        assertItems(listOf(ProductionTestTags.PRIMARY_HOME, ProductionTestTags.PRIMARY_CATEGORIES))
        rule.onNodeWithTag(ProductionTestTags.PRIMARY_HOME).assertIsSelected()
    }

    @Test fun fullCompositionPreservesAllFiveItems() {
        show(capabilityComposition())
        assertItems(
            listOf(
                ProductionTestTags.PRIMARY_HOME,
                ProductionTestTags.PRIMARY_CATEGORIES,
                ProductionTestTags.PRIMARY_SEARCH,
                ProductionTestTags.PRIMARY_WISHLIST,
                ProductionTestTags.PRIMARY_ACCOUNT
            )
        )
    }

    @Test fun everyDisabledRouteRecoversWithoutInvokingFeatureCodeOrLeavingItBehind() {
        show(capabilityComposition(false, false, false), forbidden = true)
        listOf(
            SearchRoute,
            WishlistRoute,
            AccountRoute,
            AccountDeletionRoute,
            ProfileRoute,
            AddressListRoute,
            AddressFormRoute(),
            OrderListRoute,
            OrderDetailRoute("1001")
        ).forEach { route ->
            rule.runOnIdle { controller.navigate(route) }
            rule.onNodeWithTag(ProductionTestTags.ROUTE_RECOVERY).assertIsDisplayed()
            rule.runOnIdle { assertTrue(controller.popBackStack()) }
            rule.onNodeWithTag("home").assertIsDisplayed()
        }
    }

    @Test fun disabledPrimaryAndSessionRedirectRecoverAndOrderLinkDoesNotChangeStack() {
        val composition = capabilityComposition(false, false, false)
        show(composition, forbidden = true)
        rule.runOnIdle {
            val before = controller.currentBackStackEntry?.id
            assertFalse(
                controller.handleDeepLink(Intent(Intent.ACTION_VIEW, Uri.parse("https://shop.example/orders/1001")))
            )
            assertEquals(before, controller.currentBackStackEntry?.id)
            controller.navigatePrimary(SEARCH, composition)
        }
        rule.onNodeWithTag(ProductionTestTags.ROUTE_RECOVERY).assertIsDisplayed()
        rule.runOnIdle { controller.resetToAccountAfterSessionExpiry(composition) }
        rule.onNodeWithTag(ProductionTestTags.ROUTE_RECOVERY).assertIsDisplayed()
        rule.runOnIdle { assertFalse(controller.currentDestination?.hasRoute<AccountRoute>() == true) }
    }

    @Test fun searchBranchAndSavedQuerySurvivePrimarySwitchAndRestore() {
        val composition = capabilityComposition(true, false, false, listOf(SEARCH, HOME, CATEGORIES))
        val restoration = StateRestorationTester(rule)
        restoration.setContent { NavigationTestContent(composition) }
        rule.runOnIdle { controller.navigatePrimary(SEARCH, composition) }
        rule.onNodeWithTag("change-query").performDeterministicClick()
        rule.runOnIdle { controller.navigatePrimary(CATEGORIES, composition) }
        rule.runOnIdle { controller.navigatePrimary(SEARCH, composition) }
        rule.onNodeWithText("retained query").assertIsDisplayed()
        restoration.emulateSavedInstanceStateRestore()
        rule.onNodeWithText("retained query").assertIsDisplayed()
        rule.onNodeWithTag(ProductionTestTags.PRIMARY_SEARCH).assertIsSelected()
    }

    @Test fun restoredSearchIsUnavailable() = restoredRoute(SearchRoute)

    @Test fun restoredWishlistIsUnavailable() = restoredRoute(WishlistRoute)

    @Test fun restoredAccountIsUnavailable() = restoredRoute(AccountRoute)

    @Test fun restoredAddressFormStackIsUnavailable() = restoredRoute(AddressFormRoute("synthetic-address"))

    @Test fun restoredOrderDetailStackIsUnavailable() = restoredRoute(OrderDetailRoute("1001"))

    private fun restoredRoute(route: Any) {
        val restoration = StateRestorationTester(rule)
        var reduced = false
        restoration.setContent {
            NavigationTestContent(
                if (reduced) capabilityComposition(false, false, false) else capabilityComposition(),
                forbidden = reduced
            )
        }
        rule.runOnIdle {
            controller.navigate(AccountRoute)
            controller.navigate(route)
        }
        rule.waitForIdle()
        reduced = true
        restoration.emulateSavedInstanceStateRestore()
        rule.onNodeWithTag(ProductionTestTags.ROUTE_RECOVERY).assertIsDisplayed()
        rule.runOnIdle { controller.popBackStack() }
        rule.onNodeWithTag("home").assertIsDisplayed()
    }

    private fun show(composition: ApplicationComposition, forbidden: Boolean = false) {
        rule.setContent { NavigationTestContent(composition, forbidden) }
    }

    @Composable private fun NavigationTestContent(composition: ApplicationComposition, forbidden: Boolean = false) {
        CoreTestTheme {
            val nav = rememberNavController()
            SideEffect { controller = nav }
            val sentinel: @Composable () -> Unit = {
                check(!forbidden) { "Disabled feature content was invoked" }
                Text("feature")
            }
            ProductionAppShell(
                nav,
                nav.currentBackStackEntryAsState().value?.destination,
                applicationComposition = composition
            ) {
                ProductionNavHost(
                    navController = nav,
                    deepLinks = capabilityDeepLinks,
                    applicationComposition = composition,
                    customerAccountBindings =
                        if (composition.capabilities.customerAccount is CustomerAccountCapability.Enabled) {
                            capabilityAccountBindings
                        } else {
                            null
                        },
                    content = ProductionDestinationContent(
                        home = { Text("Home", Modifier.testTag("home")) },
                        categories = { Text("Categories") },
                        search = {
                            check(!forbidden) { "Disabled Search content was invoked" }
                            var query by rememberSaveable { mutableStateOf("initial query") }
                            androidx.compose.material3.TextButton(
                                onClick = { query = "retained query" },
                                modifier = Modifier.testTag("change-query")
                            ) { Text(query) }
                        },
                        wishlist = sentinel,
                        account = sentinel,
                        accountDeletion = sentinel,
                        profile = sentinel,
                        addressList = sentinel,
                        addressForm = { sentinel() },
                        orderList = sentinel,
                        orderDetail = { sentinel() },
                        product = { Text(it.productId, Modifier.testTag("product")) }
                    )
                )
            }
        }
    }

    private fun assertItems(expected: List<String>) {
        val all =
            listOf(
                ProductionTestTags.PRIMARY_HOME,
                ProductionTestTags.PRIMARY_CATEGORIES,
                ProductionTestTags.PRIMARY_SEARCH,
                ProductionTestTags.PRIMARY_WISHLIST,
                ProductionTestTags.PRIMARY_ACCOUNT
            )
        (all - expected.toSet()).forEach { rule.onNodeWithTag(it).assertDoesNotExist() }
        val positions = expected.map {
            rule.onNodeWithTag(it).assertIsDisplayed().fetchSemanticsNode().boundsInRoot.center.x
        }
        assertEquals(positions.sorted(), positions)
    }
}

internal fun capabilityComposition(
    search: Boolean = true,
    wishlist: Boolean = true,
    account: Boolean = true,
    order: List<PrimaryNavigationDestination>? = null
): ApplicationComposition {
    val capabilities = ApplicationCapabilities(
        if (search) CapabilityState.ENABLED else CapabilityState.DISABLED,
        if (wishlist) CapabilityState.ENABLED else CapabilityState.DISABLED,
        if (account) {
            CustomerAccountCapability.Enabled(
                CustomerAccountConfiguration("", "", "", "", "", "", "", "", emptySet())
            )
        } else {
            CustomerAccountCapability.Disabled
        }
    )
    val destinations = order ?: buildList {
        add(HOME)
        add(CATEGORIES)
        if (search) add(SEARCH)
        if (wishlist) add(WISHLIST)
        if (account) add(ACCOUNT)
    }
    return ApplicationComposition(capabilities, PrimaryNavigationSpec.create(destinations, capabilities))
}

internal val capabilityDeepLinks =
    MobileDeepLinkConfiguration("https://shop.example/collections", "https://shop.example/products")
internal val capabilityAccountBindings =
    CustomerAccountFeatureBindings("https://shop.example/orders", TrackingUrlPolicy(setOf("tracking.example")), {
            _,
            _
        ->
        DeletionPageLaunchResult.REJECTED
    })
