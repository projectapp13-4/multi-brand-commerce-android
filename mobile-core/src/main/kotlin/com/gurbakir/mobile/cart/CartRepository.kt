package com.gurbakir.mobile.cart

import com.gurbakir.account.session.CustomerAccountSessionCoordinator
import com.gurbakir.account.session.CustomerSessionResolution
import com.gurbakir.storefront.CartCoordinator
import com.gurbakir.storefront.CartLineInput
import com.gurbakir.storefront.CartLineSummary
import com.gurbakir.storefront.CartLineUpdate
import com.gurbakir.storefront.CartOwnership
import com.gurbakir.storefront.CartQuantityRule
import com.gurbakir.storefront.CartReference
import com.gurbakir.storefront.CartSessionResolution
import com.gurbakir.storefront.SensitiveBuyerAccessToken
import com.gurbakir.storefront.SensitiveCartId
import com.gurbakir.storefront.SensitiveCartLineId
import com.gurbakir.storefront.SensitiveCheckoutUrl
import com.gurbakir.storefront.StorefrontFailure
import com.gurbakir.storefront.StorefrontMedia
import com.gurbakir.storefront.StorefrontMoney
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class CartStatus {
    INITIAL,
    LOADING,
    EMPTY,
    ACTIVE,
    EXPIRED,
    RESTRICTED,
    ERROR
}

enum class CartMutation {
    ADDING,
    UPDATING,
    REMOVING,
    DISCARDING
}

enum class CartFailureCategory {
    CONNECTION,
    CONFIGURATION,
    SERVICE,
    QUANTITY_OR_AVAILABILITY,
    SECURE_STORAGE,
    AMBIGUOUS_MUTATION
}

data class CartFailure(val category: CartFailureCategory, val retryable: Boolean, val cartRetained: Boolean)

data class CartLine(
    val id: SensitiveCartLineId,
    val merchandiseId: String,
    val productId: String,
    val productTitle: String,
    val variantTitle: String,
    val quantity: Int,
    val quantityRule: CartQuantityRule,
    val canRemove: Boolean,
    val canUpdateQuantity: Boolean,
    val availableForSale: Boolean,
    val currentlyNotInStock: Boolean,
    val image: StorefrontMedia?,
    val unitPrice: StorefrontMoney,
    val totalPrice: StorefrontMoney
) {
    val nextQuantity: Int?
        get() =
            quantity.takeIf { canUpdateQuantity }
                ?.let { current ->
                    val next = current.toLong() + quantityRule.increment
                    val maximum = quantityRule.maximum
                    next.takeIf { it <= Int.MAX_VALUE && (maximum == null || it <= maximum) }
                        ?.toInt()
                }

    val previousQuantity: Int?
        get() =
            quantity.takeIf { canUpdateQuantity }
                ?.minus(quantityRule.increment)
                ?.takeIf { it >= quantityRule.minimum }
}

data class CartSummary(
    val totalQuantity: Int,
    val lines: List<CartLine>,
    val subtotal: StorefrontMoney,
    val total: StorefrontMoney,
    val hasWarnings: Boolean
)

data class CartState(
    val status: CartStatus = CartStatus.INITIAL,
    val cart: CartSummary? = null,
    val ownership: CartOwnership? = null,
    val mutation: CartMutation? = null,
    val failure: CartFailure? = null
) {
    val badgeQuantity: Int
        get() = cart?.totalQuantity ?: 0
}

sealed interface CartActionResult {
    data object Completed : CartActionResult

    data class Failed(val failure: CartFailure) : CartActionResult

    data object Restricted : CartActionResult
}

sealed interface CartCheckoutResolution {
    data class Eligible(val cartId: SensitiveCartId, val checkoutUrl: SensitiveCheckoutUrl) : CartCheckoutResolution {
        override fun toString(): String = "Eligible(cartId=<redacted>, checkoutUrl=<redacted>)"
    }

    data object Empty : CartCheckoutResolution

    data object Restricted : CartCheckoutResolution

    data object Unavailable : CartCheckoutResolution

