package com.gurbakir.mobile

import android.app.Activity
import com.gurbakir.account.session.CustomerAccountSessionCoordinator
import com.gurbakir.account.session.CustomerSessionResolution
import com.gurbakir.checkout.CheckoutAdapter
import com.gurbakir.checkout.CheckoutEvent
import com.gurbakir.checkout.CheckoutFailure
import com.gurbakir.checkout.CheckoutResult
import com.gurbakir.storefront.CartCoordinator
import com.gurbakir.storefront.CartLineInput
import com.gurbakir.storefront.CartLineSummary
import com.gurbakir.storefront.CartLineUpdate
import com.gurbakir.storefront.CartReference
import com.gurbakir.storefront.CartSessionResolution
import com.gurbakir.storefront.Cursor
import com.gurbakir.storefront.ProductVariantSummary
import com.gurbakir.storefront.SensitiveBuyerAccessToken
import com.gurbakir.storefront.StorefrontFailure
import com.gurbakir.storefront.StorefrontGateway
import com.gurbakir.storefront.StorefrontResult
import java.util.concurrent.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.transform

private const val MAXIMUM_VARIANT_DISCOVERY_PAGES = 10

interface CommerceProofController {
    suspend fun restore(): CommerceProofResult

    suspend fun createCart(): CommerceProofResult

    suspend fun addLine(): CommerceProofResult

    suspend fun incrementFirstLine(): CommerceProofResult

    suspend fun removeAllLines(): CommerceProofResult

    suspend fun preloadCheckout(activity: Activity): CommerceProofResult

    suspend fun presentCheckout(activity: Activity): CommerceProofResult
}

data class CommerceProofSnapshot(
    val totalQuantity: Int,
    val lineCount: Int,
    val hasMoreLines: Boolean,
    val warningCount: Int
)

sealed interface CommerceProofResult {
    data class Active(val snapshot: CommerceProofSnapshot) : CommerceProofResult

    data class Empty(val variantAvailable: Boolean) : CommerceProofResult

    data class CheckoutStarted(val events: Flow<CommerceProofEvent>) : CommerceProofResult

    data class Failed(val failure: CommerceProofFailure, val cartRetained: Boolean) : CommerceProofResult
}

enum class CommerceProofEvent {
    CHECKOUT_COMPLETED,
    CHECKOUT_CANCELLED,
    CHECKOUT_FAILED,
    EXTERNAL_LINK_REQUIRES_POLICY
}

enum class CommerceProofFailure {
    CONFIGURATION,
    TRANSPORT,
    GRAPHQL,
    USER_INPUT,
    INVALID_CART,
    SECURE_PERSISTENCE,
    NO_AVAILABLE_VARIANT,
    ACCOUNT_SESSION,
    CHECKOUT_INVALID_URL,
    CHECKOUT_UNAVAILABLE,
    CHECKOUT_NETWORK,
    CHECKOUT_EXPIRED,
    CHECKOUT_CONFIGURATION,
    CHECKOUT_RECOVERABLE,
    CHECKOUT_FATAL
}

