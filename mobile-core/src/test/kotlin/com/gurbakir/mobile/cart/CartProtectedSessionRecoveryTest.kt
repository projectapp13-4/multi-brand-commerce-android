package com.gurbakir.mobile.cart

import android.app.Activity
import com.gurbakir.account.oauth.CustomerTokenFailure
import com.gurbakir.account.oauth.CustomerTokenResult
import com.gurbakir.checkout.CheckoutAdapter
import com.gurbakir.checkout.CheckoutResult
import com.gurbakir.mobile.account.PROTECTED_NOW
import com.gurbakir.mobile.account.ProtectedFault
import com.gurbakir.mobile.account.ProtectedSessionControllerFixture
import com.gurbakir.mobile.account.protectedSession
import com.gurbakir.mobile.checkout.CheckoutController
import com.gurbakir.mobile.checkout.CheckoutFailureCategory
import com.gurbakir.mobile.checkout.CoordinatedCheckoutCartCompleter
import com.gurbakir.storefront.CartCoordinator
import com.gurbakir.storefront.CartLineInput
import com.gurbakir.storefront.CartLineUpdate
import com.gurbakir.storefront.CartOwnership
import com.gurbakir.storefront.CartReference
import com.gurbakir.storefront.CartSessionStore
import com.gurbakir.storefront.CatalogPage
import com.gurbakir.storefront.Cursor
import com.gurbakir.storefront.PersistedCart
import com.gurbakir.storefront.SensitiveBuyerAccessToken
import com.gurbakir.storefront.SensitiveCartId
import com.gurbakir.storefront.SensitiveCartLineId
import com.gurbakir.storefront.SensitiveCustomerId
import com.gurbakir.storefront.ShopSummary
import com.gurbakir.storefront.StorefrontFailure
import com.gurbakir.storefront.StorefrontGateway
import com.gurbakir.storefront.StorefrontResult
import java.net.URI
import java.time.Clock
import java.time.ZoneOffset
import java.util.concurrent.CancellationException
import javax.inject.Provider
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.mockwebserver.MockResponse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory

/** Customer I/O faults originate in the real coordinator; cart ownership and publication remain production code. */
class CartProtectedSessionRecoveryTest {
    @TestFactory
    fun `customer storage faults hide prior summaries and keep a dedicated recovery cause`(): List<DynamicTest> =
        listOf(CartOwnership.ANONYMOUS, CartOwnership.CUSTOMER_ASSOCIATED).flatMap { previous ->
            listOf(
                ProtectedFault.READ,
                ProtectedFault.WRITE_BEFORE_APPLY,
                ProtectedFault.WRITE_AFTER_APPLY
            ).flatMap { fault ->
                CartStorageOperation.entries.map { operation ->
                    DynamicTest.dynamicTest("${previous.name} ${fault.name} ${operation.name}") {
                        runBlocking {
                            SecureCartFixture(previous).use { fixture ->
                                fixture.warm()
                                val original = fixture.store.cart
                                val remoteCalls = fixture.peer.calls
                                val identityRequests = fixture.customer.server.requestCount
                                fixture.customer.store.session = protectedSession()
                                fixture.customer.store.fault = fault
                                fixture.customer.store.faultAtRead = fixture.customer.store.reads + 1
                                val result = fixture.call(operation)
                                assertHidden(fixture.repository)
                                assertEquals(
                                    CartFailureCategory.SECURE_STORAGE,
                                    fixture.repository.state.value.failure?.category
                                )
                                assertTrue(fixture.repository.state.value.failure?.retryable == true)
                                assertTrue(fixture.repository.state.value.failure?.cartRetained == true)
                                when (operation) {
                                    CartStorageOperation.PREPARE -> {
                                        assertInstanceOf(CartCheckoutResolution.Failed::class.java, result)
                                        assertEquals(
                                            CartFailureCategory.SECURE_STORAGE,
                                            (result as CartCheckoutResolution.Failed).failure.category
                                        )
                                    }

                                    CartStorageOperation.REFRESH -> Unit

                                    else -> assertInstanceOf(CartActionResult.Failed::class.java, result)
                                }
                                assertEquals(original, fixture.store.cart, "Exact protected handle and owner survive")
                                assertEquals(remoteCalls, fixture.peer.calls)
                                assertEquals(identityRequests, fixture.customer.server.requestCount)
                                assertEquals(0, fixture.peer.merchandiseMutations)
                            }
                        }
                    }
                }
            }
        }

