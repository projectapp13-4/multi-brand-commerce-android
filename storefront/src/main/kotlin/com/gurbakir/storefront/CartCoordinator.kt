package com.gurbakir.storefront

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private const val DEFAULT_CART_LIFETIME_DAYS = 30L
private val DEFAULT_CART_LIFETIME: Duration = Duration.ofDays(DEFAULT_CART_LIFETIME_DAYS)

enum class CartOwnership {
    ANONYMOUS,
    CUSTOMER_ASSOCIATED,
    VERIFY_PENDING,
    DETACH_PENDING,
    QUARANTINED
}

enum class CartRestrictionReason {
    OWNERSHIP,
    SECURE_STORAGE
}

data class PersistedCart(
    val id: SensitiveCartId,
    val expiresAt: Instant,
    val ownership: CartOwnership,
    val customerId: SensitiveCustomerId? = null
) {
    override fun toString(): String = "PersistedCart(id=<redacted>, expiresAt=$expiresAt, ownership=$ownership)"
}

interface CartSessionStore {
    suspend fun read(): PersistedCart?

    suspend fun write(cart: PersistedCart)

    suspend fun clear()
}

sealed interface CartSessionResolution {
    data class Active(val cart: CartReference, val ownership: CartOwnership) : CartSessionResolution {
        override fun toString(): String = "Active(<redacted-cart>, ownership=$ownership)"
    }

    data object Empty : CartSessionResolution

    data object Expired : CartSessionResolution

    data class Restricted(
        val ownership: CartOwnership,
        val reason: CartRestrictionReason = CartRestrictionReason.OWNERSHIP
    ) : CartSessionResolution

    data class Failed(
        val error: StorefrontFailure,
        val persistedCartRetained: Boolean,
        val authoritativeCart: Active? = null
    ) : CartSessionResolution
}

private fun CartSessionResolution.withRejection(error: StorefrontFailure.UserErrors): CartSessionResolution =
    if (this is CartSessionResolution.Active) {
        CartSessionResolution.Failed(error, persistedCartRetained = true, authoritativeCart = this)
    } else {
        this
    }

enum class CartCompletionResolution {
    CLEARED,
    ALREADY_ABSENT,
    DIFFERENT_CART,
    SECURE_PERSISTENCE_FAILED
}

class CartCompletionCoordinator internal constructor(
    private val completeAction: suspend (SensitiveCartId) -> CartCompletionResolution
) {
    suspend fun complete(expectedCartId: SensitiveCartId): CartCompletionResolution = completeAction(expectedCartId)
}

