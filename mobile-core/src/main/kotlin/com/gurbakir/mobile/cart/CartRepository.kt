package com.gurbakir.mobile.cart

import com.gurbakir.account.CustomerAccountGateway
import com.gurbakir.account.CustomerAccountResult
import com.gurbakir.account.session.CustomerAccountSessionCoordinator
import com.gurbakir.account.session.CustomerSessionResolution
import com.gurbakir.account.session.CustomerSessionStorageException
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
import com.gurbakir.storefront.SensitiveCustomerId
import com.gurbakir.storefront.StorefrontFailure
import com.gurbakir.storefront.StorefrontMedia
import com.gurbakir.storefront.StorefrontMoney
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
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
    UNAVAILABLE,
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
    val failure: CartFailure? = null,
    val adjustment: CartActionAdjustment? = null
) {
    val badgeQuantity: Int
        get() = cart?.totalQuantity ?: 0
}

sealed interface CartActionResult {
    data object Completed : CartActionResult

    data class Adjusted(val adjustment: CartActionAdjustment) : CartActionResult

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

    suspend fun <T> withPreparedCheckout(action: suspend (CartCheckoutResolution) -> T): T = action(prepareCheckout())
}

interface CartOperations {
    suspend fun restore(): CartSessionResolution

    suspend fun <T> withRestoredCart(action: suspend (CartSessionResolution) -> T): T = action(restore())

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
    private val sessionCoordinator: CustomerAccountSessionCoordinator,
    private val identityGateway: Provider<CustomerAccountGateway>
) : CartOperations {
    override suspend fun restore(): CartSessionResolution = withRestoredCart { it }

    override suspend fun <T> withRestoredCart(action: suspend (CartSessionResolution) -> T): T =
        withCustomerSession(restricted = action) { token, customerId ->
            val resolution = when (token) {
                null -> coordinator.detach()
                else -> coordinator.restoreAuthenticated(token, requireNotNull(customerId))
            }
            action(resolution)
        }

    override suspend fun mutate(plan: (CartSessionResolution) -> CartMutationPlan): CartMutationAttempt =
        withCustomerSession(
            restricted = { CartMutationAttempt(it, CartMutationPlan.None, null) }
        ) { token, customerId ->
            val current = when (token) {
                null -> coordinator.detach()
                else -> coordinator.restoreAuthenticated(token, requireNotNull(customerId))
            }
            executeMutation(current, plan, token, customerId)
        }

    override suspend fun clear(): Boolean = coordinator.clear()

    private suspend fun executeMutation(
        current: CartSessionResolution,
        planner: (CartSessionResolution) -> CartMutationPlan,
        token: SensitiveBuyerAccessToken?,
        customerId: SensitiveCustomerId?
    ): CartMutationAttempt {
        val plan = planner(current)
        val result = when (plan) {
            CartMutationPlan.None,
            CartMutationPlan.Invalid -> null

            is CartMutationPlan.Create -> coordinator.create(plan.lines, token, customerId)

            is CartMutationPlan.Add -> when (token) {
                null -> coordinator.add(plan.lines)
                else -> coordinator.addAuthenticated(plan.lines, requireNotNull(customerId))
            }

            is CartMutationPlan.Update -> when (token) {
                null -> coordinator.update(plan.lines)
                else -> coordinator.updateAuthenticated(plan.lines, requireNotNull(customerId))
            }

            is CartMutationPlan.Remove -> when (token) {
                null -> coordinator.remove(plan.lineIds)
                else -> coordinator.removeAuthenticated(plan.lineIds, requireNotNull(customerId))
            }
        }
        return CartMutationAttempt(current, plan, result)
    }

    private suspend fun <T> withCustomerSession(
        restricted: suspend (CartSessionResolution) -> T,
        action: suspend (SensitiveBuyerAccessToken?, SensitiveCustomerId?) -> T
    ): T = try {
        sessionCoordinator.withSession { session ->
            when (session) {
                is CustomerSessionResolution.Authenticated -> {
                    val identity = identityGateway.get().loadIdentity(session.session)
                    val rawId = (identity as? CustomerAccountResult.Success)?.value?.id
                    val customerId = try {
                        rawId?.let { SensitiveCustomerId.from(it) }
                    } catch (_: IllegalArgumentException) {
                        null
                    }
                    if (customerId == null) {
                        restricted(CartSessionResolution.Restricted(CartOwnership.VERIFY_PENDING))
                    } else {
                        session.session.accessToken.useSuspending { rawToken ->
                            action(SensitiveBuyerAccessToken.from(rawToken), customerId)
                        }
                    }
                }

                CustomerSessionResolution.SignedOut -> action(null, null)

                is CustomerSessionResolution.Failed ->
                    if (session.encryptedSessionRetained) {
                        restricted(CartSessionResolution.Restricted(CartOwnership.VERIFY_PENDING))
                    } else {
                        action(null, null)
                    }
            }
        }
    } catch (_: CustomerSessionStorageException) {
        restricted(CartSessionResolution.Restricted(CartOwnership.VERIFY_PENDING))
    }
}