    @Test
    fun `real storage restriction becomes nonbusy secure checkout failure without SDK launch`() = runBlocking {
        SecureCartFixture(CartOwnership.CUSTOMER_ASSOCIATED).use { fixture ->
            fixture.warm()
            fixture.customer.store.fault = ProtectedFault.READ
            fixture.customer.store.faultAtRead = fixture.customer.store.reads + 1
            val adapter = NoLaunchAdapter()
            val checkout =
                CheckoutController(fixture.repository, CoordinatedCheckoutCartCompleter(fixture.coordinator), adapter)
            assertNull(checkout.prepare())
            assertFalse(checkout.state.value.busy)
            assertEquals(CheckoutFailureCategory.SECURE_STORAGE, checkout.state.value.failure?.category)
            assertTrue(checkout.state.value.cartRetained)
            assertHidden(fixture.repository)
            assertEquals(0, adapter.launches)
        }
    }

    @Test
    fun `cancelled fresh read preserves previously established storage restriction`() = runBlocking {
        SecureCartFixture(CartOwnership.CUSTOMER_ASSOCIATED).use { fixture ->
            fixture.warm()
            val retained = fixture.store.cart
            fixture.customer.store.fault = ProtectedFault.READ
            fixture.customer.store.faultAtRead = fixture.customer.store.reads + 1
            fixture.repository.refresh()
            assertHidden(fixture.repository)
            fixture.customer.store.fault = null
            val gate = CompletableDeferred<Unit>()
            val entered = CompletableDeferred<Unit>()
            fixture.customer.store.readGate = gate
            fixture.customer.store.readEntered = entered
            val retry = async { runCatching { fixture.repository.refresh() } }
            withTimeout(5000) { entered.await() }
            val cancellation = CancellationException("synthetic secure retry interrupted")
            fixture.customer.store.fault = ProtectedFault.READ
            fixture.customer.store.faultAtRead = fixture.customer.store.reads
            fixture.customer.store.failure = cancellation
            gate.complete(Unit)
            assertSame(cancellation, retry.await().exceptionOrNull())
            assertHidden(fixture.repository)
            assertEquals(CartFailureCategory.SECURE_STORAGE, fixture.repository.state.value.failure?.category)
            assertEquals(retained, fixture.store.cart)
            assertEquals(0, fixture.peer.merchandiseMutations)
        }
    }

    @Test
    fun `lost add response followed by storage fault keeps the fresh restricted cause`() = runBlocking {
        SecureCartFixture(CartOwnership.CUSTOMER_ASSOCIATED).use { fixture ->
            fixture.warm()
            val retained = fixture.store.cart
            val previousReads = fixture.customer.store.reads
            val previousCalls = fixture.peer.calls
            val previousIdentityRequests = fixture.customer.server.requestCount
            fixture.enqueueIdentity()
            fixture.peer.onLostAddResponse = {
                fixture.customer.store.fault = ProtectedFault.READ
                fixture.customer.store.faultAtRead = fixture.customer.store.reads + 1
            }

            val result = fixture.repository.add(LIFECYCLE_VARIANT, 1)

            assertEquals(1, fixture.peer.merchandiseMutations, "One sent attempt; no merchandise replay")
            assertEquals(previousCalls + 3, fixture.peer.calls, "Checked load/rebind precede the single add")
            assertEquals(previousReads + 2, fixture.customer.store.reads, "The reconciliation acquires a fresh lease")
            assertEquals(previousIdentityRequests + 1, fixture.customer.server.requestCount)
            assertEquals(retained, fixture.store.cart, "Exact protected handle and owner survive")
            assertHidden(fixture.repository)
            val failure = assertInstanceOf(CartActionResult.Failed::class.java, result).failure
            assertEquals(CartFailureCategory.SECURE_STORAGE, failure.category)
            assertTrue(failure.retryable)
            assertTrue(failure.cartRetained)
            assertEquals(failure, fixture.repository.state.value.failure)
        }
    }

