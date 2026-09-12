package com.gurbakir.mobile.navigation

import androidx.annotation.Keep
import kotlinx.serialization.Serializable

@Serializable
data object FoundationHome

@Serializable
data class IntegrationDetail(val integration: IntegrationId, val openedFromNotification: Boolean = false)

@Serializable
@Keep
enum class IntegrationId {
    STOREFRONT,
    CUSTOMER_ACCOUNT,
    CHECKOUT,
    FIREBASE
}
