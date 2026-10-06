package com.gurbakir.mobile.account

import com.apollographql.apollo.ApolloClient
import com.gurbakir.account.ApolloCustomerAccountGateway
import com.gurbakir.account.ApolloCustomerAddressGateway
import com.gurbakir.account.ApolloCustomerOrderGateway
import com.gurbakir.account.ApolloCustomerProfileGateway
import com.gurbakir.account.CustomerSessionResolver
import com.gurbakir.account.oauth.CustomerAccountAuthorizationCoordinator
import com.gurbakir.account.oauth.CustomerAccountAuthorizationGrant
import com.gurbakir.account.oauth.CustomerAccountTokenClient
import com.gurbakir.account.oauth.CustomerTokenPayload
import com.gurbakir.account.oauth.CustomerTokenResult
import com.gurbakir.account.oauth.UnconfiguredCustomerAccountDiscoveryClient
import com.gurbakir.account.session.CustomerAccountSessionCoordinator
import com.gurbakir.account.session.CustomerSession
import com.gurbakir.account.session.CustomerSessionStore
import com.gurbakir.account.session.SensitiveToken
import com.gurbakir.foundation.config.CustomerAccountConfiguration
import com.gurbakir.foundation.config.REQUIRED_CUSTOMER_ACCOUNT_SCOPES
import com.gurbakir.mobile.address.AddressActionResult
import com.gurbakir.mobile.address.AddressFormLoadResult
import com.gurbakir.mobile.address.AddressInput
import com.gurbakir.mobile.address.AddressLoadResult
import com.gurbakir.mobile.address.AddressTerritoryPolicy
import com.gurbakir.mobile.address.DefaultAddressController
import com.gurbakir.mobile.address.PostalCodeInputMode
import com.gurbakir.mobile.cart.CartMutationAttempt
import com.gurbakir.mobile.cart.CartMutationPlan
import com.gurbakir.mobile.cart.CartOperations
import com.gurbakir.mobile.cart.DefaultCartRepository
import com.gurbakir.mobile.order.DefaultOrderController
import com.gurbakir.mobile.order.OrderDetailResult
import com.gurbakir.mobile.order.OrderPageResult
import com.gurbakir.mobile.profile.DefaultProfileController
import com.gurbakir.mobile.profile.ProfileResult
import com.gurbakir.storefront.CartSessionResolution
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.function.ThrowingSupplier

/** Real HTTP gateway, resolver, session coordinator and private controllers; no preselected storage result. */
class PrivateSessionStorageControllerTest {
    @TestFactory
    fun `all terminal controller cleanup paths preserve uncertain removal`(): List<DynamicTest> =
        PrivateOperation.entries.flatMap { operation ->
            listOf(ProtectedFault.CLEAR_BEFORE_APPLY, ProtectedFault.CLEAR_AFTER_APPLY).flatMap { fault ->
                listOf(false, true).map { graphql ->
                    DynamicTest.dynamicTest(
                        "${operation.name} ${fault.name} ${if (graphql) "GraphQL" else "HTTP401"}"
                    ) {
                        ProtectedSessionControllerFixture().use { fixture ->
                            val original = fixture.store.session
                            fixture.store.fault = fault
                            fixture.enqueuePrefix(operation)
                            fixture.enqueueTerminal(graphql)
                            // GraphQL mutation ambiguity is followed by the real controller's authorization reread.
                            val verifierRequest = graphql && operation in listOf(
                                PrivateOperation.ADDRESS_UPDATE,
                                PrivateOperation.ADDRESS_DEFAULT,
                                PrivateOperation.ADDRESS_DELETE
                            )
                            if (verifierRequest) fixture.enqueueTerminal(graphql = true)
                            val result = noEscape { fixture.call(operation) }
                            assertEquals("SECURE_STORAGE", result.privateStorageCategory())
                            assertEquals(
                                if (fault == ProtectedFault.CLEAR_AFTER_APPLY) null else original,
                                fixture.store.session
                            )
                            assertEquals(1, fixture.store.events.count { it == "clear" })
                            assertEquals(
                                operation.prefixRequests + 1 + if (verifierRequest) 1 else 0,
                                fixture.server.requestCount
                            )
                        }
                    }
                }
            }
        }

    @TestFactory
    fun `protected resolver faults reach the selected private controller boundary`(): List<DynamicTest> =
        PrivateOperation.entries.flatMap { operation ->
            listOf(
                ProtectedFault.READ,
                ProtectedFault.WRITE_BEFORE_APPLY,
                ProtectedFault.WRITE_AFTER_APPLY
            ).map { fault ->
                DynamicTest.dynamicTest("${operation.name} ${fault.name}") {
                    ProtectedSessionControllerFixture().use { fixture ->
                        fixture.store.fault = fault
                        fixture.store.faultAtRead = operation.prefixRequests + 1
                        fixture.enqueuePrefix(operation)
                        val result = noEscape { fixture.call(operation) }
                        assertEquals("SECURE_STORAGE", result.privateStorageCategory())
                        assertEquals(
                            operation.prefixRequests,
                            fixture.server.requestCount,
                            "The faulting operation must not reach private HTTP"
                        )
                        assertEquals(0, fixture.store.events.count { it == "clear" })
                    }
                }
            }
        }

