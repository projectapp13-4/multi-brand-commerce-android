package com.gurbakir.mobile.address

import com.gurbakir.account.CustomerAccountFailure
import com.gurbakir.account.CustomerAccountResult
import com.gurbakir.account.CustomerAddress
import com.gurbakir.account.CustomerAddressDraft
import com.gurbakir.account.CustomerAddressGateway
import com.gurbakir.account.CustomerAddressIssue
import com.gurbakir.account.CustomerAddressMutationResult
import com.gurbakir.account.CustomerAddressPage
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

class AddressControllerTest {
    @Test
    fun `territory policy maps lists forms and create update drafts for configured territories`() = runTest {
        for (territory in listOf("ZZ", "XY")) {
            val gateway = FakeAddressGateway(
                pages = mutableListOf(
                    successPage(
                        listOf(
                            address("1", territory = territory),
                            address("2", territory = "XX")
                        )
                    )
                )
            )
            val subject =
                controller(
                    gateway,
                    territoryPolicy = AddressTerritoryPolicy(territory, PostalCodeInputMode.TEXT)
                )
            val listed = subject.loadAddresses() as AddressLoadResult.Content
            assertEquals(listOf(true, false), listed.addresses.map { it.isSupported })
            assertEquals(true, (subject.loadForm("1") as AddressFormLoadResult.Ready).address?.isSupported)
            assertEquals(AddressFormLoadResult.Failed(AddressFailure.UNSUPPORTED_COUNTRY), subject.loadForm("2"))
            assertEquals(AddressActionResult.Confirmed("new"), subject.saveAddress(null, input(), null, false))
            assertEquals(territory, gateway.createdDraft?.territoryCode)
            assertEquals(AddressActionResult.Confirmed("1"), subject.saveAddress("1", input("Updated"), input(), false))
            assertEquals(territory, gateway.updatedDraft?.territoryCode)
            assertEquals(
                AddressActionResult.Failed(AddressFailure.UNSUPPORTED_COUNTRY),
                subject.saveAddress("2", input(), input(), false)
            )
            assertEquals(1, gateway.updateCount)
        }
    }

    @Test
    fun `load combines bounded pages and preserves server default`() = runTest {
        val gateway = FakeAddressGateway(
            pages =
                mutableListOf(
                    successPage(listOf(address("1")), "next"),
                    successPage(listOf(address("2", isDefault = true)), null)
                )
        )

        val result = controller(gateway).loadAddresses() as AddressLoadResult.Content

        assertEquals(listOf("1", "2"), result.addresses.map { it.id })
        assertEquals("2", result.addresses.single { it.isDefault }.id)
    }

    @Test
    fun `stale edit baseline conflicts without issuing mutation`() = runTest {
        val gateway = FakeAddressGateway(pages = mutableListOf(successPage(listOf(address("1", city = "Changed")))))

        val result = controller(gateway).saveAddress(
            addressId = "1",
            input = input(city = "Local"),
            expected = input(city = "Original"),
            makeDefault = false
        )

        assertEquals(AddressActionResult.Conflict, result)
        assertEquals(0, gateway.updateCount)
    }

    @Test
    fun `ambiguous update is confirmed only after authoritative reread matches`() = runTest {
        val gateway = FakeAddressGateway(
            pages =
                mutableListOf(
                    successPage(listOf(address("1", city = "Original"))),
                    successPage(listOf(address("1", city = "Updated")))
                ),
            updateResult =
                CustomerAddressMutationResult.Failure(
                    CustomerAccountFailure.Transport(retryable = true)
                )
        )

        assertEquals(
            AddressActionResult.Confirmed("1"),
            controller(gateway).saveAddress(
                addressId = "1",
                input = input(city = "Updated"),
                expected = input(city = "Original"),
                makeDefault = false
            )
        )
    }

    @Test
    fun `ambiguous create is never retried or guessed from matching PII`() = runTest {
        val gateway = FakeAddressGateway(
            createResult =
                CustomerAddressMutationResult.Failure(
                    CustomerAccountFailure.GraphQl(setOf("INTERNAL_SERVER_ERROR"))
                )
        )

        assertEquals(
            AddressActionResult.Failed(AddressFailure.SAVE_UNCONFIRMED),
            controller(gateway).saveAddress(null, input(), null, makeDefault = true)
        )
        assertEquals(1, gateway.createCount)
    }

    @Test
    fun `set default requires the authoritative reread to mark target default`() = runTest {
        val gateway = FakeAddressGateway(
            pages =
                mutableListOf(
                    successPage(listOf(address("1"), address("2", isDefault = true))),
                    successPage(listOf(address("1", isDefault = true), address("2")))
                ),
            setDefaultResult = CustomerAddressMutationResult.Success("1")
        )

        assertEquals(AddressActionResult.Confirmed("1"), controller(gateway).setDefault("1"))
        assertEquals(1, gateway.setDefaultCount)
    }

    @Test
    fun `default address is protected before delete reaches gateway`() = runTest {
        val gateway = FakeAddressGateway(
            pages = mutableListOf(successPage(listOf(address("1", isDefault = true))))
        )

        assertEquals(
            AddressActionResult.Rejected(
                fields = setOf(com.gurbakir.account.CustomerAddressField.FORM),
                issues = setOf(CustomerAddressIssue.DEFAULT_ADDRESS_PROTECTED)
            ),
            controller(gateway).deleteAddress("1", input())
        )
        assertEquals(0, gateway.deleteCount)
    }

