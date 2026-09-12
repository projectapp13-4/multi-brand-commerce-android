package com.gurbakir.mobile.accountdeletion

import com.gurbakir.mobile.account.AccountController
import com.gurbakir.mobile.account.AccountNotice
import com.gurbakir.mobile.account.AccountResult
import com.gurbakir.mobile.cart.CartActionResult
import com.gurbakir.mobile.cart.CartRepository
import com.gurbakir.mobile.search.SearchHistoryRepository
import com.gurbakir.mobile.wishlist.WishlistMutationResult
import com.gurbakir.mobile.wishlist.WishlistRepository
import java.util.concurrent.CancellationException
import javax.inject.Inject

enum class AccountDeletionSessionCheck {
    READY,
    SIGNED_OUT,
    UNAVAILABLE
}

data class AccountDeletionClearPlan(
    val clearSearchHistory: Boolean,
    val clearWishlist: Boolean,
    val discardCart: Boolean
)

enum class AccountDeletionClearOutcome {
    NOT_SELECTED,
    CLEARED,
    FAILED
}

data class AccountDeletionLocalResult(
    val session: AccountDeletionClearOutcome,
    val searchHistory: AccountDeletionClearOutcome,
    val wishlist: AccountDeletionClearOutcome,
    val cart: AccountDeletionClearOutcome,
    val remoteLogoutUnverified: Boolean
)

interface AccountDeletionController {
    suspend fun checkSession(): AccountDeletionSessionCheck

    suspend fun clearLocalDataAndSignOut(plan: AccountDeletionClearPlan): AccountDeletionLocalResult
}

class DefaultAccountDeletionController
@Inject
constructor(
    private val accountController: AccountController,
    private val searchHistoryRepository: SearchHistoryRepository,
    private val wishlistRepository: WishlistRepository,
    private val cartRepository: CartRepository
) : AccountDeletionController {
    override suspend fun checkSession(): AccountDeletionSessionCheck =
        when (val result = safely { accountController.restore() }) {
            is SafeCall.Value ->
                when (val account = result.value) {
                    is AccountResult.Authenticated -> AccountDeletionSessionCheck.READY

                    is AccountResult.Failed ->
                        if (account.sessionRetained) {
                            AccountDeletionSessionCheck.UNAVAILABLE
                        } else {
                            AccountDeletionSessionCheck.SIGNED_OUT
                        }

                    AccountResult.Cancelled,
                    is AccountResult.SignedOut -> AccountDeletionSessionCheck.SIGNED_OUT
                }

            SafeCall.Failed -> AccountDeletionSessionCheck.UNAVAILABLE
        }

    override suspend fun clearLocalDataAndSignOut(plan: AccountDeletionClearPlan): AccountDeletionLocalResult {
        val logout = safely { accountController.logout() }
        val account = (logout as? SafeCall.Value)?.value
        return AccountDeletionLocalResult(
            session =
                if (account is AccountResult.SignedOut) {
                    AccountDeletionClearOutcome.CLEARED
                } else {
                    AccountDeletionClearOutcome.FAILED
                },
            searchHistory =
                selectedOutcome(plan.clearSearchHistory) {
                    searchHistoryRepository.clear().storageAvailable
                },
            wishlist =
                selectedOutcome(plan.clearWishlist) {
                    wishlistRepository.clear() == WishlistMutationResult.Success
                },
            cart =
                selectedOutcome(plan.discardCart) {
                    cartRepository.discard() == CartActionResult.Completed
                },
            remoteLogoutUnverified =
                (account as? AccountResult.SignedOut)
                    ?.notices
                    ?.contains(AccountNotice.REMOTE_LOGOUT_UNVERIFIED) == true
        )
    }

    private suspend fun selectedOutcome(
        selected: Boolean,
        operation: suspend () -> Boolean
    ): AccountDeletionClearOutcome {
        if (!selected) return AccountDeletionClearOutcome.NOT_SELECTED
        return when (val result = safely(operation)) {
            is SafeCall.Value ->
                if (result.value) {
                    AccountDeletionClearOutcome.CLEARED
                } else {
                    AccountDeletionClearOutcome.FAILED
                }

            SafeCall.Failed -> AccountDeletionClearOutcome.FAILED
        }
    }
}

private sealed interface SafeCall<out T> {
    data class Value<T>(val value: T) : SafeCall<T>

    data object Failed : SafeCall<Nothing>
}

private suspend fun <T> safely(operation: suspend () -> T): SafeCall<T> = try {
    SafeCall.Value(operation())
} catch (cancellation: CancellationException) {
    throw cancellation
} catch (_: Exception) {
    SafeCall.Failed
}