@Suppress("TooManyFunctions") // All cart ownership transitions must share this mutex and persistence boundary.
class CartCoordinator(
    private val gateway: StorefrontGateway,
    store: CartSessionStore,
    clock: Clock = Clock.systemUTC(),
    cartLifetime: Duration = DEFAULT_CART_LIFETIME
) {
    private val lock = Mutex()
    private val persistence = CartPersistence(store, clock, cartLifetime)
    val completion = CartCompletionCoordinator { expectedCartId ->
        lock.withLock { persistence.clearIfMatches(expectedCartId) }
    }

    init {
        require(cartLifetime > Duration.ZERO)
    }

    suspend fun create(
        lines: List<CartLineInput>,
        buyerAccessToken: SensitiveBuyerAccessToken? = null,
        customerId: SensitiveCustomerId? = null
    ): CartSessionResolution = lock.withLock {
        if ((buyerAccessToken == null) != (customerId == null)) {
            return@withLock CartSessionResolution.Restricted(CartOwnership.QUARANTINED)
        }
        val expected = if (buyerAccessToken == null) CartOwnership.ANONYMOUS else CartOwnership.CUSTOMER_ASSOCIATED
        when (val result = gateway.createCart(lines, buyerAccessToken)) {
            is StorefrontResult.Failure -> {
                val rejection = result.error as? StorefrontFailure.UserErrors
                val cart = rejection?.cart
                if (cart != null) {
                    persistence.persistNew(cart, expected, customerId).withRejection(requireNotNull(rejection))
                } else {
                    CartSessionResolution.Failed(result.error, false)
                }
            }

            is StorefrontResult.Success -> persistence.persistNew(result.value, expected, customerId)
        }
    }

    suspend fun restore(): CartSessionResolution = lock.withLock {
        val persisted = when (val read = persistence.readActive()) {
            is ActiveCartRead.Present -> read.cart

            ActiveCartRead.Empty -> return@withLock CartSessionResolution.Empty

            ActiveCartRead.Expired -> return@withLock CartSessionResolution.Expired

            ActiveCartRead.Failed ->
                return@withLock CartSessionResolution.Failed(StorefrontFailure.SecurePersistence, true)
        }
        if (persisted.ownership != CartOwnership.ANONYMOUS) {
            return@withLock CartSessionResolution.Restricted(persisted.ownership)
        }
        resolveAnonymousRemoteCart(gateway, persistence, persisted)
    }

    suspend fun restoreAuthenticated(
        buyerAccessToken: SensitiveBuyerAccessToken,
        customerId: SensitiveCustomerId
    ): CartSessionResolution = lock.withLock {
        val persisted = when (val read = persistence.readActive()) {
            is ActiveCartRead.Present -> read.cart

            ActiveCartRead.Empty -> return@withLock CartSessionResolution.Empty

            ActiveCartRead.Expired -> return@withLock CartSessionResolution.Expired

            ActiveCartRead.Failed ->
                return@withLock CartSessionResolution.Failed(StorefrontFailure.SecurePersistence, true)
        }
        when (persisted.ownership) {
            CartOwnership.ANONYMOUS,
            CartOwnership.CUSTOMER_ASSOCIATED,
            CartOwnership.VERIFY_PENDING -> verifyCustomerCart(persisted, buyerAccessToken, customerId)

            CartOwnership.DETACH_PENDING,
            CartOwnership.QUARANTINED -> CartSessionResolution.Restricted(persisted.ownership)
        }
    }

    suspend fun add(lines: List<CartLineInput>): CartSessionResolution = mutateAnonymous { persisted ->
        gateway.addCartLines(persisted.id, lines)
    }

    suspend fun update(lines: List<CartLineUpdate>): CartSessionResolution = mutateAnonymous { persisted ->
        gateway.updateCartLines(persisted.id, lines)
    }

    suspend fun remove(lineIds: List<SensitiveCartLineId>): CartSessionResolution = mutateAnonymous { persisted ->
        gateway.removeCartLines(persisted.id, lineIds)
    }

    suspend fun addAuthenticated(lines: List<CartLineInput>, customerId: SensitiveCustomerId): CartSessionResolution =
        mutateCustomerAssociated(customerId) { persisted -> gateway.addCartLines(persisted.id, lines) }

    suspend fun updateAuthenticated(
        lines: List<CartLineUpdate>,
        customerId: SensitiveCustomerId
    ): CartSessionResolution =
        mutateCustomerAssociated(customerId) { persisted -> gateway.updateCartLines(persisted.id, lines) }

    suspend fun removeAuthenticated(
        lineIds: List<SensitiveCartLineId>,
        customerId: SensitiveCustomerId
    ): CartSessionResolution =
        mutateCustomerAssociated(customerId) { persisted -> gateway.removeCartLines(persisted.id, lineIds) }

    suspend fun authenticate(
        buyerAccessToken: SensitiveBuyerAccessToken,
        customerId: SensitiveCustomerId
    ): CartSessionResolution = restoreAuthenticated(buyerAccessToken, customerId)

    suspend fun detach(): CartSessionResolution = lock.withLock {
        val persisted = when (val read = persistence.readActive()) {
            is ActiveCartRead.Present -> read.cart

            ActiveCartRead.Empty -> return@withLock CartSessionResolution.Empty

            ActiveCartRead.Expired -> return@withLock CartSessionResolution.Expired

            ActiveCartRead.Failed ->
                return@withLock CartSessionResolution.Failed(StorefrontFailure.SecurePersistence, true)
        }
        when (persisted.ownership) {
            CartOwnership.ANONYMOUS -> resolveAnonymousRemoteCart(gateway, persistence, persisted)

            CartOwnership.QUARANTINED -> CartSessionResolution.Restricted(CartOwnership.QUARANTINED)

            CartOwnership.CUSTOMER_ASSOCIATED,
            CartOwnership.VERIFY_PENDING,
            CartOwnership.DETACH_PENDING -> detachCustomerCart(persisted)
        }
    }

    suspend fun clear(): Boolean = lock.withLock { persistence.clear() }

    private suspend fun mutateAnonymous(
        operation: suspend (PersistedCart) -> StorefrontResult<CartReference>
    ): CartSessionResolution = mutateExpected(CartOwnership.ANONYMOUS, operation)

    private suspend fun mutateCustomerAssociated(
        customerId: SensitiveCustomerId,
        operation: suspend (PersistedCart) -> StorefrontResult<CartReference>
    ): CartSessionResolution = mutateExpected(CartOwnership.CUSTOMER_ASSOCIATED, operation, customerId)

    private suspend fun mutateExpected(
        expectedOwnership: CartOwnership,
        operation: suspend (PersistedCart) -> StorefrontResult<CartReference>,
        expectedCustomer: SensitiveCustomerId? = null
    ): CartSessionResolution = lock.withLock {
        val persisted = when (val read = persistence.readActive()) {
            is ActiveCartRead.Present -> read.cart

            ActiveCartRead.Empty -> return@withLock CartSessionResolution.Empty

            ActiveCartRead.Expired -> return@withLock CartSessionResolution.Expired

            ActiveCartRead.Failed ->
                return@withLock CartSessionResolution.Failed(StorefrontFailure.SecurePersistence, true)
        }
        if (persisted.ownership != expectedOwnership ||
            persisted.customerId != expectedCustomer
        ) {
            return@withLock CartSessionResolution.Restricted(persisted.ownership)
        }
        when (val result = operation(persisted)) {
            is StorefrontResult.Failure -> {
                val rejection = result.error as? StorefrontFailure.UserErrors
                val cart = rejection?.cart
                if (cart != null) {
                    persistence.persistResult(persisted, cart, expectedOwnership, expectedCustomer)
                        .withRejection(requireNotNull(rejection))
                } else {
                    persistence.handleRemoteFailure(result.error)
                }
            }

            is StorefrontResult.Success ->
                persistence.persistResult(persisted, result.value, expectedOwnership, persisted.customerId)
        }
    }

    private suspend fun detachCustomerCart(persisted: PersistedCart): CartSessionResolution {
        val pending = persisted.copy(ownership = CartOwnership.DETACH_PENDING)
        if (!persistence.write(pending)) {
            return CartSessionResolution.Failed(StorefrontFailure.SecurePersistence, true)
        }
        return when (val result = gateway.updateBuyerIdentity(persisted.id, null)) {
            is StorefrontResult.Success ->
                persistence.persistResult(pending, result.value, CartOwnership.ANONYMOUS)

            is StorefrontResult.Failure -> {
                if (result.error is StorefrontFailure.InvalidCart) {
                    persistence.handleRemoteFailure(result.error)
                } else {
                    val ownership =
                        if (result.error is StorefrontFailure.Transport && result.error.retryable) {
                            CartOwnership.DETACH_PENDING
                        } else {
                            CartOwnership.QUARANTINED
                        }
                    if (persistence.write(pending.copy(ownership = ownership))) {
                        CartSessionResolution.Restricted(ownership)
                    } else {
                        CartSessionResolution.Failed(StorefrontFailure.SecurePersistence, true)
                    }
                }
            }
        }
    }

    private suspend fun verifyCustomerCart(
        persisted: PersistedCart,
        token: SensitiveBuyerAccessToken,
        expectedCustomer: SensitiveCustomerId
    ): CartSessionResolution = if (persisted.customerId != null && persisted.customerId != expectedCustomer) {
        persistence.restrict(persisted, CartOwnership.QUARANTINED)
    } else {
        // Inspect remote ownership before writing even a legacy cart whose encrypted payload has no exact owner.
        when (val result = gateway.loadCart(persisted.id)) {
            is StorefrontResult.Failure -> persistence.handleRemoteFailure(result.error)
            is StorefrontResult.Success -> rebindVerifiedCustomerCart(persisted, result.value, token, expectedCustomer)
        }
    }

    private suspend fun rebindVerifiedCustomerCart(
        persisted: PersistedCart,
        remote: CartReference,
        token: SensitiveBuyerAccessToken,
        expectedCustomer: SensitiveCustomerId
    ): CartSessionResolution {
        val remoteMatches = when (persisted.ownership) {
            CartOwnership.ANONYMOUS -> remote.customerId == null

            CartOwnership.VERIFY_PENDING ->
                remote.customerId == null || remote.customerId == expectedCustomer

            else -> remote.customerId == expectedCustomer
        }
        if (remote.id != persisted.id || !remoteMatches) {
            return persistence.restrict(persisted, CartOwnership.QUARANTINED)
        }
        val pending = persisted.copy(ownership = CartOwnership.VERIFY_PENDING, customerId = expectedCustomer)
        return if (!persistence.write(pending)) {
            CartSessionResolution.Failed(StorefrontFailure.SecurePersistence, true)
        } else {
            when (val result = gateway.updateBuyerIdentity(persisted.id, token)) {
                is StorefrontResult.Failure ->
                    if (result.error is StorefrontFailure.InvalidCart) {
                        persistence.handleRemoteFailure(result.error)
                    } else {
                        CartSessionResolution.Restricted(CartOwnership.VERIFY_PENDING)
                    }

                is StorefrontResult.Success ->
                    persistence.persistResult(
                        pending,
                        result.value,
                        CartOwnership.CUSTOMER_ASSOCIATED,
                        expectedCustomer
                    )
            }
        }
    }
}

