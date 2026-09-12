@file:Suppress(
    "FunctionNaming",
    "TooManyFunctions",
    "ktlint:standard:function-naming"
) // Typed route graph keeps destination adapters together.

package com.gurbakir.mobile

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScope
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navDeepLink
import androidx.navigation.toRoute
import com.gurbakir.account.CustomerOrderIds
import com.gurbakir.account.oauth.CustomerAccountAuthorizationBrowser
import com.gurbakir.foundation.config.ApplicationCapability
import com.gurbakir.foundation.config.ApplicationComposition
import com.gurbakir.foundation.config.BrandConfiguration
import com.gurbakir.foundation.config.PrimaryNavigationDestination
import com.gurbakir.foundation.ui.CommerceTheme
import com.gurbakir.foundation.ui.LocalBrandSpacing
import com.gurbakir.mobile.account.AccountActions
import com.gurbakir.mobile.account.AccountEffect
import com.gurbakir.mobile.account.AccountScreen
import com.gurbakir.mobile.account.AccountViewModel
import com.gurbakir.mobile.accountdeletion.DeletionPageId
import com.gurbakir.mobile.accountdeletion.DeletionPageLaunchResult
import com.gurbakir.mobile.address.AddressConfirmationType
import com.gurbakir.mobile.address.AddressFormActions
import com.gurbakir.mobile.address.AddressFormEffect
import com.gurbakir.mobile.address.AddressFormScreen
import com.gurbakir.mobile.address.AddressFormViewModel
import com.gurbakir.mobile.address.AddressListActions
import com.gurbakir.mobile.address.AddressListEffect
import com.gurbakir.mobile.address.AddressListScreen
import com.gurbakir.mobile.address.AddressListViewModel
import com.gurbakir.mobile.cart.CartViewModel
import com.gurbakir.mobile.catalog.CategoriesScreen
import com.gurbakir.mobile.catalog.CategoriesViewModel
import com.gurbakir.mobile.catalog.CollectionActions
import com.gurbakir.mobile.catalog.CollectionScreen
import com.gurbakir.mobile.catalog.CollectionViewModel
import com.gurbakir.mobile.core.R
import com.gurbakir.mobile.home.HomeActions
import com.gurbakir.mobile.home.HomeScreen
import com.gurbakir.mobile.home.HomeViewModel
import com.gurbakir.mobile.navigation.AccountDeletionRoute
import com.gurbakir.mobile.navigation.AccountRoute
import com.gurbakir.mobile.navigation.AddressFormRoute
import com.gurbakir.mobile.navigation.AddressListRoute
import com.gurbakir.mobile.navigation.CartRoute
import com.gurbakir.mobile.navigation.CategoriesRoute
import com.gurbakir.mobile.navigation.CollectionRoute
import com.gurbakir.mobile.navigation.CustomerAccountFeatureBindings
import com.gurbakir.mobile.navigation.HomeRoute
import com.gurbakir.mobile.navigation.LegalSupportRoute
import com.gurbakir.mobile.navigation.MobileDeepLinkConfiguration
import com.gurbakir.mobile.navigation.OrderDetailRoute
import com.gurbakir.mobile.navigation.OrderListRoute
import com.gurbakir.mobile.navigation.ProductRoute
import com.gurbakir.mobile.navigation.ProfileRoute
import com.gurbakir.mobile.navigation.RouteRecoveryReason
import com.gurbakir.mobile.navigation.RouteRecoveryRoute
import com.gurbakir.mobile.navigation.SearchRoute
import com.gurbakir.mobile.navigation.WishlistRoute
import com.gurbakir.mobile.navigation.validateCustomerAccountBindings
import com.gurbakir.mobile.order.OrderDetailActions
import com.gurbakir.mobile.order.OrderDetailEffect
import com.gurbakir.mobile.order.OrderDetailScreen
import com.gurbakir.mobile.order.OrderDetailViewModel
import com.gurbakir.mobile.order.OrderListActions
import com.gurbakir.mobile.order.OrderListEffect
import com.gurbakir.mobile.order.OrderListScreen
import com.gurbakir.mobile.order.OrderListViewModel
import com.gurbakir.mobile.order.TrackingLauncher
import com.gurbakir.mobile.order.TrackingUrlPolicy
import com.gurbakir.mobile.product.ProductDetailActions
import com.gurbakir.mobile.product.ProductDetailScreen
import com.gurbakir.mobile.product.ProductDetailViewModel
import com.gurbakir.mobile.profile.ProfileActions
import com.gurbakir.mobile.profile.ProfileEffect
import com.gurbakir.mobile.profile.ProfileScreen
import com.gurbakir.mobile.profile.ProfileViewModel
import com.gurbakir.mobile.search.SearchActions
import com.gurbakir.mobile.search.SearchScreen
import com.gurbakir.mobile.search.SearchViewModel
import com.gurbakir.mobile.ui.AppNavigationItem
import com.gurbakir.mobile.ui.DestinationLevel
import com.gurbakir.mobile.ui.DestinationScaffold
import com.gurbakir.mobile.ui.consumeDestinationInsets
import com.gurbakir.mobile.update.UpdatePolicyDestination
import com.gurbakir.mobile.wishlist.WishlistDestination
import com.gurbakir.mobile.wishlist.wishlistMembership
import com.gurbakir.storefront.StorefrontProductIds

