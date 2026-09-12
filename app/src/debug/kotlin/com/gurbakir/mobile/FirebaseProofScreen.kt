@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@file:Suppress("FunctionNaming")

package com.gurbakir.mobile

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gurbakir.foundation.ui.LocalBrandSpacing

@Composable
internal fun FirebaseProofRoute(
    viewModel: FirebaseProofViewModel,
    openedFromNotification: Boolean,
    onBack: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            viewModel.notificationPermissionResult(granted)
        }

    LaunchedEffect(openedFromNotification) {
        if (openedFromNotification) viewModel.notificationOpened()
    }

    FirebaseProofScreen(
        state = state,
        onRefreshRemoteConfig = viewModel::refreshRemoteConfig,
        onEnablePush = {
            viewModel.requestPushPermission()
            if (
                Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
            ) {
                viewModel.notificationPermissionResult(true)
            } else {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        },
        onDisablePush = viewModel::unregisterPush,
        onBack = onBack
    )
}

@Composable
internal fun FirebaseProofScreen(
    state: FirebaseProofUiState,
    onRefreshRemoteConfig: () -> Unit,
    onEnablePush: () -> Unit,
    onDisablePush: () -> Unit,
    onBack: () -> Unit
) {
    val spacing = LocalBrandSpacing.current
    Scaffold(
        modifier = Modifier.testTag(FirebaseProofTestTags.ROOT),
        topBar = { TopAppBar(title = { Text(stringResource(R.string.firebase_proof_title)) }) }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(spacing.sectionDp.dp),
            verticalArrangement = Arrangement.spacedBy(spacing.generousDp.dp)
        ) {
            Text(
                text = stringResource(R.string.integration_firebase),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.semantics { heading() }
            )
            Text(stringResource(R.string.firebase_proof_explanation))
            Text(
                text = stringResource(state.phase.statusResourceId()),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.testTag(FirebaseProofTestTags.STATUS)
            )
            Button(
                onClick = onRefreshRemoteConfig,
                enabled = !state.busy,
                modifier = Modifier.testTag(FirebaseProofTestTags.REFRESH_REMOTE_CONFIG)
            ) {
                Text(stringResource(R.string.firebase_refresh_remote_config))
            }
            if (!state.pushRegistered) {
                Button(
                    onClick = onEnablePush,
                    enabled = !state.busy,
                    modifier = Modifier.testTag(FirebaseProofTestTags.ENABLE_PUSH)
                ) {
                    Text(stringResource(R.string.firebase_enable_push))
                }
            } else {
                Button(
                    onClick = onDisablePush,
                    enabled = !state.busy,
                    modifier = Modifier.testTag(FirebaseProofTestTags.DISABLE_PUSH)
                ) {
                    Text(stringResource(R.string.firebase_disable_push))
                }
            }
            Button(
                onClick = onBack,
                enabled = !state.busy,
                modifier = Modifier.testTag(FirebaseProofTestTags.BACK)
            ) {
                Text(stringResource(R.string.back))
            }
        }
    }
}

private fun FirebaseProofPhase.statusResourceId(): Int = when (this) {
    FirebaseProofPhase.READY -> R.string.firebase_status_ready
    FirebaseProofPhase.REFRESHING_REMOTE_CONFIG -> R.string.firebase_status_refreshing_remote_config
    FirebaseProofPhase.REMOTE_CONFIG_FETCHED -> R.string.firebase_status_remote_config_fetched
    FirebaseProofPhase.REMOTE_CONFIG_LOCAL_DEFAULTS -> R.string.firebase_status_remote_config_local_defaults
    FirebaseProofPhase.AWAITING_NOTIFICATION_PERMISSION -> R.string.firebase_status_awaiting_permission
    FirebaseProofPhase.NOTIFICATION_PERMISSION_DENIED -> R.string.firebase_status_permission_denied
    FirebaseProofPhase.REGISTERING_PUSH -> R.string.firebase_status_registering_push
    FirebaseProofPhase.PUSH_REGISTERED -> R.string.firebase_status_push_registered
    FirebaseProofPhase.PUSH_REGISTRATION_FAILED -> R.string.firebase_status_push_registration_failed
    FirebaseProofPhase.PUSH_NOTIFICATION_OPENED -> R.string.firebase_status_notification_opened
    FirebaseProofPhase.UNREGISTERING_PUSH -> R.string.firebase_status_unregistering_push
    FirebaseProofPhase.PUSH_UNREGISTERED -> R.string.firebase_status_push_unregistered
    FirebaseProofPhase.PUSH_UNREGISTER_FAILED -> R.string.firebase_status_push_unregister_failed
}

internal object FirebaseProofTestTags {
    const val ROOT = "firebase-proof-root"
    const val STATUS = "firebase-proof-status"
    const val REFRESH_REMOTE_CONFIG = "firebase-proof-refresh-remote-config"
    const val ENABLE_PUSH = "firebase-proof-enable-push"
    const val DISABLE_PUSH = "firebase-proof-disable-push"
    const val BACK = "firebase-proof-back"
}
