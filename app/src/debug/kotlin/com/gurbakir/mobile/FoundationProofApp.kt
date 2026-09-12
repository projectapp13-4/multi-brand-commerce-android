@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@file:Suppress("FunctionNaming")

package com.gurbakir.mobile

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.gurbakir.account.oauth.CustomerAccountAuthorizationBrowser
import com.gurbakir.foundation.config.BrandConfiguration
import com.gurbakir.foundation.config.EnvironmentId
import com.gurbakir.foundation.ui.CommerceTheme
import com.gurbakir.foundation.ui.LocalBrandSpacing
import com.gurbakir.mobile.brand.GurbakirBrand
import com.gurbakir.mobile.navigation.FoundationHome
import com.gurbakir.mobile.navigation.IntegrationDetail
import com.gurbakir.mobile.navigation.IntegrationId

@Composable
fun FoundationProofApp(
    navController: NavHostController = rememberNavController(),
    brand: BrandConfiguration = GurbakirBrand.configuration,
    notificationProofNonce: Int = 0,
    onNotificationProofConsumed: () -> Unit = {},
    firebaseConfigured: Boolean = BuildConfig.FIREBASE_CONFIGURED
) {
    val darkTheme = !LocalInspectionMode.current && LocalConfiguration.current.usesDarkUiMode
    CommerceTheme(designTokens = brand.designTokens, darkTheme = darkTheme) {
        FirebaseNotificationProofEffect(
            notificationProofNonce = notificationProofNonce,
            firebaseConfigured = firebaseConfigured,
            onNavigate = {
                navController.navigate(
                    IntegrationDetail(
                        integration = IntegrationId.FIREBASE,
                        openedFromNotification = true
                    )
                ) {
                    launchSingleTop = true
                }
            },
            onConsumed = onNotificationProofConsumed
        )
        NavHost(
            navController = navController,
            startDestination = FoundationHome
        ) {
            composable<FoundationHome> {
                val viewModel: FoundationViewModel = hiltViewModel()
                val state by viewModel.state.collectAsStateWithLifecycle()
                FoundationScreen(
                    state = state,
                    onIntegrationSelected = { navController.navigate(IntegrationDetail(it.id)) }
                )
            }
            composable<IntegrationDetail> { backStackEntry ->
                val route = backStackEntry.toRoute<IntegrationDetail>()
                if (route.integration == IntegrationId.CUSTOMER_ACCOUNT) {
                    val viewModel: CustomerAccountProofViewModel = hiltViewModel()
                    CustomerAccountProofRoute(
                        viewModel = viewModel,
                        onBack = navController::popBackStack
                    )
                } else if (route.integration == IntegrationId.CHECKOUT) {
                    val viewModel: CommerceProofViewModel = hiltViewModel()
                    CommerceProofRoute(
                        viewModel = viewModel,
                        onBack = navController::popBackStack
                    )
                } else if (route.integration == IntegrationId.FIREBASE) {
                    FirebaseProofDestination(
                        firebaseConfigured = firebaseConfigured,
                        onBack = navController::popBackStack,
                        configuredContent = {
                            val viewModel: FirebaseProofViewModel = hiltViewModel()
                            FirebaseProofRoute(
                                viewModel = viewModel,
                                openedFromNotification = route.openedFromNotification,
                                onBack = navController::popBackStack
                            )
                        }
                    )
                } else {
                    IntegrationDetailScreen(
                        integration = route.integration,
                        onBack = navController::popBackStack
                    )
                }
            }
        }
    }
}

@Composable
internal fun FirebaseNotificationProofEffect(
    notificationProofNonce: Int,
    firebaseConfigured: Boolean,
    onNavigate: () -> Unit,
    onConsumed: () -> Unit
) {
    LaunchedEffect(notificationProofNonce, firebaseConfigured) {
        if (notificationProofNonce > 0) {
            if (firebaseConfigured) onNavigate()
            onConsumed()
        }
    }
}

@Composable
internal fun FirebaseProofDestination(
    firebaseConfigured: Boolean,
    onBack: () -> Unit,
    configuredContent: @Composable () -> Unit
) {
    if (firebaseConfigured) {
        configuredContent()
    } else {
        FirebaseProofUnavailableScreen(onBack)
    }
}

@Composable
private fun FirebaseProofUnavailableScreen(onBack: () -> Unit) {
    val spacing = LocalBrandSpacing.current
    Scaffold(
        modifier = Modifier.testTag(FirebaseProofUnavailableTestTags.ROOT),
        topBar = { TopAppBar(title = { Text(stringResource(R.string.firebase_proof_unavailable_title)) }) }
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
            Text(
                text = stringResource(R.string.firebase_proof_unavailable_status),
                modifier = Modifier.testTag(FirebaseProofUnavailableTestTags.STATUS)
            )
            Text(stringResource(R.string.firebase_proof_unavailable_explanation))
            Button(
                onClick = onBack,
                modifier = Modifier.testTag(FirebaseProofUnavailableTestTags.BACK)
            ) {
                Text(stringResource(R.string.back))
            }
        }
    }
}

