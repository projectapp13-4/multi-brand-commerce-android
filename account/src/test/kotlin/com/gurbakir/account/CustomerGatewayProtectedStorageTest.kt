package com.gurbakir.account

import com.apollographql.apollo.ApolloClient
import com.gurbakir.account.oauth.CustomerAccountAuthorizationGrant
import com.gurbakir.account.oauth.CustomerAccountTokenClient
import com.gurbakir.account.oauth.CustomerTokenFailure
import com.gurbakir.account.oauth.CustomerTokenPayload
import com.gurbakir.account.oauth.CustomerTokenResult
import com.gurbakir.account.session.CustomerAccountSessionCoordinator
import com.gurbakir.account.session.CustomerSession
import com.gurbakir.account.session.CustomerSessionStore
import com.gurbakir.account.session.SensitiveToken
import com.gurbakir.foundation.config.CustomerAccountConfiguration
import com.gurbakir.foundation.config.REQUIRED_CUSTOMER_ACCOUNT_SCOPES
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.CancellationException
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
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.function.ThrowingSupplier

/** Real coordinator and all ten public Apollo operations; only durable I/O and the HTTP peer are controlled. */
class CustomerGatewayProtectedStorageTest {
    @Test
    fun `resolver programmer failures propagate without a storage result`() {
        GatewayStorageFixture(null).use { fixture ->
            val failure = IllegalArgumentException("synthetic resolver contract violation")
            val gateway = fixture.identityWithResolver(CustomerSessionResolver { throw failure })
            assertSame(
                failure,
                assertThrows(IllegalArgumentException::class.java) {
                    runBlocking { gateway.loadIdentity() }
                }
            )
            assertEquals(0, fixture.server.requestCount)
        }
    }

    @TestFactory
    fun `protected read and renewal receipts become recoverable failures before private HTTP`(): List<DynamicTest> =
        GatewayOperation.entries.flatMap { operation ->
            listOf(StoreFault.READ, StoreFault.WRITE_BEFORE_APPLY, StoreFault.WRITE_AFTER_APPLY).map { fault ->
                DynamicTest.dynamicTest("${operation.name} ${fault.name}") {
                    GatewayStorageFixture(fault).use { fixture ->
                        val original = fixture.store.session
                        val result = assertDoesNotThrow(
                            ThrowingSupplier { runBlocking { fixture.call(operation) } },
                            "A protected-session failure must return through the gateway result boundary"
                        )
                        assertEquals("SecureStorage", result.failureReason()?.javaClass?.simpleName)
                        assertEquals(0, fixture.server.requestCount, "Storage uncertainty must precede private HTTP")
                        assertEquals(
                            listOf("read") + if (fault == StoreFault.READ) emptyList() else listOf("write"),
                            fixture.store.events
                        )
                        assertEquals(
                            if (fault == StoreFault.WRITE_AFTER_APPLY) {
                                fixture.rotatedSession.copy(idToken = original?.idToken)
                            } else {
                                original
                            },
                            fixture.store.session
                        )

                        // Recovery rereads the durable truth. It does not replay a request that never left the client.
                        fixture.store.fault = null
                        fixture.replyNonterminal()
                        val recovered = runBlocking { fixture.call(operation) }
                        assertEquals(
                            CustomerAccountFailure.GraphQl(setOf("SYNTHETIC_NONTERMINAL")),
                            recovered.failureReason()
                        )
                        assertEquals(1, fixture.server.requestCount)
                    }
                }
            }
        }

    @TestFactory
    fun `terminal renewal clear failures remain uncertain before private HTTP`(): List<DynamicTest> =
        listOf(CustomerTokenFailure.Rejected, CustomerTokenFailure.InvalidResponse).flatMap { reason ->
            listOf(StoreFault.CLEAR_BEFORE_APPLY, StoreFault.CLEAR_AFTER_APPLY).map { fault ->
                DynamicTest.dynamicTest("${reason.javaClass.simpleName} ${fault.name}") {
                    GatewayStorageFixture(fault, CustomerTokenResult.Failure(reason)).use { fixture ->
                        val original = fixture.store.session
                        val result = assertDoesNotThrow(
                            ThrowingSupplier { runBlocking { fixture.call(GatewayOperation.IDENTITY) } }
                        )
                        assertEquals("SecureStorage", result.failureReason()?.javaClass?.simpleName)
                        assertEquals(listOf("read", "clear"), fixture.store.events)
                        assertEquals(
                            if (fault ==
                                StoreFault.CLEAR_AFTER_APPLY
                            ) {
                                null
                            } else {
                                original
                            },
                            fixture.store.session
                        )
                        assertEquals(0, fixture.server.requestCount)

                        fixture.store.fault = null
                        fixture.store.session = original
                        val confirmed = runBlocking { fixture.call(GatewayOperation.IDENTITY) }
                        assertEquals(CustomerAccountFailure.Authentication(reason), confirmed.failureReason())
                        assertNull(fixture.store.session)
                        assertEquals(0, fixture.server.requestCount)
                    }
                }
            }
        }

