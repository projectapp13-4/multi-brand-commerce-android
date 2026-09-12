@file:JvmName("GurbakirProductionApp")
@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package com.gurbakir.mobile

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.gurbakir.mobile.accountdeletion.GurbakirDeletionPages
import com.gurbakir.mobile.brand.GurbakirBrand
import com.gurbakir.mobile.config.BuildConfigurationSource
import com.gurbakir.mobile.legal.PackagedLegalSupportRepository
import com.gurbakir.mobile.navigation.CustomerAccountFeatureBindings
import com.gurbakir.mobile.navigation.GurbakirDeepLinkConfiguration
import com.gurbakir.mobile.order.GurbakirTrackingUrlPolicy

@Composable
fun GurbakirApp(navController: NavHostController = rememberNavController()) {
    val deletionPages = remember { GurbakirDeletionPages(PackagedLegalSupportRepository()) }
    MobileCoreApp(
        brand = GurbakirBrand.configuration,
        deepLinks = GurbakirDeepLinkConfiguration,
        applicationComposition = BuildConfigurationSource.current.applicationComposition,
        customerAccountBindings = CustomerAccountFeatureBindings(
            orderDeepLinkBasePath = "https://gurbakir.com/apps/mobile/orders",
            trackingUrlPolicy = GurbakirTrackingUrlPolicy,
            openDeletionPage = deletionPages::open
        ),
        legalSupportDestination = { onBack -> LegalSupportDestination(onBack) },
        navController = navController
    )
}
