package com.gurbakir.mobile.checkout

import android.app.Activity
import com.gurbakir.checkout.CheckoutEvent
import com.gurbakir.checkout.CheckoutFailure
import com.gurbakir.checkout.CheckoutKitClient
import com.gurbakir.checkout.CheckoutPresentationOwner
import com.gurbakir.checkout.CheckoutResult
import com.gurbakir.checkout.ShopifyCheckoutAdapter
import java.net.URI

/** Reflection drives the very callback/recovery assembly used by OfficialCheckoutKitClient.present. */
internal class CheckoutSdkTestDriver : CheckoutKitClient {
    private var sink: ((CheckoutEvent) -> Unit)? = null
    private var callbacks: Any? = null
    private var target: String? = null
    private val queue = ArrayDeque<() -> Unit>()
    var presentations = 0
    var invalidations = 0
    var disposals = 0

    override fun preload(activity: Activity, checkoutUrl: URI, eventSink: (CheckoutEvent) -> Unit): Boolean = true

    override fun present(
        activity: Activity,
        checkoutUrl: URI,
        eventSink: (CheckoutEvent) -> Unit
    ): CheckoutPresentationOwner = captureCallbacks(checkoutUrl, eventSink)

    override fun invalidate() {
        invalidations++
    }

    fun capture(adapter: ShopifyCheckoutAdapter, url: URI): CheckoutResult {
        val operation: ((CheckoutEvent) -> Unit) -> CheckoutPresentationOwner = { captureCallbacks(url, it) }
        return adapter.javaClass.methods.single { it.name.startsWith("presentWithOperation") }
            .invoke(adapter, url, operation) as CheckoutResult
    }

    fun selectActualRecovery(): CheckoutFailure {
        val error = protocolHttpException(recoverable = true)
        val callback = checkNotNull(callbacks)
        callback.javaClass.getMethod("failed", protocolExceptionType).invoke(callback, error)
        val original = protocolDefaultRecovery()
        val wrapper = protocolProviderClass.methods.single { it.name == "wrapOfficialCheckoutRecovery" }
            .invoke(null, original)
        val recoveryType = Class.forName("com.shopify.checkoutsheetkit.ErrorRecovery")
        check(recoveryType.getMethod("shouldRecoverFromError", protocolExceptionType).invoke(wrapper, error) == true)
        recoveryType.getMethod("preRecoveryActions", protocolExceptionType, String::class.java)
            .invoke(wrapper, error, checkNotNull(target))
        return protocolMapFailure(error)
    }

    fun drainProviderQueue() {
        while (queue.isNotEmpty()) queue.removeFirst().invoke()
    }

    fun emit(event: CheckoutEvent) {
        val callback = checkNotNull(callbacks)
        when (event) {
            CheckoutEvent.Completed -> callback.javaClass.getMethod("completed").invoke(callback)
            CheckoutEvent.Cancelled -> callback.javaClass.getMethod("cancelled").invoke(callback)
            else -> checkNotNull(sink)(event)
        }
    }

    private fun captureCallbacks(url: URI, eventSink: (CheckoutEvent) -> Unit): CheckoutPresentationOwner {
        presentations++
        sink = eventSink
        target = url.toASCIIString()
        val poster: (() -> Unit) -> Unit = { queue.addLast(it) }
        val callback = protocolProviderClass.methods.single {
            it.name == "createOfficialCheckoutCallbacks" && it.parameterCount == 3
        }.invoke(null, target, eventSink, poster)
        callbacks = callback
        return CheckoutPresentationOwner {
            disposals++
            callback.javaClass.getMethod("dispose").invoke(callback)
        }
    }
}

private val protocolProviderClass = Class.forName("com.gurbakir.checkout.OfficialCheckoutKitClientKt")
private val protocolExceptionType = Class.forName("com.shopify.checkoutsheetkit.CheckoutException")

private fun protocolHttpException(recoverable: Boolean): Any =
    Class.forName("com.shopify.checkoutsheetkit.HttpException")
        .getConstructor(Int::class.javaPrimitiveType, Boolean::class.javaPrimitiveType).newInstance(500, recoverable)

private fun protocolDefaultRecovery(): Any = Class.forName("com.shopify.checkoutsheetkit.Configuration\$1")
    .getDeclaredConstructor().run {
        isAccessible = true
        newInstance()
    }

private fun protocolMapFailure(error: Any): CheckoutFailure = protocolProviderClass.methods
    .single { it.name.startsWith("toProjectFailure") }.invoke(null, error) as CheckoutFailure