    @TestFactory
    fun `healthy storage reaches every existing HTTP result boundary`(): List<DynamicTest> =
        GatewayOperation.entries.map { operation ->
            DynamicTest.dynamicTest(operation.name) {
                GatewayStorageFixture(null).use { fixture ->
                    fixture.replyNonterminal()
                    val result = runBlocking { fixture.call(operation) }
                    assertEquals(CustomerAccountFailure.GraphQl(setOf("SYNTHETIC_NONTERMINAL")), result.failureReason())
                    assertEquals(1, fixture.server.requestCount)
                    assertEquals(listOf("read"), fixture.store.events)
                }
            }
        }

    @TestFactory
    fun `read write and clear cancellation propagate the same cancellation`(): List<DynamicTest> =
        listOf(StoreFault.READ, StoreFault.WRITE_BEFORE_APPLY, StoreFault.CLEAR_BEFORE_APPLY).map { fault ->
            DynamicTest.dynamicTest(fault.name) {
                GatewayStorageFixture(
                    fault,
                    if (fault == StoreFault.CLEAR_BEFORE_APPLY) {
                        CustomerTokenResult.Failure(CustomerTokenFailure.Rejected)
                    } else {
                        null
                    }
                ).use { fixture ->
                    val cancellation = CancellationException("synthetic protected I/O cancellation")
                    fixture.store.failure = cancellation
                    assertSame(
                        cancellation,
                        assertThrows(CancellationException::class.java) {
                            runBlocking { fixture.call(GatewayOperation.IDENTITY) }
                        }
                    )
                    assertEquals(0, fixture.server.requestCount)
                }
            }
        }

    @Test
    fun `explicit identity lease does not reenter a failing session resolver`() = runBlocking {
        GatewayStorageFixture(StoreFault.READ).use { fixture ->
            fixture.server.enqueue(
                MockResponse().setBody(
                    """{"data":{"customer":{"id":"gid://shopify/Customer/1","displayName":"Synthetic"}}}"""
                )
            )
            val result = fixture.identity.loadIdentity(requireNotNull(fixture.store.session))
            assertInstanceOf(CustomerAccountResult.Success::class.java, result)
            assertEquals(emptyList<String>(), fixture.store.events)
            assertEquals(1, fixture.server.requestCount)
        }
    }
}

private enum class GatewayOperation {
    IDENTITY,
    PROFILE_LOAD,
    PROFILE_UPDATE,
    ORDER_PAGE,
    ORDER_DETAIL,
    ADDRESS_LOAD,
    ADDRESS_CREATE,
    ADDRESS_UPDATE,
    ADDRESS_DEFAULT,
    ADDRESS_DELETE
}

private enum class StoreFault { READ, WRITE_BEFORE_APPLY, WRITE_AFTER_APPLY, CLEAR_BEFORE_APPLY, CLEAR_AFTER_APPLY }

private class GatewayStorageFixture(fault: StoreFault?, refreshResult: CustomerTokenResult? = null) : AutoCloseable {
    val server = MockWebServer().apply { start() }
    private val client = ApolloClient.Builder().serverUrl(server.url("graphql").toString()).build()
    val rotatedSession = gatewaySession(NOW.plusSeconds(3600), "synthetic-rotated")
    val store = GatewayFaultStore(
        gatewaySession(
            if (fault == null || fault == StoreFault.READ) NOW.plusSeconds(3600) else NOW.minusSeconds(1),
            "synthetic-original"
        ),
        fault
    )
    private val coordinator = CustomerAccountSessionCoordinator(
        gatewayConfiguration(),
        object : CustomerAccountTokenClient {
            override suspend fun exchange(grant: CustomerAccountAuthorizationGrant): CustomerTokenResult =
                error("Hosted exchange is outside this fixture")
            override suspend fun refresh(refreshToken: SensitiveToken): CustomerTokenResult = refreshResult
                ?: CustomerTokenResult.Success(
                    CustomerTokenPayload(
                        rotatedSession.accessToken,
                        rotatedSession.refreshToken,
                        null,
                        rotatedSession.expiresAt
                    )
                )
        },
        store,
        clock = Clock.fixed(NOW, ZoneOffset.UTC)
    )
    private val resolver = CustomerSessionResolver { coordinator.restore() }
    val identity = ApolloCustomerAccountGateway(client, resolver)
    fun identityWithResolver(resolver: CustomerSessionResolver) = ApolloCustomerAccountGateway(client, resolver)
    private val profile = ApolloCustomerProfileGateway(client, resolver)
    private val orders = ApolloCustomerOrderGateway(client, resolver)
    private val addresses = ApolloCustomerAddressGateway(client, resolver)