@Composable
@Suppress("LongParameterList", "LongMethod") // Keep app-owned inputs and feature adapters together.
fun MobileCoreApp(
    brand: BrandConfiguration,
    deepLinks: MobileDeepLinkConfiguration,
    applicationComposition: ApplicationComposition,
    customerAccountBindings: CustomerAccountFeatureBindings?,
    legalSupportDestination: @Composable (onBack: () -> Unit) -> Unit,
    navController: NavHostController = rememberNavController()
) {
    validateCustomerAccountBindings(applicationComposition, customerAccountBindings)
    val darkTheme = !LocalInspectionMode.current && isSystemInDarkTheme()
    CommerceTheme(designTokens = brand.designTokens, darkTheme = darkTheme) {
        val currentDestination = navController.currentBackStackEntryAsState().value?.destination
        if (applicationComposition.capabilities.isEnabled(ApplicationCapability.CUSTOMER_ACCOUNT)) {
            PrivateOrderCapturePolicy(currentDestination)
        }
        ProductionAppShell(
            navController = navController,
            currentDestination = currentDestination,
            applicationComposition = applicationComposition,
            overlay = {
                UpdatePolicyDestination(
                    onLegalSupport = {
                        navController.navigate(LegalSupportRoute) { launchSingleTop = true }
                    },
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
        ) {
            ProductionNavHost(
                navController = navController,
                deepLinks = deepLinks,
                applicationComposition = applicationComposition,
                customerAccountBindings = customerAccountBindings,
                modifier = Modifier.fillMaxSize(),
                content =
                    ProductionDestinationContent(
                        home = { HomeDestination(navController, brand, applicationComposition) },
                        categories = { CategoriesDestination(navController) },
                        collection = { route -> CollectionDestination(navController, route, applicationComposition) },
                        search = { SearchDestination(navController, applicationComposition) },
                        wishlist = { WishlistDestination(navController, applicationComposition) },
                        account = { AccountDestination(navController, applicationComposition) },
                        accountDeletion = {
                            customerAccountBindings?.let {
                                AccountDeletionDestination(navController, it.openDeletionPage, applicationComposition)
                            }
                        },
                        profile = { ProfileDestination(navController, applicationComposition) },
                        addressList = { AddressListDestination(navController, applicationComposition) },
                        addressForm = { route -> AddressFormDestination(navController, route, applicationComposition) },
                        orderList = { OrderListDestination(navController, applicationComposition) },
                        orderDetail = { route ->
                            customerAccountBindings?.let {
                                OrderDetailDestination(
                                    navController,
                                    route,
                                    it.trackingUrlPolicy,
                                    applicationComposition
                                )
                            }
                        },
                        cart = { CartDestination(navController) },
                        legalSupport = { legalSupportDestination(navController::popBackStackOrHome) },
                        product = { route -> ProductDestination(navController, route, applicationComposition) }
                    )
            )
        }
    }
}

@Composable
@Suppress("LongParameterList") // Composition, navigation state, overlay and content are independent shell inputs.
fun ProductionAppShell(
    navController: NavHostController,
    currentDestination: NavDestination?,
    modifier: Modifier = Modifier,
    applicationComposition: ApplicationComposition,
    overlay: @Composable BoxScope.() -> Unit = {},
    content: @Composable () -> Unit
) {
    val primaryDestination = currentDestination.primaryDestination()?.takeIf {
        it in applicationComposition.primaryNavigation.destinations
    }
    val defaultLayoutType =
        NavigationSuiteScaffoldDefaults.calculateFromAdaptiveInfo(currentWindowAdaptiveInfo())
    NavigationSuiteScaffold(
        modifier =
            modifier.then(
                if (primaryDestination == null) {
                    Modifier
                } else {
                    Modifier.testTag(ProductionTestTags.PRIMARY_NAVIGATION)
                }
            ),
        navigationSuiteItems = {
            if (primaryDestination != null) {
                ProductionPrimaryNavigation(navController, currentDestination, applicationComposition)
            }
        },
        layoutType =
            if (primaryDestination == null) {
                NavigationSuiteType.None
            } else {
                defaultLayoutType
            }
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            content()
            overlay()
        }
    }
}

private fun NavigationSuiteScope.ProductionPrimaryNavigation(
    navController: NavHostController,
    currentDestination: NavDestination?,
    applicationComposition: ApplicationComposition
) {
    applicationComposition.primaryNavigation.destinations.forEach { primary ->
        val destination = primary.presentation()
        AppNavigationItem(
            selected = destination.matches(currentDestination),
            onClick = { navController.navigatePrimary(primary, applicationComposition) },
            labelResourceId = destination.labelResourceId,
            accessibilityLabelResourceId = destination.accessibilityLabelResourceId,
            selectedIconResourceId = destination.selectedIconResourceId,
            unselectedIconResourceId = destination.unselectedIconResourceId,
            testTag = destination.testTag
        )
    }
}

private enum class PrimaryDestination(
    val route: Any,
    val labelResourceId: Int,
    val accessibilityLabelResourceId: Int,
    val selectedIconResourceId: Int,
    val unselectedIconResourceId: Int,
    val testTag: String
) {
    HOME(
        HomeRoute,
        R.string.primary_home,
        R.string.home_title,
        R.drawable.ic_nav_home_selected,
        R.drawable.ic_nav_home,
        ProductionTestTags.PRIMARY_HOME
    ),
    CATEGORIES(
        CategoriesRoute,
        R.string.primary_catalog,
        R.string.categories_title,
        R.drawable.ic_nav_categories_selected,
        R.drawable.ic_nav_categories,
        ProductionTestTags.PRIMARY_CATEGORIES
    ),
    SEARCH(
        SearchRoute,
        R.string.primary_search,
        R.string.search_title,
        R.drawable.ic_nav_search_selected,
        R.drawable.ic_nav_search,
        ProductionTestTags.PRIMARY_SEARCH
    ),
    WISHLIST(
        WishlistRoute,
        R.string.primary_saved,
        R.string.wishlist_title,
        R.drawable.ic_nav_wishlist_selected,
        R.drawable.ic_nav_wishlist,
        ProductionTestTags.PRIMARY_WISHLIST
    ),
    ACCOUNT(
        AccountRoute,
        R.string.primary_account,
        R.string.account_title,
        R.drawable.ic_nav_account_selected,
        R.drawable.ic_nav_account,
        ProductionTestTags.PRIMARY_ACCOUNT
    );

    fun matches(destination: NavDestination?): Boolean = when (this) {
        HOME -> destination?.hasRoute<HomeRoute>() == true
        CATEGORIES -> destination?.hasRoute<CategoriesRoute>() == true
        SEARCH -> destination?.hasRoute<SearchRoute>() == true
        WISHLIST -> destination?.hasRoute<WishlistRoute>() == true
        ACCOUNT -> destination?.hasRoute<AccountRoute>() == true
    }
}

private fun NavDestination?.primaryDestination(): PrimaryNavigationDestination? =
    PrimaryNavigationDestination.entries.firstOrNull { it.presentation().matches(this) }

private fun PrimaryNavigationDestination.presentation(): PrimaryDestination = when (this) {
    PrimaryNavigationDestination.HOME -> PrimaryDestination.HOME
    PrimaryNavigationDestination.CATEGORIES -> PrimaryDestination.CATEGORIES
    PrimaryNavigationDestination.SEARCH -> PrimaryDestination.SEARCH
    PrimaryNavigationDestination.WISHLIST -> PrimaryDestination.WISHLIST
    PrimaryNavigationDestination.ACCOUNT -> PrimaryDestination.ACCOUNT
}

internal fun NavHostController.navigatePrimary(
    destination: PrimaryNavigationDestination,
    applicationComposition: ApplicationComposition
) {
    if (destination !in applicationComposition.primaryNavigation.destinations) {
        recoverUnavailableDestination()
        return
    }
    navigate(destination.presentation().route) {
        popUpTo<HomeRoute> { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
private fun HomeDestination(
    navController: NavHostController,
    brand: BrandConfiguration,
    applicationComposition: ApplicationComposition
) {
    val viewModel: HomeViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val cartViewModel: CartViewModel = hiltViewModel()
    val cartState by cartViewModel.state.collectAsStateWithLifecycle()
    val wishlist = if (applicationComposition.capabilities.isEnabled(
            ApplicationCapability.WISHLIST
        )
    ) {
        wishlistMembership()
    } else {
        null
    }
    HomeScreen(
        state = state,
        brandDisplayName = brand.displayName,
        actions =
            HomeActions(
                retryProductRange = viewModel::retryProductRange,
                retryFeaturedProduct = viewModel::retryFeaturedProduct,
                openCategories = {
                    navController.navigatePrimary(PrimaryNavigationDestination.CATEGORIES, applicationComposition)
                },
                openCart = { navController.navigate(CartRoute) },
                openLegalSupport = { navController.navigate(LegalSupportRoute) },
                openCollection = { handle -> navController.navigate(CollectionRoute(handle)) },
                openProduct = navController::navigateProduct,
                onSetWishlist = wishlist?.onSetSaved
            ),
        wishlist = wishlist?.state,
        cartQuantity = cartState.badgeQuantity
    )
}

@Composable
private fun CategoriesDestination(navController: NavHostController) {
    val viewModel: CategoriesViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    CategoriesScreen(
        state = state,
        onRetry = viewModel::retry,
        onOpenCollection = { handle -> navController.navigate(CollectionRoute(handle)) }
    )
}

@Composable
private fun CollectionDestination(
    navController: NavHostController,
    route: CollectionRoute,
    applicationComposition: ApplicationComposition
) {
    val viewModel: CollectionViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val wishlist = if (applicationComposition.capabilities.isEnabled(
            ApplicationCapability.WISHLIST
        )
    ) {
        wishlistMembership()
    } else {
        null
    }
    LaunchedEffect(route.handle) { viewModel.start(route.handle) }
    CollectionScreen(
        state = state,
        actions =
            CollectionActions(
                onBack = { navController.popBackStackOrHome() },
                onRetry = viewModel::retry,
                onSortSelected = viewModel::selectSort,
                onProductTypeToggled = viewModel::toggleProductType,
                onLoadMore = viewModel::loadNextPage,
                onOpenProduct = navController::navigateProduct,
                onSetWishlist = wishlist?.onSetSaved
            ),
        wishlist = wishlist?.state
    )
}

@Composable
private fun SearchDestination(navController: NavHostController, applicationComposition: ApplicationComposition) {
    val viewModel: SearchViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val wishlist = if (applicationComposition.capabilities.isEnabled(
            ApplicationCapability.WISHLIST
        )
    ) {
        wishlistMembership()
    } else {
        null
    }
    SearchScreen(
        state = state,
        actions =
            SearchActions(
                onQueryChanged = viewModel::onQueryChanged,
                onSubmit = viewModel::submit,
                onRetry = viewModel::retry,
                onLoadMore = viewModel::loadNextPage,
                onSelectHistory = { query ->
                    viewModel.onQueryChanged(query)
                    viewModel.submit()
                },
                onRemoveHistory = viewModel::removeHistory,
                onClearHistory = viewModel::clearHistory,
                onHistoryEnabledChanged = viewModel::setHistoryEnabled,
                onOpenProduct = navController::navigateProduct,
                onSetWishlist = wishlist?.onSetSaved
            ),
        wishlist = wishlist?.state
    )
}

@Composable
private fun ProductDestination(
    navController: NavHostController,
    route: ProductRoute,
    applicationComposition: ApplicationComposition
) {
    val productId = StorefrontProductIds.productGidFromRoute(route.productId)
    val variantId = route.variantId?.let(StorefrontProductIds::variantGidFromRoute)
    if (productId == null || (route.variantId != null && variantId == null)) {
        LaunchedEffect(route) {
            navController.navigate(RouteRecoveryRoute(RouteRecoveryReason.UNAVAILABLE_DESTINATION)) {
                popUpTo<ProductRoute> { inclusive = true }
            }
        }
        return
    }
    val viewModel: ProductDetailViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val wishlist = if (applicationComposition.capabilities.isEnabled(
            ApplicationCapability.WISHLIST
        )
    ) {
        wishlistMembership()
    } else {
        null
    }
    LaunchedEffect(productId, variantId) { viewModel.start(productId, variantId) }
    ProductDetailScreen(
        state = state,
        actions =
            ProductDetailActions(
                onBack = { navController.popBackStackOrHome() },
                onBrowse = { navController.popBackStackOrHome() },
                onRetry = viewModel::retry,
                onSelectOption = viewModel::selectOption,
                onSelectMedia = viewModel::selectMedia,
                onOpenMediaViewer = { viewModel.setMediaViewer(true) },
                onCloseMediaViewer = { viewModel.setMediaViewer(false) },
                onAddToCart = viewModel::addToCart,
                onOpenCart = { navController.navigate(CartRoute) },
                onSetWishlist = wishlist?.onSetSaved
            ),
        wishlist = wishlist?.state
    )
}

@Composable
@Suppress("LongParameterList", "LongMethod") // Retained typed nodes and their guards are audited as one graph.
fun ProductionNavHost(
    navController: NavHostController,
    deepLinks: MobileDeepLinkConfiguration,
    content: ProductionDestinationContent,
    modifier: Modifier = Modifier,
    applicationComposition: ApplicationComposition,
    customerAccountBindings: CustomerAccountFeatureBindings? = null
) {
    validateCustomerAccountBindings(applicationComposition, customerAccountBindings)
    val capabilities = applicationComposition.capabilities
    NavHost(navController = navController, startDestination = HomeRoute, modifier = modifier) {
        composable<HomeRoute> { content.home() }
        composable<CategoriesRoute> { content.categories() }
        composable<SearchRoute> {
            CapabilityDestination(capabilities.isEnabled(ApplicationCapability.SEARCH), navController) {
                content.search()
            }
        }
        composable<WishlistRoute> {
            CapabilityDestination(capabilities.isEnabled(ApplicationCapability.WISHLIST), navController) {
                content.wishlist()
            }
        }
        composable<AccountRoute> {
            CapabilityDestination(capabilities.isEnabled(ApplicationCapability.CUSTOMER_ACCOUNT), navController) {
                content.account()
            }
        }
        composable<AccountDeletionRoute> {
            CapabilityDestination(capabilities.isEnabled(ApplicationCapability.CUSTOMER_ACCOUNT), navController) {
                content.accountDeletion()
            }
        }
        composable<ProfileRoute> {
            CapabilityDestination(capabilities.isEnabled(ApplicationCapability.CUSTOMER_ACCOUNT), navController) {
                content.profile()
            }
        }
        composable<AddressListRoute> {
            CapabilityDestination(capabilities.isEnabled(ApplicationCapability.CUSTOMER_ACCOUNT), navController) {
                content.addressList()
            }
        }
        composable<AddressFormRoute> { backStackEntry ->
            CapabilityDestination(capabilities.isEnabled(ApplicationCapability.CUSTOMER_ACCOUNT), navController) {
                content.addressForm(backStackEntry.toRoute())
            }
        }
        composable<OrderListRoute> {
            CapabilityDestination(capabilities.isEnabled(ApplicationCapability.CUSTOMER_ACCOUNT), navController) {
                content.orderList()
            }
        }
        composable<OrderDetailRoute>(
            deepLinks =
                listOfNotNull(
                    customerAccountBindings?.takeIf {
                        capabilities.isEnabled(ApplicationCapability.CUSTOMER_ACCOUNT)
                    }?.let { navDeepLink<OrderDetailRoute>(basePath = it.orderDeepLinkBasePath) }
                )
        ) { backStackEntry ->
            CapabilityDestination(capabilities.isEnabled(ApplicationCapability.CUSTOMER_ACCOUNT), navController) {
                content.orderDetail(backStackEntry.toRoute())
            }
        }
        composable<CartRoute> { content.cart() }
        composable<LegalSupportRoute> { content.legalSupport() }
        composable<ProductRoute>(
            deepLinks =
                listOf(
                    navDeepLink<ProductRoute>(basePath = deepLinks.productBasePath)
                )
        ) { backStackEntry ->
            content.product(backStackEntry.toRoute())
        }
        composable<CollectionRoute>(
            deepLinks =
                listOf(
                    navDeepLink<CollectionRoute>(basePath = deepLinks.collectionBasePath)
                )
        ) { backStackEntry ->
            content.collection(backStackEntry.toRoute())
        }
        composable<RouteRecoveryRoute> {
            RouteRecoveryScreen(
                onHome = {
                    navController.navigate(HomeRoute) {
                        popUpTo<HomeRoute> { inclusive = false }
                        launchSingleTop = true
                    }
                },
                onLegalSupport = { navController.navigate(LegalSupportRoute) }
            )
        }
    }
}

@Composable
private fun CapabilityDestination(enabled: Boolean, navController: NavHostController, content: @Composable () -> Unit) {
    if (!enabled) {
        LaunchedEffect(Unit) { navController.recoverUnavailableDestination() }
        return
    }
    content()
}

// Home remains the state anchor, even if the application renders it after another primary item.
// Clear the entire unavailable branch, including restored ancestors, before recovery is shown.
private fun NavHostController.recoverUnavailableDestination() {
    val homePresent = runCatching { getBackStackEntry<HomeRoute>() }.isSuccess
    navigate(RouteRecoveryRoute(RouteRecoveryReason.UNAVAILABLE_DESTINATION)) {
        if (homePresent) {
            popUpTo<HomeRoute> { inclusive = false }
        } else {
            popUpTo(graph.id) { inclusive = false }
        }
        launchSingleTop = true
    }
}

data class ProductionDestinationContent(
    val home: @Composable () -> Unit,
    val categories: @Composable () -> Unit = {},
    val collection: @Composable (CollectionRoute) -> Unit = {},
    val search: @Composable () -> Unit = {},
    val wishlist: @Composable () -> Unit = {},
    val account: @Composable () -> Unit = {},
    val accountDeletion: @Composable () -> Unit = {},
    val profile: @Composable () -> Unit = {},
    val addressList: @Composable () -> Unit = {},
    val addressForm: @Composable (AddressFormRoute) -> Unit = {},
    val orderList: @Composable () -> Unit = {},
    val orderDetail: @Composable (OrderDetailRoute) -> Unit = {},
    val cart: @Composable () -> Unit = {},
    val legalSupport: @Composable () -> Unit = {},
    val product: @Composable (ProductRoute) -> Unit = {}
)

@Composable
private fun PrivateOrderCapturePolicy(destination: NavDestination?) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val protected =
        destination?.hasRoute<OrderListRoute>() == true ||
            destination?.hasRoute<OrderDetailRoute>() == true
    DisposableEffect(activity, protected) {
        if (protected) {
            activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
        onDispose {
            if (protected) activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

fun NavHostController.popBackStackOrHome() {
    if (!popBackStack()) {
        navigate(HomeRoute) { launchSingleTop = true }
    }
}

internal fun NavHostController.resetToAccountAfterSessionExpiry(applicationComposition: ApplicationComposition) {
    if (!applicationComposition.capabilities.isEnabled(ApplicationCapability.CUSTOMER_ACCOUNT)) {
        recoverUnavailableDestination()
        return
    }
    navigate(AccountRoute) {
        if (currentDestination?.hasRoute<OrderDetailRoute>() == true) {
            popUpTo(graph.id) { inclusive = true }
        } else {
            popUpTo<AccountRoute> { inclusive = true }
        }
        launchSingleTop = true
    }
}

internal fun NavHostController.navigateProduct(productGid: String) {
    val routeId = StorefrontProductIds.productRouteFromGid(productGid)
    if (routeId == null) {
        navigate(RouteRecoveryRoute(RouteRecoveryReason.UNAVAILABLE_DESTINATION))
    } else {
        navigate(ProductRoute(routeId))
    }
}

@Composable
internal fun RouteRecoveryScreen(onHome: () -> Unit, onLegalSupport: () -> Unit) {
    val spacing = LocalBrandSpacing.current
    DestinationScaffold(
        title = stringResource(R.string.route_recovery_title),
        level = DestinationLevel.SECONDARY,
        onNavigateUp = onHome,
        modifier = Modifier.testTag(ProductionTestTags.ROUTE_RECOVERY)
    ) { padding ->
        Column(
            modifier =
                Modifier.fillMaxSize()
                    .padding(padding)
                    .consumeDestinationInsets(padding)
                    .padding(spacing.sectionDp.dp),
            verticalArrangement = Arrangement.spacedBy(spacing.generousDp.dp)
        ) {
            Text(stringResource(R.string.route_recovery_message))
            Button(onClick = onHome, modifier = Modifier.testTag(ProductionTestTags.ROUTE_RECOVERY_HOME)) {
                Text(stringResource(R.string.route_recovery_home))
            }
            TextButton(
                onClick = onLegalSupport,
                modifier = Modifier.testTag(ProductionTestTags.ROUTE_RECOVERY_LEGAL_SUPPORT)
            ) {
                Text(stringResource(R.string.legal_support_home_action))
            }
        }
    }
}

internal object ProductionTestTags {
    const val PRIMARY_NAVIGATION = "production-primary-navigation"
    const val PRIMARY_HOME = "production-primary-home"
    const val PRIMARY_CATEGORIES = "production-primary-categories"
    const val PRIMARY_SEARCH = "production-primary-search"
    const val PRIMARY_WISHLIST = "production-primary-wishlist"
    const val PRIMARY_ACCOUNT = "production-primary-account"
    const val ROUTE_RECOVERY = "route-recovery-root"
    const val ROUTE_RECOVERY_HOME = "route-recovery-home"
    const val ROUTE_RECOVERY_LEGAL_SUPPORT = "route-recovery-legal-support"
}
