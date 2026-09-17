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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.gurbakir.mobile.MobileCoreApp
import com.gurbakir.mobile.accountdeletion.DeletionPageLaunchResult
import com.gurbakir.mobile.navigation.CustomerAccountFeatureBindings
import com.projectapp134.multibrandtrial.brand.TrialBrand
import com.projectapp134.multibrandtrial.config.TrialConfiguration
import com.projectapp134.multibrandtrial.legal.TrialLegalRole
import com.projectapp134.multibrandtrial.order.TrialTrackingUrlPolicy

@Composable
fun TrialApp(navController: NavHostController = rememberNavController()) {
    MobileCoreApp(
        brand = TrialBrand.configuration,
        deepLinks = TrialConfiguration.deepLinks,
        applicationComposition = TrialConfiguration.app.applicationComposition,
        customerAccountBindings =
            CustomerAccountFeatureBindings(
                orderDeepLinkBasePath =
                    BuildConfig.ORDER_APP_LINK_ORIGIN + BuildConfig.ORDER_APP_LINK_PATH_PREFIX.dropLast(1),
                trackingUrlPolicy = TrialTrackingUrlPolicy,
                openDeletionPage = { _, _ -> DeletionPageLaunchResult.REJECTED }
            ),
        legalSupportDestination = { onBack -> TrialLegalSupportDestination(onBack) },
        navController = navController
    )
}

@Composable
internal fun TrialLegalSupportDestination(onBack: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(stringResource(R.string.trial_legal_title), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(R.string.trial_legal_setup_required), style = MaterialTheme.typography.bodyLarge)
        TrialConfiguration.legal.entries.forEach { entry ->
            Text(stringResource(entry.role.titleResource()), style = MaterialTheme.typography.titleMedium)
        }
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
