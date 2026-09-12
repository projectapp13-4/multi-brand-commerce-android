@file:Suppress("FunctionNaming")

package com.gurbakir.mobile

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.gurbakir.mobile.cart.CartActions
import com.gurbakir.mobile.cart.CartScreen
import com.gurbakir.mobile.cart.CartViewModel
import com.gurbakir.mobile.checkout.CheckoutViewModel
import com.gurbakir.mobile.navigation.HomeRoute

@Composable
internal fun CartDestination(navController: NavHostController) {
    val viewModel: CartViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val checkoutViewModel: CheckoutViewModel = hiltViewModel()
    val checkoutState by checkoutViewModel.state.collectAsStateWithLifecycle()
    val activity = LocalContext.current.findActivity()
    CartScreen(
        state = state,
        checkoutState = checkoutState,
        actions =
            CartActions(
                onBack = { navController.popBackStackOrHome() },
                onBrowse = {
                    navController.navigate(HomeRoute) {
                        popUpTo<HomeRoute> { inclusive = false }
                        launchSingleTop = true
                    }
                },
                onRetry = viewModel::refresh,
                onOpenProduct = navController::navigateProduct,
                onIncrease = viewModel::increase,
                onDecrease = viewModel::decrease,
                onRemove = viewModel::remove,
                onDiscard = viewModel::discard,
                onCheckout = { activity?.let(checkoutViewModel::start) }
            )
    )
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
