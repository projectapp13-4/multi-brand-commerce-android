@file:Suppress("FunctionNaming")

package com.gurbakir.mobile

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gurbakir.mobile.legal.LegalSupportActions
import com.gurbakir.mobile.legal.LegalSupportScreen
import com.gurbakir.mobile.legal.LegalSupportViewModel
import com.gurbakir.mobile.legal.OwnedPageLauncher
import com.gurbakir.mobile.legal.OwnedPagePolicy

@Composable
internal fun LegalSupportDestination(onBack: () -> Unit) {
    val viewModel: LegalSupportViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val launcher = remember(state.pages) { OwnedPageLauncher(OwnedPagePolicy(state.pages)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onActivityResumed() }
    LegalSupportScreen(
        state = state,
        actions =
            LegalSupportActions(
                onBack = onBack,
                onOpen = { page -> viewModel.onLaunchResult(page.id, launcher.open(context, page)) },
                onFocusRequestHandled = viewModel::onFocusRequestHandled
            )
    )
}