    @TestFactory
    fun `confirmed terminal cleanup still reaches the ordinary signed out result`(): List<DynamicTest> =
        PrivateOperation.entries.map { operation ->
            DynamicTest.dynamicTest(operation.name) {
                ProtectedSessionControllerFixture().use { fixture ->
                    fixture.enqueuePrefix(operation)
                    fixture.enqueueTerminal(graphql = false)
                    val result = runBlocking { fixture.call(operation) }
                    assertEquals("SignedOut", result.javaClass.simpleName)
                    assertNull(fixture.store.session)
                    assertEquals(1, fixture.store.events.count { it == "clear" })
                    assertEquals(operation.prefixRequests + 1, fixture.server.requestCount)
                }
            }
        }

    @TestFactory
    fun `Account later gateway resolution retains the existing secure storage contract`(): List<DynamicTest> =
        listOf(ProtectedFault.READ, ProtectedFault.WRITE_BEFORE_APPLY, ProtectedFault.WRITE_AFTER_APPLY).map { fault ->
            DynamicTest.dynamicTest(fault.name) {
                ProtectedSessionControllerFixture().use { fixture ->
                    fixture.store.fault = fault
                    fixture.store.faultAtRead = 2
                    val result = runBlocking { fixture.accountController().restore() }
                    assertEquals(AccountResult.Failed(AccountFailure.SECURE_STORAGE, true, true), result)
                    assertEquals(
                        2,
                        fixture.store.reads,
                        "Initial Account restore must succeed before the gateway fault"
                    )
                    assertEquals(0, fixture.server.requestCount)
                }
            }
        }

    @TestFactory
    fun `authoritative address verifier gives storage uncertainty priority without replay`(): List<DynamicTest> =
        listOf(PrivateOperation.ADDRESS_UPDATE, PrivateOperation.ADDRESS_DEFAULT, PrivateOperation.ADDRESS_DELETE)
            .map { operation ->
                DynamicTest.dynamicTest(operation.name) {
                    ProtectedSessionControllerFixture().use { fixture ->
                        fixture.enqueuePrefix(operation)
                        fixture.server.enqueue(
                            MockResponse().setBody(
                                """
                            {"errors":[{"message":"Synthetic ambiguous result",
                            "extensions":{"code":"INTERNAL_SERVER_ERROR"}}]}
                                """.trimIndent()
                            )
                        )
                        fixture.store.fault = ProtectedFault.READ
                        fixture.store.faultAtRead = 3
                        val result = noEscape { fixture.call(operation) }
                        assertEquals(
                            "SECURE_STORAGE",
                            result.privateStorageCategory(),
                            "SAVE_UNCONFIRMED must not hide a failed protected authorization reread"
                        )
                        assertEquals(
                            2,
                            fixture.server.requestCount,
                            "One preflight and one mutation; no mutation replay"
                        )
                        fixture.store.fault = null
                        fixture.enqueueAddresses()
                        assertInstanceOf(
                            AddressFormLoadResult.Ready::class.java,
                            runBlocking { fixture.address.loadForm(PROTECTED_ADDRESS_ID) }
                        )
                        assertEquals(3, fixture.server.requestCount, "Recovery is an authoritative read only")
                    }
                }
            }

    @TestFactory
    fun `private read write and clear cancellation propagate unchanged`(): List<DynamicTest> =
        listOf(ProtectedFault.READ, ProtectedFault.WRITE_BEFORE_APPLY, ProtectedFault.CLEAR_BEFORE_APPLY).map { fault ->
            DynamicTest.dynamicTest(fault.name) {
                ProtectedSessionControllerFixture().use { fixture ->
                    val cancellation = CancellationException("synthetic protected operation cancelled")
                    fixture.store.fault = fault
                    fixture.store.failure = cancellation
                    if (fault == ProtectedFault.CLEAR_BEFORE_APPLY) fixture.enqueueTerminal(false)
                    assertSame(
                        cancellation,
                        assertThrows(CancellationException::class.java) {
                            runBlocking { fixture.profile.load() }
                        }
                    )
                    assertEquals(if (fault == ProtectedFault.CLEAR_BEFORE_APPLY) 1 else 0, fixture.server.requestCount)
                }
            }
        }

