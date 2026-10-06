package com.gurbakir.mobile.accessibility

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.LocaleList
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.gurbakir.account.CustomerAddressField
import com.gurbakir.mobile.CoreTestTheme
import com.gurbakir.mobile.address.AddressActionResult
import com.gurbakir.mobile.address.AddressController
import com.gurbakir.mobile.address.AddressFailure
import com.gurbakir.mobile.address.AddressFormActions
import com.gurbakir.mobile.address.AddressFormLoadResult
import com.gurbakir.mobile.address.AddressFormPhase
import com.gurbakir.mobile.address.AddressFormScreen
import com.gurbakir.mobile.address.AddressFormViewModel
import com.gurbakir.mobile.address.AddressInput
import com.gurbakir.mobile.address.AddressLoadResult
import com.gurbakir.mobile.address.AddressTerritoryPolicy
import com.gurbakir.mobile.address.PostalCodeInputMode
import com.gurbakir.mobile.search.SearchActions
import com.gurbakir.mobile.search.SearchScreen
import com.gurbakir.mobile.search.SearchUiState
import java.io.FileDescriptor
import java.io.PrintWriter
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.json.JSONObject

private const val PROBE_TAG = "Wave4AccessibilityProbe"
private const val MAX_FORM_HEIGHT_DP = 320
private const val SCENARIO_EXTRA = "scenario"
private const val LOCALE_EXTRA = "locale"
private const val FONT_EXTRA = "font"
private const val CHECKED_EXTRA = "checked"
private const val COMMAND_EXTRA = "command"
private const val VIEWPORT_EXTRA = "viewport_mode"

/** Offline standalone androidTest-only proposal; no instrumentation or UiAutomation client is started. */
class Wave4AccessibilityProbeActivity : ComponentActivity() {
    private lateinit var runtime: ProbeRuntime

    override fun onCreate(savedInstanceState: Bundle?) {
        val spec = intent.probeSpec()
        val localized = Configuration(baseContext.resources.configuration)
        val locale = Locale.forLanguageTag(spec.locale)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            localized.setLocales(LocaleList(locale))
        } else {
            localized.setLocale(locale)
        }
        localized.fontScale = spec.font.toFloat()
        val resourceContext = baseContext.createConfigurationContext(localized)
        if (spec.viewport == ProbeViewport.NATURAL) {
            setTheme(android.R.style.Theme_Material_Light_NoActionBar)
        }
        super.onCreate(savedInstanceState)
        if (spec.viewport == ProbeViewport.NATURAL) {
            enableEdgeToEdge()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                window.isNavigationBarContrastEnforced = false
            }
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
        runtime = ProbeRuntime(spec, viewModelStore, resourceContext) {
            Log.i(PROBE_TAG, runtime.snapshot().toString())
            finish()
        }
        lifecycleScope.launch { runtime.start() }
        setContent { ProbeContent(runtime) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        runtime.command(intent.getStringExtra(COMMAND_EXTRA))
        Log.i(PROBE_TAG, runtime.snapshot().toString())
    }

    override fun dump(prefix: String, fd: FileDescriptor?, writer: PrintWriter, args: Array<out String>?) {
        // Avoid super.dump: the narrow receipt never prints entered fields or a native view hierarchy.
        writer.println(prefix + if (::runtime.isInitialized) runtime.snapshot().toString() else "Probe starting")
    }
}

@Composable
private fun ProbeContent(runtime: ProbeRuntime) {
    CoreTestTheme {
        val density = LocalDensity.current
        CompositionLocalProvider(
            LocalContext provides runtime.resourceContext,
            LocalConfiguration provides runtime.resourceContext.resources.configuration,
            LocalResources provides runtime.resourceContext.resources,
            LocalDensity provides Density(density.density, runtime.spec.font.toFloat())
        ) {
            Box(Modifier.fillMaxSize().imePadding().semantics { testTagsAsResourceId = true }) {
                if (runtime.spec.scenario.search) {
                    val state by runtime.searchState.collectAsStateWithLifecycle()
                    SearchScreen(state, runtime.searchActions())
                } else {
                    val model = checkNotNull(runtime.address)
                    val state by model.state.collectAsStateWithLifecycle()
                    val formModifier = if (runtime.spec.viewport == ProbeViewport.NATURAL) {
                        Modifier.fillMaxSize().clipToBounds()
                    } else {
                        Modifier.fillMaxWidth().heightIn(max = MAX_FORM_HEIGHT_DP.dp).clipToBounds()
                    }
                    Box(formModifier) {
                        AddressFormScreen(state, runtime.addressActions())
                    }
                }
            }
        }
    }
}

