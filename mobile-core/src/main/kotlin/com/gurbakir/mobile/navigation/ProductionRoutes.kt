package com.gurbakir.mobile.navigation

import androidx.annotation.Keep
import kotlinx.serialization.Serializable

@Serializable
data object HomeRoute

@Serializable
data object CategoriesRoute

@Serializable
data object SearchRoute

@Serializable
data object WishlistRoute

@Serializable
data object AccountRoute

@Serializable
data object AccountDeletionRoute

@Serializable
data object ProfileRoute

@Serializable
data object AddressListRoute

@Serializable
data class AddressFormRoute(val addressId: String? = null)

@Serializable
data object OrderListRoute

@Serializable
data class OrderDetailRoute(val orderId: String) {
    override fun toString(): String = "OrderDetailRoute(orderId=<redacted>)"
}

@Serializable
data object CartRoute

@Serializable
data object LegalSupportRoute

@Serializable
data class CollectionRoute(val handle: String)

@Serializable
data class ProductRoute(val productId: String, val variantId: String? = null)

@Serializable
data class RouteRecoveryRoute(val reason: RouteRecoveryReason)

@Serializable
@Keep
enum class RouteRecoveryReason {
    UNAVAILABLE_DESTINATION
}
