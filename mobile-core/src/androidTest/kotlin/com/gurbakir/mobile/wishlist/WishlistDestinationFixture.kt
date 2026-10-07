package com.gurbakir.mobile.wishlist

import android.content.Context
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.gurbakir.foundation.config.PrimaryNavigationDestination
import com.gurbakir.mobile.CoreTestTheme
import com.gurbakir.mobile.ProductionDestinationContent
import com.gurbakir.mobile.ProductionNavHost
import com.gurbakir.mobile.capabilityComposition
import com.gurbakir.mobile.capabilityDeepLinks
import com.gurbakir.mobile.navigatePrimary
import com.gurbakir.storefront.ProductDetailRequest
import com.gurbakir.storefront.StorefrontMoney
import com.gurbakir.storefront.StorefrontProductDetail
import com.gurbakir.storefront.StorefrontProductGateway
import com.gurbakir.storefront.StorefrontProductVariant
import com.gurbakir.storefront.StorefrontResult
import java.math.BigDecimal
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

internal const val WISHLIST_HOST_HOME = "wishlist-actual-host-home"
internal const val WISHLIST_HOST_CATEGORIES = "wishlist-actual-host-categories"
private val DESTINATION_PARTITION = WishlistPartition("offline-entry", "fixture-market")

/** Actual destination/default VM key + actual primary stack, with only the test-owned model factory preseeded. */
internal class WishlistDestinationFixture(count: Int = 1, holdReads: Boolean = true, val fontScale: Float = 1f) {
    val initial = (1..count).map { StoredWishlistEntry("gid://shopify/Product/$it", 100L - it) }
    val store = DestinationWishlistStore(initial)
    val gateway = DestinationHydrationGateway().apply { this.holdReads = holdReads }
    val repository = DefaultWishlistRepository(store, gateway, DESTINATION_PARTITION) { 200L }
    val hostEpoch = mutableStateOf(0)
    val layoutInset = mutableStateOf(0)
    private val owners = mutableMapOf<Int, ViewModelStoreOwner>()
    private val composition = capabilityComposition(search = false, wishlist = true, account = false)
    lateinit var navController: NavHostController
        private set
    lateinit var currentModel: WishlistViewModel
        private set
    lateinit var currentEntry: NavBackStackEntry
        private set
    lateinit var context: Context
        private set
    var creations = 0
        private set

    @Composable
    fun Content() {
        key(hostEpoch.value) {
            val rootOwner = owners.getOrPut(hostEpoch.value) {
                object : ViewModelStoreOwner {
                    override val viewModelStore = ViewModelStore()
                }
            }
            CompositionLocalProvider(LocalViewModelStoreOwner provides rootOwner) {
                NavigationContent()
            }
        }
    }

    @Composable
    private fun NavigationContent() {
        val controller = rememberNavController()
        SideEffect { navController = controller }
        val density = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
            CoreTestTheme { ActualNavGraph(controller) }
        }
    }

    @Composable
    private fun ActualNavGraph(controller: NavHostController) {
        ProductionNavHost(
            navController = controller,
            deepLinks = capabilityDeepLinks,
            applicationComposition = composition,
            modifier = Modifier.padding(layoutInset.value.dp),
            content = ProductionDestinationContent(
                home = { Text("Offline Home", Modifier.testTag(WISHLIST_HOST_HOME)) },
                categories = { Text("Offline Categories", Modifier.testTag(WISHLIST_HOST_CATEGORIES)) },
                wishlist = { ActualWishlistEntry(controller) }
            )
        )
    }

    @Composable
    private fun ActualWishlistEntry(controller: NavHostController) {
        val entry = checkNotNull(LocalViewModelStoreOwner.current) as NavBackStackEntry
        check(LocalLifecycleOwner.current === entry) { "The production slot must keep its actual entry lifecycle" }
        val model = remember(entry) {
            ViewModelProvider(
                entry.viewModelStore,
                object : ViewModelProvider.Factory {
                    override fun <T : ViewModel> create(modelClass: Class<T>): T {
                        check(modelClass == WishlistViewModel::class.java)
                        creations++
                        return requireNotNull(modelClass.cast(WishlistViewModel(repository)))
                    }
                }
            )[WishlistViewModel::class.java]
        }
        val owner = remember(entry) {
            object : ViewModelStoreOwner {
                override val viewModelStore = entry.viewModelStore
            }
        }
        val localContext = LocalContext.current
        SideEffect {
            currentModel = model
            currentEntry = entry
            context = localContext
        }
        // Hilt's pinned null-factory path reads the real, default-key model from this same entry store.
        // LocalLifecycleOwner remains the actual NavBackStackEntry; no test refresh/resume effect is added.
        CompositionLocalProvider(LocalViewModelStoreOwner provides owner) {
            WishlistDestination(controller, composition)
        }
    }

    fun navigate(destination: PrimaryNavigationDestination) {
        navController.navigatePrimary(destination, composition)
    }

    fun replaceOwnedHost() {
        owners.getValue(hostEpoch.value).viewModelStore.clear()
        hostEpoch.value++
    }

    fun close() {
        owners.values.forEach { it.viewModelStore.clear() }
        gateway.releaseAll()
    }
}