private enum class ProbeScenario(val wire: String, val search: Boolean = false) {
    ADDRESS_LOCAL("address_local"),
    ADDRESS_SERVER("address_server"),
    ADDRESS_DEFAULT("address_default"),
    ADDRESS_SAVING("address_saving"),
    ADDRESS_FAILED("address_failed"),
    SEARCH_AVAILABLE("search_available", search = true),
    SEARCH_UNAVAILABLE("search_unavailable", search = true),
    SEARCH_LOADING("search_loading", search = true)
}

private enum class ProbeViewport(val wire: String) {
    BOUNDED_320("bounded320"),
    NATURAL("natural")
}

private data class ProbeSpec(
    val scenario: ProbeScenario,
    val locale: String,
    val font: Int,
    val checked: Boolean,
    val viewport: ProbeViewport
)

private fun Intent.probeSpec(): ProbeSpec {
    val scenario = ProbeScenario.entries.firstOrNull { it.wire == getStringExtra(SCENARIO_EXTRA) }
        ?: error("An explicit supported test-only probe scenario is required")
    val locale = getStringExtra(LOCALE_EXTRA) ?: "tr"
    val font = getIntExtra(FONT_EXTRA, 1)
    require(locale == "tr" || locale == "en") { "Only tr/en probe resources are supported" }
    require(font == 1 || font == 2) { "Only normal/double probe fonts are supported" }
    val viewportWire = getStringExtra(VIEWPORT_EXTRA) ?: ProbeViewport.BOUNDED_320.wire
    val viewport = ProbeViewport.entries.firstOrNull { it.wire == viewportWire }
        ?: error("Only bounded320/natural probe viewports are supported")
    return ProbeSpec(scenario, locale, font, getBooleanExtra(CHECKED_EXTRA, false), viewport)
}

private enum class ProbeEvent {
    BACK,
    SAVE_ACTION,
    DEFAULT_CHANGE,
    FOCUS_HANDLED,
    RELOAD,
    QUERY_CHANGE,
    SEARCH_SUBMIT,
    HISTORY_CHANGE,
    HISTORY_CLEAR,
    OTHER_SEARCH_ACTION,
    REPORT_COMMAND,
    REJECTED_COMMAND
}

private class ProbeCounters {
    private val counts = ProbeEvent.entries.associateWith { AtomicInteger() }
    fun record(event: ProbeEvent) = checkNotNull(counts[event]).incrementAndGet()
    fun snapshot(): JSONObject = JSONObject().apply {
        counts.forEach { (event, value) -> put(event.name.lowercase(Locale.ROOT), value.get()) }
    }
}