    suspend fun call(operation: GatewayOperation): Any = when (operation) {
        GatewayOperation.IDENTITY -> identity.loadIdentity()
        GatewayOperation.PROFILE_LOAD -> profile.loadProfile()
        GatewayOperation.PROFILE_UPDATE -> profile.updateProfile(CustomerProfileUpdate("Synthetic", "Customer"))
        GatewayOperation.ORDER_PAGE -> orders.loadOrderPage()
        GatewayOperation.ORDER_DETAIL -> orders.loadOrder("gid://shopify/Order/1")
        GatewayOperation.ADDRESS_LOAD -> addresses.loadAddresses()
        GatewayOperation.ADDRESS_CREATE -> addresses.createAddress(DRAFT, false)
        GatewayOperation.ADDRESS_UPDATE -> addresses.updateAddress(ADDRESS_ID, DRAFT)
        GatewayOperation.ADDRESS_DEFAULT -> addresses.setDefaultAddress(ADDRESS_ID)
        GatewayOperation.ADDRESS_DELETE -> addresses.deleteAddress(ADDRESS_ID)
    }

    fun replyNonterminal() {
        server.enqueue(
            MockResponse().setBody(
                """{"errors":[{"message":"Synthetic error","extensions":{"code":"SYNTHETIC_NONTERMINAL"}}]}"""
            )
        )
    }

    override fun close() {
        client.close()
        server.shutdown()
    }
}

private class GatewayFaultStore(var session: CustomerSession?, var fault: StoreFault?) : CustomerSessionStore {
    var failure: RuntimeException = IllegalStateException("synthetic unconfirmed protected receipt")
    val events = mutableListOf<String>()
    override suspend fun read(): CustomerSession? {
        events += "read"
        if (fault == StoreFault.READ) throw failure
        return session
    }
    override suspend fun write(session: CustomerSession) {
        events += "write"
        if (fault == StoreFault.WRITE_BEFORE_APPLY) throw failure
        this.session = session
        if (fault == StoreFault.WRITE_AFTER_APPLY) throw failure
    }
    override suspend fun clear() {
        events += "clear"
        if (fault == StoreFault.CLEAR_BEFORE_APPLY) throw failure
        session = null
        if (fault == StoreFault.CLEAR_AFTER_APPLY) throw failure
    }
}

private fun Any.failureReason(): CustomerAccountFailure? = when (this) {
    is CustomerAccountResult.Failure -> reason
    is CustomerProfileUpdateResult.Failure -> reason
    is CustomerAddressMutationResult.Failure -> reason
    else -> null
}

private fun gatewaySession(expiresAt: Instant, marker: String) = CustomerSession(
    SensitiveToken.from(marker),
    SensitiveToken.from("synthetic-refresh"),
    SensitiveToken.from("synthetic-id"),
    expiresAt
)

private fun gatewayConfiguration() = CustomerAccountConfiguration(
    "synthetic-public-client", "https://shop.example/customer-account",
    "https://shop.example/authentication/oauth/authorize", "https://shop.example/authentication/oauth/token",
    "https://shop.example/authentication/logout", "https://shop.example/customer/api/2026-07/graphql",
    "shop.123456.synthetic://oauth/callback", "Test-Android", REQUIRED_CUSTOMER_ACCOUNT_SCOPES
)

private val NOW = Instant.parse("2026-10-06T12:00:00Z")
private const val ADDRESS_ID = "gid://shopify/CustomerAddress/1"
private val DRAFT = CustomerAddressDraft(
    "Synthetic", "Customer", null, "Synthetic street", null,
    "Istanbul", "34000", null, "TR"
)
