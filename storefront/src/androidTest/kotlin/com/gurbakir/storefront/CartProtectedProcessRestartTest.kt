package com.gurbakir.storefront

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.Process
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.security.KeyStore
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CartProtectedProcessRestartTest {
    @Test
    fun exactOwnerAndVerificationPendingRestoreInDistinctProcess() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val nonce = UUID.randomUUID().toString().replace("-", "")
        val identity = CartOwnershipProbeContract.identity(nonce)
        val store = AndroidKeystoreCartSessionStore(context, identity)
        val connected = CountDownLatch(1)
        val replied = CountDownLatch(1)
        val remote = AtomicReference<Messenger>()
        val result = AtomicReference<Bundle>()
        val responseHandler = object : Handler(Looper.getMainLooper()) {
            override fun handleMessage(message: Message) {
                if (message.what == CartOwnershipProbeContract.RESULT) {
                    result.set(message.data)
                    replied.countDown()
                }
            }
        }
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, service: IBinder) {
                remote.set(Messenger(service))
                connected.countDown()
            }

            override fun onServiceDisconnected(name: ComponentName) = Unit
        }
        var bound = false
        try {
            // Commit before starting the reader, which has never opened this unique preference file.
            store.write(CartOwnershipProbeContract.persistedCart(nonce))
            val preferences = context.getSharedPreferences(identity.preferencesName, Context.MODE_PRIVATE)
            assertTrue("Encrypted payload was not committed", preferences.contains("ciphertext"))
            assertFalse(preferences.all.toString().contains(CartOwnershipProbeContract.owner(nonce)))
            assertFalse(preferences.all.toString().contains(CartOwnershipProbeContract.cart(nonce)))

            bound = context.bindService(
                Intent(context, CartOwnershipProbeService::class.java),
                connection,
                Context.BIND_AUTO_CREATE
            )
            assertTrue("Test-only secondary-process service could not bind", bound)
            assertTrue("Secondary-process connection timed out", connected.await(TIMEOUT_SECONDS, TimeUnit.SECONDS))
            remote.get().send(
                Message.obtain(null, CartOwnershipProbeContract.READ).apply {
                    data = Bundle().apply { putString(CartOwnershipProbeContract.NONCE, nonce) }
                    replyTo = Messenger(responseHandler)
                }
            )
            assertTrue("Secondary-process read timed out", replied.await(TIMEOUT_SECONDS, TimeUnit.SECONDS))
            assertRemoteRead(result.get())
        } finally {
            try {
                if (bound) context.unbindService(connection)
                responseHandler.removeCallbacksAndMessages(null)
            } finally {
                try {
                    store.clear()
                    assertNull("Synthetic encrypted cart was not cleared", store.read())
                } finally {
                    KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(identity.keyAlias)
                }
            }
        }
    }

    private fun assertRemoteRead(result: Bundle) {
        assertNull(result.getString(CartOwnershipProbeContract.FAILURE))
        assertTrue(
            "Reader ran in the instrumentation process",
            result.getInt(CartOwnershipProbeContract.PID) != Process.myPid()
        )
        assertEquals(
            "Reader does not share the protected-store UID",
            Process.myUid(),
            result.getInt(CartOwnershipProbeContract.UID)
        )
        listOf(
            CartOwnershipProbeContract.PRESENT,
            CartOwnershipProbeContract.CART_MATCHES,
            CartOwnershipProbeContract.OWNER_MATCHES,
            CartOwnershipProbeContract.EXPIRY_MATCHES,
            CartOwnershipProbeContract.OWNERSHIP_MATCHES,
            CartOwnershipProbeContract.CIPHERTEXT_PRESENT,
            CartOwnershipProbeContract.PREFERENCES_REDACTED,
            CartOwnershipProbeContract.MODEL_REDACTED
        ).forEach { comparison ->
            assertTrue("Distinct-process comparison failed: $comparison", result.getBoolean(comparison))
        }
    }

    private companion object {
        const val TIMEOUT_SECONDS = 20L
    }
}
