package com.gurbakir.mobile.navigation

import android.content.Context
import com.gurbakir.foundation.config.ApplicationCapability
import com.gurbakir.foundation.config.ApplicationComposition
import com.gurbakir.mobile.accountdeletion.DeletionPageId
import com.gurbakir.mobile.accountdeletion.DeletionPageLaunchResult
import com.gurbakir.mobile.order.TrackingUrlPolicy

data class CustomerAccountFeatureBindings(
    val orderDeepLinkBasePath: String,
    val trackingUrlPolicy: TrackingUrlPolicy,
    val openDeletionPage: (Context, DeletionPageId) -> DeletionPageLaunchResult
) {
    init {
        requireSecureDeepLinkBase(orderDeepLinkBasePath)
    }
}

internal fun validateCustomerAccountBindings(
    composition: ApplicationComposition,
    bindings: CustomerAccountFeatureBindings?
) {
    require(composition.capabilities.isEnabled(ApplicationCapability.CUSTOMER_ACCOUNT) == (bindings != null)) {
        "Customer Account bindings must be supplied exactly when the capability is enabled."
    }
}
