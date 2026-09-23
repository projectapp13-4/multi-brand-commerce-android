@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package com.projectapp134.multibrandtrial

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.gurbakir.mobile.MobileCoreApp
import com.gurbakir.mobile.navigation.CustomerAccountFeatureBindings
import com.projectapp134.multibrandtrial.accountdeletion.TrialDeletionPages
import com.projectapp134.multibrandtrial.brand.TrialBrand
import com.projectapp134.multibrandtrial.config.TrialConfiguration
import com.projectapp134.multibrandtrial.legal.TrialLegalLaunchResult
import com.projectapp134.multibrandtrial.legal.TrialLegalPageLauncher
import com.projectapp134.multibrandtrial.legal.TrialLegalPagePolicy
import com.projectapp134.multibrandtrial.legal.TrialLegalRole
import com.projectapp134.multibrandtrial.order.TrialTrackingUrlPolicy

@Composable
fun TrialApp(navController: NavHostController = rememberNavController()) {
    val legalLauncher = remember { TrialLegalPageLauncher(TrialLegalPagePolicy(TrialConfiguration.legal)) }
    val deletionPages = remember { TrialDeletionPages(TrialConfiguration.legal) }
    MobileCoreApp(
        brand = TrialBrand.configuration,
        deepLinks = TrialConfiguration.deepLinks,
        applicationComposition = TrialConfiguration.app.applicationComposition,
        customerAccountBindings =
            CustomerAccountFeatureBindings(
                orderDeepLinkBasePath =
                    BuildConfig.ORDER_APP_LINK_ORIGIN + BuildConfig.ORDER_APP_LINK_PATH_PREFIX.dropLast(1),
                trackingUrlPolicy = TrialTrackingUrlPolicy,
                openDeletionPage = deletionPages::open
            ),
        legalSupportDestination = { onBack -> TrialLegalSupportDestination(onBack, legalLauncher) },
        navController = navController
    )
}

@Composable
internal fun TrialLegalSupportDestination(onBack: () -> Unit, launcher: TrialLegalPageLauncher) {
    val context = LocalContext.current
    var feedbackResource by remember { mutableStateOf<Int?>(null) }
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(stringResource(R.string.trial_legal_title), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(R.string.trial_legal_provisional), style = MaterialTheme.typography.bodyLarge)
        TrialConfiguration.legal.entries.forEach { entry ->
            TextButton(
                onClick = {
                    feedbackResource =
                        when (launcher.open(context, entry)) {
                            TrialLegalLaunchResult.OPENED -> R.string.trial_legal_opened
                            TrialLegalLaunchResult.NO_BROWSER -> R.string.trial_legal_no_browser
                            TrialLegalLaunchResult.REJECTED -> R.string.trial_legal_rejected
                        }
                }
            ) {
                Text(stringResource(entry.role.titleResource()), style = MaterialTheme.typography.titleMedium)
            }
        }
        feedbackResource?.let { Text(stringResource(it), style = MaterialTheme.typography.bodyMedium) }
        TextButton(onClick = onBack) { Text(stringResource(R.string.trial_back)) }
    }
}

private fun TrialLegalRole.titleResource(): Int = when (this) {
    TrialLegalRole.SUPPORT -> R.string.trial_legal_support
    TrialLegalRole.PRIVACY -> R.string.trial_legal_privacy
    TrialLegalRole.TERMS -> R.string.trial_legal_terms
    TrialLegalRole.SHIPPING -> R.string.trial_legal_shipping
    TrialLegalRole.RETURNS -> R.string.trial_legal_returns
    TrialLegalRole.LEGAL_NOTICE -> R.string.trial_legal_notice
}