    private fun noEscape(action: suspend () -> Any): Any = assertDoesNotThrow(
        ThrowingSupplier { runBlocking { action() } },
        "Protected-storage uncertainty must remain a recoverable feature result"
    )
}

internal enum class PrivateOperation(val prefixRequests: Int = 0) {
    PROFILE_LOAD,
    PROFILE_SAVE_PREFLIGHT,
    PROFILE_UPDATE(1),
    ORDER_PAGE,
    ORDER_DETAIL,
    ADDRESS_LIST,
    ADDRESS_FORM,
    ADDRESS_CREATE,
    ADDRESS_UPDATE_PREFLIGHT,
    ADDRESS_UPDATE(1),
    ADDRESS_DEFAULT_PREFLIGHT,
    ADDRESS_DEFAULT(1),
    ADDRESS_DELETE_PREFLIGHT,
    ADDRESS_DELETE(1)
}

internal enum class ProtectedFault {
    READ,
    WRITE_BEFORE_APPLY,
    WRITE_AFTER_APPLY,
    CLEAR_BEFORE_APPLY,
    CLEAR_AFTER_APPLY
}

internal class ProtectedSessionControllerFixture : AutoCloseable {
    val server = MockWebServer().apply { start() }
    private val client = ApolloClient.Builder().serverUrl(server.url("graphql").toString()).build()
    val store = ProtectedFaultStore(protectedSession())
    var refreshResult: CustomerTokenResult? = null
    val sessions = CustomerAccountSessionCoordinator(
        protectedConfiguration(),
        object : CustomerAccountTokenClient {
            override suspend fun exchange(grant: CustomerAccountAuthorizationGrant): CustomerTokenResult =
                error("Hosted exchange is outside the fixture")
            override suspend fun refresh(refreshToken: SensitiveToken): CustomerTokenResult =
                refreshResult ?: CustomerTokenResult.Success(
                    CustomerTokenPayload(
                        SensitiveToken.from("synthetic-rotated"),
                        SensitiveToken.from("synthetic-refresh"),
                        null,
                        PROTECTED_NOW.plusSeconds(3600)
                    )
                )
        },
        store,
        clock = Clock.fixed(PROTECTED_NOW, ZoneOffset.UTC)
    )
    private val resolver = CustomerSessionResolver { sessions.restore() }
    val identity = ApolloCustomerAccountGateway(client, resolver)
    val profile = DefaultProfileController(ApolloCustomerProfileGateway(client, resolver), sessions)
    val orders = DefaultOrderController(ApolloCustomerOrderGateway(client, resolver), sessions)
    val address = DefaultAddressController(
        ApolloCustomerAddressGateway(client, resolver),
        sessions,
        AddressTerritoryPolicy("TR", PostalCodeInputMode.NUMERIC)
    )

    private val idleCarts = DefaultCartRepository(object : CartOperations {
        override suspend fun restore(): CartSessionResolution =
            error("Storage recovery must precede cart reconciliation")
        override suspend fun mutate(plan: (CartSessionResolution) -> CartMutationPlan): CartMutationAttempt =
            error("No cart mutation belongs to private recovery")
        override suspend fun clear(): Boolean = error("No cart clear belongs to private recovery")
    })

    fun accountController() = DefaultAccountController(
        CustomerAccountAuthorizationCoordinator(
            protectedConfiguration(),
            UnconfiguredCustomerAccountDiscoveryClient()
        ),
        sessions,
        identity,
        idleCarts
    )

    private suspend fun callProfileSave(): ProfileResult = profile.save("Updated", "Customer", "Synthetic", "Customer")

    internal suspend fun call(operation: PrivateOperation): Any = when (operation) {
        PrivateOperation.PROFILE_LOAD -> profile.load()

        PrivateOperation.PROFILE_SAVE_PREFLIGHT, PrivateOperation.PROFILE_UPDATE -> callProfileSave()

        PrivateOperation.ORDER_PAGE -> orders.loadPage()

        PrivateOperation.ORDER_DETAIL -> orders.loadDetail("gid://shopify/Order/1")

        PrivateOperation.ADDRESS_LIST -> address.loadAddresses()

        PrivateOperation.ADDRESS_FORM -> address.loadForm(PROTECTED_ADDRESS_ID)

        PrivateOperation.ADDRESS_CREATE -> address.saveAddress(null, PROTECTED_INPUT, null, false)

        PrivateOperation.ADDRESS_UPDATE_PREFLIGHT, PrivateOperation.ADDRESS_UPDATE ->
            address.saveAddress(PROTECTED_ADDRESS_ID, PROTECTED_INPUT.copy(city = "Updated"), PROTECTED_INPUT, false)

        PrivateOperation.ADDRESS_DEFAULT_PREFLIGHT, PrivateOperation.ADDRESS_DEFAULT ->
            address.setDefault(PROTECTED_ADDRESS_ID)

        PrivateOperation.ADDRESS_DELETE_PREFLIGHT, PrivateOperation.ADDRESS_DELETE ->
            address.deleteAddress(PROTECTED_ADDRESS_ID, PROTECTED_INPUT)
    }

