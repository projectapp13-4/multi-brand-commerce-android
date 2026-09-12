@file:Suppress("FunctionNaming", "LongMethod")

package com.gurbakir.mobile

import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.gurbakir.foundation.ui.CommerceTheme
import com.gurbakir.mobile.account.AccountActions
import com.gurbakir.mobile.account.AccountPhase
import com.gurbakir.mobile.account.AccountScreen
import com.gurbakir.mobile.account.AccountSummary
import com.gurbakir.mobile.account.AccountUiState
import com.gurbakir.mobile.address.AddressFormActions
import com.gurbakir.mobile.address.AddressFormPhase
import com.gurbakir.mobile.address.AddressFormScreen
import com.gurbakir.mobile.address.AddressFormUiState
import com.gurbakir.mobile.address.AddressInput
import com.gurbakir.mobile.address.PostalCodeInputMode
import com.gurbakir.mobile.brand.GurbakirBrand
import com.gurbakir.mobile.config.BuildConfigurationSource
import com.gurbakir.mobile.localization.withAppLocale
import com.gurbakir.mobile.navigation.AccountRoute
import com.gurbakir.mobile.navigation.AddressFormRoute
import com.gurbakir.mobile.navigation.HomeRoute
import com.gurbakir.mobile.navigation.ProfileRoute
import com.gurbakir.mobile.profile.ProfileActions
import com.gurbakir.mobile.profile.ProfilePhase
import com.gurbakir.mobile.profile.ProfileScreen
import com.gurbakir.mobile.profile.ProfileUiState

/** Debug-only deterministic visual fixture. It contains no credentials and performs no mutations. */
class Stage3EvidenceActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(newBase.withAppLocale(BuildConfigurationSource.current.localization, true))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        val screen = Stage3EvidenceScreen.from(intent.getStringExtra(EXTRA_SCREEN))
        setContent { Stage3EvidenceApp(screen) }
    }

    private companion object {
        const val EXTRA_SCREEN = "screen"
    }
}

@Composable
private fun Stage3EvidenceApp(screen: Stage3EvidenceScreen) {
    val navController = rememberNavController()
    val currentDestination by navController.currentBackStackEntryAsState()
    LaunchedEffect(screen) {
        when (screen) {
            Stage3EvidenceScreen.ACCOUNT ->
                navController.navigate(AccountRoute) {
                    popUpTo<HomeRoute> { inclusive = true }
                    launchSingleTop = true
                }

            Stage3EvidenceScreen.PROFILE ->
                navController.navigate(ProfileRoute) {
                    popUpTo<HomeRoute> { inclusive = true }
                    launchSingleTop = true
                }

            Stage3EvidenceScreen.ADDRESS ->
                navController.navigate(AddressFormRoute()) {
                    popUpTo<HomeRoute> { inclusive = true }
                    launchSingleTop = true
                }
        }
    }
    CommerceTheme(
        designTokens = GurbakirBrand.configuration.designTokens,
        darkTheme = isSystemInDarkTheme()
    ) {
        ProductionAppShell(
            applicationComposition =
                com.gurbakir.mobile.config.BuildConfigurationSource.current.applicationComposition,
            navController = navController,
            currentDestination = currentDestination?.destination
        ) {
            ProductionNavHost(
                customerAccountBindings = com.gurbakir.mobile.navigation.CustomerAccountFeatureBindings(
                    "https://gurbakir.com/apps/mobile/orders",
                    com.gurbakir.mobile.order.GurbakirTrackingUrlPolicy,
                    { _, _ -> com.gurbakir.mobile.accountdeletion.DeletionPageLaunchResult.REJECTED }
                ),
                applicationComposition = BuildConfigurationSource.current.applicationComposition,
                deepLinks = com.gurbakir.mobile.navigation.GurbakirDeepLinkConfiguration,
                navController = navController,
                content =
                    ProductionDestinationContent(
                        home = {},
                        account = {
                            AccountScreen(
                                state =
                                    AccountUiState(
                                        phase = AccountPhase.AUTHENTICATED,
                                        summary = AccountSummary("Test Customer")
                                    ),
                                actions = evidenceAccountActions(navController)
                            )
                        },
                        profile = {
                            ProfileScreen(
                                state = evidenceProfileState(),
                                actions = evidenceProfileActions(navController)
                            )
                        },
                        addressForm = {
                            AddressFormScreen(
                                state = evidenceAddressState(),
                                actions = evidenceAddressActions(navController)
                            )
                        }
                    )
            )
        }
    }
}

private fun evidenceAccountActions(navController: androidx.navigation.NavHostController) = AccountActions(
    onSignIn = {},
    onRetry = {},
    onRefresh = {},
    onLogout = {},
    onProfile = { navController.navigate(ProfileRoute) },
    onAddresses = { navController.navigate(AddressFormRoute()) },
    onOrders = {},
    onAccountDeletion = {},
    onLegalSupport = {},
    onSearchHistory = {},
    onWishlist = {},
    onCart = {}
)

private fun evidenceProfileState() = ProfileUiState(
    phase = ProfilePhase.READY,
    firstName = "Ada",
    lastName = "Lovelace",
    originalFirstName = "Ada",
    originalLastName = "Lovelace",
    loaded = true
)

private fun evidenceProfileActions(navController: androidx.navigation.NavHostController) = ProfileActions(
    onBack = { navController.popBackStack() },
    onFirstNameChanged = {},
    onLastNameChanged = {},
    onSave = {},
    onReload = {},
    onFocusHandled = {}
)

private fun evidenceAddressState() = AddressFormUiState(
    postalCodeInputMode = PostalCodeInputMode.NUMERIC,
    phase = AddressFormPhase.READY,
    input =
        AddressInput(
            firstName = "Ada",
            lastName = "Lovelace",
            company = "",
            address1 = "Test Sokak 1",
            address2 = "",
            city = "İstanbul",
            zip = "34000",
            phoneNumber = ""
        ),
    loaded = true
)

private fun evidenceAddressActions(navController: androidx.navigation.NavHostController) = AddressFormActions(
    onBack = { navController.popBackStack() },
    onFieldChanged = { _, _ -> },
    onMakeDefaultChanged = {},
    onSave = {},
    onReload = {},
    onFocusHandled = {}
)

private enum class Stage3EvidenceScreen {
    ACCOUNT,
    PROFILE,
    ADDRESS;

    companion object {
        fun from(raw: String?): Stage3EvidenceScreen =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: ACCOUNT
    }
}
