package com.gurbakir.mobile.accountdeletion

import com.gurbakir.mobile.account.AccountController
import com.gurbakir.mobile.account.AccountFailure
import com.gurbakir.mobile.account.AccountNotice
import com.gurbakir.mobile.account.AccountPreparation
import com.gurbakir.mobile.account.AccountResult
import com.gurbakir.mobile.account.AccountSummary
import com.gurbakir.mobile.cart.CartActionResult
import com.gurbakir.mobile.cart.CartCheckoutResolution
import com.gurbakir.mobile.cart.CartRepository
import com.gurbakir.mobile.cart.CartState
import com.gurbakir.mobile.search.SearchHistoryRepository
import com.gurbakir.mobile.search.SearchHistoryState
import com.gurbakir.mobile.wishlist.WishlistLoadResult
import com.gurbakir.mobile.wishlist.WishlistMembershipState
import com.gurbakir.mobile.wishlist.WishlistMutationResult
import com.gurbakir.mobile.wishlist.WishlistRepository
import com.gurbakir.storefront.SensitiveCartLineId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AccountDeletionControllerTest {
    @Test
    fun `session check fails closed when a transient failure retains private state`() = runTest {
        val controller =
            controller(
                account =
                    FakeAccountController(
                        restoreResult =
                            AccountResult.Failed(
                                reason = AccountFailure.IDENTITY_TRANSPORT,
                                retryable = true,
                                sessionRetained = true
                            )
                    )
            )

        assertEquals(AccountDeletionSessionCheck.UNAVAILABLE, controller.checkSession())
    }

    @Test
    fun `selected local classes report independently and wishlist stays untouched when not selected`() = runTest {
        val account =
            FakeAccountController(
                logoutResult =
                    AccountResult.SignedOut(setOf(AccountNotice.REMOTE_LOGOUT_UNVERIFIED))
            )
        val search = FakeSearchRepository(storageAvailable = false)
        val wishlist = FakeWishlistRepository()
        val cart = FakeCartRepository(CartActionResult.Completed)
        val controller = controller(account, search, wishlist, cart)

        val result =
            controller.clearLocalDataAndSignOut(
                AccountDeletionClearPlan(
                    clearSearchHistory = true,
                    clearWishlist = false,
                    discardCart = true
                )
            )

        assertEquals(AccountDeletionClearOutcome.CLEARED, result.session)
        assertEquals(AccountDeletionClearOutcome.FAILED, result.searchHistory)
        assertEquals(AccountDeletionClearOutcome.NOT_SELECTED, result.wishlist)
        assertEquals(AccountDeletionClearOutcome.CLEARED, result.cart)
        assertEquals(true, result.remoteLogoutUnverified)
        assertEquals(0, wishlist.clearCount)
        assertEquals(1, search.clearCount)
        assertEquals(1, cart.discardCount)
    }

    @Test
    fun `an exception in one local class does not skip the remaining selected cleanup`() = runTest {
        val search = FakeSearchRepository(throwOnClear = true)
        val wishlist = FakeWishlistRepository(WishlistMutationResult.Success)
        val cart = FakeCartRepository(CartActionResult.Restricted)
        val controller = controller(search = search, wishlist = wishlist, cart = cart)

        val result =
            controller.clearLocalDataAndSignOut(
                AccountDeletionClearPlan(
                    clearSearchHistory = true,
                    clearWishlist = true,
                    discardCart = true
                )
            )

        assertEquals(AccountDeletionClearOutcome.FAILED, result.searchHistory)
        assertEquals(AccountDeletionClearOutcome.CLEARED, result.wishlist)
        assertEquals(AccountDeletionClearOutcome.FAILED, result.cart)
        assertEquals(1, wishlist.clearCount)
        assertEquals(1, cart.discardCount)
    }

    private fun controller(
        account: FakeAccountController = FakeAccountController(),
        search: FakeSearchRepository = FakeSearchRepository(),
        wishlist: FakeWishlistRepository = FakeWishlistRepository(),
        cart: FakeCartRepository = FakeCartRepository()
    ) = DefaultAccountDeletionController(account, search, wishlist, cart)

    private class FakeAccountController(
        private val restoreResult: AccountResult = AccountResult.Authenticated(AccountSummary("Customer")),
        private val logoutResult: AccountResult = AccountResult.SignedOut()
    ) : AccountController {
        override suspend fun restore(): AccountResult = restoreResult

        override suspend fun prepareAuthorization(): AccountPreparation =
            AccountPreparation.Failed(AccountFailure.DISCOVERY)

        override suspend fun consumeCallback(rawRedirectUri: String): AccountResult = AccountResult.Cancelled

        override fun cancelAuthorization() = Unit

        override suspend fun refresh(): AccountResult = restoreResult

        override suspend fun logout(): AccountResult = logoutResult
    }

    private class FakeSearchRepository(
        private val storageAvailable: Boolean = true,
        private val throwOnClear: Boolean = false
    ) : SearchHistoryRepository {
        var clearCount = 0

        override suspend fun load(): SearchHistoryState = state()

        override suspend fun record(displayQuery: String): SearchHistoryState = state()

        override suspend fun remove(normalizedQuery: String): SearchHistoryState = state()

        override suspend fun clear(): SearchHistoryState {
            clearCount += 1
            if (throwOnClear) error("synthetic storage failure")
            return state()
        }

        override suspend fun setEnabled(enabled: Boolean): SearchHistoryState = state()

        private fun state() =
            SearchHistoryState(enabled = true, entries = emptyList(), storageAvailable = storageAvailable)
    }

    private class FakeWishlistRepository(
        private val clearResult: WishlistMutationResult = WishlistMutationResult.Success
    ) : WishlistRepository {
        var clearCount = 0

        override fun observeMembership(): Flow<WishlistMembershipState> =
            flowOf(WishlistMembershipState.Available(emptySet()))

        override suspend fun setSaved(productId: String, saved: Boolean): WishlistMutationResult =
            WishlistMutationResult.Success

        override suspend fun load(forceRefresh: Boolean): WishlistLoadResult = WishlistLoadResult.Content(emptyList())

        override suspend fun clear(): WishlistMutationResult {
            clearCount += 1
            return clearResult
        }
    }

    private class FakeCartRepository(private val discardResult: CartActionResult = CartActionResult.Completed) :
        CartRepository {
        override val state = MutableStateFlow(CartState())
        var discardCount = 0

        override suspend fun refresh() = Unit

        override suspend fun add(merchandiseId: String, quantity: Int): CartActionResult = CartActionResult.Completed

        override suspend fun update(lineId: SensitiveCartLineId, quantity: Int): CartActionResult =
            CartActionResult.Completed

        override suspend fun remove(lineId: SensitiveCartLineId): CartActionResult = CartActionResult.Completed

        override suspend fun discard(): CartActionResult {
            discardCount += 1
            return discardResult
        }

        override suspend fun prepareCheckout(): CartCheckoutResolution = CartCheckoutResolution.Empty
    }
}
