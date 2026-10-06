package com.gurbakir.mobile.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import com.apollographql.apollo.ApolloClient
import com.gurbakir.account.ApolloCustomerAddressGateway
import com.gurbakir.account.ApolloCustomerOrderGateway
import com.gurbakir.account.ApolloCustomerProfileGateway
import com.gurbakir.account.CustomerAddressField
import com.gurbakir.account.CustomerProfileField
import com.gurbakir.account.CustomerSessionResolver
import com.gurbakir.account.oauth.CustomerAccountAuthorizationGrant
import com.gurbakir.account.oauth.CustomerAccountTokenClient
import com.gurbakir.account.oauth.CustomerTokenPayload
import com.gurbakir.account.oauth.CustomerTokenResult
import com.gurbakir.account.session.CustomerAccountSessionCoordinator
import com.gurbakir.account.session.CustomerSession
import com.gurbakir.account.session.CustomerSessionStore
import com.gurbakir.account.session.SensitiveToken
import com.gurbakir.foundation.config.CustomerAccountConfiguration
import com.gurbakir.foundation.config.REQUIRED_CUSTOMER_ACCOUNT_SCOPES
import com.gurbakir.mobile.address.AddressConfirmationType
import com.gurbakir.mobile.address.AddressFormFailure
import com.gurbakir.mobile.address.AddressFormPhase
import com.gurbakir.mobile.address.AddressFormUiState
import com.gurbakir.mobile.address.AddressFormViewModel
import com.gurbakir.mobile.address.AddressListPhase
import com.gurbakir.mobile.address.AddressListViewModel
import com.gurbakir.mobile.address.AddressPostalCodePolicy
import com.gurbakir.mobile.address.AddressTerritoryPolicy
import com.gurbakir.mobile.address.DefaultAddressController
import com.gurbakir.mobile.address.PostalCodeInputMode
import com.gurbakir.mobile.order.DefaultOrderController
import com.gurbakir.mobile.order.OrderDetailPhase
import com.gurbakir.mobile.order.OrderDetailViewModel
import com.gurbakir.mobile.order.OrderFailure
import com.gurbakir.mobile.order.OrderListPhase
import com.gurbakir.mobile.order.OrderListViewModel
import com.gurbakir.mobile.order.TrackingLaunchResult
import com.gurbakir.mobile.profile.DefaultProfileController
import com.gurbakir.mobile.profile.ProfileFailure
import com.gurbakir.mobile.profile.ProfilePhase
import com.gurbakir.mobile.profile.ProfileViewModel
import java.io.Closeable
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.jupiter.api.Assertions.assertAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Connected storage regressions using the old public API. No failure enum added by Wave 3 is
 * referenced here. The real coordinator creates the storage exception; the real gateways,
 * controllers and ViewModels must turn it into a nonbusy result and suppress private presentation.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class Wave3PrivateStorageRecoveryTest {
    @Test
    fun loadedProfileReadFailureSuppressesDraftAndReleasesBusyState() = profileStorageFailure(StoreFault.READ)

    @Test
    fun loadedProfileRenewalWriteFailureSuppressesDraftAndReleasesBusyState() =
        profileStorageFailure(StoreFault.RENEWAL_WRITE)

    private fun profileStorageFailure(fault: StoreFault) = connected { fixture ->
        fixture.enqueue(profileResponse())
        val viewModel = fixture.own(ProfileViewModel(fixture.profileController))
        val effects = fixture.observe(viewModel.effects)
        fixture.await { viewModel.state.value.phase != ProfilePhase.LOADING }
        assertTrue(viewModel.state.value.loaded, "Synthetic profile must load through the real gateway")
        viewModel.onFirstNameChanged("Private draft")
        fixture.store.arm(fault)
        viewModel.save()
        fixture.await { fixture.escaped.isNotEmpty() || !viewModel.state.value.busy }

        val state = viewModel.state.value
        assertAll(
            "Protected profile storage uncertainty suppresses presentation",
            { assertFalse(state.busy) },
            { assertEquals(ProfilePhase.FAILED, state.phase) },
            { assertFalse(state.loaded) },
            { assertEquals("", state.firstName) },
            { assertEquals("", state.lastName) },
            { assertEquals("", state.originalFirstName) },
            { assertEquals("", state.originalLastName) },
            { assertTrue(state.fieldErrors.isEmpty()) },
            { assertNull(state.focusRequest) },
            { assertNull(state.notice) },
            { assertNotNull(state.failure) },
            { assertEquals("SECURE_STORAGE", state.failure?.name) },
            { assertTrue(state.canReload) },
            { assertFalse(state.canSave) },
            { assertTrue(effects.isEmpty()) },
            { assertEquals(1, fixture.requests.size, "Save preflight must not send private HTTP after the fault") },
            { assertEquals(0, fixture.store.clearCount) },
            { assertTrue(fixture.store.faultCount > 0) },
            { fixture.assertNoEscapes() }
        )
        fixture.store.recover()
        fixture.enqueue(profileResponse(firstName = "Recovered"))
        viewModel.reload()
        fixture.await { !viewModel.state.value.busy }
        assertEquals("Recovered", viewModel.state.value.firstName)
        assertTrue(viewModel.state.value.loaded)
        assertEquals(0, fixture.count("CustomerProfileUpdate"))
    }

    @Test
    fun appendedOrdersReadFailureClearsPrivatePagesAndRetriesFromInitialPage() =
        orderPageStorageFailure(StoreFault.READ)

    @Test
    fun appendedOrdersRenewalWriteFailureClearsPrivatePagesAndRetriesFromInitialPage() =
        orderPageStorageFailure(StoreFault.RENEWAL_WRITE)

    private fun orderPageStorageFailure(fault: StoreFault) = connected { fixture ->
        fixture.enqueue(orderPageResponse())
        val viewModel = fixture.own(OrderListViewModel(fixture.orderController))
        val effects = fixture.observe(viewModel.effects)
        fixture.await { !viewModel.state.value.busy }
        assertEquals(1, viewModel.state.value.orders.size)
        assertEquals("cursor-1", viewModel.state.value.nextCursor)
        fixture.store.arm(fault)
        viewModel.loadNextPage()
        fixture.await { fixture.escaped.isNotEmpty() || !viewModel.state.value.busy }

        val state = viewModel.state.value
        assertAll(
            "Storage failure cannot retain an appended private page",
            { assertFalse(state.busy) },
            { assertEquals(OrderListPhase.FAILED, state.phase) },
            { assertTrue(state.orders.isEmpty()) },
            { assertNull(state.nextCursor) },
            { assertFalse(state.loaded) },
            { assertEquals(0, state.addedOrderCount) },
            { assertNotNull(state.failure) },
            { assertEquals("SECURE_STORAGE", state.failure?.name) },
            { assertTrue(effects.isEmpty()) },
            { assertEquals(1, fixture.requests.size) },
            { fixture.assertNoEscapes() }
        )
        fixture.store.recover()
        fixture.enqueue(orderPageResponse(orderId = "2002", nextCursor = null))
        viewModel.retry()
        fixture.await { !viewModel.state.value.busy }
        assertEquals("2002", viewModel.state.value.orders.single().routeId)
        assertNull(fixture.requests.last().variables["after"]?.jsonPrimitive?.contentOrNull)
    }

    @Test
    fun loadedOrderDetailReadFailureReleasesLoadingAndClearsTrackingFeedback() =
        orderDetailStorageFailure(StoreFault.READ)

    @Test
    fun loadedOrderDetailRenewalWriteFailureReleasesLoadingAndClearsTrackingFeedback() =
        orderDetailStorageFailure(StoreFault.RENEWAL_WRITE)

    private fun orderDetailStorageFailure(fault: StoreFault) = connected { fixture ->
        fixture.enqueue(orderDetailResponse())
        val viewModel = fixture.own(OrderDetailViewModel(fixture.orderController))
        val effects = fixture.observe(viewModel.effects)
        viewModel.start(ORDER_ID)
        fixture.await { viewModel.state.value.phase != OrderDetailPhase.LOADING }
        assertNotNull(viewModel.state.value.order)
        viewModel.onTrackingLaunchResult(TrackingLaunchResult.OPENED)
        fixture.store.arm(fault)
        viewModel.retry()
        fixture.await { fixture.escaped.isNotEmpty() || viewModel.state.value.phase != OrderDetailPhase.LOADING }

        val state = viewModel.state.value
        assertAll(
            "Order detail storage recovery is nonbusy and private",
            { assertEquals(OrderDetailPhase.FAILED, state.phase) },
            { assertNull(state.order) },
            { assertNull(state.trackingFeedback) },
            { assertNotNull(state.failure) },
            { assertEquals("SECURE_STORAGE", state.failure?.name) },
            { assertTrue(effects.isEmpty()) },
            { assertEquals(1, fixture.requests.size) },
            { fixture.assertNoEscapes() }
        )
        fixture.store.recover()
        fixture.enqueue(orderDetailResponse())
        viewModel.retry()
        fixture.await { viewModel.state.value.phase != OrderDetailPhase.LOADING }
        assertEquals(ORDER_ID, viewModel.state.value.order?.id)
        assertEquals(ORDER_ID, fixture.requests.last().variables["id"]?.jsonPrimitive?.contentOrNull)
    }

    @Test
    fun loadedAddressListReadFailureSuppressesRowsAndConfirmation() = addressListStorageFailure(StoreFault.READ)

    @Test
    fun loadedAddressListRenewalWriteFailureSuppressesRowsAndConfirmation() =
        addressListStorageFailure(StoreFault.RENEWAL_WRITE)

    private fun addressListStorageFailure(fault: StoreFault) = connected { fixture ->
        fixture.enqueue(addressesResponse())
        val viewModel = fixture.own(AddressListViewModel(fixture.addressController))
        val effects = fixture.observe(viewModel.effects)
        fixture.await { !viewModel.state.value.busy }
        assertEquals(2, viewModel.state.value.addresses.size)
        viewModel.requestConfirmation(ADDRESS_ID, AddressConfirmationType.DELETE)
        assertNotNull(viewModel.state.value.confirmation)
        fixture.store.arm(fault)
        viewModel.confirm()
        fixture.await { fixture.escaped.isNotEmpty() || !viewModel.state.value.busy }

        val state = viewModel.state.value
        assertAll(
            "Address action preflight storage failure clears private list",
            { assertFalse(state.busy) },
            { assertEquals(AddressListPhase.FAILED, state.phase) },
            { assertTrue(state.addresses.isEmpty()) },
            { assertFalse(state.loaded) },
            { assertNull(state.confirmation) },
            { assertNull(state.notice) },
            { assertNotNull(state.failure) },
            { assertEquals("SECURE_STORAGE", state.failure?.name) },
            { assertTrue(state.canReload) },
            { assertTrue(effects.isEmpty()) },
            { assertEquals(1, fixture.requests.size) },
            { assertEquals(0, fixture.count("CustomerAddressDelete")) },
            { fixture.assertNoEscapes() }
        )
        fixture.store.recover()
        fixture.enqueue(addressesResponse(city = "Recovered city"))
        viewModel.reload()
        fixture.await { !viewModel.state.value.busy }
        assertEquals("Recovered city", viewModel.state.value.addresses.first().city)
        assertEquals(0, fixture.count("CustomerAddressDelete"))
    }

    @Test
    fun editAddressReadFailureKeepsImmutableRecoveryTargetAndRechecksAuthorization() =
        editAddressStorageFailure(StoreFault.READ)

    @Test
    fun editAddressRenewalWriteFailureKeepsImmutableRecoveryTargetAndRechecksAuthorization() =
        editAddressStorageFailure(StoreFault.RENEWAL_WRITE)

    private fun editAddressStorageFailure(fault: StoreFault) = connected { fixture ->
        fixture.enqueue(addressesResponse())
        val viewModel = fixture.own(AddressFormViewModel(fixture.addressController, territoryPolicy))
        val effects = fixture.observe(viewModel.effects)
        viewModel.start(ADDRESS_ID)
        fixture.await { !viewModel.state.value.busy }
        assertEquals(ADDRESS_ID, viewModel.state.value.addressId)
        viewModel.update(CustomerAddressField.CITY, "Private draft city")
        fixture.store.arm(fault)
        viewModel.save()
        fixture.await { fixture.escaped.isNotEmpty() || !viewModel.state.value.busy }
        assertFormSuppressed(viewModel.state.value, fixture)
        assertTrue(effects.isEmpty())
        assertEquals(1, fixture.requests.size)

        val readsBeforeRetry = fixture.store.readCount
        viewModel.reload()
        fixture.await { !viewModel.state.value.busy || fixture.escaped.isNotEmpty() }
        assertTrue(
            fixture.store.readCount > readsBeforeRetry,
            "Retry must consult protected authorization instead of opening a local blank New form"
        )
        assertFormSuppressed(viewModel.state.value, fixture)
        assertEquals(0, fixture.count("CustomerAddressCreate"))
        assertEquals(0, fixture.count("CustomerAddressUpdate"))

        fixture.store.recover()
        fixture.enqueue(addressesResponse(city = "Authoritative edit city"))
        viewModel.reload()
        fixture.await { !viewModel.state.value.busy }
        assertEquals(ADDRESS_ID, viewModel.state.value.addressId, "Visible suppression must not change Edit to New")
        assertEquals("Authoritative edit city", viewModel.state.value.input.city)
        assertFalse(viewModel.state.value.isCreate)
        assertEquals(0, fixture.count("CustomerAddressCreate"))
        assertEquals(0, fixture.count("CustomerAddressUpdate"))
        assertTrue(effects.isEmpty())
    }

    @Test
    fun newAddressStorageRetryRechecksAuthorizationBeforeRestoringMutationControls() = connected { fixture ->
        val viewModel = fixture.own(AddressFormViewModel(fixture.addressController, territoryPolicy))
        val effects = fixture.observe(viewModel.effects)
        viewModel.start(null)
        fixture.await { !viewModel.state.value.busy }
        assertTrue(viewModel.state.value.loaded, "Normal initial New form intentionally remains local")
        assertEquals(0, fixture.store.readCount)
        assertEquals(0, fixture.requests.size)
        fillNewAddress(viewModel)
        fixture.store.arm(StoreFault.READ)
        viewModel.save()
        fixture.await { fixture.escaped.isNotEmpty() || !viewModel.state.value.busy }
        assertFormSuppressed(viewModel.state.value, fixture)
        assertTrue(effects.isEmpty())
        assertEquals(0, fixture.requests.size)

        val readsBeforeRetry = fixture.store.readCount
        viewModel.reload()
        fixture.await { !viewModel.state.value.busy || fixture.escaped.isNotEmpty() }
        assertTrue(fixture.store.readCount > readsBeforeRetry, "Storage Retry must recheck New-form authorization")
        assertFormSuppressed(viewModel.state.value, fixture)

        fixture.store.recover()
        fixture.enqueue(addressesResponse())
        viewModel.reload()
        fixture.await { !viewModel.state.value.busy }
        assertTrue(viewModel.state.value.loaded)
        assertTrue(viewModel.state.value.isCreate)
        assertNull(viewModel.state.value.addressId)
        assertEquals("", viewModel.state.value.input.address1)
        assertEquals(0, fixture.count("CustomerAddressCreate"))
        assertEquals(0, fixture.count("CustomerAddressUpdate"))
        assertTrue(effects.isEmpty())
    }

    @Test
    fun newAddressRecoveryKeepsAuthorizationGateAfterTransientRereadFailure() = connected { fixture ->
        val viewModel = fixture.own(AddressFormViewModel(fixture.addressController, territoryPolicy))
        val effects = fixture.observe(viewModel.effects)
        viewModel.start(null)
        fixture.await { !viewModel.state.value.busy }
        fillNewAddress(viewModel)
        fixture.store.arm(StoreFault.READ)
        viewModel.save()
        fixture.await { fixture.escaped.isNotEmpty() || !viewModel.state.value.busy }
        assertFormSuppressed(viewModel.state.value, fixture)

        fixture.store.recover()
        fixture.enqueueHttpFailure()
        viewModel.reload()
        fixture.await { !viewModel.state.value.busy }
        assertEquals(AddressFormFailure.CONNECTION, viewModel.state.value.failure)
        assertFalse(viewModel.state.value.loaded)
        assertFalse(viewModel.state.value.canSave)
        assertEquals(1, fixture.requests.size)

        fixture.store.arm(StoreFault.READ)
        val readsBeforeRetry = fixture.store.readCount
        viewModel.reload()
        fixture.await { fixture.escaped.isNotEmpty() || !viewModel.state.value.busy }
        assertTrue(
            fixture.store.readCount > readsBeforeRetry,
            "A transient recovery failure must not turn a later Retry into local New-form loading"
        )
        assertFormSuppressed(viewModel.state.value, fixture)
        assertEquals(1, fixture.requests.size)
        assertEquals(0, fixture.count("CustomerAddressCreate"))
        assertEquals(0, fixture.count("CustomerAddressUpdate"))
        assertTrue(effects.isEmpty())
    }

    @Test
    fun confirmedDeleteReloadStorageFailureHidesNoticeWithoutReplay() = connected { fixture ->
        fixture.enqueue(addressesResponse())
        val viewModel = fixture.own(AddressListViewModel(fixture.addressController))
        fixture.await { !viewModel.state.value.busy }
        fixture.enqueue(addressesResponse())
        fixture.enqueue(
            """{"data":{"customerAddressDelete":{"deletedAddressId":"$ADDRESS_ID","userErrors":[]}}}"""
        )
        // Real reads: current-address preflight, delete authorization, then post-confirmation list.
        fixture.store.armReadAt(fixture.store.readCount + 3)
        viewModel.requestConfirmation(ADDRESS_ID, AddressConfirmationType.DELETE)
        viewModel.confirm()
        fixture.await { fixture.escaped.isNotEmpty() || !viewModel.state.value.busy }

        val state = viewModel.state.value
        assertAll(
            "A later storage fault hides private presentation without reversing known deletion",
            { assertFalse(state.busy) },
            { assertEquals(AddressListPhase.FAILED, state.phase) },
            { assertTrue(state.addresses.isEmpty()) },
            { assertFalse(state.loaded) },
            { assertNull(state.notice) },
            { assertNull(state.confirmation) },
            { assertNotNull(state.failure) },
            { assertEquals("SECURE_STORAGE", state.failure?.name) },
            { assertEquals(1, fixture.count("CustomerAddressDelete")) },
            { assertEquals(3, fixture.requests.size) },
            { fixture.assertNoEscapes() }
        )
        fixture.store.recover()
        fixture.enqueue(addressesResponse(includeEditedAddress = false))
        viewModel.reload()
        fixture.await { !viewModel.state.value.busy }
        assertEquals(listOf(OTHER_ADDRESS_ID), viewModel.state.value.addresses.map { it.id })
        assertNull(viewModel.state.value.notice)
        assertEquals(1, fixture.count("CustomerAddressDelete"), "Reload must not replay the confirmed mutation")
    }

    @Test
    fun ordinaryProfileUnconfirmedSaveRetainsDraftUntilExplicitReload() = connected { fixture ->
        fixture.enqueue(profileResponse())
        val viewModel = fixture.own(ProfileViewModel(fixture.profileController))
        fixture.await { !viewModel.state.value.busy }
        viewModel.onFirstNameChanged("Retained draft")
        fixture.enqueue(profileResponse())
        fixture.enqueueHttpFailure()
        viewModel.save()
        fixture.await { !viewModel.state.value.busy }
        assertEquals(ProfileFailure.SAVE_UNCONFIRMED, viewModel.state.value.failure)
        assertEquals("Retained draft", viewModel.state.value.firstName)
        assertTrue(viewModel.state.value.loaded)
        assertFalse(viewModel.state.value.canSave)
        assertEquals(1, fixture.count("CustomerProfileUpdate"))
        fixture.enqueue(profileResponse(firstName = "Reloaded"))
        viewModel.reload()
        fixture.await { !viewModel.state.value.busy }
        assertEquals("Reloaded", viewModel.state.value.firstName)
        assertEquals(1, fixture.count("CustomerProfileUpdate"))
    }

    @Test
    fun ordinaryProfileValidationAndConflictPreserveUserDraftWithoutMutation() = connected { fixture ->
        fixture.enqueue(profileResponse())
        val viewModel = fixture.own(ProfileViewModel(fixture.profileController))
        fixture.await { !viewModel.state.value.busy }
        viewModel.onFirstNameChanged("Invalid\nname")
        viewModel.save()
        assertTrue(viewModel.state.value.fieldErrors.isNotEmpty())
        assertEquals(CustomerProfileField.FIRST_NAME, viewModel.state.value.focusRequest)
        assertEquals(1, fixture.requests.size)
        viewModel.onFirstNameChanged("Retained conflict draft")
        fixture.enqueue(profileResponse(firstName = "Changed elsewhere"))
        viewModel.save()
        fixture.await { !viewModel.state.value.busy }
        assertEquals(ProfileFailure.CONFLICT, viewModel.state.value.failure)
        assertEquals("Retained conflict draft", viewModel.state.value.firstName)
        assertTrue(viewModel.state.value.loaded)
        assertEquals(0, fixture.count("CustomerProfileUpdate"))
    }

    @Test
    fun ordinaryOrderAppendConnectionFailureRetainsRowsAndRetriesSameCursor() = connected { fixture ->
        fixture.enqueue(orderPageResponse())
        val viewModel = fixture.own(OrderListViewModel(fixture.orderController))
        fixture.await { !viewModel.state.value.busy }
        fixture.enqueueHttpFailure()
        viewModel.loadNextPage()
        fixture.await { !viewModel.state.value.busy }
        assertEquals(OrderFailure.CONNECTION, viewModel.state.value.failure)
        assertEquals(listOf("1001"), viewModel.state.value.orders.map { it.routeId })
        assertEquals("cursor-1", viewModel.state.value.nextCursor)
        assertTrue(viewModel.state.value.loaded)
        fixture.enqueue(orderPageResponse(orderId = "2002", nextCursor = null))
        viewModel.retry()
        fixture.await { !viewModel.state.value.busy }
        assertEquals(listOf("1001", "2002"), viewModel.state.value.orders.map { it.routeId })
        assertEquals("cursor-1", fixture.requests[1].variables["after"]?.jsonPrimitive?.contentOrNull)
        assertEquals("cursor-1", fixture.requests[2].variables["after"]?.jsonPrimitive?.contentOrNull)
    }

    @Test
    fun ordinaryUnownedOrderRemainsUnavailableWithoutClearingSession() = connected { fixture ->
        fixture.enqueue("""{"errors":[{"message":"Synthetic unowned order","extensions":{"code":"ACCESS_DENIED"}}]}""")
        val viewModel = fixture.own(OrderDetailViewModel(fixture.orderController))
        val effects = fixture.observe(viewModel.effects)
        viewModel.start(ORDER_ID)
        fixture.await { viewModel.state.value.phase != OrderDetailPhase.LOADING }
        assertEquals(OrderDetailPhase.UNAVAILABLE, viewModel.state.value.phase)
        assertNull(viewModel.state.value.order)
        assertEquals(0, fixture.store.clearCount)
        assertNotNull(fixture.store.session)
        assertTrue(effects.isEmpty())
        assertEquals(1, fixture.requests.size)
    }

    @Test
    fun ordinaryAddressUnconfirmedUpdateRetainsDraftUntilAuthoritativeReload() = connected { fixture ->
        fixture.enqueue(addressesResponse())
        val viewModel = fixture.own(AddressFormViewModel(fixture.addressController, territoryPolicy))
        val effects = fixture.observe(viewModel.effects)
        viewModel.start(ADDRESS_ID)
        fixture.await { !viewModel.state.value.busy }
        viewModel.update(CustomerAddressField.CITY, "Retained draft city")
        fixture.enqueue(addressesResponse())
        fixture.enqueueHttpFailure()
        fixture.enqueue(addressesResponse())
        viewModel.save()
        fixture.await { !viewModel.state.value.busy }
        assertEquals(AddressFormFailure.SAVE_UNCONFIRMED, viewModel.state.value.failure)
        assertEquals("Retained draft city", viewModel.state.value.input.city)
        assertEquals(ADDRESS_ID, viewModel.state.value.addressId)
        assertTrue(viewModel.state.value.loaded)
        assertFalse(viewModel.state.value.canSave)
        assertTrue(effects.isEmpty())
        assertEquals(1, fixture.count("CustomerAddressUpdate"))
        fixture.enqueue(addressesResponse(city = "Reloaded city"))
        viewModel.reload()
        fixture.await { !viewModel.state.value.busy }
        assertEquals("Reloaded city", viewModel.state.value.input.city)
        assertEquals(1, fixture.count("CustomerAddressUpdate"))
    }

    private fun assertFormSuppressed(state: AddressFormUiState, fixture: ConnectedFixture) {
        assertAll(
            "Address storage recovery cannot expose or enable the retained private form",
            { assertFalse(state.busy) },
            { assertEquals(AddressFormPhase.FAILED, state.phase) },
            { assertFalse(state.loaded) },
            { assertNull(state.addressId) },
            { assertNull(state.original) },
            {
                assertEquals(
                    listOf("", "", "", "", "", "", "", ""),
                    state.input.let {
                        listOf(
                            it.firstName,
                            it.lastName,
                            it.company,
                            it.address1,
                            it.address2,
                            it.city,
                            it.zip,
                            it.phoneNumber
                        )
                    }
                )
            },
            { assertFalse(state.makeDefault) },
            { assertTrue(state.fieldErrors.isEmpty()) },
            { assertNull(state.focusRequest) },
            { assertNotNull(state.failure) },
            { assertEquals("SECURE_STORAGE", state.failure?.name) },
            { assertFalse(state.canSave) },
            { assertTrue(state.canReload) },
            { fixture.assertNoEscapes() }
        )
    }

    private fun fillNewAddress(viewModel: AddressFormViewModel) {
        mapOf(
            CustomerAddressField.FIRST_NAME to "Synthetic",
            CustomerAddressField.LAST_NAME to "Customer",
            CustomerAddressField.ADDRESS1 to "Synthetic street",
            CustomerAddressField.CITY to "Synthetic city",
            CustomerAddressField.ZIP to "12345",
            CustomerAddressField.PHONE to "+15551234567"
        ).forEach { (field, value) -> viewModel.update(field, value) }
        viewModel.setMakeDefault(true)
    }

    private fun connected(block: (ConnectedFixture) -> Unit) {
        ConnectedFixture().use { fixture ->
            block(fixture)
            fixture.assertNoEscapes()
        }
    }

    private enum class StoreFault {
        READ,
        RENEWAL_WRITE
    }

    private class FaultSessionStore : CustomerSessionStore {
        var session: CustomerSession? = healthySession()
        var readCount = 0
            private set
        var clearCount = 0
            private set
        var faultCount = 0
            private set
        private var readFailureFrom: Int? = null
        private var writeFailure = false

        fun arm(fault: StoreFault) {
            when (fault) {
                StoreFault.READ -> armReadAt(readCount + 1)

                StoreFault.RENEWAL_WRITE -> {
                    session = healthySession().copy(expiresAt = now.minusSeconds(1))
                    writeFailure = true
                }
            }
        }

        fun armReadAt(read: Int) {
            readFailureFrom = read
        }

        fun recover() {
            readFailureFrom = null
            writeFailure = false
        }

        override suspend fun read(): CustomerSession? {
            readCount += 1
            if (readFailureFrom?.let { readCount >= it } == true) {
                faultCount += 1
                error("Synthetic protected read refusal")
            }
            return session
        }

        override suspend fun write(session: CustomerSession) {
            if (writeFailure) {
                faultCount += 1
                error("Synthetic protected commit refusal")
            }
            this.session = session
        }

        override suspend fun clear() {
            clearCount += 1
            session = null
        }
    }

    private class ConnectedFixture : Closeable {
        val scheduler = TestCoroutineScheduler()
        private val dispatcher = StandardTestDispatcher(scheduler)
        private val effectsScope = CoroutineScope(SupervisorJob() + dispatcher)
        private val viewModels = ViewModelStore()
        private var viewModelIndex = 0
        private val testThread = Thread.currentThread()
        private val priorExceptionHandler = testThread.uncaughtExceptionHandler
        val escaped = mutableListOf<Throwable>()
        val store = FaultSessionStore()
        val requests = CopyOnWriteArrayList<GraphqlRequest>()
        private val responses = LinkedBlockingQueue<MockResponse>()
        private val server = MockWebServer()
        private val client: ApolloClient
        val profileController: DefaultProfileController
        val orderController: DefaultOrderController
        val addressController: DefaultAddressController

        init {
            Dispatchers.setMain(dispatcher)
            // Observe an old ViewModel's exact escape without replacing its controller/result.
            // Every test requires this list to be empty. Captured exceptions can never earn PASS.
            testThread.uncaughtExceptionHandler = Thread.UncaughtExceptionHandler { _, failure ->
                escaped += failure
            }
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val body = Json.parseToJsonElement(request.body.readUtf8()).jsonObject
                    requests += GraphqlRequest(
                        operation = body.getValue("operationName").jsonPrimitive.content,
                        variables = body["variables"]?.jsonObject ?: JsonObject(emptyMap())
                    )
                    return responses.poll(2, TimeUnit.SECONDS)
                        ?: MockResponse().setResponseCode(500)
                }
            }
            server.start()
            client = ApolloClient.Builder().serverUrl(server.url("/graphql").toString()).build()
            val coordinator = CustomerAccountSessionCoordinator(
                configuration = configuration(),
                tokenClient = object : CustomerAccountTokenClient {
                    override suspend fun exchange(grant: CustomerAccountAuthorizationGrant): CustomerTokenResult =
                        error("Private recovery must not exchange a new identity")

                    override suspend fun refresh(refreshToken: SensitiveToken): CustomerTokenResult =
                        CustomerTokenResult.Success(
                            CustomerTokenPayload(
                                SensitiveToken.from("synthetic-renewed-access"),
                                null,
                                null,
                                now.plusSeconds(3_600)
                            )
                        )
                },
                sessionStore = store,
                clock = Clock.fixed(now, ZoneOffset.UTC)
            )
            val resolver = CustomerSessionResolver { coordinator.restore() }
            profileController = DefaultProfileController(ApolloCustomerProfileGateway(client, resolver), coordinator)
            orderController = DefaultOrderController(ApolloCustomerOrderGateway(client, resolver), coordinator)
            addressController = DefaultAddressController(
                ApolloCustomerAddressGateway(client, resolver),
                coordinator,
                territoryPolicy
            )
        }

        fun <T : ViewModel> own(viewModel: T): T {
            viewModels.put("private-storage-" + viewModelIndex++, viewModel)
            return viewModel
        }

        fun <T> observe(flow: Flow<T>): MutableList<T> = mutableListOf<T>().also { values ->
            flow.onEach { values += it }.launchIn(effectsScope)
        }

        fun enqueue(body: String) {
            responses.add(
                MockResponse().setResponseCode(200).setHeader("Content-Type", "application/json").setBody(body)
            )
        }

        fun enqueueHttpFailure() {
            responses.add(MockResponse().setResponseCode(500))
        }

        fun count(operation: String): Int = requests.count { it.operation == operation }

        fun await(settled: () -> Boolean) {
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
            do {
                scheduler.runCurrent()
                if (settled()) return
                Thread.sleep(2)
            } while (System.nanoTime() < deadline)
            assertTrue(settled(), "Connected private operation did not settle within its bounded local fixture")
        }

        fun assertNoEscapes() {
            assertTrue(escaped.isEmpty(), "A real ViewModel operation escaped its typed recovery boundary")
        }

        override fun close() {
            try {
                viewModels.clear()
                effectsScope.cancel()
                scheduler.runCurrent()
                client.close()
                server.shutdown()
            } finally {
                testThread.uncaughtExceptionHandler = priorExceptionHandler
                Dispatchers.resetMain()
            }
        }
    }

    private data class GraphqlRequest(val operation: String, val variables: JsonObject) {
        override fun toString(): String = "GraphqlRequest(operation=$operation, variables=<redacted>)"
    }

    private companion object {
        const val ADDRESS_ID = "gid://shopify/CustomerAddress/101"
        const val OTHER_ADDRESS_ID = "gid://shopify/CustomerAddress/202"
        const val ORDER_ID = "gid://shopify/Order/1001"
        val now: Instant = Instant.parse("2026-10-06T09:00:00Z")
        val territoryPolicy = AddressTerritoryPolicy(
            supportedTerritoryCode = "TR",
            postalCodeInputMode = PostalCodeInputMode.NUMERIC,
            postalCodePolicy = AddressPostalCodePolicy { Regex("^[0-9]{5}$").matches(it) }
        )

        fun healthySession() = CustomerSession(
            SensitiveToken.from("synthetic-access"),
            SensitiveToken.from("synthetic-refresh"),
            SensitiveToken.from("synthetic-id"),
            now.plusSeconds(3_600)
        )

        fun configuration() = CustomerAccountConfiguration(
            clientId = "synthetic-public-client",
            issuer = "https://shop.example/customer-account",
            authorizationEndpoint = "https://shop.example/authentication/oauth/authorize",
            tokenEndpoint = "https://shop.example/authentication/oauth/token",
            logoutEndpoint = "https://shop.example/authentication/logout",
            graphqlEndpoint = "https://shop.example/customer/api/2026-07/graphql",
            redirectUri = "shop.123456.example://oauth/callback",
            userAgent = "Synthetic-Android",
            scopes = REQUIRED_CUSTOMER_ACCOUNT_SCOPES
        )

        fun profileResponse(firstName: String = "Synthetic") =
            """{"data":{"customer":{"firstName":"$firstName","lastName":"Customer"}}}"""

        fun orderPageResponse(orderId: String = "1001", nextCursor: String? = "cursor-1"): String {
            val cursor = nextCursor?.let { "\"$it\"" } ?: "null"
            val hasNext = nextCursor != null
            return """{"data":{"customer":{"orders":{
              "nodes":[{"id":"gid://shopify/Order/$orderId","name":"#$orderId",
                "processedAt":"2026-10-01T10:00:00Z","financialStatus":"PAID","fulfillmentStatus":"UNFULFILLED",
                "totalPrice":{"amount":"20.00","currencyCode":"TRY"}}],
              "pageInfo":{"hasNextPage":$hasNext,"endCursor":$cursor}
            }}}}"""
        }

        fun orderDetailResponse(): String = """{"data":{"order":{
          "id":"$ORDER_ID","name":"#1001","processedAt":"2026-10-01T10:00:00Z","cancelledAt":null,
          "financialStatus":"PAID","fulfillmentStatus":"UNFULFILLED",
          "subtotal":{"amount":"20.00","currencyCode":"TRY"},
          "totalPrice":{"amount":"20.00","currencyCode":"TRY"},
          "totalRefunded":{"amount":"0.00","currencyCode":"TRY"},
          "totalShipping":{"amount":"0.00","currencyCode":"TRY"},
          "totalTax":{"amount":"0.00","currencyCode":"TRY"},
          "shippingAddress":{"formatted":["Synthetic Customer","Synthetic street"]},
          "lineItems":{"nodes":[{"id":"gid://shopify/LineItem/1","name":"Synthetic item",
            "variantTitle":null,"quantity":1,"totalPrice":{"amount":"20.00","currencyCode":"TRY"}}],
            "pageInfo":{"hasNextPage":false}},
          "fulfillments":{"nodes":[],"pageInfo":{"hasNextPage":false}}
        }}}"""

        fun addressesResponse(city: String = "Initial city", includeEditedAddress: Boolean = true): String {
            val edited = if (includeEditedAddress) addressNode(ADDRESS_ID, city) + "," else ""
            val other = addressNode(OTHER_ADDRESS_ID, "Other city")
            return """{"data":{"customer":{"defaultAddress":{"id":"$OTHER_ADDRESS_ID"},
              "addresses":{"nodes":[$edited$other],
                "pageInfo":{"hasNextPage":false,"endCursor":null}}}}}"""
        }

        fun addressNode(id: String, city: String): String = """{
          "id":"$id","firstName":"Synthetic","lastName":"Customer","company":"",
          "address1":"Synthetic street","address2":"","city":"$city","zip":"12345",
          "phoneNumber":"+15551234567","territoryCode":"TR","zoneCode":null,
          "formatted":["Synthetic street","Synthetic city"]
        }"""
    }
}