internal class DestinationWishlistStore(initial: List<StoredWishlistEntry>) : WishlistStore {
    private val values = MutableStateFlow(initial)
    val writes = AtomicInteger()
    val clears = AtomicInteger()

    override fun observe(partition: WishlistPartition): Flow<List<StoredWishlistEntry>> {
        check(partition == DESTINATION_PARTITION)
        return values
    }

    override suspend fun load(partition: WishlistPartition): List<StoredWishlistEntry> {
        check(partition == DESTINATION_PARTITION)
        return snapshot()
    }

    override suspend fun setSaved(
        partition: WishlistPartition,
        productId: String,
        saved: Boolean,
        addedAtEpochMillis: Long
    ) {
        check(partition == DESTINATION_PARTITION)
        val before = values.value
        values.value = if (saved) {
            if (before.any { it.productId == productId }) {
                before
            } else {
                (before + StoredWishlistEntry(productId, addedAtEpochMillis)).ordered()
            }
        } else {
            before.filterNot { it.productId == productId }
        }
        writes.incrementAndGet()
    }

    override suspend fun clear(partition: WishlistPartition) {
        check(partition == DESTINATION_PARTITION)
        values.value = emptyList()
        clears.incrementAndGet()
    }

    fun snapshot(): List<StoredWishlistEntry> = values.value

    private fun List<StoredWishlistEntry>.ordered(): List<StoredWishlistEntry> =
        sortedWith(compareByDescending<StoredWishlistEntry> { it.addedAtEpochMillis }.thenBy { it.productId })
}

/** No image URL or HTTP implementation. Held callbacks deliberately ignore cancellation until physically released. */
internal class DestinationHydrationGateway : StorefrontProductGateway {
    private val calls = mutableListOf<CompletableDeferred<Unit>>()
    private val activeReads = AtomicInteger()
    private val completedReads = AtomicInteger()

    @Volatile var holdReads = true

    @Volatile var removed = false

    @Volatile var title = "Initial offline product"

    @Volatile var available = true

    @Volatile var price = "100.00"
    val started: Int get() = synchronized(calls) { calls.size }
    val active: Int get() = activeReads.get()
    val completed: Int get() = completedReads.get()

    override suspend fun loadProductDetail(request: ProductDetailRequest): StorefrontResult<StorefrontProductDetail?> {
        check(request.isValid())
        val value = if (removed) null else product(request.productId)
        val release = CompletableDeferred<Unit>()
        synchronized(calls) { calls += release }
        activeReads.incrementAndGet()
        if (!holdReads) release.complete(Unit)
        try {
            suspendCoroutine<Unit> { continuation -> release.invokeOnCompletion { continuation.resume(Unit) } }
            completedReads.incrementAndGet()
            return StorefrontResult.Success(value)
        } finally {
            activeReads.decrementAndGet()
        }
    }

    fun releaseAll() {
        holdReads = false
        synchronized(calls) { calls.toList() }.forEach { it.complete(Unit) }
    }

    private fun product(id: String): StorefrontProductDetail = StorefrontProductDetail(
        id = id,
        handle = "offline-${id.substringAfterLast('/')}",
        title = title,
        description = "Public offline fixture",
        availableForSale = available,
        options = emptyList(),
        variants = listOf(
            StorefrontProductVariant(
                id = "gid://shopify/ProductVariant/${id.substringAfterLast('/')}",
                title = "Default",
                availableForSale = available,
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
