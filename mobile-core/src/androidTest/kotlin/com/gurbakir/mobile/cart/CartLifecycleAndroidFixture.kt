package com.gurbakir.mobile.cart

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import com.gurbakir.mobile.product.ProductDetailActions
import com.gurbakir.mobile.product.ProductDetailLoad
import com.gurbakir.mobile.product.ProductDetailRepository
import com.gurbakir.mobile.product.ProductDetailViewModel
import com.gurbakir.storefront.CartLineSummary
import com.gurbakir.storefront.CartOwnership
import com.gurbakir.storefront.CartReference
import com.gurbakir.storefront.CartSessionResolution
import com.gurbakir.storefront.SensitiveCartId
import com.gurbakir.storefront.SensitiveCartLineId
import com.gurbakir.storefront.SensitiveCheckoutUrl
import com.gurbakir.storefront.SensitiveCustomerId
import com.gurbakir.storefront.StorefrontMoney
import com.gurbakir.storefront.StorefrontProductDetail
import com.gurbakir.storefront.StorefrontProductOption
import com.gurbakir.storefront.StorefrontProductOptionValue
import com.gurbakir.storefront.StorefrontProductVariant
import com.gurbakir.storefront.StorefrontSelectedOption
import java.math.BigDecimal
import java.net.URI
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred

internal const val LIFECYCLE_PRODUCT_ID = "gid://shopify/Product/901"
internal const val LIFECYCLE_VARIANT_A = "gid://shopify/ProductVariant/9011"
internal const val LIFECYCLE_VARIANT_B = "gid://shopify/ProductVariant/9012"

/** Created and disposed on main; only the Product owner is cleared during the regression. */
internal class CartLifecycleAndroidFixture(ownership: CartOwnership = CartOwnership.ANONYMOUS) {
    val operations = ControlledLifecycleCartOperations(ownership)
    val repository = DefaultCartRepository(operations)
    val cartStore = ViewModelStore()
    val productStore = ViewModelStore()
    val cart = ViewModelProvider(
        cartStore,
        LifecycleViewModelFactory { CartViewModel(repository) }
    )[CartViewModel::class.java]
    val product = ViewModelProvider(
        productStore,
        LifecycleViewModelFactory {
            ProductDetailViewModel(
                object : ProductDetailRepository {
                    override suspend fun load(productId: String): ProductDetailLoad =
                        ProductDetailLoad.Content(lifecycleProduct())
                },
                repository,
                SavedStateHandle()
            )
        }
    )[ProductDetailViewModel::class.java]

    init {
        product.start(LIFECYCLE_PRODUCT_ID, LIFECYCLE_VARIANT_A)
    }

    fun cartActions() = CartActions(
        onBack = {},
        onBrowse = {},
        onRetry = cart::refresh,
        onOpenProduct = {},
        onIncrease = cart::increase,
        onDecrease = cart::decrease,
        onRemove = cart::remove,
        onDiscard = cart::discard,
        onCheckout = {}
    )

    fun productActions() = ProductDetailActions(
        onBack = {},
        onBrowse = {},
        onRetry = product::retry,
        onSelectOption = product::selectOption,
        onSelectMedia = product::selectMedia,
        onOpenMediaViewer = { product.setMediaViewer(true) },
        onCloseMediaViewer = { product.setMediaViewer(false) },
        onClearSelection = product::clearSelection,
        onAddToCart = product::addToCart
    )

    fun close() {
        productStore.clear()
        cartStore.clear()
    }
}

/** Controls the Android presentation boundary; the joined JVM fixture proves HTTP/lease/storage. */
internal class ControlledLifecycleCartOperations(private val ownership: CartOwnership) : CartOperations {
    private val result = CompletableDeferred<CartSessionResolution>()
    private var current: CartSessionResolution = active(2)
    val started = CompletableDeferred<Unit>()
    val submitted = mutableListOf<CartMutationPlan>()
    var restoreCount = 0
        private set
    var clearCount = 0
        private set

    @Volatile
    var cancellationObserved = false
        private set

    override suspend fun restore(): CartSessionResolution {
        restoreCount++
        return current
    }

