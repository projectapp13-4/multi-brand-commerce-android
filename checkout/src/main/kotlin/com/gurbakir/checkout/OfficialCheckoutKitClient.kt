package com.gurbakir.checkout

import android.app.Activity
import android.net.Uri
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
import com.shopify.checkoutsheetkit.HttpException
import com.shopify.checkoutsheetkit.LogLevel
import com.shopify.checkoutsheetkit.ShopifyCheckoutSheetKit
import com.shopify.checkoutsheetkit.lifecycleevents.CheckoutCompletedEvent
import com.shopify.checkoutsheetkit.pixelevents.PixelEvent
import java.net.URI

class OfficialCheckoutKitClient : CheckoutKitClient {
    init {
        ShopifyCheckoutSheetKit.configure { configuration ->
            configuration.logLevel = LogLevel.ERROR
        }
    }

    override fun preload(activity: Activity, checkoutUrl: URI, eventSink: (CheckoutEvent) -> Unit): Boolean {
        val componentActivity = activity as? ComponentActivity ?: return false
        ShopifyCheckoutSheetKit.preload(checkoutUrl.toASCIIString(), componentActivity)
        return true
    }

    override fun present(activity: Activity, checkoutUrl: URI, eventSink: (CheckoutEvent) -> Unit): Boolean {
        val componentActivity = activity as? ComponentActivity ?: return false
        val processor = RestrictedCheckoutEventProcessor(componentActivity, eventSink)
        return ShopifyCheckoutSheetKit.present(
            checkoutUrl.toASCIIString(),
            componentActivity,
            processor
        ) != null
    }

    override fun invalidate() {
        ShopifyCheckoutSheetKit.invalidate()
    }
}

private class RestrictedCheckoutEventProcessor(
    activity: ComponentActivity,
    private val eventSink: (CheckoutEvent) -> Unit
) : DefaultCheckoutEventProcessor(activity) {
    override fun onCheckoutCompleted(checkoutCompletedEvent: CheckoutCompletedEvent) {
        eventSink(CheckoutEvent.Completed)
    }

    override fun onCheckoutFailed(error: CheckoutException) {
        eventSink(CheckoutEvent.Failed(error.toProjectFailure()))
    }

    override fun onCheckoutCanceled() {
        eventSink(CheckoutEvent.Cancelled)
    }

    override fun onCheckoutLinkClicked(uri: Uri) {
        runCatching { URI(uri.toString()) }
            .onSuccess { eventSink(CheckoutEvent.ExternalLinkRequested(it)) }
            .onFailure { eventSink(CheckoutEvent.Failed(CheckoutFailure.FATAL)) }
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

internal fun CheckoutException.toProjectFailure(): CheckoutFailure = when (this) {
    is CheckoutExpiredException -> CheckoutFailure.EXPIRED_OR_COMPLETED_CART
    is ConfigurationException -> CheckoutFailure.CONFIGURATION
    is HttpException -> CheckoutFailure.NETWORK
    else -> if (isRecoverable) CheckoutFailure.RECOVERABLE else CheckoutFailure.FATAL
}
