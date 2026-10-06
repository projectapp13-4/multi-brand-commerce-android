package com.gurbakir.mobile.account

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.Process
import com.gurbakir.account.session.AndroidKeystoreCustomerSessionStore
import com.gurbakir.account.session.CustomerSession
import com.gurbakir.account.session.SensitiveToken
import com.gurbakir.foundation.config.ProtectedStoreIdentity
import com.gurbakir.storefront.AndroidKeystoreCartSessionStore
import com.gurbakir.storefront.CartOwnership
import com.gurbakir.storefront.PersistedCart
import com.gurbakir.storefront.SensitiveCartId
import com.gurbakir.storefront.SensitiveCustomerId
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Distinct test-only processes; IPC carries comparisons and process identity, never protected values. */
class ProtectedStateBeforeClearService : ProtectedStateProbeService()

class ProtectedStateAfterClearService : ProtectedStateProbeService()

abstract class ProtectedStateProbeService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val messenger = Messenger(object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(message: Message) {
            if (message.what != ProtectedStateProbe.READ) return
            val recipient = message.replyTo ?: return
            val request = Bundle(message.data)
            scope.launch {
                val response = Message.obtain(null, ProtectedStateProbe.RESULT).apply {
                    data = readResult(request)
                }
                runCatching { recipient.send(response) }
            }
        }
    })

    override fun onBind(intent: Intent): IBinder = messenger.binder

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private suspend fun readResult(request: Bundle): Bundle {
        val result = Bundle().apply {
            putInt("pid", Process.myPid())
            putInt("uid", Process.myUid())
        }
        val outcome = runCatching {
            val nonce = requireNotNull(request.getString("nonce"))
            require(nonce.matches(Regex("[a-f0-9]{32}")))
            val ownership = CartOwnership.valueOf(requireNotNull(request.getString("ownership")))
            val expectedSession = ProtectedStateProbe.session(nonce)
            val expectedCart = ProtectedStateProbe.cart(nonce, ownership)
            val session = AndroidKeystoreCustomerSessionStore(this, ProtectedStateProbe.sessionIdentity(nonce)).read()
            val cart = AndroidKeystoreCartSessionStore(this, ProtectedStateProbe.cartIdentity(nonce)).read()
            result.putBoolean("sessionPresent", session != null)
            result.putBoolean("cartPresent", cart != null)
            result.putBoolean("sessionMatches", session?.matches(expectedSession) == true)
            result.putBoolean("cartMatches", cart == expectedCart)
            result.putBoolean(
                "preferencesAbsent",
                identities(nonce).all { identity ->
                    val preferences = getSharedPreferences(identity.preferencesName, Context.MODE_PRIVATE)
                    !preferences.contains("iv") && !preferences.contains("ciphertext")
                }
            )
            result.putBoolean(
                "valuesRedacted",
                identities(nonce).all { identity ->
                    getSharedPreferences(identity.preferencesName, Context.MODE_PRIVATE).all.values.none { value ->
                        value.toString().contains(nonce)
                    }
                } && !session.toString().contains(nonce) && !cart.toString().contains(nonce)
            )
        }
        outcome.exceptionOrNull()?.let { failure ->
            if (failure is CancellationException) throw failure
            result.putString("failure", failure.javaClass.simpleName)
        }
        return result
    }

    private fun identities(nonce: String) =
        listOf(ProtectedStateProbe.sessionIdentity(nonce), ProtectedStateProbe.cartIdentity(nonce))

    private fun CustomerSession.matches(expected: CustomerSession): Boolean {
        val accessMatches = accessToken.use { actual -> expected.accessToken.use { it == actual } }
        val refreshMatches = refreshToken?.use { actual -> expected.refreshToken?.use { it == actual } } == true
        val idMatches = idToken?.use { actual -> expected.idToken?.use { it == actual } } == true
        return expiresAt == expected.expiresAt && accessMatches && refreshMatches && idMatches
    }
}

internal object ProtectedStateProbe {
    const val READ = 1
    const val RESULT = 2

    fun sessionIdentity(nonce: String) = ProtectedStoreIdentity("state_session_$nonce", "state.session.$nonce")

    fun cartIdentity(nonce: String) = ProtectedStoreIdentity("state_cart_$nonce", "state.cart.$nonce")

    fun session(nonce: String) = CustomerSession(
        SensitiveToken.from("state-access-$nonce"),
        SensitiveToken.from("state-refresh-$nonce"),
        SensitiveToken.from("state-id-$nonce"),
        Instant.ofEpochSecond(2_000_000_000L, 123_456_789L)
    )

    fun cart(nonce: String, ownership: CartOwnership) = PersistedCart(
        // The opaque factory is provider-internal; this follows the existing core test-only fixture seam.
        SensitiveCartId::class.java.getDeclaredConstructor(String::class.java).run {
            isAccessible = true
            newInstance("state-cart-$nonce")
        },
        Instant.ofEpochSecond(2_000_000_000L, 123_456_789L),
        ownership,
        SensitiveCustomerId.from("gid://shopify/Customer/state-owner-$nonce")
    )
}