    override suspend fun mutate(plan: (CartSessionResolution) -> CartMutationPlan): CartMutationAttempt {
        val before = current
        val selectedPlan = plan(before)
        submitted += selectedPlan
        started.complete(Unit)
        val settled = try {
            result.await()
        } catch (cancelled: CancellationException) {
            cancellationObserved = true
            throw cancelled
        }
        return CartMutationAttempt(before, selectedPlan, settled)
    }

    override suspend fun clear(): Boolean {
        clearCount++
        current = CartSessionResolution.Empty
        return true
    }

    fun completeMutation(quantity: Int) {
        current = active(quantity)
        result.complete(current)
    }

    fun setRestoredQuantity(quantity: Int) {
        current = active(quantity)
    }

    fun requireVerification() {
        current = CartSessionResolution.Restricted(CartOwnership.VERIFY_PENDING)
    }

    private fun active(quantity: Int) = CartSessionResolution.Active(
        lifecycleCart(quantity, ownership),
        ownership
    )
}

private class LifecycleViewModelFactory(private val create: () -> ViewModel) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T = requireNotNull(modelClass.cast(create()))
}

private fun lifecycleCart(quantity: Int, ownership: CartOwnership): CartReference {
    val price = StorefrontMoney(BigDecimal("100.00"), "TRY")
    val total = StorefrontMoney(price.amount.multiply(quantity.toBigDecimal()), "TRY")
    return CartReference(
        id = syntheticOpaque(SensitiveCartId::class.java, "gid://shopify/Cart/lifecycle?key=synthetic"),
        checkoutUrl = SensitiveCheckoutUrl::class.java.getDeclaredConstructor(URI::class.java).run {
            isAccessible = true
            newInstance(URI("https://shop.example/cart/c/lifecycle-synthetic"))
        },
        totalQuantity = quantity,
        lines = listOf(
            CartLineSummary(
                id = syntheticOpaque(SensitiveCartLineId::class.java, "gid://shopify/CartLine/lifecycle"),
                merchandiseId = LIFECYCLE_VARIANT_A,
                quantity = quantity,
                productId = LIFECYCLE_PRODUCT_ID,
                productTitle = "Synthetic copper pan",
                variantTitle = "Small",
                unitPrice = price,
                totalPrice = total
            )
        ),
        hasMoreLines = false,
        warningCodes = emptySet(),
        subtotal = total,
        total = total,
        customerId = if (ownership == CartOwnership.CUSTOMER_ASSOCIATED) {
            SensitiveCustomerId.from("gid://shopify/Customer/lifecycle-synthetic")
        } else {
            null
        }
    )
}

private fun <T> syntheticOpaque(type: Class<T>, value: String): T =
    type.getDeclaredConstructor(String::class.java).run {
        isAccessible = true
        newInstance(value)
    }

private fun lifecycleProduct() = StorefrontProductDetail(
    id = LIFECYCLE_PRODUCT_ID,
    handle = "lifecycle-synthetic-pan",
    title = "Synthetic copper pan",
    description = "Offline lifecycle regression fixture.",
    availableForSale = true,
    options = listOf(
        StorefrontProductOption(
            id = "gid://shopify/ProductOption/901",
            name = "Size",
            values = listOf(
                StorefrontProductOptionValue("gid://shopify/ProductOptionValue/9011", "Small"),
                StorefrontProductOptionValue("gid://shopify/ProductOptionValue/9012", "Large")
            )
        )
    ),
    variants = listOf(
        lifecycleVariant(LIFECYCLE_VARIANT_A, "Small", "100.00"),
        lifecycleVariant(LIFECYCLE_VARIANT_B, "Large", "200.00")
    ),
    media = emptyList()
)

private fun lifecycleVariant(id: String, size: String, price: String) = StorefrontProductVariant(
    id = id,
    title = size,
    availableForSale = true,
    currentlyNotInStock = false,
    price = StorefrontMoney(BigDecimal(price), "TRY"),
    compareAtPrice = null,
    image = null,
    selectedOptions = listOf(StorefrontSelectedOption("Size", size))
)
