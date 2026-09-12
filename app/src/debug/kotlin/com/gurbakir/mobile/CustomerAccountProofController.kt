package com.gurbakir.mobile

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

interface CustomerAccountProofController {
    suspend fun restore(): CustomerAccountProofResult

    suspend fun prepareAuthorization(): CustomerAccountProofPreparation

    suspend fun consumeCallback(rawRedirectUri: String): CustomerAccountProofResult

    fun cancelAuthorization()

    suspend fun refresh(): CustomerAccountProofResult

    suspend fun logout(): CustomerAccountProofResult
}

sealed interface CustomerAccountProofPreparation {
    data class Ready(val plan: CustomerAccountAuthorizationPlan) : CustomerAccountProofPreparation {
        override fun toString(): String = "Ready(<redacted>)"
    }

    data class Failed(val reason: CustomerAccountProofFailure) : CustomerAccountProofPreparation
}

sealed interface CustomerAccountProofResult {
    data object Authenticated : CustomerAccountProofResult

    data object SignedOut : CustomerAccountProofResult

    data object Cancelled : CustomerAccountProofResult

    data class Failed(val reason: CustomerAccountProofFailure) : CustomerAccountProofResult
}

enum class CustomerAccountProofFailure {
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
    IDENTITY_TRANSPORT,
    REMOTE_LOGOUT
}

class DefaultCustomerAccountProofController(
    private val authorizationCoordinator: CustomerAccountAuthorizationCoordinator,
    private val sessionCoordinator: CustomerAccountSessionCoordinator,
    private val gateway: CustomerAccountGateway
) : CustomerAccountProofController {
    override suspend fun restore(): CustomerAccountProofResult = sessionCoordinator.restore().toProofResult()

    override suspend fun prepareAuthorization(): CustomerAccountProofPreparation =
        when (val preparation = authorizationCoordinator.prepare()) {
            is CustomerAccountAuthorizationPreparation.Prepared ->
                CustomerAccountProofPreparation.Ready(preparation.plan)

            is CustomerAccountAuthorizationPreparation.Failed ->
                CustomerAccountProofPreparation.Failed(preparation.reason.toProofFailure())
        }

    override suspend fun consumeCallback(rawRedirectUri: String): CustomerAccountProofResult =
        when (val callback = authorizationCoordinator.validateAndConsumeCallback(rawRedirectUri)) {
            is CustomerAccountCallbackResult.Authorized ->
                sessionCoordinator.exchange(callback.grant).toProofResult()

            CustomerAccountCallbackResult.Cancelled -> CustomerAccountProofResult.Cancelled

            CustomerAccountCallbackResult.RejectedExpired,
            CustomerAccountCallbackResult.RejectedMalformed,
            CustomerAccountCallbackResult.RejectedMissingResponse,
            CustomerAccountCallbackResult.RejectedMissingTransaction,
            CustomerAccountCallbackResult.RejectedRoute,
            CustomerAccountCallbackResult.RejectedState ->
                CustomerAccountProofResult.Failed(CustomerAccountProofFailure.CALLBACK)

            else -> CustomerAccountProofResult.Failed(CustomerAccountProofFailure.CALLBACK)
        }

    override fun cancelAuthorization() {
        authorizationCoordinator.cancel()
    }

    override suspend fun refresh(): CustomerAccountProofResult = sessionCoordinator.refresh().toProofResult()

    override suspend fun logout(): CustomerAccountProofResult = when (sessionCoordinator.logout()) {
        CustomerLogoutResolution.Completed,
        CustomerLogoutResolution.SignedOut -> CustomerAccountProofResult.SignedOut

        is CustomerLogoutResolution.RemoteFailed ->
            CustomerAccountProofResult.Failed(CustomerAccountProofFailure.REMOTE_LOGOUT)
    }

    private suspend fun CustomerSessionResolution.toProofResult(): CustomerAccountProofResult = when (this) {
        CustomerSessionResolution.SignedOut -> CustomerAccountProofResult.SignedOut
        is CustomerSessionResolution.Failed -> CustomerAccountProofResult.Failed(reason.toProofFailure())
        is CustomerSessionResolution.Authenticated -> verifyTypedIdentity()
    }

    private suspend fun verifyTypedIdentity(): CustomerAccountProofResult = when (val result = gateway.loadIdentity()) {
        is CustomerAccountResult.Success -> CustomerAccountProofResult.Authenticated
        is CustomerAccountResult.Failure -> CustomerAccountProofResult.Failed(result.reason.toProofFailure())
    }
}

private fun CustomerAccountDiscoveryFailure.toProofFailure(): CustomerAccountProofFailure =
    CustomerAccountProofFailure.DISCOVERY

private fun CustomerTokenFailure.toProofFailure(): CustomerAccountProofFailure = when (this) {
    CustomerTokenFailure.Rejected -> CustomerAccountProofFailure.TOKEN_REJECTED
    CustomerTokenFailure.Transient -> CustomerAccountProofFailure.TOKEN_TRANSIENT
    CustomerTokenFailure.InvalidResponse -> CustomerAccountProofFailure.TOKEN_INVALID
    CustomerTokenFailure.MissingRequiredFields -> CustomerAccountProofFailure.TOKEN_MISSING_FIELDS
    CustomerTokenFailure.UnsupportedTokenType -> CustomerAccountProofFailure.TOKEN_TYPE
    CustomerTokenFailure.ScopeMismatch -> CustomerAccountProofFailure.TOKEN_SCOPE
    CustomerTokenFailure.ExpiredAccessToken -> CustomerAccountProofFailure.TOKEN_EXPIRED
    CustomerTokenFailure.InvalidIdToken -> CustomerAccountProofFailure.TOKEN_IDENTITY
}

private fun CustomerAccountFailure.toProofFailure(): CustomerAccountProofFailure = when (this) {
    CustomerAccountFailure.SignedOut,
    is CustomerAccountFailure.Authentication -> CustomerAccountProofFailure.IDENTITY_AUTHENTICATION

    is CustomerAccountFailure.Discovery -> CustomerAccountProofFailure.IDENTITY_DISCOVERY

    is CustomerAccountFailure.GraphQl -> CustomerAccountProofFailure.IDENTITY_GRAPHQL

    is CustomerAccountFailure.Transport -> CustomerAccountProofFailure.IDENTITY_TRANSPORT
}
