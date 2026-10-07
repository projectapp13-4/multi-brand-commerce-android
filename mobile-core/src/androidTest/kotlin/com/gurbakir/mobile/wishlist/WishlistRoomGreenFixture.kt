package com.gurbakir.mobile.wishlist

import android.content.Context
import androidx.room.Room
import com.gurbakir.mobile.search.LocalCommerceDatabase
import com.gurbakir.storefront.ProductDetailRequest
import com.gurbakir.storefront.StorefrontFailure
import com.gurbakir.storefront.StorefrontMoney
import com.gurbakir.storefront.StorefrontProductDetail
import com.gurbakir.storefront.StorefrontProductGateway
import com.gurbakir.storefront.StorefrontProductVariant
import com.gurbakir.storefront.StorefrontResult
import java.math.BigDecimal
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/** Ignored GREEN-only draft. Real version-2 Room database, existing store/repository APIs, no provider. */
internal class WishlistRoomGreenFixture(context: Context) {
    val partition = WishlistPartition("green-offline", "fixture-market")
    val foreign = WishlistPartition("green-foreign", "fixture-market")
    val database = Room.inMemoryDatabaseBuilder(context, LocalCommerceDatabase::class.java).build()
    val dao = database.wishlistDao()
    val store = RoomWishlistStore(database)
    val gateway = RoomGreenGateway()
    val now = AtomicLong(100L)
    val repository = DefaultWishlistRepository(store, gateway, partition) { now.get() }

    suspend fun seed(vararg rows: StoredWishlistEntry) {
        rows.forEach { store.setSaved(partition, it.productId, true, it.addedAtEpochMillis) }
    }

    suspend fun directInsert(row: StoredWishlistEntry, destination: WishlistPartition = partition) {
        dao.insert(
            WishlistEntity(destination.environmentId, destination.marketId, row.productId, row.addedAtEpochMillis)
        )
    }

    suspend fun directClear() = dao.clear(partition.environmentId, partition.marketId)

    suspend fun directDelete(id: String) = dao.delete(partition.environmentId, partition.marketId, id)

    fun rowsProbe(): RoomGreenRowsProbe = RoomGreenRowsProbe(store, partition)

    suspend fun finishPhysicalReads() {
        gateway.releaseAll()
        gateway.awaitSnapshot { it.active == 0 && it.completed == it.started }
    }

    fun closeDatabase() {
        check(gateway.snapshot().active == 0) { "Release and settle physical reads before closing Room" }
        database.close()
    }
}

/** One continuous subscription. Await after each write; do not demand every conflatable intermediate emission. */
internal class RoomGreenRowsProbe(store: WishlistStore, partition: WishlistPartition) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val values = Channel<List<StoredWishlistEntry>>(Channel.UNLIMITED)
    private val collector: Job = scope.launch { store.observe(partition).collect { values.send(it) } }

    suspend fun await(expected: List<StoredWishlistEntry>) = withTimeout(5_000) {
        var actual = values.receive()
        while (actual != expected) actual = values.receive()
        actual
    }

    suspend fun close() {
        collector.cancelAndJoin()
        scope.cancel()
        values.close()
    }
}

internal enum class RoomGreenReply { PRODUCT, CONNECTION, CONFIGURATION, REMOVED }

internal data class RoomGreenPhysicalSnapshot(val started: Int, val active: Int, val completed: Int, val maximum: Int)

/** Captures immutable product truth on entry. Cancellation does not release a physical callback. */
internal class RoomGreenGateway : StorefrontProductGateway {
    private val lock = Any()
    private val gates = mutableListOf<CompletableDeferred<Unit>>()
    private val replies = mutableMapOf<String, RoomGreenReply>()
    private var active = 0
    private var completed = 0
    private var maximum = 0
    private var autoRelease = false
    private var title = "Initial Room product"
    private var price = "100.00"

    fun setProduct(title: String, price: String = "125.00") = synchronized(lock) {
        this.title = title
        this.price = price
    }

    fun reply(id: String, reply: RoomGreenReply) = synchronized(lock) { replies[id] = reply }

    fun allowImmediateReplies() = synchronized(lock) { autoRelease = true }

    fun holdNewReplies() = synchronized(lock) { autoRelease = false }

    override suspend fun loadProductDetail(request: ProductDetailRequest): StorefrontResult<StorefrontProductDetail?> {
        check(request.isValid()) { "Only valid synthetic product requests belong to this gateway" }
        val gate = CompletableDeferred<Unit>()
        val captured = synchronized(lock) {
            val result = when (replies[request.productId] ?: RoomGreenReply.PRODUCT) {
                RoomGreenReply.PRODUCT -> StorefrontResult.Success(product(request.productId, title, price))

                RoomGreenReply.CONNECTION -> StorefrontResult.Failure(StorefrontFailure.Transport(true))

                RoomGreenReply.CONFIGURATION -> StorefrontResult.Failure(
                    StorefrontFailure.Configuration(setOf("offline"))
                )

                RoomGreenReply.REMOVED -> StorefrontResult.Success(null)
            }
            gates += gate
            active++
            maximum = maxOf(maximum, active)
            if (autoRelease) gate.complete(Unit)
            result
        }
        try {
            suspendCoroutine<Unit> { continuation -> gate.invokeOnCompletion { continuation.resume(Unit) } }
            return captured
        } finally {
            synchronized(lock) {
                completed++
                active--
            }
        }
    }

    fun snapshot(): RoomGreenPhysicalSnapshot = synchronized(lock) {
        RoomGreenPhysicalSnapshot(gates.size, active, completed, maximum)
    }

    suspend fun awaitSnapshot(predicate: (RoomGreenPhysicalSnapshot) -> Boolean) = withTimeout(5_000) {
        while (!predicate(snapshot())) delay(10)
    }

    fun release(callNumber: Int) {
        val gate = synchronized(lock) { gates[callNumber - 1] }
        gate.complete(Unit)
    }

    fun releaseAll() {
        val pending = synchronized(lock) {
            autoRelease = true
            gates.toList()
        }
        pending.forEach { it.complete(Unit) }
    }

    private fun product(id: String, title: String, price: String) = StorefrontProductDetail(
        id = id,
        handle = "offline-${id.substringAfterLast('/')}",
        title = title,
        description = "Public offline Room fixture",
        availableForSale = true,
        options = emptyList(),
        variants = listOf(
            StorefrontProductVariant(
                id = "gid://shopify/ProductVariant/${id.substringAfterLast('/')}",
                title = "Default",
                availableForSale = true,
                currentlyNotInStock = false,
                price = StorefrontMoney(BigDecimal(price), "TRY"),
                compareAtPrice = null,
                image = null,
                selectedOptions = emptyList()
            )
        ),
        media = emptyList()
    )
}

internal fun roomGreenId(number: Int) = "gid://shopify/Product/$number"