    data class Failed(val failure: CartFailure) : CartCheckoutResolution
}

interface CartRepository {
    val state: StateFlow<CartState>

    suspend fun refresh()

    suspend fun add(merchandiseId: String, quantity: Int): CartActionResult

    suspend fun update(lineId: SensitiveCartLineId, quantity: Int): CartActionResult

    suspend fun remove(lineId: SensitiveCartLineId): CartActionResult

    suspend fun discard(): CartActionResult

    suspend fun prepareCheckout(): CartCheckoutResolution
}

interface CartOperations {
    suspend fun restore(): CartSessionResolution

    suspend fun mutate(plan: (CartSessionResolution) -> CartMutationPlan): CartMutationAttempt

    suspend fun clear(): Boolean
}

sealed interface CartMutationPlan {
    data object None : CartMutationPlan

    data object Invalid : CartMutationPlan

    data class Create(val lines: List<CartLineInput>) : CartMutationPlan

    data class Add(val lines: List<CartLineInput>) : CartMutationPlan

    data class Update(val lines: List<CartLineUpdate>) : CartMutationPlan

    data class Remove(val lineIds: List<SensitiveCartLineId>) : CartMutationPlan
}

data class CartMutationAttempt(
    val before: CartSessionResolution,
    val plan: CartMutationPlan,
    val result: CartSessionResolution?
)

class CoordinatedCartOperations
@Inject
constructor(
    private val coordinator: CartCoordinator,
    private val sessionCoordinator: CustomerAccountSessionCoordinator
) : CartOperations {
    override suspend fun restore(): CartSessionResolution = withCustomerSession(
        authenticated = coordinator::restoreAuthenticated,
        signedOut = coordinator::detach
    )

    override suspend fun mutate(plan: (CartSessionResolution) -> CartMutationPlan): CartMutationAttempt =
        when (val session = sessionCoordinator.restore()) {
            is CustomerSessionResolution.Authenticated ->
                session.session.accessToken.useSuspending { rawToken ->
                    val token = SensitiveBuyerAccessToken.from(rawToken)
                    executeMutation(coordinator.restoreAuthenticated(token), plan, token)
                }

            CustomerSessionResolution.SignedOut -> executeMutation(coordinator.detach(), plan, null)

            is CustomerSessionResolution.Failed ->
                if (session.encryptedSessionRetained) {
                    val current = coordinator.restore()
                    CartMutationAttempt(current, CartMutationPlan.None, null)
                } else {
                    executeMutation(coordinator.detach(), plan, null)
                }
        }

    override suspend fun clear(): Boolean = coordinator.clear()

    private suspend fun executeMutation(
        current: CartSessionResolution,
        planner: (CartSessionResolution) -> CartMutationPlan,
        token: SensitiveBuyerAccessToken?
    ): CartMutationAttempt {
        val plan = planner(current)
        val result = when (plan) {
            CartMutationPlan.None,
            CartMutationPlan.Invalid -> null

            is CartMutationPlan.Create -> coordinator.create(plan.lines, token)

            is CartMutationPlan.Add ->
                if (token == null) coordinator.add(plan.lines) else coordinator.addAuthenticated(plan.lines)

            is CartMutationPlan.Update ->
                if (token == null) coordinator.update(plan.lines) else coordinator.updateAuthenticated(plan.lines)

            is CartMutationPlan.Remove ->
                if (token == null) coordinator.remove(plan.lineIds) else coordinator.removeAuthenticated(plan.lineIds)
        }
        return CartMutationAttempt(current, plan, result)
    }

    private suspend fun withCustomerSession(
        authenticated: suspend (SensitiveBuyerAccessToken) -> CartSessionResolution,
        signedOut: suspend () -> CartSessionResolution
    ): CartSessionResolution = when (val session = sessionCoordinator.restore()) {
        is CustomerSessionResolution.Authenticated ->
            session.session.accessToken.useSuspending { rawToken ->
                authenticated(SensitiveBuyerAccessToken.from(rawToken))
            }

        CustomerSessionResolution.SignedOut -> signedOut()

        is CustomerSessionResolution.Failed ->
            if (session.encryptedSessionRetained) {
                coordinator.restore()
            } else {
                signedOut()
            }
    }
}