class DefaultCommerceProofController(
    private val storefrontGateway: StorefrontGateway,
    private val cartCoordinator: CartCoordinator,
    private val customerSessionCoordinator: CustomerAccountSessionCoordinator,
    private val checkoutAdapter: CheckoutAdapter
) : CommerceProofController {
    private val variantResolver = AvailableVariantResolver(storefrontGateway)
    private val cartCreator = CommerceCartCreator(customerSessionCoordinator, cartCoordinator)
    private var activeCart: CartReference? = null

    private fun checkoutEvents(events: Flow<CheckoutEvent>): Flow<CommerceProofEvent> = events.transform { event ->
        when (event) {
            CheckoutEvent.Completed -> {
                activeCart?.let { cartCoordinator.completion.complete(it.id) }
                activeCart = null
                checkoutAdapter.invalidate()
                emit(CommerceProofEvent.CHECKOUT_COMPLETED)
            }

            CheckoutEvent.Cancelled -> emit(CommerceProofEvent.CHECKOUT_CANCELLED)

            is CheckoutEvent.Failed -> emit(CommerceProofEvent.CHECKOUT_FAILED)

            is CheckoutEvent.ExternalLinkRequested -> emit(CommerceProofEvent.EXTERNAL_LINK_REQUIRES_POLICY)
        }
    }

    override suspend fun restore(): CommerceProofResult = when (val variant = variantResolver.resolve()) {
        is VariantResolution.Available -> cartCoordinator.restore().toProofResult()
        is VariantResolution.Failed -> variant.failure.toCommerceFailure(cartRetained = false)
        VariantResolution.None -> noVariantFailure(activeCart != null)
    }

    override suspend fun createCart(): CommerceProofResult = when (val variant = variantResolver.resolve()) {
        is VariantResolution.Available -> {
            val cartResult = cartCreator.create(variant.variant)
            if (cartResult == null) {
                CommerceProofResult.Failed(CommerceProofFailure.ACCOUNT_SESSION, cartRetained = false)
            } else {
                checkoutAdapter.invalidate()
                cartResult.toProofResult()
            }
        }

        is VariantResolution.Failed -> variant.failure.toCommerceFailure(cartRetained = false)

        VariantResolution.None -> noVariantFailure(activeCart != null)
    }

    override suspend fun addLine(): CommerceProofResult {
        val variant = variantResolver.availableVariant ?: return noVariantFailure(activeCart != null)
        return mutateCart { cartCoordinator.add(listOf(CartLineInput(variant.id, quantity = 1))) }
    }

    override suspend fun incrementFirstLine(): CommerceProofResult {
        val line = activeCart?.lines?.firstOrNull()
        return if (line == null) {
            CommerceProofResult.Empty(variantResolver.availableVariant != null)
        } else {
            mutateCart {
                cartCoordinator.update(listOf(CartLineUpdate(line.id, line.quantity + 1)))
            }
        }
    }

    override suspend fun removeAllLines(): CommerceProofResult {
        val cart = activeCart
        return when {
            cart == null -> CommerceProofResult.Empty(variantResolver.availableVariant != null)
            cart.hasMoreLines -> CommerceProofResult.Failed(CommerceProofFailure.USER_INPUT, cartRetained = true)
            else -> mutateCart { cartCoordinator.remove(cart.lines.map(CartLineSummary::id)) }
        }
    }

    override suspend fun preloadCheckout(activity: Activity): CommerceProofResult =
        launchCheckout(activity) { targetActivity, url ->
            checkoutAdapter.preload(targetActivity, url)
        }

    override suspend fun presentCheckout(activity: Activity): CommerceProofResult =
        launchCheckout(activity) { targetActivity, url ->
            checkoutAdapter.present(targetActivity, url)
        }

    private suspend fun mutateCart(operation: suspend () -> CartSessionResolution): CommerceProofResult {
        val result = operation()
        if (result is CartSessionResolution.Active) checkoutAdapter.invalidate()
        return result.toProofResult()
    }

    private suspend fun launchCheckout(
        activity: Activity,
        operation: suspend (Activity, java.net.URI) -> CheckoutResult
    ): CommerceProofResult {
        val refreshed = cartCoordinator.restore()
        val cart = (refreshed as? CartSessionResolution.Active)?.cart
            ?: return refreshed.toProofResult()
        activeCart = cart
        return cart.checkoutUrl.useSuspending { url ->
            when (val result = operation(activity, url)) {
                CheckoutResult.Preloaded -> CommerceProofResult.CheckoutStarted(kotlinx.coroutines.flow.emptyFlow())

                is CheckoutResult.Presented -> CommerceProofResult.CheckoutStarted(checkoutEvents(result.events))

                is CheckoutResult.Rejected ->
                    CommerceProofResult.Failed(result.reason.toCommerceFailure(), cartRetained = true)
            }
        }
    }

    private fun CartSessionResolution.toProofResult(): CommerceProofResult = when (this) {
        is CartSessionResolution.Active -> {
            activeCart = cart
            CommerceProofResult.Active(cart.toSnapshot())
        }

        CartSessionResolution.Empty,
        CartSessionResolution.Expired -> {
            activeCart = null
            CommerceProofResult.Empty(variantResolver.availableVariant != null)
        }

        is CartSessionResolution.Restricted -> {
            activeCart = null
            CommerceProofResult.Failed(CommerceProofFailure.ACCOUNT_SESSION, cartRetained = true)
        }

        is CartSessionResolution.Failed -> error.toCommerceFailure(persistedCartRetained)
    }
}

private sealed interface VariantResolution {
    data class Available(val variant: ProductVariantSummary) : VariantResolution

