package com.gurbakir.checkout

import android.app.Activity
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.activity.ComponentActivity
import com.shopify.checkoutsheetkit.CheckoutException
import com.shopify.checkoutsheetkit.CheckoutExpiredException
import com.shopify.checkoutsheetkit.ConfigurationException
import com.shopify.checkoutsheetkit.DefaultCheckoutEventProcessor
import com.shopify.checkoutsheetkit.ErrorRecovery
import com.shopify.checkoutsheetkit.HttpException
import com.shopify.checkoutsheetkit.LogLevel
import com.shopify.checkoutsheetkit.ShopifyCheckoutSheetKit
import com.shopify.checkoutsheetkit.lifecycleevents.CheckoutCompletedEvent
import com.shopify.checkoutsheetkit.pixelevents.PixelEvent
import java.net.URI

class OfficialCheckoutKitClient : CheckoutKitClient {
    init {
        OfficialCheckoutConfiguration.ensureInstalled()
    }

    override fun preload(activity: Activity, checkoutUrl: URI, eventSink: (CheckoutEvent) -> Unit): Boolean {
        val componentActivity = activity as? ComponentActivity ?: return false
        ShopifyCheckoutSheetKit.preload(checkoutUrl.toASCIIString(), componentActivity)
        return true
    }

    override fun present(
        activity: Activity,
        checkoutUrl: URI,
        eventSink: (CheckoutEvent) -> Unit
    ): CheckoutPresentationOwner? {
        val componentActivity = activity as? ComponentActivity ?: return null
        val callbacks = createOfficialCheckoutCallbacks(checkoutUrl.toASCIIString(), eventSink)
        var owner: CheckoutPresentationOwner? = null
        try {
            val dialog = ShopifyCheckoutSheetKit.present(
                checkoutUrl.toASCIIString(),
                componentActivity,
                createOfficialCheckoutProcessor(componentActivity, callbacks)
            )
            owner = dialog?.let { OfficialPresentationOwner(callbacks, it::dismiss) }
            return owner
        } finally {
            if (owner == null) callbacks.dispose()
        }
    }

    override fun invalidate() {
        ShopifyCheckoutSheetKit.invalidate()
    }
}

private class RestrictedCheckoutEventProcessor(
    activity: ComponentActivity,
    private val callbacks: OfficialCheckoutCallbacks
) : DefaultCheckoutEventProcessor(activity) {
    override fun onCheckoutCompleted(checkoutCompletedEvent: CheckoutCompletedEvent) {
        callbacks.completed()
    }

    override fun onCheckoutFailed(error: CheckoutException) {
        callbacks.failed(error)
    }

    override fun onCheckoutCanceled() {
        callbacks.cancelled()
    }

    override fun onCheckoutLinkClicked(uri: Uri) {
        runCatching { URI(uri.toString()) }
            .onSuccess(callbacks::externalLink)
            .onFailure { callbacks.invalidLink() }
    }

    override fun onWebPixelEvent(event: PixelEvent) = Unit

    override fun onPermissionRequest(permissionRequest: PermissionRequest) {
        permissionRequest.deny()
    }

    override fun onShowFileChooser(
        webView: WebView,
        filePathCallback: ValueCallback<Array<Uri>>,
        fileChooserParams: WebChromeClient.FileChooserParams
    ): Boolean {
        filePathCallback.onReceiveValue(null)
        return true
    }

    override fun onGeolocationPermissionsShowPrompt(origin: String, callback: GeolocationPermissions.Callback) {
        callback.invoke(origin, false, false)
    }

    override fun onGeolocationPermissionsHidePrompt() = Unit
}

internal fun createOfficialCheckoutProcessor(
    activity: ComponentActivity,
    callbacks: OfficialCheckoutCallbacks
): DefaultCheckoutEventProcessor = RestrictedCheckoutEventProcessor(activity, callbacks)

private class OfficialPresentationOwner(
    private val callbacks: OfficialCheckoutCallbacks,
    private val dismiss: () -> Unit
) : CheckoutPresentationOwner {
    private val disposed = java.util.concurrent.atomic.AtomicBoolean(false)

    override fun dispose() {
        if (disposed.compareAndSet(false, true)) {
            callbacks.dispose()
            if (Looper.myLooper() == Looper.getMainLooper()) {
                dismiss()
            } else {
                Handler(Looper.getMainLooper()).post { dismiss() }
            }
        }
    }
}

private object OfficialCheckoutConfiguration {
    private var installed = false

    @Synchronized
    fun ensureInstalled() {
        if (!installed) {
            ShopifyCheckoutSheetKit.configure { configuration ->
                configuration.logLevel = LogLevel.ERROR
                configuration.errorRecovery = wrapOfficialCheckoutRecovery(configuration.errorRecovery)
            }
            installed = true
        }
    }
}

private val officialRecoveryRegistry = CheckoutRecoveryRegistry()

internal fun wrapOfficialCheckoutRecovery(delegate: ErrorRecovery): ErrorRecovery =
    officialRecoveryRegistry.wrap(delegate)

@JvmOverloads
internal fun createOfficialCheckoutCallbacks(
    checkoutUrl: String,
    sink: (CheckoutEvent) -> Unit,
    post: (() -> Unit) -> Unit = { action -> Handler(Looper.getMainLooper()).post { action() } }
): OfficialCheckoutCallbacks {
    val bridge = CheckoutSdkRecoveryBridge(checkoutUrl, sink, CheckoutEventPoster(post))
    return OfficialCheckoutCallbacks(bridge, officialRecoveryRegistry.register(bridge))
}

internal class OfficialCheckoutCallbacks(
    private val bridge: CheckoutSdkRecoveryBridge,
    private val registration: CheckoutPresentationOwner
) : CheckoutPresentationOwner {
    fun failed(error: CheckoutException) = bridge.onFailure(error)

    fun completed() = bridge.onCompleted()

    fun cancelled() = bridge.onCancelled()

    fun externalLink(uri: URI) = bridge.onExternalLink(uri)

    fun invalidLink() = bridge.onInvalidLink()

    override fun dispose() = registration.dispose()
}

internal fun CheckoutException.toProjectFailure(): CheckoutFailure = when (this) {
    is CheckoutExpiredException -> CheckoutFailure.EXPIRED_OR_COMPLETED_CART
    is ConfigurationException -> CheckoutFailure.CONFIGURATION
    is HttpException -> CheckoutFailure.NETWORK
    else -> if (isRecoverable) CheckoutFailure.RECOVERABLE else CheckoutFailure.FATAL
}
