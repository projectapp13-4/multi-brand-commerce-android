package com.gurbakir.mobile.checkout

import androidx.activity.ComponentActivity
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.gurbakir.checkout.CheckoutEvent
import com.gurbakir.checkout.CheckoutFailure
import com.gurbakir.checkout.OfficialCheckoutKitClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CheckoutMainPostWiringTest {
    @Test
    fun actualOfficialProcessorAndConfiguredHookAlwaysPostSelectedRecoveryAfterMainStack() {
        ActivityScenario.launch(CheckoutOwnerProbeActivity::class.java).use { scenario ->
            val events = mutableListOf<CheckoutEvent>()
            lateinit var assembly: AndroidOfficialAssembly
            scenario.onActivity { activity ->
                assembly = AndroidOfficialAssembly(activity, events::add)
                assembly.failure(recoverable = true, selectRecovery = true)
                assertTrue("Handler.post must not settle inside the SDK stack", events.isEmpty())
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity {
                assertEquals(listOf(CheckoutEvent.RecoveryStarted(CheckoutFailure.NETWORK)), events)
                assembly.cancel()
                assembly.dispose()
                assertEquals(CheckoutEvent.Cancelled, events.last())
            }
        }
    }

    @Test
    fun actualOfficialProcessorWithoutRecoveryHookPostsDefinitiveFailure() {
        ActivityScenario.launch(CheckoutOwnerProbeActivity::class.java).use { scenario ->
            val events = mutableListOf<CheckoutEvent>()
            lateinit var assembly: AndroidOfficialAssembly
            scenario.onActivity { activity ->
                assembly = AndroidOfficialAssembly(activity, events::add)
                assembly.failure(recoverable = true, selectRecovery = false)
                assertTrue(events.isEmpty())
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity {
                assertEquals(listOf(CheckoutEvent.Failed(CheckoutFailure.NETWORK)), events)
                assembly.cancel()
                assembly.dispose()
                assertEquals(1, events.size)
            }
        }
    }

    @Test
    fun disposingActualOfficialAssemblyBeforeQueuedSettlementSilencesLateCallbacks() {
        ActivityScenario.launch(CheckoutOwnerProbeActivity::class.java).use { scenario ->
            val events = mutableListOf<CheckoutEvent>()
            scenario.onActivity { activity ->
                val assembly = AndroidOfficialAssembly(activity, events::add)
                assembly.failure(recoverable = true, selectRecovery = true)
                assembly.dispose()
                assembly.cancel()
                assertTrue(events.isEmpty())
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity { assertTrue(events.isEmpty()) }
        }
    }
}

class CheckoutOwnerProbeActivity : ComponentActivity()

/** No Dialog is launched: this is the same registered processor and hook used by the production provider. */
private class AndroidOfficialAssembly(activity: ComponentActivity, sink: (CheckoutEvent) -> Unit) {
    private val url = "https://gurbakir.com/cart/c/synthetic-main-post"
    private val provider = Class.forName("com.gurbakir.checkout.OfficialCheckoutKitClientKt")
    private val exceptionType = Class.forName("com.shopify.checkoutsheetkit.CheckoutException")
    private val callbacks: Any
    private val processor: Any
    private val policy: Any

    init {
        OfficialCheckoutKitClient()
        callbacks = provider.methods.single {
            it.name == "createOfficialCheckoutCallbacks" && it.parameterCount == 2
        }.invoke(null, url, sink)
        processor = provider.methods.single { it.name == "createOfficialCheckoutProcessor" }
            .invoke(null, activity, callbacks)
        val sdk = Class.forName("com.shopify.checkoutsheetkit.ShopifyCheckoutSheetKit")
        val configuration = sdk.getMethod("getConfiguration").invoke(null)
        policy = configuration.javaClass.getMethod("getErrorRecovery").invoke(configuration)
    }

    fun failure(recoverable: Boolean, selectRecovery: Boolean) {
        val error = Class.forName("com.shopify.checkoutsheetkit.HttpException")
            .getConstructor(Int::class.javaPrimitiveType, Boolean::class.javaPrimitiveType)
            .newInstance(500, recoverable)
        processor.javaClass.getMethod("onCheckoutFailed", exceptionType).apply { isAccessible = true }
            .invoke(processor, error)
        if (selectRecovery) {
            val contract = Class.forName("com.shopify.checkoutsheetkit.ErrorRecovery")
            assertTrue(contract.getMethod("shouldRecoverFromError", exceptionType).invoke(policy, error) == true)
            contract.getMethod("preRecoveryActions", exceptionType, String::class.java).invoke(policy, error, url)
        }
    }

    fun cancel() {
        processor.javaClass.getMethod("onCheckoutCanceled").apply { isAccessible = true }.invoke(processor)
    }

    fun dispose() {
        callbacks.javaClass.getMethod("dispose").invoke(callbacks)
    }
}