private suspend fun resolveAnonymousRemoteCart(
    gateway: StorefrontGateway,
    persistence: CartPersistence,
    persisted: PersistedCart
): CartSessionResolution = when (val result = gateway.loadCart(persisted.id)) {
    is StorefrontResult.Failure -> persistence.handleRemoteFailure(result.error)

    is StorefrontResult.Success ->
        persistence.persistResult(persisted, result.value, CartOwnership.ANONYMOUS)
}

private class CartPersistence(store: CartSessionStore, private val clock: Clock, private val cartLifetime: Duration) {
    private val safeStore = SafeCartSessionStore(store)

    suspend fun readActive(): ActiveCartRead = when (val read = safeStore.read()) {
        StoredCartRead.Empty -> ActiveCartRead.Empty

        StoredCartRead.Failed -> ActiveCartRead.Failed

        is StoredCartRead.Present -> {
            if (!read.cart.expiresAt.isAfter(clock.instant())) {
                safeStore.clear()
                ActiveCartRead.Expired
            } else {
                ActiveCartRead.Present(read.cart)
            }
        }
    }

    suspend fun persistNew(
        cart: CartReference,
        expectedOwnership: CartOwnership,
        expectedCustomer: SensitiveCustomerId?
    ): CartSessionResolution {
        val ownershipMatches = cart.customerId == expectedCustomer
        val persistedOwnership = if (ownershipMatches) expectedOwnership else CartOwnership.QUARANTINED
        val persisted =
            PersistedCart(
                id = cart.id,
                expiresAt = clock.instant().plus(cartLifetime),
                ownership = persistedOwnership,
                customerId = expectedCustomer
            )
        return if (safeStore.write(persisted)) {
            if (ownershipMatches) {
                CartSessionResolution.Active(cart, expectedOwnership)
            } else {
                CartSessionResolution.Restricted(CartOwnership.QUARANTINED)
            }
        } else {
            CartSessionResolution.Failed(StorefrontFailure.SecurePersistence, !safeStore.clear())
        }
    }