    @Test
    fun `unsupported address blocks edit and default but permits explicit non default delete`() = runTest {
        val formGateway = FakeAddressGateway(
            pages = mutableListOf(successPage(listOf(address("1", territory = "XX"))))
        )
        assertEquals(
            AddressFormLoadResult.Failed(AddressFailure.UNSUPPORTED_COUNTRY),
            controller(formGateway).loadForm("1")
        )

        val defaultGateway = FakeAddressGateway(
            pages = mutableListOf(successPage(listOf(address("1", territory = "XX"))))
        )
        assertEquals(
            AddressActionResult.Failed(AddressFailure.UNSUPPORTED_COUNTRY),
            controller(defaultGateway).setDefault("1")
        )
        assertEquals(0, defaultGateway.setDefaultCount)

        val deleteGateway = FakeAddressGateway(
            pages = mutableListOf(successPage(listOf(address("1", territory = "XX"))))
        )
        assertEquals(
            AddressActionResult.Confirmed("1"),
            controller(deleteGateway).deleteAddress("1", input())
        )
        assertEquals(1, deleteGateway.deleteCount)
    }

    @Test
    fun `ambiguous delete is confirmed by authoritative absence`() = runTest {
        val gateway = FakeAddressGateway(
            pages =
                mutableListOf(
                    successPage(listOf(address("1"))),
                    successPage(emptyList())
                ),
            deleteResult =
                CustomerAddressMutationResult.Failure(
                    CustomerAccountFailure.Transport(retryable = true)
                )
        )

        assertEquals(
            AddressActionResult.Confirmed("1"),
            controller(gateway).deleteAddress("1", input())
        )
    }

    @Test
    fun `terminal address failure clears Keystore session abstraction`() = runTest {
        val store = InMemorySessionStore(activeSession())
        val gateway = FakeAddressGateway(
            pages =
                mutableListOf(
                    CustomerAccountResult.Failure(
                        CustomerAccountFailure.Authentication(CustomerTokenFailure.Rejected)
                    )
                )
        )

        assertEquals(AddressLoadResult.SignedOut, controller(gateway, store).loadAddresses())
        assertNull(store.session)
    }

    private fun controller(
        gateway: CustomerAddressGateway,
        store: InMemorySessionStore = InMemorySessionStore(activeSession()),
        territoryPolicy: AddressTerritoryPolicy = AddressTerritoryPolicy("ZZ", PostalCodeInputMode.TEXT)
    ) = DefaultAddressController(
        gateway,
        CustomerAccountSessionCoordinator(
            configuration = configuration(),
            tokenClient = UnconfiguredCustomerAccountTokenClient(),
            sessionStore = store,
            logoutClient = CustomerAccountLogoutClient { CustomerLogoutResult.Success },
            clock = FIXED_CLOCK
        ),
        territoryPolicy
    )

    private class FakeAddressGateway(
        private val pages: MutableList<CustomerAccountResult<CustomerAddressPage>> =
            mutableListOf(successPage(emptyList())),
        private val createResult: CustomerAddressMutationResult = CustomerAddressMutationResult.Success("new"),
        private val updateResult: CustomerAddressMutationResult = CustomerAddressMutationResult.Success("1"),
        private val setDefaultResult: CustomerAddressMutationResult = CustomerAddressMutationResult.Success("1"),
        private val deleteResult: CustomerAddressMutationResult = CustomerAddressMutationResult.Success("1")
    ) : CustomerAddressGateway {
        var createCount = 0
        var updateCount = 0
        var setDefaultCount = 0
        var deleteCount = 0
        var createdDraft: CustomerAddressDraft? = null
        var updatedDraft: CustomerAddressDraft? = null

        override suspend fun loadAddresses(after: String?): CustomerAccountResult<CustomerAddressPage> =
            if (pages.size == 1) pages.single() else pages.removeAt(0)

        override suspend fun createAddress(
            address: CustomerAddressDraft,
            makeDefault: Boolean
        ): CustomerAddressMutationResult {
            createCount += 1
            createdDraft = address
            return createResult
        }

        override suspend fun updateAddress(
            addressId: String,
            address: CustomerAddressDraft
        ): CustomerAddressMutationResult {
            updateCount += 1
            updatedDraft = address
            return updateResult
        }

        override suspend fun setDefaultAddress(addressId: String): CustomerAddressMutationResult {
            setDefaultCount += 1
            return setDefaultResult
        }

        override suspend fun deleteAddress(addressId: String): CustomerAddressMutationResult {
            deleteCount += 1
            return deleteResult
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

        fun address(id: String, city: String = "Original", isDefault: Boolean = false, territory: String = "ZZ") =
            CustomerAddress(
                id = id,
                firstName = "Synthetic",
                lastName = "Customer",
                company = null,
                address1 = "Private street",
                address2 = null,
                city = city,
                zip = "ZX-1234",
                phoneNumber = "+15551234567",
                territoryCode = territory,
                zoneCode = null,
                formatted = listOf("<redacted>"),
                isDefault = isDefault
            )

        fun input(city: String = "Original") = AddressInput(
            firstName = "Synthetic",
            lastName = "Customer",
            company = "",
            address1 = "Private street",
            address2 = "",
            city = city,
            zip = "ZX-1234",
            phoneNumber = "+15551234567"
        )

        fun successPage(
            addresses: List<CustomerAddress>,
            nextCursor: String? = null
        ): CustomerAccountResult<CustomerAddressPage> =
            CustomerAccountResult.Success(CustomerAddressPage(addresses, nextCursor))

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