private class ProbeRuntime(
    val spec: ProbeSpec,
    store: ViewModelStore,
    val resourceContext: Context,
    private val onFinish: () -> Unit
) {
    private val instanceId = UUID.randomUUID().toString()
    private val counters = ProbeCounters()
    private val controller = if (spec.scenario.search) null else ProbeAddressController(spec.scenario)
    val address = controller?.let {
        AddressFormViewModel(it, AddressTerritoryPolicy("ZZ", PostalCodeInputMode.TEXT)).also { model ->
            store.put("wave4-accessibility-address", model)
        }
    }
    val searchState = MutableStateFlow(
        SearchUiState(
            historyEnabled = spec.checked,
            historyStorageAvailable = spec.scenario != ProbeScenario.SEARCH_UNAVAILABLE,
            historyLoading = spec.scenario == ProbeScenario.SEARCH_LOADING
        )
    )
    private var lastDefaultRequest: Boolean? = null
    private var lastHistoryRequest: Boolean? = null
    private var prepared = false

    suspend fun start() {
        val model = address
        if (model == null) {
            prepared = true
            return
        }
        model.start(null)
        model.state.first { it.loaded && it.phase == AddressFormPhase.READY }
        model.setMakeDefault(spec.checked)
        if (spec.scenario in VALID_DRAFT_SCENARIOS) model.seedSyntheticDraft()
        if (spec.scenario == ProbeScenario.ADDRESS_SAVING || spec.scenario == ProbeScenario.ADDRESS_FAILED) {
            model.save()
        }
        prepared = true
    }

    fun addressActions(): AddressFormActions {
        val model = checkNotNull(address)
        return AddressFormActions(
            onBack = {
                counters.record(ProbeEvent.BACK)
                onFinish()
            },
            onFieldChanged = model::update,
            onMakeDefaultChanged = {
                counters.record(ProbeEvent.DEFAULT_CHANGE)
                lastDefaultRequest = it
                model.setMakeDefault(it)
            },
            onSave = {
                counters.record(ProbeEvent.SAVE_ACTION)
                model.save()
            },
            onReload = {
                counters.record(ProbeEvent.RELOAD)
                model.reload()
            },
            onFocusHandled = { requestId ->
                counters.record(ProbeEvent.FOCUS_HANDLED)
                // Forward only the request identity that the screen actually handled.
                model.consumeFocusRequest(requestId)
            }
        )
    }

    fun searchActions() = SearchActions(
        onQueryChanged = {
            counters.record(ProbeEvent.QUERY_CHANGE)
            searchState.value = searchState.value.copy(query = it)
        },
        onSubmit = { counters.record(ProbeEvent.SEARCH_SUBMIT) },
        onRetry = { counters.record(ProbeEvent.OTHER_SEARCH_ACTION) },
        onLoadMore = { counters.record(ProbeEvent.OTHER_SEARCH_ACTION) },
        onSelectHistory = { counters.record(ProbeEvent.OTHER_SEARCH_ACTION) },
        onRemoveHistory = { counters.record(ProbeEvent.OTHER_SEARCH_ACTION) },
        onClearHistory = {
            counters.record(ProbeEvent.HISTORY_CLEAR)
            searchState.value = searchState.value.copy(history = emptyList())
        },
        onHistoryEnabledChanged = {
            counters.record(ProbeEvent.HISTORY_CHANGE)
            lastHistoryRequest = it
            searchState.value = searchState.value.copy(historyEnabled = it)
        },
        onOpenProduct = { counters.record(ProbeEvent.OTHER_SEARCH_ACTION) }
    )

    fun command(command: String?) {
        when (command) {
            "report" -> counters.record(ProbeEvent.REPORT_COMMAND)

            "reject" -> {
                if (spec.scenario != ProbeScenario.ADDRESS_SERVER || controller?.rejectPending() != true) {
                    counters.record(ProbeEvent.REJECTED_COMMAND)
                }
            }

            "finish" -> onFinish()

            else -> counters.record(ProbeEvent.REJECTED_COMMAND)
        }
    }

    fun snapshot(): JSONObject = JSONObject().apply {
        put("instance", instanceId)
        put("scenario", spec.scenario.wire)
        put("locale", spec.locale)
        put("resource_scope", "composition-only")
        put("font", spec.font)
        put("viewport_mode", spec.viewport.wire)
        put("form_max_dp", if (spec.viewport == ProbeViewport.BOUNDED_320) MAX_FORM_HEIGHT_DP else JSONObject.NULL)
        put("theme_mode", if (spec.viewport == ProbeViewport.NATURAL) "framework_no_action_bar" else "legacy_default")
        put(
            "adjust_resize_mode",
            if (spec.viewport == ProbeViewport.NATURAL) "explicit_adjust_resize" else "legacy_default"
        )
        put("edge_to_edge_mode", if (spec.viewport == ProbeViewport.NATURAL) "explicit_enabled" else "legacy_default")
        put("prepared", prepared)
        put("callbacks", counters.snapshot())
        put("last_default_request", lastDefaultRequest ?: JSONObject.NULL)
        put("last_history_request", lastHistoryRequest ?: JSONObject.NULL)
        put("controller", controller?.snapshot() ?: JSONObject.NULL)
        address?.state?.value?.let { state ->
            put("address_phase", state.phase.name)
            put("address_loaded", state.loaded)
            put("address_default_checked", state.makeDefault)
            put("address_busy", state.busy)
            put("address_can_save", state.canSave)
            put("focus_requested_field", state.focusRequest?.field?.name ?: JSONObject.NULL)
        }
        if (spec.scenario.search) {
            put("history_checked", searchState.value.historyEnabled)
            put("history_storage_available", searchState.value.historyStorageAvailable)
            put("history_loading", searchState.value.historyLoading)
        }
    }
}

