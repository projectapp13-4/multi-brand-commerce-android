package com.gurbakir.mobile.profile

import com.gurbakir.account.CustomerAccountFailure
import com.gurbakir.account.CustomerAccountResult
import com.gurbakir.account.CustomerProfile
import com.gurbakir.account.CustomerProfileField
import com.gurbakir.account.CustomerProfileGateway
import com.gurbakir.account.CustomerProfileUpdate
import com.gurbakir.account.CustomerProfileUpdateResult
import com.gurbakir.account.oauth.CustomerAccountDiscoveryFailure
import com.gurbakir.account.oauth.CustomerTokenFailure
import com.gurbakir.account.session.CustomerAccountSessionCoordinator
import javax.inject.Inject
import javax.inject.Singleton

data class ProfileContent(val firstName: String, val lastName: String) {
    override fun toString(): String = "ProfileContent(<redacted>)"
}

enum class ProfileFailure {
    CONNECTION,
    SERVICE,
    CONFLICT,
    SAVE_UNCONFIRMED
}

sealed interface ProfileResult {
    data class Content(val profile: ProfileContent) : ProfileResult

    data class Rejected(val fields: Set<CustomerProfileField>) : ProfileResult

    data object Conflict : ProfileResult

    data object SignedOut : ProfileResult

    data class Failed(val reason: ProfileFailure) : ProfileResult
}

interface ProfileController {
    suspend fun load(): ProfileResult

    suspend fun save(
        firstName: String?,
        lastName: String?,
        expectedFirstName: String?,
        expectedLastName: String?
    ): ProfileResult
}

@Singleton
class DefaultProfileController
@Inject
constructor(
    private val gateway: CustomerProfileGateway,
    private val sessionCoordinator: CustomerAccountSessionCoordinator
) : ProfileController {
    override suspend fun load(): ProfileResult = when (val result = gateway.loadProfile()) {
        is CustomerAccountResult.Success -> ProfileResult.Content(result.value.toProfileContent())
        is CustomerAccountResult.Failure -> resolveFailure(result.reason, outcomeMayBeUnknown = false)
    }

    override suspend fun save(
        firstName: String?,
        lastName: String?,
        expectedFirstName: String?,
        expectedLastName: String?
    ): ProfileResult {
        val result = gateway.loadProfile()
        return when (result) {
            is CustomerAccountResult.Failure ->
                resolveFailure(result.reason, outcomeMayBeUnknown = false)

            is CustomerAccountResult.Success ->
                if (
                    result.value.toProfileContent() ==
                    ProfileContent(expectedFirstName.orEmpty(), expectedLastName.orEmpty())
                ) {
                    updateProfile(firstName, lastName)
                } else {
                    ProfileResult.Conflict
                }
        }
    }

    private suspend fun updateProfile(firstName: String?, lastName: String?): ProfileResult =
        when (val result = gateway.updateProfile(CustomerProfileUpdate(firstName, lastName))) {
            is CustomerProfileUpdateResult.Success -> ProfileResult.Content(result.profile.toProfileContent())

            is CustomerProfileUpdateResult.Rejected -> ProfileResult.Rejected(result.fields)

            is CustomerProfileUpdateResult.Failure ->
                resolveFailure(result.reason, outcomeMayBeUnknown = result.reason.mayHaveReachedServer())
        }

    private suspend fun resolveFailure(failure: CustomerAccountFailure, outcomeMayBeUnknown: Boolean): ProfileResult =
        when {
            failure.isTerminal() -> {
                sessionCoordinator.clearForLogout()
                ProfileResult.SignedOut
            }

            outcomeMayBeUnknown -> ProfileResult.Failed(ProfileFailure.SAVE_UNCONFIRMED)

            failure.isConnectionFailure() -> ProfileResult.Failed(ProfileFailure.CONNECTION)

            else -> ProfileResult.Failed(ProfileFailure.SERVICE)
        }
}

private val TERMINAL_PROFILE_ERROR_CODES =
    setOf("UNAUTHENTICATED", "UNAUTHORIZED", "ACCESS_DENIED", "TOKEN_INVALID")

private fun CustomerProfile.toProfileContent(): ProfileContent = ProfileContent(firstName.orEmpty(), lastName.orEmpty())

private fun CustomerAccountFailure.isTerminal(): Boolean = when (this) {
    CustomerAccountFailure.SignedOut -> true

    is CustomerAccountFailure.Authentication -> reason != CustomerTokenFailure.Transient

    is CustomerAccountFailure.GraphQl -> errorCodes.any(TERMINAL_PROFILE_ERROR_CODES::contains)

    is CustomerAccountFailure.Discovery,
    is CustomerAccountFailure.Transport -> false
}

private fun CustomerAccountFailure.isConnectionFailure(): Boolean = when (this) {
    is CustomerAccountFailure.Authentication -> reason == CustomerTokenFailure.Transient

    is CustomerAccountFailure.Discovery -> reason == CustomerAccountDiscoveryFailure.NETWORK

    is CustomerAccountFailure.Transport -> retryable

    is CustomerAccountFailure.GraphQl,
    CustomerAccountFailure.SignedOut -> false
}

private fun CustomerAccountFailure.mayHaveReachedServer(): Boolean =
    this is CustomerAccountFailure.GraphQl || this is CustomerAccountFailure.Transport