    @Test
    fun `ordinary transient ownership restriction keeps its existing generic result`() = runBlocking {
        SecureCartFixture(CartOwnership.CUSTOMER_ASSOCIATED).use { fixture ->
            fixture.warm()
            fixture.customer.store.session = protectedSession().copy(expiresAt = PROTECTED_NOW.minusSeconds(1))
            fixture.customer.refreshResult = CustomerTokenResult.Failure(CustomerTokenFailure.Transient)
            assertEquals(CartActionResult.Restricted, fixture.repository.add(LIFECYCLE_VARIANT, 1))
            assertEquals(CartCheckoutResolution.Restricted, fixture.repository.prepareCheckout())
            assertHidden(fixture.repository)
            assertNull(fixture.repository.state.value.failure)
            assertEquals(0, fixture.peer.merchandiseMutations)
        }
    }

    @Test
    fun `known anonymous cart storage failure retains its intentionally visible summary`() = runBlocking {
        SecureCartFixture(CartOwnership.ANONYMOUS).use { fixture ->
            fixture.warm()
            val retained = fixture.store.cart
            fixture.store.readFailure = IllegalStateException("synthetic cart record unreadable")
            fixture.repository.refresh()
            assertEquals(CartStatus.ACTIVE, fixture.repository.state.value.status)
            assertEquals(CartOwnership.ANONYMOUS, fixture.repository.state.value.ownership)
            assertEquals(2, fixture.repository.state.value.badgeQuantity)
            assertEquals(CartFailureCategory.SECURE_STORAGE, fixture.repository.state.value.failure?.category)
            assertEquals(retained, fixture.store.cart)
            assertNull(fixture.repository.state.value.mutation)
        }
    }

    private fun assertHidden(repository: DefaultCartRepository) {
        assertEquals(CartStatus.RESTRICTED, repository.state.value.status)
        assertEquals(CartOwnership.VERIFY_PENDING, repository.state.value.ownership)
        assertNull(repository.state.value.cart)
        assertEquals(0, repository.state.value.badgeQuantity)
        assertNull(repository.state.value.mutation)
        assertNull(repository.state.value.adjustment)
    }
}

private enum class CartStorageOperation { REFRESH, ADD, UPDATE, REMOVE, PREPARE }

private class SecureCartFixture(private val ownership: CartOwnership) : AutoCloseable {
    val customer = ProtectedSessionControllerFixture()
    val store = SecureCartStore(
        PersistedCart(
            lifecycleCartId(),
            PROTECTED_NOW.plusSeconds(3600),
            ownership,
            if (ownership == CartOwnership.CUSTOMER_ASSOCIATED) SensitiveCustomerId.from(LIFECYCLE_CUSTOMER) else null
        )
    )
    val peer = SecureCartPeer(lifecycleActive(2, ownership).cart)
    val coordinator = CartCoordinator(peer, store, Clock.fixed(PROTECTED_NOW, ZoneOffset.UTC))
    val repository =
        DefaultCartRepository(CoordinatedCartOperations(coordinator, customer.sessions, Provider { customer.identity }))

    suspend fun warm() {
        if (ownership == CartOwnership.ANONYMOUS) {
            customer.store.session = null
        } else {
            enqueueIdentity()
        }
        repository.refresh()
        assertEquals(CartStatus.ACTIVE, repository.state.value.status)
        assertEquals(ownership, repository.state.value.ownership)
        assertEquals(2, repository.state.value.badgeQuantity)
    }