@Singleton
class DefaultCartRepository
@Inject
constructor(private val operations: CartOperations) : CartRepository {
    private val lock = Mutex()
    private val _state = MutableStateFlow(CartState())
    override val state: StateFlow<CartState> = _state.asStateFlow()
    private var activeCart: CartReference? = null

    override suspend fun refresh(): Unit = lock.withLock {
        _state.value =
            _state.value.copy(
                status = if (activeCart == null) CartStatus.LOADING else CartStatus.ACTIVE,
                mutation = null,
                failure = null
            )
        applyResolution(operations.restore())
    }

    override suspend fun add(merchandiseId: String, quantity: Int): CartActionResult = lock.withLock {
        if (merchandiseId.isBlank() || quantity <= 0) return@withLock invalidAction()
        _state.value = _state.value.copy(mutation = CartMutation.ADDING, failure = null)
        val lines = listOf(CartLineInput(merchandiseId, quantity))
        val attempt = operations.mutate { current ->
            when (current) {
                CartSessionResolution.Empty,
                CartSessionResolution.Expired -> CartMutationPlan.Create(lines)

                is CartSessionResolution.Active -> CartMutationPlan.Add(lines)

                is CartSessionResolution.Failed,
                is CartSessionResolution.Restricted -> CartMutationPlan.None
            }
        }
        val result = attempt.result ?: return@withLock applyResolution(attempt.before)
        val before = (attempt.before as? CartSessionResolution.Active)?.cart
        reconcileMutation(
            result = result,
            completed = { cart ->
                val beforeQuantity = before?.quantityOf(merchandiseId) ?: 0
                cart.quantityOf(merchandiseId) >= beforeQuantity + quantity
            }
        )
    }

    override suspend fun update(lineId: SensitiveCartLineId, quantity: Int): CartActionResult = lock.withLock {
        _state.value = _state.value.copy(mutation = CartMutation.UPDATING, failure = null)
        val attempt = operations.mutate { current ->
            val cart = (current as? CartSessionResolution.Active)?.cart
                ?: return@mutate CartMutationPlan.None
            val line = cart.lines.firstOrNull { it.id == lineId }
                ?: return@mutate CartMutationPlan.Invalid
            if (!line.accepts(quantity)) return@mutate CartMutationPlan.Invalid
            CartMutationPlan.Update(listOf(CartLineUpdate(lineId, quantity)))
        }
        if (attempt.plan == CartMutationPlan.Invalid) return@withLock invalidAction(cartRetained = true)
        val result = attempt.result ?: return@withLock applyResolution(attempt.before)
        reconcileMutation(
            result = result,
            completed = { updated -> updated.lines.firstOrNull { it.id == lineId }?.quantity == quantity }
        )
    }

    override suspend fun remove(lineId: SensitiveCartLineId): CartActionResult = lock.withLock {
        _state.value = _state.value.copy(mutation = CartMutation.REMOVING, failure = null)
        val attempt = operations.mutate { current ->
            val cart = (current as? CartSessionResolution.Active)?.cart
                ?: return@mutate CartMutationPlan.None
            val line = cart.lines.firstOrNull { it.id == lineId }
                ?: return@mutate CartMutationPlan.Invalid
            if (!line.canRemove) return@mutate CartMutationPlan.Invalid
            CartMutationPlan.Remove(listOf(lineId))
        }
        if (attempt.plan == CartMutationPlan.Invalid) return@withLock invalidAction(cartRetained = true)
        val result = attempt.result ?: return@withLock applyResolution(attempt.before)
        reconcileMutation(
            result = result,
            completed = { updated -> updated.lines.none { it.id == lineId } }
        )
    }

    override suspend fun discard(): CartActionResult = lock.withLock {
        _state.value = _state.value.copy(mutation = CartMutation.DISCARDING, failure = null)
        if (operations.clear()) {
            activeCart = null
            _state.value = CartState(status = CartStatus.EMPTY)
            CartActionResult.Completed
        } else {
            val failure = CartFailure(CartFailureCategory.SECURE_STORAGE, retryable = true, cartRetained = true)
            _state.value = _state.value.copy(mutation = null, failure = failure)
            CartActionResult.Failed(failure)
        }
    }

    override suspend fun prepareCheckout(): CartCheckoutResolution = lock.withLock {
        _state.value =
            _state.value.copy(
                status = if (activeCart == null) CartStatus.LOADING else CartStatus.ACTIVE,
                mutation = null,
                failure = null
            )
        val resolution = operations.restore()
        val action = applyResolution(resolution)
        resolution.toCheckoutResolution(action)
    }

    private suspend fun reconcileMutation(
        result: CartSessionResolution,
        completed: (CartReference) -> Boolean
    ): CartActionResult {
        if (result !is CartSessionResolution.Failed || !result.error.isAmbiguousMutation()) {
            return applyResolution(result)
        }
        return when (val reread = operations.restore()) {
            is CartSessionResolution.Active -> {
                if (completed(reread.cart)) {
                    applyResolution(reread)
                } else {
                    activeCart = reread.cart
                    val failure =
                        result.error.toCartFailure(
                            retained = true,
                            categoryOverride = CartFailureCategory.AMBIGUOUS_MUTATION
                        )
                    _state.value = reread.cart.toState(failure = failure)
                    CartActionResult.Failed(failure)
                }
            }

            else -> {
                val failure =
                    result.error.toCartFailure(
                        retained = result.persistedCartRetained,
                        categoryOverride = CartFailureCategory.AMBIGUOUS_MUTATION
                    )
                applyResolution(reread)
                _state.value = _state.value.copy(failure = failure, mutation = null)
                CartActionResult.Failed(failure)
            }
        }
    }

    private fun applyResolution(resolution: CartSessionResolution): CartActionResult = when (resolution) {
        is CartSessionResolution.Active -> {
            activeCart = resolution.cart
            val nextState = resolution.cart.toState(ownership = resolution.ownership)
            _state.value = nextState
            if (nextState.status == CartStatus.ACTIVE) {
                CartActionResult.Completed
            } else {
                CartActionResult.Failed(requireNotNull(nextState.failure))
            }
        }

        CartSessionResolution.Empty -> {
            activeCart = null
            _state.value = CartState(status = CartStatus.EMPTY)
            CartActionResult.Completed
        }

        CartSessionResolution.Expired -> {
            activeCart = null
            _state.value = CartState(status = CartStatus.EXPIRED)
            CartActionResult.Completed
        }

        is CartSessionResolution.Restricted -> {
            activeCart = null
            _state.value = CartState(status = CartStatus.RESTRICTED, ownership = resolution.ownership)
            CartActionResult.Restricted
        }

        is CartSessionResolution.Failed -> {
            val failure = resolution.error.toCartFailure(resolution.persistedCartRetained)
            _state.value =
                activeCart?.toState(failure = failure)
                    ?: CartState(status = CartStatus.ERROR, failure = failure)
            CartActionResult.Failed(failure)
        }
    }

    private fun invalidAction(cartRetained: Boolean = activeCart != null): CartActionResult.Failed {
        val failure =
            CartFailure(
                category = CartFailureCategory.QUANTITY_OR_AVAILABILITY,
                retryable = false,
                cartRetained = cartRetained
            )
        _state.value = _state.value.copy(mutation = null, failure = failure)
        return CartActionResult.Failed(failure)
    }
}

