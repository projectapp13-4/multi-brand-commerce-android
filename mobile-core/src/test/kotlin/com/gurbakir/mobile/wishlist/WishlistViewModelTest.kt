package com.gurbakir.mobile.wishlist

import com.gurbakir.mobile.product.productFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WishlistViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `refresh retains remote failures and local remove works without another read`() = runTest(dispatcher) {
        val product = productFixture()
        val failedId = "gid://shopify/Product/2"
        val repository =
            FakeWishlistRepository(
                WishlistLoadResult.Content(
                    listOf(
                        WishlistResolvedEntry(product.id, 2L, product = product),
                        WishlistResolvedEntry(failedId, 1L, issue = WishlistItemIssue.CONNECTION)
                    )
                )
            )
        val viewModel = WishlistViewModel(repository)
        advanceUntilIdle()

        assertEquals(2, viewModel.state.value.entries.size)
        viewModel.remove(failedId)
        advanceUntilIdle()

        assertEquals(listOf(product.id), viewModel.state.value.entries.map { it.productId })
        assertEquals(1, repository.loadCount)
        assertFalse(viewModel.state.value.loading)
    }

    @Test
    fun `clear and storage failure have honest states`() = runTest(dispatcher) {
        val repository =
            FakeWishlistRepository(
                WishlistLoadResult.Content(
                    listOf(WishlistResolvedEntry(productFixture().id, 1L, product = productFixture()))
                )
            )
        val viewModel = WishlistViewModel(repository)
        advanceUntilIdle()
        viewModel.clear()
        advanceUntilIdle()
        assertEquals(emptyList<WishlistResolvedEntry>(), viewModel.state.value.entries)

        repository.loadResult = WishlistLoadResult.StorageUnavailable
        viewModel.refresh()
        advanceUntilIdle()
        assertFalse(viewModel.state.value.storageAvailable)
    }

    @Test
    fun `membership changes reload saved products without recreating the view model`() = runTest(dispatcher) {
        val repository = FakeWishlistRepository(WishlistLoadResult.Content(emptyList()))
        val viewModel = WishlistViewModel(repository)
        advanceUntilIdle()
        assertEquals(emptyList<WishlistResolvedEntry>(), viewModel.state.value.entries)

        val product = productFixture()
        repository.loadResult =
            WishlistLoadResult.Content(
                listOf(WishlistResolvedEntry(product.id, 1L, product = product))
            )
        repository.membership.value = WishlistMembershipState.Available(setOf(product.id))
        advanceUntilIdle()

        assertEquals(listOf(product.id), viewModel.state.value.entries.map { it.productId })
        assertEquals(2, repository.loadCount)
    }
}

private class FakeWishlistRepository(var loadResult: WishlistLoadResult) : WishlistRepository {
    var loadCount = 0
    val membership = MutableStateFlow<WishlistMembershipState>(
        when (val initial = loadResult) {
            is WishlistLoadResult.Content -> WishlistMembershipState.Available(
                initial.entries.mapTo(linkedSetOf()) { it.productId }
            )

            WishlistLoadResult.StorageUnavailable -> WishlistMembershipState.StorageUnavailable
        }
    )

    override fun observeMembership(): Flow<WishlistMembershipState> = membership

    override fun observeLocalEntries(): Flow<WishlistLocalState> = membership.map { current ->
        when (current) {
            is WishlistMembershipState.Available -> when (val result = loadResult) {
                is WishlistLoadResult.Content -> WishlistLocalState.Available(
                    result.entries.filter { it.productId in current.productIds }.map {
                        StoredWishlistEntry(it.productId, it.addedAtEpochMillis)
                    }
                )

                WishlistLoadResult.StorageUnavailable -> WishlistLocalState.StorageUnavailable
            }

            WishlistMembershipState.StorageUnavailable -> WishlistLocalState.StorageUnavailable
        }
    }

    override suspend fun setSaved(productId: String, saved: Boolean): WishlistMutationResult {
        if (!saved) {
            val result = loadResult
            if (result is WishlistLoadResult.Content) {
                loadResult = result.copy(entries = result.entries.filterNot { it.productId == productId })
            }
            val current = membership.value
            if (current is WishlistMembershipState.Available) {
                membership.value = current.copy(productIds = current.productIds - productId)
            }
        }
        return WishlistMutationResult.Success
    }

    override suspend fun load(forceRefresh: Boolean): WishlistLoadResult {
        loadCount += 1
        return loadResult
    }

    override suspend fun clear(): WishlistMutationResult {
        loadResult = WishlistLoadResult.Content(emptyList())
        membership.value = WishlistMembershipState.Available(emptySet())
        return WishlistMutationResult.Success
    }
}
