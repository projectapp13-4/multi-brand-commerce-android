@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package com.gurbakir.mobile

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.gurbakir.account.CustomerOrderIds
import com.gurbakir.account.oauth.CustomerAccountAuthorizationBrowser
import com.gurbakir.foundation.config.ApplicationCapability
import com.gurbakir.foundation.config.ApplicationComposition
import com.gurbakir.foundation.config.PrimaryNavigationDestination
import com.gurbakir.mobile.account.AccountActions
import com.gurbakir.mobile.account.AccountEffect
import com.gurbakir.mobile.account.AccountScreen
import com.gurbakir.mobile.account.AccountViewModel
import com.gurbakir.mobile.address.AddressConfirmationType
import com.gurbakir.mobile.address.AddressFormActions
import com.gurbakir.mobile.address.AddressFormEffect
import com.gurbakir.mobile.address.AddressFormScreen
import com.gurbakir.mobile.address.AddressFormViewModel
import com.gurbakir.mobile.address.AddressListActions
import com.gurbakir.mobile.address.AddressListEffect
import com.gurbakir.mobile.address.AddressListScreen
import com.gurbakir.mobile.address.AddressListViewModel
import com.gurbakir.mobile.navigation.AccountDeletionRoute
import com.gurbakir.mobile.navigation.AddressFormRoute
import com.gurbakir.mobile.navigation.AddressListRoute
import com.gurbakir.mobile.navigation.CartRoute
import com.gurbakir.mobile.navigation.LegalSupportRoute
import com.gurbakir.mobile.navigation.OrderDetailRoute
import com.gurbakir.mobile.navigation.OrderListRoute
import com.gurbakir.mobile.navigation.ProfileRoute
import com.gurbakir.mobile.navigation.RouteRecoveryReason
import com.gurbakir.mobile.navigation.RouteRecoveryRoute
import com.gurbakir.mobile.navigation.SearchRoute
import com.gurbakir.mobile.navigation.WishlistRoute
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
import com.gurbakir.mobile.profile.ProfileActions
import com.gurbakir.mobile.profile.ProfileEffect
import com.gurbakir.mobile.profile.ProfileScreen
import com.gurbakir.mobile.profile.ProfileViewModel

@Composable
internal fun AccountDestination(navController: NavHostController, applicationComposition: ApplicationComposition) {
    val viewModel: AccountViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val authorizationBrowser = remember(context.applicationContext) {
        CustomerAccountAuthorizationBrowser(context.applicationContext)
    }
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            viewModel.consumeAuthorizationResult(result.data?.dataString)
        }

    DisposableEffect(authorizationBrowser) {
        onDispose(authorizationBrowser::close)
    }
    LaunchedEffect(viewModel, launcher, authorizationBrowser) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is AccountEffect.LaunchAuthorization -> {
                    runCatching {
                        launcher.launch(authorizationBrowser.createAuthorizationIntent(effect.plan))
                    }.onFailure {
                        viewModel.authorizationLaunchFailed()
                    }
                }
            }
        }
    }

    AccountScreen(
        state = state,
        actions =
            AccountActions(
                onSignIn = viewModel::startAuthorization,
                onRetry = viewModel::retry,
                onRefresh = viewModel::refresh,
                onLogout = viewModel::logout,
                onProfile = { navController.navigate(ProfileRoute) },
                onAddresses = { navController.navigate(AddressListRoute) },
                onOrders = { navController.navigate(OrderListRoute) },
                onAccountDeletion = { navController.navigate(AccountDeletionRoute) },
                onLegalSupport = { navController.navigate(LegalSupportRoute) },
                onSearchHistory = if (applicationComposition.capabilities.isEnabled(ApplicationCapability.SEARCH)) {
                    { navController.navigatePrimary(PrimaryNavigationDestination.SEARCH, applicationComposition) }
                } else {
                    null
                },
                onWishlist = if (applicationComposition.capabilities.isEnabled(ApplicationCapability.WISHLIST)) {
                    { navController.navigatePrimary(PrimaryNavigationDestination.WISHLIST, applicationComposition) }
                } else {
                    null
                },
                onCart = { navController.navigate(CartRoute) }
            )
    )
}

@Composable
internal fun OrderListDestination(navController: NavHostController, applicationComposition: ApplicationComposition) {
    val viewModel: OrderListViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onResumed() }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.clearPrivateContent() }
    LaunchedEffect(viewModel, navController, applicationComposition) {
        viewModel.effects.collect { effect ->
            when (effect) {
                OrderListEffect.ReturnToAccount -> navController.resetToAccountAfterSessionExpiry(
                    applicationComposition
                )
            }
        }
    }
    OrderListScreen(
        state = state,
        actions =
            OrderListActions(
                onBack = {
                    viewModel.clearPrivateContent()
                    navController.popBackStackOrHome()
                },
                onRefresh = viewModel::refresh,
                onRetry = viewModel::retry,
                onLoadMore = viewModel::loadNextPage,
                onOpenOrder = { routeId ->
                    viewModel.clearPrivateContent()
                    navController.navigate(OrderDetailRoute(routeId))
                },
                onSupport = {
                    viewModel.clearPrivateContent()
                    navController.navigate(LegalSupportRoute)
                }
            )
    )
}