private fun CartSessionResolution.toCheckoutResolution(action: CartActionResult): CartCheckoutResolution = when (this) {
    is CartSessionResolution.Active ->
        if (action == CartActionResult.Completed && cart.isCheckoutEligible()) {
            CartCheckoutResolution.Eligible(cart.id, cart.checkoutUrl)
        } else if (action is CartActionResult.Failed) {
            CartCheckoutResolution.Failed(action.failure)
        } else {
            CartCheckoutResolution.Unavailable
        }

    CartSessionResolution.Empty,
    CartSessionResolution.Expired -> CartCheckoutResolution.Empty

    is CartSessionResolution.Restricted -> CartCheckoutResolution.Restricted

    is CartSessionResolution.Failed ->
        CartCheckoutResolution.Failed(
            (action as CartActionResult.Failed).failure
        )
}

private fun CartReference.isCheckoutEligible(): Boolean =
    !hasMoreLines && lines.isNotEmpty() && totalQuantity > 0 && subtotal != null && total != null &&
        lines.sumOf(CartLineSummary::quantity) == totalQuantity &&
        lines.all { line -> line.quantity > 0 && line.availableForSale }

private fun CartReference.toState(
    ownership: CartOwnership = CartOwnership.ANONYMOUS,
    failure: CartFailure? = null
): CartState {
    val mappedLines = lines.mapNotNull(CartLineSummary::toCartLine)
    val presentationComplete =
        subtotal != null && total != null && mappedLines.size == lines.size && !hasMoreLines
    return if (!presentationComplete) {
        CartState(
            status = CartStatus.ERROR,
            failure = CartFailure(CartFailureCategory.SERVICE, retryable = true, cartRetained = true)
        )
    } else {
        CartState(
            status = CartStatus.ACTIVE,
            cart =
                CartSummary(
                    totalQuantity = totalQuantity,
                    lines = mappedLines,
                    subtotal = requireNotNull(subtotal),
                    total = requireNotNull(total),
                    hasWarnings = warningCodes.isNotEmpty()
                ),
            ownership = ownership,
            failure = failure
        )
    }
}

