package com.gurbakir.mobile.wishlist

import androidx.lifecycle.ViewModelStore
import com.gurbakir.mobile.product.productFixture
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** Deliberately noncooperative repository callback seam, separate from the real-repository/gateway proofs. */
@OptIn(ExperimentalCoroutinesApi::class)
class WishlistPresentationOwnershipTest {
    private val dispatcher = StandardTestDispatcher()

    @BeforeEach
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `old response after replacement cannot overwrite the current presentation`() = runTest(dispatcher) {
        val fixture = LatePresentationFixture()
        try {
            runCurrent()
            assertEquals(1, fixture.repository.responses.size)
            fixture.model.refresh()
            runCurrent()
            assertEquals(2, fixture.repository.responses.size)
            fixture.repository.responses[1].complete(presentationContent("Current"))
            runCurrent()
            assertEquals("Current", fixture.model.state.value.entries.single().product?.title)
            fixture.repository.responses[0].complete(presentationContent("Obsolete"))
            runCurrent()
            assertEquals("Current", fixture.model.state.value.entries.single().product?.title)
            assertFalse(fixture.model.state.value.loading)
        } finally {
            fixture.close()
            runCurrent()
        }
    }

    @Test
    fun `late load completion cannot reset a current local mutation flag`() = runTest(dispatcher) {
        val fixture = LatePresentationFixture()
        try {
            runCurrent()
            fixture.repository.responses[0].complete(presentationContent("Initial"))
            runCurrent()
            fixture.model.refresh()
            runCurrent()
            assertEquals(2, fixture.repository.responses.size)
            fixture.model.remove(productFixture().id)
            runCurrent()
            assertEquals(1, fixture.repository.mutations)
            assertTrue(fixture.model.state.value.mutating)
            fixture.repository.responses[1].complete(presentationContent("Resolved"))
            runCurrent()
            assertTrue(fixture.model.state.value.mutating, "Remote publication must merge with current local work")
            fixture.repository.mutationReply.complete(Unit)
            runCurrent()
            assertFalse(fixture.model.state.value.mutating)
        } finally {
            fixture.close()
            runCurrent()
        }
    }

    @Test
    fun `late load callback cannot erase a newer explicit local storage failure`() = runTest(dispatcher) {
        val fixture = LatePresentationFixture()
        try {
            runCurrent()
            assertEquals(1, fixture.repository.responses.size)
            fixture.repository.membership.value = WishlistMembershipState.StorageUnavailable
            runCurrent()
            assertFalse(fixture.model.state.value.storageAvailable)
            fixture.repository.responses[0].complete(presentationContent("Obsolete"))
            runCurrent()
            assertFalse(fixture.model.state.value.storageAvailable)
            assertTrue(fixture.model.state.value.entries.isEmpty())
        } finally {
            fixture.close()
            runCurrent()
        }
    }
}

private class LatePresentationFixture : AutoCloseable {
    val repository = LatePresentationRepository()
    val model = WishlistViewModel(repository)
    private val store = ViewModelStore().apply { put("owned-late-presentation", model) }

    override fun close() {
        store.clear()
        repository.mutationReply.complete(Unit)
        repository.responses.forEach { it.complete(WishlistLoadResult.Content(emptyList())) }
    }
}

private class LatePresentationRepository : WishlistRepository {
    private val storedEntries = listOf(StoredWishlistEntry(productFixture().id, 1L))
    val membership = MutableStateFlow<WishlistMembershipState>(
        WishlistMembershipState.Available(setOf(productFixture().id))
    )
    val responses = mutableListOf<LateWishlistResponse>()
    val mutationReply = CompletableDeferred<Unit>()
    var mutations = 0
        private set

    override fun observeMembership(): Flow<WishlistMembershipState> = membership

    override fun observeLocalEntries(): Flow<WishlistLocalState> = membership.map { current ->
        when (current) {
            is WishlistMembershipState.Available -> WishlistLocalState.Available(
                storedEntries.filter { it.productId in current.productIds }
            )

            WishlistMembershipState.StorageUnavailable -> WishlistLocalState.StorageUnavailable
        }
    }

    override suspend fun load(forceRefresh: Boolean): WishlistLoadResult = suspendCoroutine { continuation ->
        responses += LateWishlistResponse(continuation)
    }

    override suspend fun setSaved(productId: String, saved: Boolean): WishlistMutationResult {
        mutations++
        mutationReply.await()
        val current = membership.value
        if (!saved && current is WishlistMembershipState.Available) {
            membership.value = current.copy(productIds = current.productIds - productId)
        }
        return WishlistMutationResult.Success
    }

    override suspend fun clear(): WishlistMutationResult {
        membership.value = WishlistMembershipState.Available(emptySet())
        return WishlistMutationResult.Success
    }
}

private class LateWishlistResponse(private val continuation: Continuation<WishlistLoadResult>) {
    private var delivered = false

    fun complete(result: WishlistLoadResult) {
        if (!delivered) {
            delivered = true
            continuation.resume(result)
        }
    }
}

private fun presentationContent(title: String): WishlistLoadResult.Content = WishlistLoadResult.Content(
    listOf(WishlistResolvedEntry(productFixture().id, 1L, product = productFixture().copy(title = title)))
)
