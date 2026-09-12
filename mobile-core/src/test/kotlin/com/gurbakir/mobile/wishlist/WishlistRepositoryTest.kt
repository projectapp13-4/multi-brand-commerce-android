package com.gurbakir.mobile.wishlist

import android.database.sqlite.SQLiteException
import com.gurbakir.mobile.product.productFixture
import com.gurbakir.storefront.ProductDetailRequest
import com.gurbakir.storefront.StorefrontFailure
import com.gurbakir.storefront.StorefrontProductDetail
import com.gurbakir.storefront.StorefrontProductGateway
import com.gurbakir.storefront.StorefrontResult
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test

class WishlistRepositoryTest {
    private val partition = WishlistPartition("development", "TR")

    @Test
    fun `membership is idempotent partitioned and clearable`() = runTest {
        val store = InMemoryWishlistStore()
        var now = 10L
        val repository = DefaultWishlistRepository(store, FakeGateway(), partition) { now }
        val productId = productFixture().id

        repository.setSaved(productId, true)
        now = 20L
        repository.setSaved(productId, true)

        val membership = assertInstanceOf(
            WishlistMembershipState.Available::class.java,
            repository.observeMembership().first()
        )
        assertEquals(setOf(productId), membership.productIds)
        assertEquals(10L, store.load(partition).single().addedAtEpochMillis)
        repository.clear()
        assertEquals(emptyList<StoredWishlistEntry>(), store.load(partition))
    }

    @Test
    fun `load rehydrates current removed and failed products without deleting identities`() = runTest {
        val existing = productFixture()
        val removedId = "gid://shopify/Product/2"
        val failedId = "gid://shopify/Product/3"
        val store =
            InMemoryWishlistStore(
                listOf(
                    StoredWishlistEntry(existing.id, 30L),
                    StoredWishlistEntry(removedId, 20L),
                    StoredWishlistEntry(failedId, 10L)
                )
            )
        val gateway =
            FakeGateway(
                mutableMapOf(
                    existing.id to StorefrontResult.Success(existing),
                    removedId to StorefrontResult.Success(null),
                    failedId to StorefrontResult.Failure(StorefrontFailure.Transport(true))
                )
            )
        val repository = DefaultWishlistRepository(store, gateway, partition) { 100L }

        val content = assertInstanceOf(WishlistLoadResult.Content::class.java, repository.load())

        assertEquals(existing, content.entries[0].product)
        assertEquals(WishlistItemIssue.REMOVED, content.entries[1].issue)
        assertEquals(WishlistItemIssue.CONNECTION, content.entries[2].issue)
        assertEquals(3, store.load(partition).size)
    }

    @Test
    fun `invalid identifiers never enter storage and database failure is explicit`() = runTest {
        val repository =
            DefaultWishlistRepository(ThrowingWishlistStore(), FakeGateway(), partition) { 1L }

        assertEquals(WishlistMutationResult.InvalidProduct, repository.setSaved("not-a-gid", true))
        assertEquals(WishlistMutationResult.StorageUnavailable, repository.setSaved(productFixture().id, true))
        assertEquals(WishlistLoadResult.StorageUnavailable, repository.load())
        assertEquals(WishlistMembershipState.StorageUnavailable, repository.observeMembership().first())
    }

    @Test
    fun `hydration preserves stored order while bounding concurrent requests`() = runTest {
        val entries = (1L..9L).map { id -> StoredWishlistEntry("gid://shopify/Product/$id", 100L - id) }
        val gateway = TrackingGateway()
        val repository = DefaultWishlistRepository(InMemoryWishlistStore(entries), gateway, partition) { 1L }

        val content = assertInstanceOf(WishlistLoadResult.Content::class.java, repository.load())

        assertEquals(entries.map(StoredWishlistEntry::productId), content.entries.map(WishlistResolvedEntry::productId))
        assertEquals(4, gateway.maximumConcurrentCalls)
        assertEquals(entries.size, gateway.calls)
    }