private fun CartLineSummary.toCartLine(): CartLine? {
    val presentationComplete =
        unitPrice != null && totalPrice != null && productId.isNotBlank() &&
            productTitle.isNotBlank() && merchandiseId.isNotBlank()
    return if (!presentationComplete) {
        null
    } else {
        CartLine(
            id = id,
            merchandiseId = merchandiseId,
            productId = productId,
            productTitle = productTitle,
            variantTitle = variantTitle,
            quantity = quantity,
            quantityRule = quantityRule,
            canRemove = canRemove,
            canUpdateQuantity = canUpdateQuantity,
            availableForSale = availableForSale,
            currentlyNotInStock = currentlyNotInStock,
            image = image,
            unitPrice = requireNotNull(unitPrice),
            totalPrice = requireNotNull(totalPrice)
        )
    }
}

private fun CartReference.quantityOf(merchandiseId: String): Int =
    lines.filter { it.merchandiseId == merchandiseId }.sumOf(CartLineSummary::quantity)

private fun CartLineSummary.accepts(candidate: Int): Boolean = quantityRule.maximum.let { maximum ->
    canUpdateQuantity && candidate >= quantityRule.minimum &&
        (maximum == null || candidate <= maximum) &&
        candidate % quantityRule.increment == 0
}

private fun StorefrontFailure.isAmbiguousMutation(): Boolean =
    this is StorefrontFailure.Transport || this is StorefrontFailure.GraphQl

private fun StorefrontFailure.toCartFailure(
    retained: Boolean,
    categoryOverride: CartFailureCategory? = null
): CartFailure = CartFailure(
    category =
        categoryOverride
            ?: when (this) {
                is StorefrontFailure.Configuration -> CartFailureCategory.CONFIGURATION

                is StorefrontFailure.Transport -> CartFailureCategory.CONNECTION

                is StorefrontFailure.UserErrors,
                is StorefrontFailure.InvalidCart -> CartFailureCategory.QUANTITY_OR_AVAILABILITY

                StorefrontFailure.SecurePersistence -> CartFailureCategory.SECURE_STORAGE

                is StorefrontFailure.GraphQl -> CartFailureCategory.SERVICE
            },
    retryable =
        when (this) {
            is StorefrontFailure.Configuration,
            is StorefrontFailure.UserErrors,
            is StorefrontFailure.InvalidCart -> false

            is StorefrontFailure.Transport -> retryable

            is StorefrontFailure.GraphQl,
            StorefrontFailure.SecurePersistence -> true
        },
    cartRetained = retained
)
