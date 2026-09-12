package com.gurbakir.mobile.wishlist

import com.gurbakir.mobile.product.productFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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
    val membership =
        MutableStateFlow<WishlistMembershipState>(WishlistMembershipState.Available(emptySet()))

    override fun observeMembership(): Flow<WishlistMembershipState> = membership

    override suspend fun setSaved(productId: String, saved: Boolean): WishlistMutationResult =
        WishlistMutationResult.Success

    override suspend fun load(forceRefresh: Boolean): WishlistLoadResult {
        loadCount += 1
        return loadResult
    }

    override suspend fun clear(): WishlistMutationResult = WishlistMutationResult.Success
}
