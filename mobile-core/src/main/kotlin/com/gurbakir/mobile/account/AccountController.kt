package com.gurbakir.mobile.account

import com.gurbakir.account.CustomerAccountFailure
import com.gurbakir.account.CustomerAccountGateway
import com.gurbakir.account.CustomerAccountResult
import com.gurbakir.account.oauth.CustomerAccountAuthorizationCoordinator
import com.gurbakir.account.oauth.CustomerAccountAuthorizationPlan
import com.gurbakir.account.oauth.CustomerAccountAuthorizationPreparation
import com.gurbakir.account.oauth.CustomerAccountCallbackResult
import com.gurbakir.account.oauth.CustomerAccountDiscoveryFailure
import com.gurbakir.account.oauth.CustomerTokenFailure
import com.gurbakir.account.session.CustomerAccountSessionCoordinator
import com.gurbakir.account.session.CustomerLogoutResolution
import com.gurbakir.account.session.CustomerSessionResolution
import com.gurbakir.account.session.CustomerSessionStorageException
import com.gurbakir.mobile.cart.CartRepository
import com.gurbakir.mobile.cart.CartStatus
import java.util.concurrent.CancellationException
import javax.inject.Inject
import javax.inject.Singleton

data class AccountSummary(val displayName: String)

enum class AccountNotice {
    AUTHORIZATION_CANCELLED,
    SESSION_EXPIRED,
    REMOTE_LOGOUT_UNVERIFIED,
    CART_PROTECTED,
    CART_RECONCILIATION_FAILED
}

enum class AccountFailure {
    SECURE_STORAGE,
    DISCOVERY,
    CALLBACK,
    TOKEN_REJECTED,
    TOKEN_TRANSIENT,
    TOKEN_INVALID,
    TOKEN_MISSING_FIELDS,
    TOKEN_TYPE,
    TOKEN_SCOPE,
    TOKEN_EXPIRED,
    TOKEN_IDENTITY,
    IDENTITY_AUTHENTICATION,
    IDENTITY_DISCOVERY,
    IDENTITY_GRAPHQL,
    IDENTITY_TRANSPORT
}

sealed interface AccountPreparation {
    data class Ready(val plan: CustomerAccountAuthorizationPlan) : AccountPreparation {
        override fun toString(): String = "Ready(<redacted>)"
    }

    data class Failed(val reason: AccountFailure) : AccountPreparation
}

sealed interface AccountResult {
    data class Authenticated(val summary: AccountSummary, val notices: Set<AccountNotice> = emptySet()) : AccountResult

    data class SignedOut(val notices: Set<AccountNotice> = emptySet()) : AccountResult

    data object Cancelled : AccountResult

    data class Failed(val reason: AccountFailure, val retryable: Boolean, val sessionRetained: Boolean) : AccountResult
}

interface AccountController {
    suspend fun restore(): AccountResult

    suspend fun prepareAuthorization(): AccountPreparation

    suspend fun consumeCallback(rawRedirectUri: String): AccountResult

    fun cancelAuthorization()

    suspend fun refresh(): AccountResult

    suspend fun logout(): AccountResult
}