@Composable
private fun CustomerAccountProofRoute(viewModel: CustomerAccountProofViewModel, onBack: () -> Unit) {
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
                is CustomerAccountProofEffect.LaunchAuthorization -> {
                    runCatching {
                        launcher.launch(authorizationBrowser.createAuthorizationIntent(effect.plan))
                    }.onFailure {
                        viewModel.authorizationLaunchFailed()
                    }
                }
            }
        }
    }

    CustomerAccountProofScreen(
        state = state,
        onSignIn = viewModel::startAuthorization,
        onRefresh = viewModel::refresh,
        onLogout = viewModel::logout,
        onBack = onBack
    )
}

@Composable
internal fun CustomerAccountProofScreen(
    state: CustomerAccountProofUiState,
    onSignIn: () -> Unit,
    onRefresh: () -> Unit,
    onLogout: () -> Unit,
    onBack: () -> Unit
) {
    val spacing = LocalBrandSpacing.current
    Scaffold(
        modifier = Modifier.testTag(CustomerAccountProofTestTags.ROOT),
        topBar = { TopAppBar(title = { Text(stringResource(R.string.customer_account_proof_title)) }) }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(spacing.sectionDp.dp),
            verticalArrangement = Arrangement.spacedBy(spacing.generousDp.dp)
        ) {
            Text(
                text = stringResource(R.string.integration_customer_account),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.semantics { heading() }
            )
            Text(stringResource(R.string.customer_account_proof_explanation))
            Text(
                text = stringResource(state.statusResourceId()),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.testTag(CustomerAccountProofTestTags.STATUS)
            )
            if (state.canSignIn) {
                Button(
                    onClick = onSignIn,
                    modifier = Modifier.testTag(CustomerAccountProofTestTags.SIGN_IN)
                ) {
                    Text(stringResource(R.string.customer_account_sign_in))
                }
            }
            if (state.canUseSessionActions) {
                Row(horizontalArrangement = Arrangement.spacedBy(spacing.compactDp.dp)) {
                    Button(
                        onClick = onRefresh,
                        modifier = Modifier.testTag(CustomerAccountProofTestTags.REFRESH)
                    ) {
                        Text(stringResource(R.string.customer_account_refresh))
                    }
                    Button(
                        onClick = onLogout,
                        modifier = Modifier.testTag(CustomerAccountProofTestTags.LOGOUT)
                    ) {
                        Text(stringResource(R.string.customer_account_logout))
                    }
                }
            }
            Button(
                onClick = onBack,
                enabled = !state.busy,
                modifier = Modifier.testTag(CustomerAccountProofTestTags.BACK)
            ) {
                Text(stringResource(R.string.back))
            }
        }
    }
}

@Composable
internal fun FoundationScreen(state: FoundationUiState, onIntegrationSelected: (IntegrationStatus) -> Unit) {
    val spacing = LocalBrandSpacing.current
    Scaffold(
        modifier = Modifier.testTag(FoundationTestTags.ROOT),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.foundation_title, state.brandDisplayName),
                        modifier = Modifier.testTag(FoundationTestTags.TITLE)
                    )
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(spacing.sectionDp.dp),
            verticalArrangement = Arrangement.spacedBy(spacing.generousDp.dp)
        ) {
            item {
                Text(
                    text =
                        stringResource(
                            R.string.environment_label,
                            stringResource(state.environment.stringResourceId())
                        ),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.semantics { heading() }.testTag(FoundationTestTags.ENVIRONMENT)
                )
            }
            item {
                Text(
                    text =
                        if (state.configurationIssues.isEmpty()) {
                            stringResource(R.string.configuration_complete)
                        } else {
                            pluralStringResource(
                                R.plurals.configuration_incomplete,
                                state.configurationIssues.size,
                                state.configurationIssues.size
                            )
                        },
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.testTag(FoundationTestTags.CONFIGURATION_STATUS)
                )
            }
            items(
                items = state.integrations,
                key = { it.id.name }
            ) { integration ->
                IntegrationCard(
                    status = integration,
                    onClick = { onIntegrationSelected(integration) }
                )
            }
        }
    }
}

@Composable
private fun IntegrationCard(status: IntegrationStatus, onClick: () -> Unit) {
    val spacing = LocalBrandSpacing.current
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(spacing.sectionDp.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(status.id.titleResourceId()), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(spacing.compactDp.dp))
                Text(
                    text =
                        stringResource(
                            if (status.ready) R.string.integration_configured else R.string.integration_waiting
                        ),
                    modifier = Modifier.testTag(FoundationTestTags.integrationStatus(status.id))
                )
            }
            Button(
                onClick = onClick,
                enabled = status.id != IntegrationId.FIREBASE || status.ready,
                modifier = Modifier.testTag(FoundationTestTags.integrationAction(status.id))
            ) {
                Text(stringResource(R.string.details))
            }
        }
    }
}