    suspend fun persistResult(
        persisted: PersistedCart,
        cart: CartReference,
        expectedOwnership: CartOwnership,
        expectedCustomer: SensitiveCustomerId? = null
    ): CartSessionResolution {
        val result =
            if (cart.id != persisted.id) {
                restrict(persisted, CartOwnership.QUARANTINED)
            } else {
                persistMatchingCart(persisted, cart, expectedOwnership, expectedCustomer)
            }
        return result
    }

    suspend fun handleRemoteFailure(error: StorefrontFailure): CartSessionResolution =
        if (error is StorefrontFailure.InvalidCart) {
            safeStore.clear()
            CartSessionResolution.Expired
        } else {
            CartSessionResolution.Failed(error, persistedCartRetained = true)
        }

    suspend fun write(cart: PersistedCart): Boolean = safeStore.write(cart)

    suspend fun restrict(cart: PersistedCart, ownership: CartOwnership): CartSessionResolution =
        if (write(cart.copy(ownership = ownership))) {
            CartSessionResolution.Restricted(ownership)
        } else {
            CartSessionResolution.Failed(StorefrontFailure.SecurePersistence, true)
        }

    suspend fun clear(): Boolean = safeStore.clear()

    suspend fun clearIfMatches(expectedCartId: SensitiveCartId): CartCompletionResolution =
        when (val stored = safeStore.read()) {
            StoredCartRead.Empty -> CartCompletionResolution.ALREADY_ABSENT

            StoredCartRead.Failed -> CartCompletionResolution.SECURE_PERSISTENCE_FAILED

            is StoredCartRead.Present ->
                when {
                    stored.cart.id != expectedCartId -> CartCompletionResolution.DIFFERENT_CART
                    safeStore.clear() -> CartCompletionResolution.CLEARED
                    else -> CartCompletionResolution.SECURE_PERSISTENCE_FAILED
                }
        }

