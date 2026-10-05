package com.gurbakir.storefront

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
import android.os.RemoteException
import com.gurbakir.foundation.config.ProtectedStoreIdentity
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Test-APK-only reader: no provider client and no private values in its IPC reply. */
class CartOwnershipProbeService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val messenger = Messenger(
        object : Handler(Looper.getMainLooper()) {
            override fun handleMessage(message: Message) {
                if (message.what != CartOwnershipProbeContract.READ) return
                val recipient = message.replyTo ?: return
                val nonce = message.data.getString(CartOwnershipProbeContract.NONCE)
                scope.launch {
                    val response = Message.obtain(null, CartOwnershipProbeContract.RESULT).apply {
                        data = readResult(nonce)
                    }
                    try {
                        recipient.send(response)
                    } catch (_: RemoteException) {
                        // The instrumentation client may already have unbound after a timeout.
                    }
                }
            }
        }
    )

    override fun onBind(intent: Intent): IBinder = messenger.binder

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private suspend fun readResult(nonce: String?): Bundle {
        val result = Bundle().apply {
            putInt(CartOwnershipProbeContract.PID, Process.myPid())
            putInt(CartOwnershipProbeContract.UID, Process.myUid())
        }
        try {
            require(nonce != null && nonce.matches(Regex("[a-f0-9]{32}")))
            val identity = CartOwnershipProbeContract.identity(nonce)
            val expected = CartOwnershipProbeContract.persistedCart(nonce)
            val restored = AndroidKeystoreCartSessionStore(this, identity).read()
            val preferences = getSharedPreferences(identity.preferencesName, Context.MODE_PRIVATE)
            val rawOwner = CartOwnershipProbeContract.owner(nonce)
            val rawCart = CartOwnershipProbeContract.cart(nonce)
            result.apply {
                putBoolean(CartOwnershipProbeContract.PRESENT, restored != null)
                putBoolean(CartOwnershipProbeContract.CART_MATCHES, restored?.id == expected.id)
                putBoolean(CartOwnershipProbeContract.OWNER_MATCHES, restored?.customerId == expected.customerId)
                putBoolean(CartOwnershipProbeContract.EXPIRY_MATCHES, restored?.expiresAt == expected.expiresAt)
                putBoolean(CartOwnershipProbeContract.OWNERSHIP_MATCHES, restored?.ownership == expected.ownership)
                putBoolean(
                    CartOwnershipProbeContract.CIPHERTEXT_PRESENT,
                    preferences.contains("iv") && preferences.contains("ciphertext")
                )
                putBoolean(
                    CartOwnershipProbeContract.PREFERENCES_REDACTED,
                    preferences.all.values.none { value ->
                        value.toString().contains(rawOwner) || value.toString().contains(rawCart)
                    }
                )
                putBoolean(
                    CartOwnershipProbeContract.MODEL_REDACTED,
                    !restored.toString().contains(rawOwner) && !restored.toString().contains(rawCart)
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            result.putString(CartOwnershipProbeContract.FAILURE, "Probe read failed: ${error.javaClass.simpleName}")
        }
        return result
    }
}

internal object CartOwnershipProbeContract {
    const val READ = 1
    const val RESULT = 2
    const val NONCE = "nonce"
    const val PID = "pid"
    const val UID = "uid"
    const val PRESENT = "present"
    const val CART_MATCHES = "cartMatches"
    const val OWNER_MATCHES = "ownerMatches"
    const val EXPIRY_MATCHES = "expiryMatches"
    const val OWNERSHIP_MATCHES = "ownershipMatches"
    const val CIPHERTEXT_PRESENT = "ciphertextPresent"
    const val PREFERENCES_REDACTED = "preferencesRedacted"
    const val MODEL_REDACTED = "modelRedacted"
    const val FAILURE = "failure"

    fun identity(nonce: String): ProtectedStoreIdentity =
        ProtectedStoreIdentity("cart_process_probe_$nonce", "cart.process.probe.$nonce")

    fun owner(nonce: String): String = "gid://shopify/Customer/synthetic-cart-process-$nonce"

    fun cart(nonce: String): String = "synthetic-cart-process-$nonce"

    fun persistedCart(nonce: String): PersistedCart = PersistedCart(
        id = SensitiveCartId.from(cart(nonce)),
        expiresAt = Instant.ofEpochSecond(2_000_000_000L, 123_456_789L),
        ownership = CartOwnership.VERIFY_PENDING,
        customerId = SensitiveCustomerId.from(owner(nonce))
    )
}