    @Test
    fun `concurrent automatic loads coalesce and explicit refresh retries`() = runTest {
        val entries = (1L..3L).map { id -> StoredWishlistEntry("gid://shopify/Product/$id", id) }
        val gateway = TrackingGateway()
        val repository = DefaultWishlistRepository(InMemoryWishlistStore(entries), gateway, partition) { 1L }

        val first = async { repository.load() }
        val second = async { repository.load() }
        assertEquals(first.await(), second.await())
        assertEquals(entries.size, gateway.calls)

        repository.load(forceRefresh = true)
        assertEquals(entries.size * 2, gateway.calls)
    }

    @Test
    fun `successful membership mutation invalidates the hydrated snapshot`() = runTest {
        val existing = StoredWishlistEntry("gid://shopify/Product/1", 1L)
        val addedId = "gid://shopify/Product/2"
        val gateway = TrackingGateway()
        val repository = DefaultWishlistRepository(InMemoryWishlistStore(listOf(existing)), gateway, partition) { 2L }
        repository.load()

        repository.setSaved(addedId, true)
        val content = assertInstanceOf(WishlistLoadResult.Content::class.java, repository.load())

        assertEquals(listOf(existing.productId, addedId), content.entries.map(WishlistResolvedEntry::productId))
        assertEquals(3, gateway.calls)
    }
}

private class InMemoryWishlistStore(initial: List<StoredWishlistEntry> = emptyList()) : WishlistStore {
    private val values = MutableStateFlow(initial)

    override fun observe(partition: WishlistPartition): Flow<List<StoredWishlistEntry>> = values

    override suspend fun load(partition: WishlistPartition): List<StoredWishlistEntry> = values.value

    override suspend fun setSaved(
        partition: WishlistPartition,
        productId: String,
        saved: Boolean,
        addedAtEpochMillis: Long
    ) {
        values.value =
            if (saved) {
                values.value.takeIf { entries -> entries.any { it.productId == productId } }
                    ?: (values.value + StoredWishlistEntry(productId, addedAtEpochMillis))
            } else {
                values.value.filterNot { it.productId == productId }
            }
    }

    override suspend fun clear(partition: WishlistPartition) {
        values.value = emptyList()
    }
}

private class ThrowingWishlistStore : WishlistStore {
    override fun observe(partition: WishlistPartition): Flow<List<StoredWishlistEntry>> =
        kotlinx.coroutines.flow.flow { throw SQLiteException("synthetic") }

    override suspend fun load(partition: WishlistPartition): List<StoredWishlistEntry> =
        throw SQLiteException("synthetic")

    override suspend fun setSaved(
        partition: WishlistPartition,
        productId: String,
        saved: Boolean,
        addedAtEpochMillis: Long
    ) = throw SQLiteException("synthetic")

    override suspend fun clear(partition: WishlistPartition) = throw SQLiteException("synthetic")
}

private class FakeGateway(
    private val results: MutableMap<String, StorefrontResult<StorefrontProductDetail?>> = mutableMapOf()
) : StorefrontProductGateway {
    override suspend fun loadProductDetail(request: ProductDetailRequest): StorefrontResult<StorefrontProductDetail?> =
        results[request.productId] ?: StorefrontResult.Success(productFixture())
}

private class TrackingGateway : StorefrontProductGateway {
    var calls = 0
        private set
    var maximumConcurrentCalls = 0
        private set
    private var concurrentCalls = 0

    override suspend fun loadProductDetail(request: ProductDetailRequest): StorefrontResult<StorefrontProductDetail?> {
        calls += 1
        concurrentCalls += 1
        maximumConcurrentCalls = maxOf(maximumConcurrentCalls, concurrentCalls)
        delay(10)
        concurrentCalls -= 1
        return StorefrontResult.Success(productFixture().copy(id = request.productId))
    }
}
