@file:Suppress("FunctionNaming")

package com.gurbakir.mobile.wishlist

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
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
import java.util.Locale
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext

internal const val ROOM_GREEN_HOME = "room-green-home"
internal const val ROOM_GREEN_CATEGORIES = "room-green-categories"
internal const val ROOM_GREEN_DISPOSED = "room-green-composition-removed"

/**
 * GREEN-only draft. Actual production nav entry, destination and default-key VM. No test lifecycle hook.
 * Requires the proposed production resume/pause-or-dispose behavior; never an old-source RED recipe.
 */
internal class WishlistGreenDestinationFixture(
    val room: WishlistRoomGreenFixture,
    private val locale: String = "tr",
    private val font: Float = 1f,
    private val onActualEntry: ((NavBackStackEntry) -> Unit)? = null,
    private val onLoadStarted: ((Job, Boolean) -> Unit)? = null
) {
    val attached = mutableStateOf(true)
    val layoutInset = mutableStateOf(0)
    val observedRepository = RoomGreenObservedRepository(room.repository, onLoadStarted)
    private val composition = capabilityComposition(search = false, wishlist = true, account = false)
    lateinit var navController: NavHostController
        private set
    lateinit var currentModel: WishlistViewModel
        private set
    lateinit var currentEntry: NavBackStackEntry
        private set
    private lateinit var ownedModelStore: ViewModelStore
    lateinit var context: Context
        private set
    var creations = 0
        private set

    @Composable
    fun Content() {
        val base = LocalContext.current
        val baseConfiguration = LocalConfiguration.current
        val resourceContext = remember(base, locale, baseConfiguration) {
            val configuration = Configuration(baseConfiguration)
            val selected = Locale.forLanguageTag(locale)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                configuration.setLocales(LocaleList(selected))
            } else {
                configuration.setLocale(selected)
            }
            base.createConfigurationContext(configuration)
        }
        val density = LocalDensity.current
        val controller = rememberNavController()
        SideEffect {
            navController = controller
            context = resourceContext
        }
        CompositionLocalProvider(
            LocalContext provides resourceContext,
            LocalConfiguration provides resourceContext.resources.configuration,
            LocalResources provides resourceContext.resources,
            LocalDensity provides Density(density.density, font)
        ) {
            CoreTestTheme {
                ProductionNavHost(
                    navController = controller,
                    deepLinks = capabilityDeepLinks,
                    applicationComposition = composition,
                    modifier = Modifier.padding(layoutInset.value.dp),
                    content = ProductionDestinationContent(
                        home = { Text("Offline Home", Modifier.testTag(ROOM_GREEN_HOME)) },
                        categories = { Text("Offline Categories", Modifier.testTag(ROOM_GREEN_CATEGORIES)) },
                        wishlist = {
                            if (attached.value) {
                                ActualWishlistEntry(controller)
                            } else {
                                Text("Destination composition removed", Modifier.testTag(ROOM_GREEN_DISPOSED))
                            }
                        }
                    )
                )
            }
        }
    }

    @Composable
    private fun ActualWishlistEntry(controller: NavHostController) {
        val entry = checkNotNull(LocalViewModelStoreOwner.current) as NavBackStackEntry
        check(LocalLifecycleOwner.current === entry) { "Keep the actual entry lifecycle" }
        val model = remember(entry) {
            onActualEntry?.invoke(entry)
            ViewModelProvider(
                entry.viewModelStore,
                object : ViewModelProvider.Factory {
                    override fun <T : ViewModel> create(modelClass: Class<T>): T {
                        check(modelClass == WishlistViewModel::class.java)
                        creations++
                        return requireNotNull(modelClass.cast(WishlistViewModel(observedRepository)))
                    }
                }
            )[WishlistViewModel::class.java]
        }
        val owner = remember(entry) {
            object : ViewModelStoreOwner {
                override val viewModelStore = entry.viewModelStore
            }
        }
        SideEffect {
            currentModel = model
            currentEntry = entry
            ownedModelStore = owner.viewModelStore
        }
        // Same pinned null-factory/default-key path as the preserved v2 fixture. No effect calls resume/refresh.
        CompositionLocalProvider(LocalViewModelStoreOwner provides owner) {
            WishlistDestination(controller, composition)
        }
    }

    fun navigate(destination: PrimaryNavigationDestination) = navController.navigatePrimary(destination, composition)

    fun clearOwnedModel() {
        check(!attached.value) { "Dispose actual destination composition before clearing its test-owned VM" }
        if (::currentEntry.isInitialized) ownedModelStore.clear()
    }
}

internal data class RoomGreenLoadSnapshot(val started: Int, val active: Int, val completed: Int)

/** Transparent delegation forwards any future local-feed method; only observes real default-repository calls. */
internal class RoomGreenObservedRepository(
    private val delegate: WishlistRepository,
    private val onLoadStarted: ((Job, Boolean) -> Unit)? = null
) : WishlistRepository by delegate {
    private val lock = Any()
    private var started = 0
    private var active = 0
    private var completed = 0

    override suspend fun load(forceRefresh: Boolean): WishlistLoadResult {
        synchronized(lock) {
            started++
            active++
        }
        try {
            onLoadStarted?.invoke(checkNotNull(currentCoroutineContext()[Job]), forceRefresh)
            return delegate.load(forceRefresh)
        } finally {
            synchronized(lock) {
                active--
                completed++
            }
        }
    }

    fun snapshot(): RoomGreenLoadSnapshot = synchronized(lock) { RoomGreenLoadSnapshot(started, active, completed) }
}