    internal fun enqueuePrefix(operation: PrivateOperation) {
        when (operation) {
            PrivateOperation.PROFILE_UPDATE -> server.enqueue(
                MockResponse().setBody(
                    """{"data":{"customer":{"firstName":"Synthetic","lastName":"Customer"}}}"""
                )
            )

            PrivateOperation.ADDRESS_UPDATE, PrivateOperation.ADDRESS_DEFAULT, PrivateOperation.ADDRESS_DELETE ->
                enqueueAddresses()

            else -> Unit
        }
    }

    fun enqueueAddresses() {
        server.enqueue(
            MockResponse().setBody(
                """{"data":{"customer":{"defaultAddress":null,
            "addresses":{"nodes":[{"id":"$PROTECTED_ADDRESS_ID","firstName":"Synthetic","lastName":"Customer",
                "company":null,"address1":"Synthetic street","address2":null,"city":"Istanbul","zip":"34000",
                "phoneNumber":null,"territoryCode":"TR","zoneCode":null,"formatted":["Synthetic street"]}],
                "pageInfo":{"hasNextPage":false,"endCursor":null}}}}}"""
            )
        )
    }

    fun enqueueTerminal(graphql: Boolean) {
        server.enqueue(
            if (graphql) {
                MockResponse().setBody(
                    """{"errors":[{"message":"Synthetic terminal response","extensions":{"code":"UNAUTHENTICATED"}}]}"""
                )
            } else {
                MockResponse().setResponseCode(401)
            }
        )
    }

    override fun close() {
        client.close()
        server.shutdown()
    }
}

internal class ProtectedFaultStore(var session: CustomerSession?) : CustomerSessionStore {
    var fault: ProtectedFault? = null
    var faultAtRead = 1
    var reads = 0
    var failure: RuntimeException = IllegalStateException("synthetic protected receipt refused")
    var readGate: CompletableDeferred<Unit>? = null
    var readEntered: CompletableDeferred<Unit>? = null
    val events = mutableListOf<String>()
    override suspend fun read(): CustomerSession? {
        reads++
        events += "read"
        readGate?.let { gate ->
            readEntered?.complete(Unit)
            gate.await()
        }
        if (reads == faultAtRead) {
            if (fault == ProtectedFault.READ) throw failure
            if (fault == ProtectedFault.WRITE_BEFORE_APPLY || fault == ProtectedFault.WRITE_AFTER_APPLY) {
                session = session?.copy(expiresAt = PROTECTED_NOW.minusSeconds(1))
            }
        }
        return session
    }
    override suspend fun write(session: CustomerSession) {
        events += "write"
        if (fault == ProtectedFault.WRITE_BEFORE_APPLY) throw failure
        this.session = session
        if (fault == ProtectedFault.WRITE_AFTER_APPLY) throw failure
    }
    override suspend fun clear() {
        events += "clear"
        if (fault == ProtectedFault.CLEAR_BEFORE_APPLY) throw failure
        session = null
        if (fault == ProtectedFault.CLEAR_AFTER_APPLY) throw failure
    }
}

private fun Any.privateStorageCategory(): String? = when (this) {
    is ProfileResult.Failed -> reason.name
    is OrderPageResult.Failed -> reason.name
    is OrderDetailResult.Failed -> reason.name
    is AddressLoadResult.Failed -> reason.name
    is AddressFormLoadResult.Failed -> reason.name
    is AddressActionResult.Failed -> reason.name
    else -> null
}

internal fun protectedSession() = CustomerSession(
    SensitiveToken.from("synthetic-current"),
    SensitiveToken.from("synthetic-refresh"),
    SensitiveToken.from("synthetic-id"),
    PROTECTED_NOW.plusSeconds(3600)
)

internal fun protectedConfiguration() = CustomerAccountConfiguration(
    "synthetic-public-client", "https://shop.example/customer-account",
    "https://shop.example/authentication/oauth/authorize", "https://shop.example/authentication/oauth/token",
    "https://shop.example/authentication/logout", "https://shop.example/customer/api/2026-07/graphql",
    "shop.123456.synthetic://oauth/callback", "Test-Android", REQUIRED_CUSTOMER_ACCOUNT_SCOPES
)

private const val PROTECTED_ADDRESS_ID = "gid://shopify/CustomerAddress/1"
private val PROTECTED_INPUT = AddressInput("Synthetic", "Customer", "", "Synthetic street", "", "Istanbul", "34000", "")
internal val PROTECTED_NOW = Instant.parse("2026-10-06T12:00:00Z")