@Singleton
class DefaultAccountController
@Inject
constructor(
    private val authorizationCoordinator: CustomerAccountAuthorizationCoordinator,
    private val sessionCoordinator: CustomerAccountSessionCoordinator,
    private val gateway: CustomerAccountGateway,
    private val cartRepository: CartRepository
) : AccountController {
    override suspend fun restore(): AccountResult =
        withStorageFailure { resolveSession(sessionCoordinator.restore(), terminalSessionMeansSignedOut = true) }

    override suspend fun prepareAuthorization(): AccountPreparation =
        when (val preparation = authorizationCoordinator.prepare()) {
            is CustomerAccountAuthorizationPreparation.Prepared ->
                AccountPreparation.Ready(preparation.plan)

            is CustomerAccountAuthorizationPreparation.Failed ->
                AccountPreparation.Failed(preparation.reason.toAccountFailure())
        }

    override suspend fun consumeCallback(rawRedirectUri: String): AccountResult = withStorageFailure {
        when (val callback = authorizationCoordinator.validateAndConsumeCallback(rawRedirectUri)) {
            is CustomerAccountCallbackResult.Authorized ->
                resolveSession(
                    sessionCoordinator.exchange(callback.grant),
                    terminalSessionMeansSignedOut = false
                )

            CustomerAccountCallbackResult.Cancelled -> AccountResult.Cancelled

            CustomerAccountCallbackResult.RejectedExpired,
            CustomerAccountCallbackResult.RejectedMalformed,
            CustomerAccountCallbackResult.RejectedMissingResponse,
            CustomerAccountCallbackResult.RejectedMissingTransaction,
            CustomerAccountCallbackResult.RejectedRoute,
            CustomerAccountCallbackResult.RejectedState ->
                AccountResult.Failed(AccountFailure.CALLBACK, retryable = true, sessionRetained = false)

            else -> AccountResult.Failed(AccountFailure.CALLBACK, retryable = true, sessionRetained = false)
        }
    }

    override fun cancelAuthorization() {
        authorizationCoordinator.cancel()
    }

    override suspend fun refresh(): AccountResult =
        withStorageFailure { resolveSession(sessionCoordinator.refresh(), terminalSessionMeansSignedOut = true) }

    override suspend fun logout(): AccountResult = withStorageFailure {
        val logout = sessionCoordinator.logout()
        val notices = reconcileCartForCurrentSession().toMutableSet()
        if (logout is CustomerLogoutResolution.RemoteFailed) {
            notices += AccountNotice.REMOTE_LOGOUT_UNVERIFIED
        }
        AccountResult.SignedOut(notices)
    }

    private suspend fun resolveSession(
        resolution: CustomerSessionResolution,
        terminalSessionMeansSignedOut: Boolean
    ): AccountResult = when (resolution) {
        is CustomerSessionResolution.Authenticated -> loadIdentity()

        CustomerSessionResolution.SignedOut ->
            AccountResult.SignedOut(reconcileCartForCurrentSession())

        is CustomerSessionResolution.Failed ->
            if (terminalSessionMeansSignedOut && !resolution.encryptedSessionRetained) {
                AccountResult.SignedOut(
                    reconcileCartForCurrentSession() + AccountNotice.SESSION_EXPIRED
                )
            } else {
                resolution.reason.toAccountResult(resolution.encryptedSessionRetained)
            }
    }

    private suspend fun loadIdentity(): AccountResult = when (val identity = gateway.loadIdentity()) {
        is CustomerAccountResult.Success ->
            AccountResult.Authenticated(
                summary = AccountSummary(identity.value.displayName),
                notices = reconcileCartForCurrentSession()
            )

        is CustomerAccountResult.Failure -> resolveIdentityFailure(identity.reason)
    }

    private suspend fun resolveIdentityFailure(failure: CustomerAccountFailure): AccountResult = when (failure) {
        CustomerAccountFailure.SignedOut ->
            AccountResult.SignedOut(
                reconcileCartForCurrentSession() + AccountNotice.SESSION_EXPIRED
            )

        is CustomerAccountFailure.Authentication ->
            if (failure.reason == CustomerTokenFailure.Transient) {
                failure.reason.toAccountResult(sessionRetained = true)
            } else {
                sessionCoordinator.clearForLogout()
                AccountResult.SignedOut(
                    reconcileCartForCurrentSession() + AccountNotice.SESSION_EXPIRED
                )
            }

        is CustomerAccountFailure.GraphQl ->
            if (failure.errorCodes.any(TERMINAL_ACCOUNT_ERROR_CODES::contains)) {
                sessionCoordinator.clearForLogout()
                AccountResult.SignedOut(
                    reconcileCartForCurrentSession() + AccountNotice.SESSION_EXPIRED
                )
            } else {
                failure.toAccountResult(sessionRetained = true)
            }

        else -> failure.toAccountResult(sessionRetained = true)
    }

    private suspend fun reconcileCartForCurrentSession(): Set<AccountNotice> = try {
        cartRepository.refresh()
        when (cartRepository.state.value.status) {
            CartStatus.RESTRICTED -> setOf(AccountNotice.CART_PROTECTED)

            CartStatus.ERROR,
            CartStatus.INITIAL,
            CartStatus.LOADING -> setOf(AccountNotice.CART_RECONCILIATION_FAILED)

            CartStatus.EMPTY,
            CartStatus.ACTIVE,
            CartStatus.EXPIRED -> emptySet()
        }
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Exception) {
        setOf(AccountNotice.CART_RECONCILIATION_FAILED)
    }
}

private suspend fun withStorageFailure(action: suspend () -> AccountResult): AccountResult = try {
    action()
} catch (_: CustomerSessionStorageException) {
    // Durable removal is unconfirmed, so callers must continue treating the session as retained.
    AccountResult.Failed(AccountFailure.SECURE_STORAGE, retryable = true, sessionRetained = true)
}

private val TERMINAL_ACCOUNT_ERROR_CODES =
    setOf("UNAUTHENTICATED", "UNAUTHORIZED", "ACCESS_DENIED", "TOKEN_INVALID")

private fun CustomerAccountDiscoveryFailure.toAccountFailure(): AccountFailure = AccountFailure.DISCOVERY

private fun CustomerTokenFailure.toAccountFailure(): AccountFailure = when (this) {
    CustomerTokenFailure.Rejected -> AccountFailure.TOKEN_REJECTED
    CustomerTokenFailure.Transient -> AccountFailure.TOKEN_TRANSIENT
    CustomerTokenFailure.InvalidResponse -> AccountFailure.TOKEN_INVALID
    CustomerTokenFailure.MissingRequiredFields -> AccountFailure.TOKEN_MISSING_FIELDS
    CustomerTokenFailure.UnsupportedTokenType -> AccountFailure.TOKEN_TYPE
    CustomerTokenFailure.ScopeMismatch -> AccountFailure.TOKEN_SCOPE
    CustomerTokenFailure.ExpiredAccessToken -> AccountFailure.TOKEN_EXPIRED
    CustomerTokenFailure.InvalidIdToken -> AccountFailure.TOKEN_IDENTITY
}

private fun CustomerTokenFailure.toAccountResult(sessionRetained: Boolean): AccountResult.Failed = AccountResult.Failed(
    reason = toAccountFailure(),
    retryable = this == CustomerTokenFailure.Transient,
    sessionRetained = sessionRetained
)

private fun CustomerAccountFailure.toAccountFailure(): AccountFailure = when (this) {
    CustomerAccountFailure.SignedOut,
    is CustomerAccountFailure.Authentication -> AccountFailure.IDENTITY_AUTHENTICATION

    is CustomerAccountFailure.Discovery -> AccountFailure.IDENTITY_DISCOVERY

    is CustomerAccountFailure.GraphQl -> AccountFailure.IDENTITY_GRAPHQL

    is CustomerAccountFailure.Transport -> AccountFailure.IDENTITY_TRANSPORT
}

private fun CustomerAccountFailure.toAccountResult(sessionRetained: Boolean): AccountResult.Failed =
    AccountResult.Failed(
        reason = toAccountFailure(),
        retryable =
            (this is CustomerAccountFailure.Transport && retryable) ||
                this is CustomerAccountFailure.Discovery,
        sessionRetained = sessionRetained
    )
