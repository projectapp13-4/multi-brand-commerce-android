@file:Suppress("FunctionNaming")

package com.gurbakir.mobile

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.gurbakir.foundation.config.ApplicationCapability
import com.gurbakir.foundation.config.ApplicationComposition
import com.gurbakir.foundation.config.PrimaryNavigationDestination
import com.gurbakir.mobile.accountdeletion.AccountDeletionActions
import com.gurbakir.mobile.accountdeletion.AccountDeletionEffect
import com.gurbakir.mobile.accountdeletion.AccountDeletionScreen
import com.gurbakir.mobile.accountdeletion.AccountDeletionViewModel
import com.gurbakir.mobile.accountdeletion.DeletionPageId
import com.gurbakir.mobile.accountdeletion.DeletionPageLaunchResult

@Composable
internal fun AccountDeletionDestination(
    navController: NavHostController,
    openDeletionPage: (Context, DeletionPageId) -> DeletionPageLaunchResult,
    applicationComposition: ApplicationComposition
) {
    val viewModel: AccountDeletionViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onActivityResumed() }
    LaunchedEffect(viewModel, navController, applicationComposition) {
        viewModel.effects.collect { effect ->
            when (effect) {
                AccountDeletionEffect.ReturnToAccount ->
                    navController.resetToAccountAfterSessionExpiry(applicationComposition)
            }
        }
    }
    AccountDeletionScreen(
        state = state,
        actions =
            AccountDeletionActions(
                onBack = navController::popBackStackOrHome,
                onOpenPage = { page ->
                    viewModel.onLaunchResult(page.id, openDeletionPage(context, page.id))
                },
                onClearSearchHistoryChanged = viewModel::setClearSearchHistory,
                onClearWishlistChanged = viewModel::setClearWishlist,
                onDiscardCartChanged = viewModel::setDiscardCart,
                onRequestLocalClear = viewModel::requestLocalClearConfirmation,
                onDismissLocalClear = viewModel::dismissLocalClearConfirmation,
                onConfirmLocalClear = viewModel::confirmLocalClearAndSignOut,
                onRetry = viewModel::retry,
                onFinish = viewModel::finish
            )
    )
}
