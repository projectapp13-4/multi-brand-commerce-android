package com.gurbakir.mobile.wishlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import com.gurbakir.mobile.product.productFixture
import com.gurbakir.storefront.ProductDetailRequest
import com.gurbakir.storefront.StorefrontMoney
import com.gurbakir.storefront.StorefrontProductDetail
import com.gurbakir.storefront.StorefrontProductGateway
import com.gurbakir.storefront.StorefrontResult
import java.math.BigDecimal
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

internal val WAVE5_PARTITION = WishlistPartition("fixture-environment", "fixture-market")

internal fun wave5Entries(count: Int): List<StoredWishlistEntry> =
    (1..count).map { StoredWishlistEntry("gid://shopify/Product/$it", 100L - it) }

/** Bounded local Room-like identity/order/emission double; it performs no disk/provider operation. */
internal class PartitionedHydrationStore(initial: List<StoredWishlistEntry>) : WishlistStore {
    private val states = mutableMapOf(WAVE5_PARTITION to MutableStateFlow<LocalSnapshot>(LocalSnapshot.Rows(initial)))
    var writes = 0
        private set
    var clears = 0
        private set

    override fun observe(partition: WishlistPartition): Flow<List<StoredWishlistEntry>> =
        state(partition).map { it.rows() }

    override suspend fun load(partition: WishlistPartition): List<StoredWishlistEntry> = state(partition).value.rows()

    override suspend fun setSaved(
        partition: WishlistPartition,
        productId: String,
        saved: Boolean,
        addedAtEpochMillis: Long
    ) {
        val current = state(partition)
        val before = current.value.rows()
        val next = if (saved) {
            if (before.any { it.productId == productId }) {
                before
            } else {
                before + StoredWishlistEntry(productId, addedAtEpochMillis)
            }
        } else {
            before.filterNot { it.productId == productId }
        }
        current.value = LocalSnapshot.Rows(next.ordered())
        writes++
    }

    override suspend fun clear(partition: WishlistPartition) {
        state(partition).value.rows()
        state(partition).value = LocalSnapshot.Rows(emptyList())
        clears++
    }

    fun seed(partition: WishlistPartition, entries: List<StoredWishlistEntry>) {
        state(partition).value = LocalSnapshot.Rows(entries.ordered())
    }

    fun fail(partition: WishlistPartition) {
        state(partition).value = LocalSnapshot.Unavailable
    }

    private fun state(partition: WishlistPartition): MutableStateFlow<LocalSnapshot> =
        states.getOrPut(partition) { MutableStateFlow(LocalSnapshot.Rows(emptyList())) }
}

private sealed interface LocalSnapshot {
    data class Rows(val values: List<StoredWishlistEntry>) : LocalSnapshot
    data object Unavailable : LocalSnapshot
}

private fun LocalSnapshot.rows(): List<StoredWishlistEntry> = when (this) {
    is LocalSnapshot.Rows -> values.ordered()
    LocalSnapshot.Unavailable -> error("Synthetic local wishlist failure")
}

private fun List<StoredWishlistEntry>.ordered(): List<StoredWishlistEntry> =
    sortedWith(compareByDescending<StoredWishlistEntry> { it.addedAtEpochMillis }.thenBy { it.productId })

/** Physical read counters remain active until each controlled read actually exits, including after cancellation. */
internal class HeldHydrationGateway : StorefrontProductGateway {
    private val calls = mutableListOf<HydrationRead>()
    private val outcomes = mutableMapOf<String, StorefrontResult<StorefrontProductDetail?>>()
    var holdReads = true
    var cooperative = true
    var title = "Initial synthetic product"
    var available = true
    var price = "100.00"
    var active = 0
        private set
    var maximumActive = 0
        private set
    var completed = 0
        private set
    var canceled = 0
        private set
    val started: Int get() = calls.size
    val requestedIds: List<String> get() = calls.map { it.productId }

    override suspend fun loadProductDetail(request: ProductDetailRequest): StorefrontResult<StorefrontProductDetail?> {
        check(request.isValid()) { "All gateway inputs must be exact synthetic Product GIDs" }
        val outcome = outcomes[request.productId] ?: StorefrontResult.Success(product(request.productId))
        if (outcome is StorefrontResult.Success) check(outcome.value?.id?.let { it == request.productId } != false)
        val read = HydrationRead(request.productId, outcome, cooperative)
        calls += read
        active++
        maximumActive = maxOf(maximumActive, active)
        if (!holdReads) read.release.complete(Unit)
        try {
            if (read.cooperative) {
                read.release.await()
            } else {
                suspendCoroutine<Unit> { continuation ->
                    read.release.invokeOnCompletion { continuation.resume(Unit) }
                }
            }
            completed++
            return read.outcome
        } catch (error: CancellationException) {
            canceled++
            throw error
        } finally {
            active--
        }
    }

    fun result(productId: String, result: StorefrontResult<StorefrontProductDetail?>) {
        outcomes[productId] = result
    }

    fun releaseThrough(startedCount: Int) {
        calls.take(startedCount).forEach { it.release.complete(Unit) }
    }

    fun releaseAll() {
        holdReads = false
        calls.forEach { it.release.complete(Unit) }
    }

    private fun product(id: String): StorefrontProductDetail = productFixture().copy(
        id = id,
        handle = "synthetic-${id.substringAfterLast('/')}",
        title = title,
        availableForSale = available,
        variants = productFixture().variants.mapIndexed { index, variant ->
            variant.copy(
                id = "gid://shopify/ProductVariant/${id.substringAfterLast('/')}${index + 1}",
                availableForSale = available,
                price = StorefrontMoney(BigDecimal(price), "TRY"),
                image = null
            )
        },
        media = emptyList()
    )
}

private data class HydrationRead(
    val productId: String,
    val outcome: StorefrontResult<StorefrontProductDetail?>,
    val cooperative: Boolean,
    val release: CompletableDeferred<Unit> = CompletableDeferred()
)

internal class WishlistHydrationFixture(count: Int = 3) : AutoCloseable {
    val initial = wave5Entries(count)
    val store = PartitionedHydrationStore(initial)
    val gateway = HeldHydrationGateway()
    var now = 200L
    val repository = DefaultWishlistRepository(store, gateway, WAVE5_PARTITION) { now }
    private val modelStores = mutableMapOf<ViewModel, ViewModelStore>()
    private val jobs = mutableListOf<Job>()

    fun wishlist(): WishlistViewModel = WishlistViewModel(repository).also { own(it) }

    fun membership(): WishlistMembershipViewModel = WishlistMembershipViewModel(repository).also { own(it) }

    fun clearModel(model: ViewModel) {
        checkNotNull(modelStores.remove(model)).clear()
    }

    fun <T : Job> track(job: T): T = job.also { jobs += it }

    private fun own(model: ViewModel) {
        modelStores[model] = ViewModelStore().apply { put("owned-fixture-model", model) }
    }

    override fun close() {
        modelStores.values.forEach(ViewModelStore::clear)
        jobs.forEach { it.cancel() }
        gateway.releaseAll()
    }
}
