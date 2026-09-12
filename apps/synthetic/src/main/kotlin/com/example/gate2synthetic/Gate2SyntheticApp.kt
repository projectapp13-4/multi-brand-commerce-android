@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package com.example.gate2synthetic

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
import com.example.gate2synthetic.brand.Gate2SyntheticBrand
import com.example.gate2synthetic.config.Gate2SyntheticConfiguration
import com.gurbakir.mobile.MobileCoreApp

@Composable
fun Gate2SyntheticApp(navController: NavHostController = rememberNavController()) {
    MobileCoreApp(
        brand = Gate2SyntheticBrand.configuration,
        deepLinks = Gate2SyntheticConfiguration.deepLinks,
        applicationComposition = Gate2SyntheticConfiguration.app.applicationComposition,
        customerAccountBindings = null,
        legalSupportDestination = { onBack -> Gate2SyntheticLegalSupportDestination(onBack) },
        navController = navController
    )
}

@Composable
internal fun Gate2SyntheticLegalSupportDestination(onBack: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(stringResource(R.string.synthetic_legal_title), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(R.string.synthetic_legal_message), style = MaterialTheme.typography.bodyLarge)
        Text(stringResource(R.string.synthetic_privacy), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.synthetic_support), style = MaterialTheme.typography.titleMedium)
        TextButton(onClick = onBack) { Text(stringResource(R.string.synthetic_back)) }
    }
}