@Composable
internal fun OrderDetailDestination(
    navController: NavHostController,
    route: OrderDetailRoute,
    policy: TrackingUrlPolicy,
    applicationComposition: ApplicationComposition
) {
    val orderId = CustomerOrderIds.orderGidFromRoute(route.orderId)
    if (orderId == null) {
        LaunchedEffect(route) {
            navController.navigate(RouteRecoveryRoute(RouteRecoveryReason.UNAVAILABLE_DESTINATION)) {
                popUpTo<OrderDetailRoute> { inclusive = true }
            }
        }
        return
    }
    val viewModel: OrderDetailViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val launcher = remember(policy) { TrackingLauncher(policy) }
    LaunchedEffect(orderId) { viewModel.start(orderId) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onResumed() }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.clearPrivateContent() }
    LaunchedEffect(viewModel, navController, applicationComposition) {
        viewModel.effects.collect { effect ->
            when (effect) {
                OrderDetailEffect.ReturnToAccount -> navController.resetToAccountAfterSessionExpiry(
                    applicationComposition
                )
            }
        }
    }
    OrderDetailScreen(
        state = state,
        actions =
            OrderDetailActions(
                onBack = {
                    viewModel.clearPrivateContent()
                    navController.popBackStackOrHome()
                },
                onRetry = viewModel::retry,
                onOpenTracking = { url -> viewModel.onTrackingLaunchResult(launcher.open(context, url)) },
                onSupport = {
                    viewModel.clearPrivateContent()
                    navController.navigate(LegalSupportRoute)
                }
            ),
        isTrackingAllowed = policy::isAllowed
    )
}

@Composable
internal fun AddressListDestination(navController: NavHostController, applicationComposition: ApplicationComposition) {
    val viewModel: AddressListViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onResumed() }
    LaunchedEffect(viewModel, navController, applicationComposition) {
        viewModel.effects.collect { effect ->
            when (effect) {
                AddressListEffect.ReturnToAccount -> navController.resetToAccountAfterSessionExpiry(
                    applicationComposition
                )
            }
        }
    }
    AddressListScreen(
        state = state,
        actions =
            AddressListActions(
                onBack = navController::popBackStackOrHome,
                onCreate = {
                    viewModel.refreshAfterForm()
                    navController.navigate(AddressFormRoute())
                },
                onEdit = { addressId ->
                    viewModel.refreshAfterForm()
                    navController.navigate(AddressFormRoute(addressId))
                },
                onSetDefault = { addressId ->
                    viewModel.requestConfirmation(addressId, AddressConfirmationType.SET_DEFAULT)
                },
                onDelete = { addressId ->
                    viewModel.requestConfirmation(addressId, AddressConfirmationType.DELETE)
                },
                onConfirm = viewModel::confirm,
                onDismissConfirmation = viewModel::dismissConfirmation,
                onReload = viewModel::reload
            )
    )
}

@Composable
internal fun AddressFormDestination(
    navController: NavHostController,
    route: AddressFormRoute,
    applicationComposition: ApplicationComposition
) {
    val viewModel: AddressFormViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(route.addressId) { viewModel.start(route.addressId) }
    LaunchedEffect(viewModel, navController, applicationComposition) {
        viewModel.effects.collect { effect ->
            when (effect) {
                AddressFormEffect.Saved -> navController.popBackStackOrHome()

                AddressFormEffect.ReturnToAccount -> navController.resetToAccountAfterSessionExpiry(
                    applicationComposition
                )
            }
        }
    }
    AddressFormScreen(
        state = state,
        actions =
            AddressFormActions(
                onBack = navController::popBackStackOrHome,
                onFieldChanged = viewModel::update,
                onMakeDefaultChanged = viewModel::setMakeDefault,
                onSave = viewModel::save,
                onReload = viewModel::reload,
                onFocusHandled = viewModel::consumeFocusRequest
            )
    )
}

@Composable
internal fun ProfileDestination(navController: NavHostController, applicationComposition: ApplicationComposition) {
    val viewModel: ProfileViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel, navController, applicationComposition) {
        viewModel.effects.collect { effect ->
            when (effect) {
                ProfileEffect.ReturnToAccount -> navController.resetToAccountAfterSessionExpiry(applicationComposition)
            }
        }
    }
    ProfileScreen(
        state = state,
        actions =
            ProfileActions(
                onBack = navController::popBackStackOrHome,
                onFirstNameChanged = viewModel::onFirstNameChanged,
                onLastNameChanged = viewModel::onLastNameChanged,
                onSave = viewModel::save,
                onReload = viewModel::reload,
                onFocusHandled = viewModel::consumeFocusRequest
            )
    )
}
