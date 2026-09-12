@file:Suppress("FunctionNaming")

package com.gurbakir.mobile.wishlist

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.gurbakir.foundation.config.ApplicationCapability
import com.gurbakir.foundation.config.ApplicationComposition
import com.gurbakir.foundation.config.PrimaryNavigationDestination
import com.gurbakir.mobile.navigatePrimary
import com.gurbakir.mobile.navigateProduct
import com.gurbakir.mobile.navigation.CategoriesRoute

@Composable
internal fun WishlistDestination(navController: NavHostController, applicationComposition: ApplicationComposition) {
    val viewModel: WishlistViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    WishlistScreen(
        state = state,
        actions =
            WishlistActions(
                onBrowse = {
                    navController.navigatePrimary(PrimaryNavigationDestination.CATEGORIES, applicationComposition)
                },
                onRetry = viewModel::refresh,
                onOpenProduct = navController::navigateProduct,
                onRemove = viewModel::remove,
                onClear = viewModel::clear
            )
    )
}