private class ProbeAddressController(private val scenario: ProbeScenario) : AddressController {
    private val loads = AtomicInteger()
    private val saves = AtomicInteger()
    private val defaults = AtomicInteger()
    private val deletes = AtomicInteger()
    private val releases = AtomicInteger()
    private var pending: CompletableDeferred<AddressActionResult>? = null

    override suspend fun loadForm(addressId: String?): AddressFormLoadResult {
        check(addressId == null) { "The probe only supports synthetic new-address forms" }
        loads.incrementAndGet()
        return AddressFormLoadResult.Ready(null)
    }

    override suspend fun loadAddresses(): AddressLoadResult = error("No provider address list belongs to this probe")

    override suspend fun saveAddress(
        addressId: String?,
        input: AddressInput,
        expected: AddressInput?,
        makeDefault: Boolean
    ): AddressActionResult {
        saves.incrementAndGet()
        check(addressId == null && expected == null) { "No real/edit address identity belongs to this probe" }
        return when (scenario) {
            ProbeScenario.ADDRESS_SERVER, ProbeScenario.ADDRESS_SAVING ->
                CompletableDeferred<AddressActionResult>().also { pending = it }.await()

            ProbeScenario.ADDRESS_FAILED -> AddressActionResult.Failed(AddressFailure.SAVE_UNCONFIRMED)

            else -> error("Local validation must not reach even an inert controller save")
        }
    }

    override suspend fun setDefault(addressId: String): AddressActionResult {
        defaults.incrementAndGet()
        error("No provider default mutation belongs to this probe")
    }

    override suspend fun deleteAddress(addressId: String, expected: AddressInput): AddressActionResult {
        deletes.incrementAndGet()
        error("No provider delete mutation belongs to this probe")
    }

    fun rejectPending(): Boolean {
        val reply = pending ?: return false
        val accepted = reply.complete(AddressActionResult.Rejected(setOf(CustomerAddressField.FIRST_NAME), emptySet()))
        if (accepted) releases.incrementAndGet()
        return accepted
    }

    fun snapshot(): JSONObject = JSONObject().apply {
        put("loads", loads.get())
        put("saves", saves.get())
        put("defaults", defaults.get())
        put("deletes", deletes.get())
        put("rejection_releases", releases.get())
        put("save_pending", pending?.isCompleted == false)
    }
}

private val VALID_DRAFT_SCENARIOS = setOf(
    ProbeScenario.ADDRESS_SERVER,
    ProbeScenario.ADDRESS_SAVING,
    ProbeScenario.ADDRESS_FAILED
)

private fun AddressFormViewModel.seedSyntheticDraft() {
    listOf(
        CustomerAddressField.FIRST_NAME to "Synthetic",
        CustomerAddressField.LAST_NAME to "Customer",
        CustomerAddressField.COMPANY to "Public fixture",
        CustomerAddressField.ADDRESS1 to "Synthetic street",
        CustomerAddressField.ADDRESS2 to "",
        CustomerAddressField.CITY to "Example City",
        CustomerAddressField.ZIP to "ZZ-1234",
        CustomerAddressField.PHONE to "+905550000000"
    ).forEach { (field, value) -> update(field, value) }
}