@Singleton
@Suppress("TooManyFunctions") // Cart actions and bounded checkout preparation share one mutex and published state.
class DefaultCartRepository
@Inject
constructor(private val operations: CartOperations) : CartRepository {
    private val lock = Mutex()
    private val _state = MutableStateFlow(CartState())
    override val state: StateFlow<CartState> = _state.asStateFlow()
    private var activeCart: CartReference? = null

    override suspend fun refresh(): Unit = withOwnedOperation(CartFailureCategory.SERVICE) { operation ->
        _state.value =
            _state.value.copy(
                status = if (activeCart == null) CartStatus.LOADING else CartStatus.ACTIVE,
                mutation = null,
                failure = null,
                adjustment = null
            )
        applyResolution(operation.restore())
    }

    override suspend fun add(merchandiseId: String, quantity: Int): CartActionResult =
        withOwnedOperation(CartFailureCategory.AMBIGUOUS_MUTATION) { operation ->
            if (merchandiseId.isBlank() || quantity <= 0) return@withOwnedOperation invalidAction()
            _state.value = _state.value.copy(mutation = CartMutation.ADDING, failure = null, adjustment = null)
            val lines = listOf(CartLineInput(merchandiseId, quantity))
            val attempt = operations.mutate { current ->
                operation.record(current)
                when (current) {
                    CartSessionResolution.Empty,
                    CartSessionResolution.Expired -> CartMutationPlan.Create(lines)

                    is CartSessionResolution.Active -> CartMutationPlan.Add(lines)

                    is CartSessionResolution.Failed,
                    is CartSessionResolution.Restricted -> CartMutationPlan.None
                }
            }
            val before = (attempt.before as? CartSessionResolution.Active)?.cart
            val previous = before?.quantityOf(merchandiseId) ?: 0L
            finishMutation(
                attempt,
                CartMutationIntent(
                    CartActionKind.ADD,
                    CartMutationTarget.Merchandise(merchandiseId),
                    previous,
                    previous + quantity.toLong()
                ),
                operation
            )
        }

    override suspend fun update(lineId: SensitiveCartLineId, quantity: Int): CartActionResult =
        withOwnedOperation(CartFailureCategory.AMBIGUOUS_MUTATION) { operation ->
            _state.value = _state.value.copy(mutation = CartMutation.UPDATING, failure = null, adjustment = null)
            val attempt = operations.mutate { current ->
                operation.record(current)
                val cart = (current as? CartSessionResolution.Active)?.cart
                    ?: return@mutate CartMutationPlan.None
                val line = cart.lines.firstOrNull { it.id == lineId }
                    ?: return@mutate CartMutationPlan.Invalid
                if (!line.accepts(quantity)) return@mutate CartMutationPlan.Invalid
                CartMutationPlan.Update(listOf(CartLineUpdate(lineId, quantity)))
            }
            val before = (attempt.before as? CartSessionResolution.Active)?.cart
            val previous = before?.lines?.firstOrNull { it.id == lineId }?.quantity?.toLong() ?: 0L
            finishMutation(
                attempt,
                CartMutationIntent(CartActionKind.UPDATE, CartMutationTarget.Line(lineId), previous, quantity.toLong()),
                operation
            )
        }

    override suspend fun remove(lineId: SensitiveCartLineId): CartActionResult =
        withOwnedOperation(CartFailureCategory.AMBIGUOUS_MUTATION) { operation ->
            _state.value = _state.value.copy(mutation = CartMutation.REMOVING, failure = null, adjustment = null)
            val attempt = operations.mutate { current ->
                operation.record(current)
                val cart = (current as? CartSessionResolution.Active)?.cart
                    ?: return@mutate CartMutationPlan.None
                val line = cart.lines.firstOrNull { it.id == lineId }
                    ?: return@mutate CartMutationPlan.Invalid
                if (!line.canRemove) return@mutate CartMutationPlan.Invalid
                CartMutationPlan.Remove(listOf(lineId))
            }
            val before = (attempt.before as? CartSessionResolution.Active)?.cart
            val previous = before?.lines?.firstOrNull { it.id == lineId }?.quantity?.toLong() ?: 0L
            finishMutation(
                attempt,
                CartMutationIntent(CartActionKind.REMOVE, CartMutationTarget.Line(lineId), previous, 0L),
                operation
            )
        }

    override suspend fun discard(): CartActionResult = withOwnedOperation(CartFailureCategory.SECURE_STORAGE) {
        _state.value = _state.value.copy(mutation = CartMutation.DISCARDING, failure = null, adjustment = null)
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

    override suspend fun prepareCheckout(): CartCheckoutResolution = withPreparedCheckout { it }

    override suspend fun <T> withPreparedCheckout(action: suspend (CartCheckoutResolution) -> T): T =
        withOwnedOperation(CartFailureCategory.SERVICE) { operation ->
            _state.value =
                _state.value.copy(
                    status = if (activeCart == null) CartStatus.LOADING else CartStatus.ACTIVE,
                    mutation = null,
                    failure = null,
                    adjustment = null
                )
            operation.withRestoredCart { resolution ->
                val result = applyResolution(resolution)
                action(resolution.toCheckoutResolution(result))
            }
        }

    private suspend fun <T> withOwnedOperation(
        cancelledCategory: CartFailureCategory,
        action: suspend (CartOperationObservation) -> T
    ): T = lock.withLock {
        val previous = _state.value
        val operation = CartOperationObservation(operations)
        if (cancelledCategory == CartFailureCategory.SECURE_STORAGE) {
            activeCart?.let { cart ->
                previous.ownership?.let { ownership ->
                    operation.record(CartSessionResolution.Active(cart, ownership))
                }
            }
        }
        try {
            action(operation)
        } catch (cancelled: CancellationException) {
            _state.value = cancelledState(operation.resolution, previous, cancelledCategory)
            throw cancelled
        } finally {
            // This owner still holds the mutex; a cancelled waiter or an old finalizer cannot clear a newer action.
            _state.value = _state.value.copy(mutation = null)
        }
    }

    private fun cancelledState(
        observed: CartSessionResolution?,
        previous: CartState,
        category: CartFailureCategory
    ): CartState {
        val verified = observed as? CartSessionResolution.Active
        val restricted = observed as? CartSessionResolution.Restricted
        val priorRestriction = previous.ownership.takeIf { previous.status == CartStatus.RESTRICTED }
        val ownership = verified?.ownership ?: restricted?.ownership ?: priorRestriction
            ?: previous.ownership?.takeUnless { it == CartOwnership.ANONYMOUS } ?: CartOwnership.VERIFY_PENDING
        val retained = activeCart != null || previous.ownership != null || verified != null ||
            (observed is CartSessionResolution.Failed && observed.persistedCartRetained)
        val failure = CartFailure(category, retryable = true, cartRetained = retained)
        return when {
            restricted != null || (observed == null && priorRestriction != null) -> {
                activeCart = null
                CartState(status = CartStatus.RESTRICTED, ownership = ownership)
            }

            verified?.ownership == CartOwnership.ANONYMOUS -> {
                activeCart = verified.cart
                verified.cart.toState(ownership = CartOwnership.ANONYMOUS, failure = failure)
            }

            else -> CartState(status = CartStatus.ERROR, ownership = ownership, failure = failure)
        }
    }

    private suspend fun finishMutation(
        attempt: CartMutationAttempt,
        intent: CartMutationIntent,
        operation: CartOperationObservation
    ): CartActionResult = when {
        attempt.plan == CartMutationPlan.Invalid -> {
            applyResolution(attempt.before)
            invalidAction()
        }

        attempt.result == null -> unavailableAction(attempt.before)

        else -> reconcileMutation(requireNotNull(attempt.result), intent, attempt.before, operation)
    }

    private suspend fun reconcileMutation(
        result: CartSessionResolution,
        intent: CartMutationIntent,
        before: CartSessionResolution,
        operation: CartOperationObservation
    ): CartActionResult = when (result) {
        is CartSessionResolution.Active -> directOutcome(result, intent)

        is CartSessionResolution.Failed -> when {
            result.error is StorefrontFailure.UserErrors -> reconcileRejection(result, before, operation)
            result.error.isAmbiguousMutation() -> reconcileAmbiguous(result, intent, operation)
            else -> applyResolution(result)
        }

        else -> unavailableAction(result)
    }

    private fun directOutcome(result: CartSessionResolution.Active, intent: CartMutationIntent): CartActionResult {
        val publication = applyResolution(result)
        return if (publication != CartActionResult.Completed || intent.isComplete(result.cart)) {
            publication
        } else {
            val adjustment = intent.adjustment(result.cart)
            if (adjustment != null) {
                _state.value = _state.value.copy(adjustment = adjustment)
                CartActionResult.Adjusted(adjustment)
            } else {
                invalidAction(cartRetained = true)
            }
        }
    }

    private suspend fun reconcileRejection(
        result: CartSessionResolution.Failed,
        before: CartSessionResolution,
        operation: CartOperationObservation
    ): CartActionResult {
        val error = result.error as StorefrontFailure.UserErrors
        val current = result.authoritativeCart ?: if (error.isClientValidation) before else operation.restore()
        operation.record(current)
        val publication = applyResolution(current)
        return when {
            publication == CartActionResult.Restricted -> publication

            current is CartSessionResolution.Failed -> publication

            publication is CartActionResult.Failed -> publication

            else -> {
                val retained = current is CartSessionResolution.Active
                val failure = error.toCartFailure(retained)
                _state.value = _state.value.copy(failure = failure, mutation = null)
                CartActionResult.Failed(failure)
            }
        }
    }

    private suspend fun reconcileAmbiguous(
        result: CartSessionResolution.Failed,
        intent: CartMutationIntent,
        operation: CartOperationObservation
    ): CartActionResult {
        val current = operation.restore()
        val publication = applyResolution(current)
        return when {
            publication == CartActionResult.Restricted -> publication

            current is CartSessionResolution.Failed &&
                current.error == StorefrontFailure.SecurePersistence -> publication

            current is CartSessionResolution.Active && publication == CartActionResult.Completed &&
                intent.isComplete(current.cart) -> CartActionResult.Completed

            else -> {
                val retained = current is CartSessionResolution.Active ||
                    (current is CartSessionResolution.Failed && current.persistedCartRetained)
                val failure = result.error.toCartFailure(retained, CartFailureCategory.AMBIGUOUS_MUTATION)
                _state.value = _state.value.copy(failure = failure, mutation = null)
                CartActionResult.Failed(failure)
            }
        }
    }

    private fun unavailableAction(resolution: CartSessionResolution): CartActionResult {
        val publication = applyResolution(resolution)
        return if (publication is CartActionResult.Failed || publication == CartActionResult.Restricted) {
            publication
        } else {
            val failure = CartFailure(
                CartFailureCategory.UNAVAILABLE,
                retryable = false,
                cartRetained = activeCart != null
            )
            _state.value = _state.value.copy(failure = failure, mutation = null)
            CartActionResult.Failed(failure)
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

        is CartSessionResolution.Failed -> applyFailure(resolution)
    }

    private fun applyFailure(resolution: CartSessionResolution.Failed): CartActionResult.Failed {
        val failure = resolution.error.toCartFailure(resolution.persistedCartRetained)
        val retainedOwnership = _state.value.ownership
            ?: activeCart?.let { cart ->
                if (cart.customerId != null) CartOwnership.CUSTOMER_ASSOCIATED else CartOwnership.ANONYMOUS
            }
        val failedSecureWrite = resolution.error == StorefrontFailure.SecurePersistence &&
            resolution.persistedCartRetained
        val ownershipIsUnconfirmed = retainedOwnership != CartOwnership.ANONYMOUS
        _state.value = if (activeCart != null && failedSecureWrite && ownershipIsUnconfirmed) {
            CartState(status = CartStatus.ERROR, ownership = retainedOwnership, failure = failure)
        } else {
            activeCart?.toState(ownership = retainedOwnership ?: CartOwnership.ANONYMOUS, failure = failure)
                ?: CartState(status = CartStatus.ERROR, failure = failure)
        }
        return CartActionResult.Failed(failure)
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

private class CartOperationObservation(private val operations: CartOperations) {
    var resolution: CartSessionResolution? = null
        private set

    fun record(resolution: CartSessionResolution) {
        this.resolution = resolution
    }

    suspend fun restore(): CartSessionResolution {
        // A newly acquired lease can belong to a different customer; older anonymous proof cannot survive it.
        resolution = null
        return operations.restore().also(::record)
    }

    suspend fun <T> withRestoredCart(action: suspend (CartSessionResolution) -> T): T {
        resolution = null
        return operations.withRestoredCart { observed ->
            record(observed)
            action(observed)
        }
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
