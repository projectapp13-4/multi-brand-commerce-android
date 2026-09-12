package com.gurbakir.account.oauth

import android.content.Context
import android.content.Intent
import java.io.Closeable
import net.openid.appauth.AuthorizationService

/**
 * Owns the AppAuth browser integration without exposing AppAuth types to the application module.
 * The returned intent launches the user's system browser or Custom Tab, never an embedded WebView.
 */
class CustomerAccountAuthorizationBrowser(context: Context) : Closeable {
    private val authorizationService = AuthorizationService(context.applicationContext)

    fun createAuthorizationIntent(plan: CustomerAccountAuthorizationPlan): Intent =
        authorizationService.getAuthorizationRequestIntent(
            AppAuthCustomerAccountRequestFactory.create(plan)
        )

    override fun close() {
        authorizationService.dispose()
    }
}
