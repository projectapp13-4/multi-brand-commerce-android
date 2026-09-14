package com.gurbakir.mobile.profile

import com.gurbakir.account.CustomerAccountFailure
import com.gurbakir.account.CustomerAccountResult
import com.gurbakir.account.CustomerProfile
import com.gurbakir.account.CustomerProfileField
import com.gurbakir.account.CustomerProfileGateway
import com.gurbakir.account.CustomerProfileUpdate
import com.gurbakir.account.CustomerProfileUpdateResult
import com.gurbakir.account.oauth.CustomerAccountLogoutClient
import com.gurbakir.account.oauth.CustomerLogoutResult
import com.gurbakir.account.oauth.CustomerTokenFailure
import com.gurbakir.account.oauth.UnconfiguredCustomerAccountTokenClient
import com.gurbakir.account.session.CustomerAccountSessionCoordinator
import com.gurbakir.account.session.CustomerSession
import com.gurbakir.account.session.CustomerSessionStore
import com.gurbakir.account.session.SensitiveToken
import com.gurbakir.foundation.config.CustomerAccountConfiguration
import com.gurbakir.foundation.config.REQUIRED_CUSTOMER_ACCOUNT_SCOPES
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ProfileControllerTest {
    @Test
    fun `load maps only approved profile fields`() = runTest {
        val controller =
            controller(
                FakeProfileGateway(
                    loadResult =
                        CustomerAccountResult.Success(CustomerProfile("Synthetic", "Customer"))
                )
            )

        assertEquals(
            ProfileResult.Content(ProfileContent("Synthetic", "Customer")),
            controller.load()
        )
    }

    @Test
    fun `save retains structured field rejection and never trusts an unconfirmed profile`() = runTest {
        val controller =
            controller(
                FakeProfileGateway(
                    updateResult =
                        CustomerProfileUpdateResult.Rejected(
                            setOf(CustomerProfileField.FIRST_NAME)
                        )
                )
            )

        assertEquals(
            ProfileResult.Rejected(setOf(CustomerProfileField.FIRST_NAME)),
            controller.save("Invalid", "Name", "Original", "Name")
        )
    }

    @Test
    fun `ambiguous mutation transport result requires server reload`() = runTest {
        val controller =
            controller(
                FakeProfileGateway(
                    updateResult =
                        CustomerProfileUpdateResult.Failure(
                            CustomerAccountFailure.Transport(retryable = true)
                        )
                )
            )

        assertEquals(
            ProfileResult.Failed(ProfileFailure.SAVE_UNCONFIRMED),
            controller.save("Synthetic", "Customer", "Original", "Name")
        )
    }

    @Test
    fun `stale baseline returns conflict without issuing a mutation`() = runTest {
        val gateway =
            FakeProfileGateway(
                loadResult = CustomerAccountResult.Success(CustomerProfile("Changed", "Elsewhere")),
                updateResult =
                    CustomerProfileUpdateResult.Success(CustomerProfile("Local", "Draft"))
            )
        val controller = controller(gateway)

        assertEquals(
            ProfileResult.Conflict,
            controller.save("Local", "Draft", "Original", "Name")
        )
        assertEquals(0, gateway.updateCount)
    }

    @Test
    fun `null and empty server names share one conflict baseline`() = runTest {
        val gateway =
            FakeProfileGateway(
                loadResult = CustomerAccountResult.Success(CustomerProfile("", null)),
                updateResult =
                    CustomerProfileUpdateResult.Success(CustomerProfile("Synthetic", null))
            )
        val controller = controller(gateway)

        assertEquals(
            ProfileResult.Content(ProfileContent("Synthetic", "")),
            controller.save("Synthetic", null, null, null)
        )
        assertEquals(1, gateway.updateCount)
    }

    @Test
    fun `terminal profile failure clears the customer session`() = runTest {
        val store = InMemorySessionStore(activeSession())
        val controller =
            controller(
                gateway =
                    FakeProfileGateway(
                        loadResult =
                            CustomerAccountResult.Failure(
                                CustomerAccountFailure.Authentication(CustomerTokenFailure.Rejected)
                            )
                    ),
                store = store
            )

        assertEquals(ProfileResult.SignedOut, controller.load())
        assertNull(store.session)
    }

    private fun controller(
        gateway: CustomerProfileGateway,
        store: InMemorySessionStore = InMemorySessionStore(activeSession())
    ): DefaultProfileController = DefaultProfileController(
        gateway = gateway,
        sessionCoordinator =
            CustomerAccountSessionCoordinator(
                configuration = configuration(),
                tokenClient = UnconfiguredCustomerAccountTokenClient(),
                sessionStore = store,
                logoutClient = CustomerAccountLogoutClient { CustomerLogoutResult.Success },
                clock = FIXED_CLOCK
            )
    )

    private class FakeProfileGateway(
        private val loadResult: CustomerAccountResult<CustomerProfile> =
            CustomerAccountResult.Success(CustomerProfile("Original", "Name")),
        private val updateResult: CustomerProfileUpdateResult =
            CustomerProfileUpdateResult.Failure(CustomerAccountFailure.SignedOut)
    ) : CustomerProfileGateway {
        var updateCount = 0

        override suspend fun loadProfile(): CustomerAccountResult<CustomerProfile> = loadResult

        override suspend fun updateProfile(update: CustomerProfileUpdate): CustomerProfileUpdateResult {
            updateCount += 1
            return updateResult
        }
    }

    private class InMemorySessionStore(var session: CustomerSession?) : CustomerSessionStore {
        override suspend fun read(): CustomerSession? = session

        override suspend fun write(session: CustomerSession) {
            this.session = session
        }

        override suspend fun clear() {
            session = null
        }
    }

    private companion object {
        val NOW: Instant = Instant.parse("2026-08-11T12:00:00Z")
        val FIXED_CLOCK: Clock = Clock.fixed(NOW, ZoneOffset.UTC)

        fun activeSession(): CustomerSession = CustomerSession(
            accessToken = SensitiveToken.from("synthetic-access-token"),
            refreshToken = SensitiveToken.from("synthetic-refresh-token"),
            idToken = SensitiveToken.from("synthetic-id-token"),
            expiresAt = NOW.plusSeconds(3_600)
        )

        fun configuration(): CustomerAccountConfiguration = CustomerAccountConfiguration(
            clientId = "public-client-id",
            issuer = "https://shop.example/customer-account",
            authorizationEndpoint = "https://shop.example/authentication/oauth/authorize",
            tokenEndpoint = "https://shop.example/authentication/oauth/token",
            logoutEndpoint = "https://shop.example/authentication/logout",
            graphqlEndpoint = "https://shop.example/customer/api/2026-07/graphql",
            redirectUri = "shop.123456.example://oauth/callback",
            userAgent = "Test-Android",
            scopes = REQUIRED_CUSTOMER_ACCOUNT_SCOPES
        )
    }
}
