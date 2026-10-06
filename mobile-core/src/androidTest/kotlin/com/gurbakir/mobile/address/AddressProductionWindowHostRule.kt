package com.gurbakir.mobile.address

import android.app.Activity
import android.app.Application
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/** Test-only host policy matching the application window; adds no keyboard, focus or input action. */
internal class AddressProductionWindowHostRule : TestRule {
    private val configured = AtomicInteger()
    private val setupFailure = AtomicReference<Throwable?>()

    val contentGuard: TestRule = TestRule { base, description ->
        object : Statement() {
            override fun evaluate() {
                checkConfigured()
                Log.i("W4_ADDRESS_HOST", "case=${description.methodName} setupConfirmed=true configuredCount=1")
                base.evaluate()
            }
        }
    }

    override fun apply(base: Statement, description: Description): Statement = object : Statement() {
        override fun evaluate() {
            val application =
                InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application
            val callbacks = AddressHostLifecycleCallbacks(configured, setupFailure)
            application.registerActivityLifecycleCallbacks(callbacks)
            var primaryFailure: Throwable? = null
            try {
                base.evaluate()
            } catch (failure: Throwable) {
                primaryFailure = failure
                throw failure
            } finally {
                finishHost(application, callbacks, description, primaryFailure)
            }
        }
    }

    private fun checkConfigured() {
        setupFailure.get()?.let { throw IllegalStateException("Address host window setup failed", it) }
        check(configured.get() == 1) { "Exactly one managed ComponentActivity must be configured" }
    }

    private fun finishHost(
        application: Application,
        callbacks: AddressHostLifecycleCallbacks,
        description: Description,
        primaryFailure: Throwable?
    ) {
        val cleanupFailures = mutableListOf<Throwable>()
        var callbacksUnregistered = false
        try {
            application.unregisterActivityLifecycleCallbacks(callbacks)
            callbacksUnregistered = true
        } catch (failure: Throwable) {
            cleanupFailures += failure
        }
        try {
            checkConfigured()
        } catch (failure: Throwable) {
            cleanupFailures += failure
        }
        Log.i(
            "W4_ADDRESS_HOST",
            "case=${description.methodName} callbacksUnregistered=$callbacksUnregistered finalSetupConfirmed=${cleanupFailures.isEmpty()} configuredCount=${configured.get()}"
        )
        val cleanupFailure = cleanupFailures.firstOrNull() ?: return
        val preservedFailure = primaryFailure ?: cleanupFailure
        cleanupFailures.filter { it !== preservedFailure }.forEach(preservedFailure::addSuppressed)
        if (primaryFailure == null) throw cleanupFailure
    }
}

private class AddressHostLifecycleCallbacks(
    private val configured: AtomicInteger,
    private val setupFailure: AtomicReference<Throwable?>
) : Application.ActivityLifecycleCallbacks {
    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        if (activity.javaClass != ComponentActivity::class.java) return
        try {
            configureAddressHost(activity as ComponentActivity)
            configured.incrementAndGet()
        } catch (failure: Throwable) {
            setupFailure.compareAndSet(null, failure)
        }
    }

    override fun onActivityStarted(activity: Activity) = Unit
    override fun onActivityResumed(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit
}

@Suppress("DEPRECATION") // Public decor flags verify the API23/API30 edge-to-edge request.
private fun configureAddressHost(activity: ComponentActivity) {
    check(Looper.myLooper() == Looper.getMainLooper()) { "Host policy must be configured on the main thread" }
    activity.enableEdgeToEdge()
    val window = activity.window
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        window.isNavigationBarContrastEnforced = false
        check(!window.isNavigationBarContrastEnforced) { "Navigation-bar contrast policy was not applied" }
    }
    window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
    check(
        window.attributes.softInputMode and WindowManager.LayoutParams.SOFT_INPUT_MASK_ADJUST ==
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
    ) { "Host must use adjustResize before content" }
    val expectedFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        View.SYSTEM_UI_FLAG_LAYOUT_STABLE
    } else {
        View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
    }
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
        check(window.decorView.systemUiVisibility and expectedFlags == expectedFlags) {
            "Edge-to-edge decor flags were not applied"
        }
    }
    Log.i(
        "W4_ADDRESS_HOST",
        "activity=${activity.javaClass.name} mainThread=true edgeToEdgeRequested=true decorFitsRequested=false " +
            "adjustResizeConfirmed=true navigationContrastFalse=${Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q}"
    )
}