    data class Failed(val failure: StorefrontFailure) : VariantResolution

    data object None : VariantResolution
}

private class AvailableVariantResolver(private val storefrontGateway: StorefrontGateway) {
    var availableVariant: ProductVariantSummary? = null
        private set

    suspend fun resolve(): VariantResolution {
        var resolution: VariantResolution? = availableVariant?.let(VariantResolution::Available)
        var cursor: Cursor? = null
        var pageCount = 0
        while (resolution == null && pageCount < MAXIMUM_VARIANT_DISCOVERY_PAGES) {
            when (val result = storefrontGateway.loadCatalogPage(cursor)) {
                is StorefrontResult.Failure -> resolution = VariantResolution.Failed(result.error)

                is StorefrontResult.Success -> {
                    val variant = result.value.products.asSequence()
                        .flatMap { product -> product.variants.asSequence() }
                        .firstOrNull(ProductVariantSummary::availableForSale)
                    when {
                        variant != null -> {
                            availableVariant = variant
                            resolution = VariantResolution.Available(variant)
                        }

                        !result.value.hasNextPage || result.value.endCursor == null ->
                            resolution = VariantResolution.None

                        else -> cursor = result.value.endCursor
                    }
                }
            }
            pageCount += 1
        }
        return resolution ?: VariantResolution.None
    }
}

private class CommerceCartCreator(
    private val customerSessionCoordinator: CustomerAccountSessionCoordinator,
    private val cartCoordinator: CartCoordinator
) {
    suspend fun create(variant: ProductVariantSummary): CartSessionResolution? =
        when (val customerSession = customerSessionCoordinator.restore()) {
            is CustomerSessionResolution.Authenticated ->
                customerSession.session.accessToken.useSuspending { rawToken ->
                    cartCoordinator.create(
                        listOf(CartLineInput(variant.id, quantity = 1)),
                        SensitiveBuyerAccessToken.from(rawToken)
                    )
                }

            CustomerSessionResolution.SignedOut ->
                cartCoordinator.create(listOf(CartLineInput(variant.id, quantity = 1)))

            is CustomerSessionResolution.Failed -> null
        }
}

private fun noVariantFailure(cartRetained: Boolean): CommerceProofResult.Failed =
    CommerceProofResult.Failed(CommerceProofFailure.NO_AVAILABLE_VARIANT, cartRetained)

private fun CartReference.toSnapshot(): CommerceProofSnapshot = CommerceProofSnapshot(
    totalQuantity = totalQuantity,
    lineCount = lines.size,
    hasMoreLines = hasMoreLines,
    warningCount = warningCodes.size
)

private fun StorefrontFailure.toCommerceFailure(cartRetained: Boolean): CommerceProofResult.Failed =
    CommerceProofResult.Failed(
        failure =
            when (this) {
                is StorefrontFailure.Configuration -> CommerceProofFailure.CONFIGURATION
                is StorefrontFailure.Transport -> CommerceProofFailure.TRANSPORT
                is StorefrontFailure.GraphQl -> CommerceProofFailure.GRAPHQL
                is StorefrontFailure.UserErrors -> CommerceProofFailure.USER_INPUT
                is StorefrontFailure.InvalidCart -> CommerceProofFailure.INVALID_CART
                StorefrontFailure.SecurePersistence -> CommerceProofFailure.SECURE_PERSISTENCE
            },
        cartRetained = cartRetained
    )

private fun CheckoutFailure.toCommerceFailure(): CommerceProofFailure = when (this) {
    CheckoutFailure.INVALID_CHECKOUT_URL -> CommerceProofFailure.CHECKOUT_INVALID_URL
    CheckoutFailure.SDK_UNAVAILABLE -> CommerceProofFailure.CHECKOUT_UNAVAILABLE
    CheckoutFailure.NETWORK -> CommerceProofFailure.CHECKOUT_NETWORK
    CheckoutFailure.EXPIRED_OR_COMPLETED_CART -> CommerceProofFailure.CHECKOUT_EXPIRED
    CheckoutFailure.CONFIGURATION -> CommerceProofFailure.CHECKOUT_CONFIGURATION
    CheckoutFailure.RECOVERABLE -> CommerceProofFailure.CHECKOUT_RECOVERABLE
    CheckoutFailure.FATAL -> CommerceProofFailure.CHECKOUT_FATAL
}