@Composable
private fun IntegrationDetailScreen(integration: IntegrationId, onBack: () -> Unit) {
    val spacing = LocalBrandSpacing.current
    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.integration_proof_title)) }) }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(spacing.sectionDp.dp),
            verticalArrangement = Arrangement.spacedBy(spacing.generousDp.dp)
        ) {
            Text(
                text = stringResource(integration.titleResourceId()),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.semantics { heading() }
            )
            Text(
                stringResource(R.string.integration_proof_explanation)
            )
            Button(onClick = onBack) {
                Text(stringResource(R.string.back))
            }
        }
    }
}

private fun EnvironmentId.stringResourceId(): Int = when (this) {
    EnvironmentId.DEVELOPMENT -> R.string.environment_development
    EnvironmentId.STAGING -> R.string.environment_staging
}

private fun IntegrationId.titleResourceId(): Int = when (this) {
    IntegrationId.STOREFRONT -> R.string.integration_storefront
    IntegrationId.CUSTOMER_ACCOUNT -> R.string.integration_customer_account
    IntegrationId.CHECKOUT -> R.string.integration_checkout
    IntegrationId.FIREBASE -> R.string.integration_firebase
}

private val CUSTOMER_ACCOUNT_STATUS_RESOURCES = mapOf(
    CustomerAccountProofPhase.RESTORING to R.string.customer_account_status_restoring,
    CustomerAccountProofPhase.SIGNED_OUT to R.string.customer_account_status_signed_out,
    CustomerAccountProofPhase.PREPARING to R.string.customer_account_status_preparing,
    CustomerAccountProofPhase.AWAITING_BROWSER to R.string.customer_account_status_awaiting_browser,
    CustomerAccountProofPhase.EXCHANGING to R.string.customer_account_status_exchanging,
    CustomerAccountProofPhase.AUTHENTICATED to R.string.customer_account_status_authenticated,
    CustomerAccountProofPhase.REFRESHING to R.string.customer_account_status_refreshing,
    CustomerAccountProofPhase.LOGGING_OUT to R.string.customer_account_status_logging_out,
    CustomerAccountProofPhase.CANCELLED to R.string.customer_account_status_cancelled,
    CustomerAccountProofPhase.DISCOVERY_FAILED to R.string.customer_account_status_discovery_failed,
    CustomerAccountProofPhase.CALLBACK_FAILED to R.string.customer_account_status_callback_failed,
    CustomerAccountProofPhase.TOKEN_FAILED to R.string.customer_account_status_token_failed,
    CustomerAccountProofPhase.IDENTITY_FAILED to R.string.customer_account_status_identity_failed,
    CustomerAccountProofPhase.LAUNCH_FAILED to R.string.customer_account_status_launch_failed,
    CustomerAccountProofPhase.LOGOUT_REMOTE_FAILED to R.string.customer_account_status_logout_remote_failed
)

private fun CustomerAccountProofPhase.statusResourceId(): Int = CUSTOMER_ACCOUNT_STATUS_RESOURCES.getValue(this)

private fun CustomerAccountProofUiState.statusResourceId(): Int = when (failure) {
    CustomerAccountProofFailure.TOKEN_REJECTED -> R.string.customer_account_status_token_rejected
    CustomerAccountProofFailure.TOKEN_TRANSIENT -> R.string.customer_account_status_token_transient
    CustomerAccountProofFailure.TOKEN_INVALID -> R.string.customer_account_status_token_invalid
    CustomerAccountProofFailure.TOKEN_MISSING_FIELDS -> R.string.customer_account_status_token_missing_fields
    CustomerAccountProofFailure.TOKEN_TYPE -> R.string.customer_account_status_token_type
    CustomerAccountProofFailure.TOKEN_SCOPE -> R.string.customer_account_status_token_scope
    CustomerAccountProofFailure.TOKEN_EXPIRED -> R.string.customer_account_status_token_expired
    CustomerAccountProofFailure.TOKEN_IDENTITY -> R.string.customer_account_status_token_identity
    else -> phase.statusResourceId()
}

internal object FoundationTestTags {
    const val ROOT = "foundation-root"
    const val TITLE = "foundation-title"
    const val ENVIRONMENT = "foundation-environment"
    const val CONFIGURATION_STATUS = "foundation-configuration-status"

    fun integrationStatus(id: IntegrationId): String = "integration-status-${id.name.lowercase()}"

    fun integrationAction(id: IntegrationId): String = "integration-action-${id.name.lowercase()}"
}

internal object FirebaseProofUnavailableTestTags {
    const val ROOT = "firebase-proof-unavailable-root"
    const val STATUS = "firebase-proof-unavailable-status"
    const val BACK = "firebase-proof-unavailable-back"
}

internal object CustomerAccountProofTestTags {
    const val ROOT = "customer-account-proof-root"
    const val STATUS = "customer-account-proof-status"
    const val SIGN_IN = "customer-account-proof-sign-in"
    const val REFRESH = "customer-account-proof-refresh"
    const val LOGOUT = "customer-account-proof-logout"
    const val BACK = "customer-account-proof-back"
}

private val android.content.res.Configuration.usesDarkUiMode: Boolean
    get() =
        uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
