@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package com.tumiart.mobile

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.gurbakir.foundation.ui.CommerceTheme
import com.gurbakir.mobile.MobileCoreApp
import com.gurbakir.mobile.navigation.CustomerAccountFeatureBindings
import com.tumiart.mobile.brand.TumiArtBrand
import com.tumiart.mobile.config.TumiArtConfiguration
import com.tumiart.mobile.legal.TumiArtLegalPages
import com.tumiart.mobile.order.TumiArtTrackingUrlPolicy

@Composable
fun TumiArtApp() {
    if (!TumiArtConfiguration.isLaunchReady()) {
        CommerceTheme(designTokens = TumiArtBrand.configuration.designTokens, darkTheme = isSystemInDarkTheme()) {
            Column(Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp)) {
                Text(TumiArtBrand.configuration.displayName, style = MaterialTheme.typography.headlineMedium)
                Text(stringResource(R.string.tumiart_setup_required), style = MaterialTheme.typography.bodyLarge)
            }
        }
        return
    }
    val pages = remember { TumiArtLegalPages() }
    MobileCoreApp(
        brand = TumiArtBrand.configuration,
        deepLinks = TumiArtConfiguration.deepLinks,
        applicationComposition = TumiArtConfiguration.app.applicationComposition,
        customerAccountBindings = CustomerAccountFeatureBindings(
            orderDeepLinkBasePath =
                BuildConfig.ORDER_APP_LINK_ORIGIN + BuildConfig.ORDER_APP_LINK_PATH_PREFIX.dropLast(1),
            trackingUrlPolicy = TumiArtTrackingUrlPolicy,
            openDeletionPage = pages::openDeletionPage
        ),
        legalSupportDestination = { onBack -> TumiArtLegalDestination(onBack, pages) }
    )
}

@Composable
private fun TumiArtLegalDestination(onBack: () -> Unit, pages: TumiArtLegalPages) {
    val context = LocalContext.current
    var feedback by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(stringResource(R.string.tumiart_legal_title), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(R.string.tumiart_legal_provisional))
        pages.entries.forEach { entry ->
            TextButton(onClick = { feedback = !pages.open(context, entry) }, enabled = entry.uri != null) {
                Text(stringResource(entry.titleResource))
            }
        }
        if (feedback) Text(stringResource(R.string.tumiart_legal_no_browser))
        TextButton(onClick = onBack) { Text(stringResource(R.string.tumiart_back)) }
    }
}