    fun enqueueIdentity() {
        customer.server.enqueue(
            MockResponse().setBody(
                """{"data":{"customer":{"id":"$LIFECYCLE_CUSTOMER","displayName":"Synthetic"}}}"""
            )
        )
    }

    suspend fun call(operation: CartStorageOperation): Any = when (operation) {
        CartStorageOperation.REFRESH -> repository.refresh()
        CartStorageOperation.ADD -> repository.add(LIFECYCLE_VARIANT, 1)
        CartStorageOperation.UPDATE -> repository.update(lifecycleLineId(), 3)
        CartStorageOperation.REMOVE -> repository.remove(lifecycleLineId())
        CartStorageOperation.PREPARE -> repository.prepareCheckout()
    }

    override fun close() = customer.close()
}

private class SecureCartStore(var cart: PersistedCart?) : CartSessionStore {
    var readFailure: Exception? = null
    override suspend fun read(): PersistedCart? {
        readFailure?.let { throw it }
        return cart
    }
    override suspend fun write(cart: PersistedCart) {
        this.cart = cart
    }
    override suspend fun clear() {
        cart = null
    }
}

/** Only the remote peer is synthetic. Every ownership read/rebind/planner decision is production code. */
private class SecureCartPeer(private val remote: CartReference) : StorefrontGateway {
    var calls = 0
    var merchandiseMutations = 0
    var onLostAddResponse: (() -> Unit)? = null
    override suspend fun loadCart(cartId: SensitiveCartId): StorefrontResult<CartReference> {
        calls++
        assertEquals(remote.id, cartId)
        return StorefrontResult.Success(remote)
    }
    override suspend fun updateBuyerIdentity(
        cartId: SensitiveCartId,
        buyerAccessToken: SensitiveBuyerAccessToken?
    ): StorefrontResult<CartReference> {
        calls++
        assertEquals(remote.id, cartId)
        return StorefrontResult.Success(remote)
    }
    override suspend fun createCart(
        lines: List<CartLineInput>,
        buyerAccessToken: SensitiveBuyerAccessToken?
    ): StorefrontResult<CartReference> = unexpectedMutation()
    override suspend fun addCartLines(
        cartId: SensitiveCartId,
        lines: List<CartLineInput>
    ): StorefrontResult<CartReference> {
        val lostResponse = onLostAddResponse ?: return unexpectedMutation()
        calls++
        merchandiseMutations++
        assertEquals(remote.id, cartId)
        assertEquals(listOf(CartLineInput(LIFECYCLE_VARIANT, 1)), lines)
        lostResponse()
        // Only the response is modeled; a live Shopify commit/dedup outcome is outside this fixture.
        return StorefrontResult.Failure(StorefrontFailure.Transport(retryable = true))
    }
    override suspend fun updateCartLines(
        cartId: SensitiveCartId,
        lines: List<CartLineUpdate>
    ): StorefrontResult<CartReference> = unexpectedMutation()
    override suspend fun removeCartLines(
        cartId: SensitiveCartId,
        lineIds: List<SensitiveCartLineId>
    ): StorefrontResult<CartReference> = unexpectedMutation()
    override suspend fun loadShopSummary(): StorefrontResult<ShopSummary> = error("No shop read")
    override suspend fun loadCatalogPage(after: Cursor?): StorefrontResult<CatalogPage> = error("No catalog read")
    private fun unexpectedMutation(): Nothing {
        merchandiseMutations++
        error("Unverified customer storage must not reach a merchandise mutation")
    }
}

private class NoLaunchAdapter : CheckoutAdapter {
    var launches = 0
    override suspend fun preload(activity: Activity, checkoutUrl: URI): CheckoutResult = error("No preload")
    override suspend fun present(activity: Activity, checkoutUrl: URI): CheckoutResult {
        launches++
        error("No checkout presentation")
    }
    override fun invalidate() = Unit
}