    private suspend fun persistMatchingCart(
        persisted: PersistedCart,
        cart: CartReference,
        expectedOwnership: CartOwnership,
        expectedCustomer: SensitiveCustomerId?
    ): CartSessionResolution {
        val associationMatches = cart.customerId == expectedCustomer
        val ownership = if (associationMatches) expectedOwnership else CartOwnership.QUARANTINED
        return if (!safeStore.write(persisted.copy(ownership = ownership, customerId = expectedCustomer))) {
            CartSessionResolution.Failed(StorefrontFailure.SecurePersistence, true)
        } else if (associationMatches) {
            CartSessionResolution.Active(cart, ownership)
        } else {
            CartSessionResolution.Restricted(CartOwnership.QUARANTINED)
        }
    }
}

private class SafeCartSessionStore(private val store: CartSessionStore) {
    suspend fun read(): StoredCartRead = try {
        store.read()?.let(StoredCartRead::Present) ?: StoredCartRead.Empty
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Exception) {
        StoredCartRead.Failed
    }

    suspend fun write(cart: PersistedCart): Boolean = try {
        store.write(cart)
        true
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Exception) {
        false
    }

    suspend fun clear(): Boolean = try {
        store.clear()
        true
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Exception) {
        false
    }
}

private sealed interface StoredCartRead {
    data class Present(val cart: PersistedCart) : StoredCartRead

    data object Empty : StoredCartRead

    data object Failed : StoredCartRead
}

private sealed interface ActiveCartRead {
    data class Present(val cart: PersistedCart) : ActiveCartRead

    data object Empty : ActiveCartRead

    data object Expired : ActiveCartRead

    data object Failed : ActiveCartRead
}
