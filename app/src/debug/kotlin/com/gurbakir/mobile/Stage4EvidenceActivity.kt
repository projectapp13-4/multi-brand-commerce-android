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
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.gurbakir.foundation.ui.CommerceTheme
import com.gurbakir.mobile.brand.GurbakirBrand
import com.gurbakir.mobile.cart.CartActions
import com.gurbakir.mobile.cart.CartFailure
import com.gurbakir.mobile.cart.CartFailureCategory
import com.gurbakir.mobile.cart.CartLine
import com.gurbakir.mobile.cart.CartScreen
import com.gurbakir.mobile.cart.CartState
import com.gurbakir.mobile.cart.CartStatus
import com.gurbakir.mobile.cart.CartSummary
import com.gurbakir.mobile.config.BuildConfigurationSource
import com.gurbakir.mobile.localization.withAppLocale
import com.gurbakir.mobile.navigation.CartRoute
import com.gurbakir.mobile.navigation.HomeRoute
import com.gurbakir.mobile.navigation.WishlistRoute
import com.gurbakir.mobile.wishlist.WishlistActions
import com.gurbakir.mobile.wishlist.WishlistResolvedEntry
import com.gurbakir.mobile.wishlist.WishlistScreen
import com.gurbakir.mobile.wishlist.WishlistUiState
import com.gurbakir.storefront.CartOwnership
import com.gurbakir.storefront.CartQuantityRule
import com.gurbakir.storefront.SensitiveCartLineId
import com.gurbakir.storefront.StorefrontMoney
import com.gurbakir.storefront.StorefrontProductDetail
import com.gurbakir.storefront.StorefrontProductOption
import com.gurbakir.storefront.StorefrontProductOptionValue
import com.gurbakir.storefront.StorefrontProductVariant
import com.gurbakir.storefront.StorefrontSelectedOption
import java.math.BigDecimal

/** Debug-only Stage 4 visual fixture. It contains no credentials and performs no mutations. */
class Stage4EvidenceActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(newBase.withAppLocale(BuildConfigurationSource.current.localization, true))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        val screen = Stage4EvidenceScreen.from(intent.getStringExtra(EXTRA_SCREEN))
        setContent { Stage4EvidenceApp(screen) }
    }

    private companion object {
        const val EXTRA_SCREEN = "screen"
    }
}

@Composable
private fun Stage4EvidenceApp(screen: Stage4EvidenceScreen) {
    val navController = rememberNavController()
    val currentDestination by navController.currentBackStackEntryAsState()
    LaunchedEffect(screen) {
        navController.navigate(screen.route) {
            popUpTo<HomeRoute> { inclusive = true }
            launchSingleTop = true
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
                        wishlist = {
                            WishlistScreen(
                                state = screen.wishlistState(),
                                actions = evidenceWishlistActions(navController)
                            )
                        },
                        cart = {
                            CartScreen(
                                state = screen.cartState(),
                                actions = evidenceCartActions(navController)
                            )
                        }
                    )
            )
        }
    }
}

private fun Stage4EvidenceScreen.wishlistState(): WishlistUiState = when (this) {
    Stage4EvidenceScreen.WISHLIST_STORAGE_ERROR -> WishlistUiState(storageAvailable = false)

    else -> {
        val product = evidenceProduct()
        WishlistUiState(
            entries = listOf(WishlistResolvedEntry(product.id, 1L, product = product))
        )
    }
}

private fun evidenceWishlistActions(navController: NavHostController) = WishlistActions(
    onBrowse = { navController.navigate(HomeRoute) },
    onRetry = {},
    onOpenProduct = {},
    onRemove = {},
    onClear = {}
)

private fun Stage4EvidenceScreen.cartState(): CartState = when (this) {
    Stage4EvidenceScreen.CART_CUSTOMER -> evidenceCustomerCartState()

    Stage4EvidenceScreen.CART_RESTRICTED ->
        CartState(status = CartStatus.RESTRICTED, ownership = CartOwnership.QUARANTINED)

    Stage4EvidenceScreen.CART_ERROR ->
        CartState(
            status = CartStatus.ERROR,
            failure =
                CartFailure(
                    category = CartFailureCategory.CONNECTION,
                    retryable = true,
                    cartRetained = false
                )
        )

    Stage4EvidenceScreen.WISHLIST_SAVED,
    Stage4EvidenceScreen.WISHLIST_STORAGE_ERROR -> CartState()
}

private fun evidenceCustomerCartState(): CartState {
    val unitPrice = StorefrontMoney(BigDecimal("649.50"), "TRY")
    val line =
        CartLine(
            id = evidenceCartLineId(),
            merchandiseId = "gid://gurbakir/ProductVariant/evidence",
            productId = "gid://gurbakir/Product/evidence",
            productTitle = "Handcrafted copper pan",
            variantTitle = "Medium",
            quantity = 2,
            quantityRule = CartQuantityRule(minimum = 1, maximum = 5, increment = 1),
            canRemove = true,
            canUpdateQuantity = true,
            availableForSale = true,
            currentlyNotInStock = false,
            image = null,
            unitPrice = unitPrice,
            totalPrice = StorefrontMoney(BigDecimal("1299.00"), "TRY")
        )
    val total = StorefrontMoney(BigDecimal("1299.00"), "TRY")
    return CartState(
        status = CartStatus.ACTIVE,
        cart = CartSummary(2, listOf(line), total, total, hasWarnings = false),
        ownership = CartOwnership.CUSTOMER_ASSOCIATED
    )
}

private fun evidenceCartActions(navController: NavHostController) = CartActions(
    onBack = { navController.popBackStackOrHome() },
    onBrowse = { navController.navigate(HomeRoute) },
    onRetry = {},
    onOpenProduct = {},
    onIncrease = {},
    onDecrease = {},
    onRemove = {},
    onDiscard = {},
    onCheckout = {}
)

private fun evidenceProduct(): StorefrontProductDetail = StorefrontProductDetail(
    id = "gid://gurbakir/Product/evidence",
    handle = "handcrafted-copper-pan",
    title = "Handcrafted copper pan",
    description = "Synthetic local visual-evidence product.",
    availableForSale = true,
    options =
        listOf(
            StorefrontProductOption(
                "gid://gurbakir/ProductOption/evidence",
                "Size",
                listOf(
                    StorefrontProductOptionValue(
                        "gid://gurbakir/ProductOptionValue/evidence",
                        "Medium"
                    )
                )
            )
        ),
    variants =
        listOf(
            StorefrontProductVariant(
                id = "gid://gurbakir/ProductVariant/evidence",
                title = "Medium",
                availableForSale = true,
                currentlyNotInStock = false,
                price = StorefrontMoney(BigDecimal("649.50"), "TRY"),
                compareAtPrice = null,
                image = null,
                selectedOptions = listOf(StorefrontSelectedOption("Size", "Medium"))
            )
        ),
    media = emptyList()
)

private fun evidenceCartLineId(): SensitiveCartLineId =
    SensitiveCartLineId::class.java.getDeclaredConstructor(String::class.java).run {
        isAccessible = true
        newInstance("gid://gurbakir/CartLine/evidence")
    }

private enum class Stage4EvidenceScreen(val route: Any) {
    WISHLIST_SAVED(WishlistRoute),
    WISHLIST_STORAGE_ERROR(WishlistRoute),
    CART_CUSTOMER(CartRoute),
    CART_RESTRICTED(CartRoute),
    CART_ERROR(CartRoute);

    companion object {
        fun from(raw: String?): Stage4EvidenceScreen =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: WISHLIST_SAVED
    }
}
