package com.gurbakir.mobile.account

import android.app.Service
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
import com.gurbakir.account.session.AndroidKeystoreCustomerSessionStore
import com.gurbakir.storefront.AndroidKeystoreCartSessionStore
import com.gurbakir.storefront.CartOwnership
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
class ProtectedStateProcessRecoveryTest {
    @Test
    fun sessionAndExactCartOwnerRestoreBeforeCheckedClearAndFreshProcessObservesAbsence() = runBlocking {
        assertFreshProcessRecovery(CartOwnership.CUSTOMER_ASSOCIATED)
    }

    @Test
    fun sessionAndPendingCartOwnerRestoreBeforeCheckedClearAndFreshProcessObservesAbsence() = runBlocking {
        assertFreshProcessRecovery(CartOwnership.VERIFY_PENDING)
    }

    private suspend fun assertFreshProcessRecovery(ownership: CartOwnership) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val nonce = UUID.randomUUID().toString().replace("-", "")
        val sessionStore = AndroidKeystoreCustomerSessionStore(context, ProtectedStateProbe.sessionIdentity(nonce))
        val cartStore = AndroidKeystoreCartSessionStore(context, ProtectedStateProbe.cartIdentity(nonce))
        val bindings = mutableListOf<BoundReader>()
        try {
            sessionStore.write(ProtectedStateProbe.session(nonce))
            cartStore.write(ProtectedStateProbe.cart(nonce, ownership))
            val beforeReader = BoundReader(context, ProtectedStateBeforeClearService::class.java).also {
                bindings += it
            }
            val before = beforeReader.read(nonce, ownership)
            assertProcessIdentity(before)
            assertTrue(before.getBoolean("sessionPresent"))
            assertTrue(before.getBoolean("cartPresent"))
            assertTrue(before.getBoolean("sessionMatches"))
            assertTrue(before.getBoolean("cartMatches"))
            assertTrue(before.getBoolean("valuesRedacted"))

            // Both checked native commits complete before this second process first opens the unique files.
            sessionStore.clear()
            cartStore.clear()
            assertNull(sessionStore.read())
            assertNull(cartStore.read())
            val afterReader = BoundReader(context, ProtectedStateAfterClearService::class.java).also { bindings += it }
            val after = afterReader.read(nonce, ownership)
            assertProcessIdentity(after)
            assertTrue("Reader after clear reused the earlier process", before.getInt("pid") != after.getInt("pid"))
            assertFalse(after.getBoolean("sessionPresent"))
            assertFalse(after.getBoolean("cartPresent"))
            assertTrue(after.getBoolean("preferencesAbsent"))
            assertTrue(after.getBoolean("valuesRedacted"))
            println(
                "Protected-state comparison: instrumentationPid=${Process.myPid()}, " +
                    "beforePid=${before.getInt("pid")}, afterPid=${after.getInt("pid")}, sameUid=true"
            )
        } finally {
            bindings.forEach { it.close() }
            try {
                sessionStore.clear()
                cartStore.clear()
            } finally {
                val keystore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
                keystore.deleteEntry(ProtectedStateProbe.sessionIdentity(nonce).keyAlias)
                keystore.deleteEntry(ProtectedStateProbe.cartIdentity(nonce).keyAlias)
            }
        }
    }

    private fun assertProcessIdentity(result: Bundle) {
        assertNull("Secondary-process storage comparison failed", result.getString("failure"))
        assertTrue("Reader is the instrumentation process", result.getInt("pid") != Process.myPid())
        assertEquals("Reader must share the protected-store UID", Process.myUid(), result.getInt("uid"))
    }

    private class BoundReader(private val context: Context, serviceClass: Class<out Service>) {
        private val connected = CountDownLatch(1)
        private val remote = AtomicReference<Messenger>()
        private val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, service: IBinder) {
                remote.set(Messenger(service))
                connected.countDown()
            }

            override fun onServiceDisconnected(name: ComponentName) = Unit
        }
        private val bound = context.bindService(Intent(context, serviceClass), connection, Context.BIND_AUTO_CREATE)

        fun read(nonce: String, ownership: CartOwnership): Bundle {
            assertTrue("Test-only reader could not bind", bound)
            assertTrue("Reader connection timed out", connected.await(TIMEOUT_SECONDS, TimeUnit.SECONDS))
            val replied = CountDownLatch(1)
            val result = AtomicReference<Bundle>()
            val handler = object : Handler(Looper.getMainLooper()) {
                override fun handleMessage(message: Message) {
                    if (message.what == ProtectedStateProbe.RESULT) {
                        result.set(message.data)
                        replied.countDown()
                    }
                }
            }
            try {
                remote.get().send(
                    Message.obtain(null, ProtectedStateProbe.READ).apply {
                        data = Bundle().apply {
                            putString("nonce", nonce)
                            putString("ownership", ownership.name)
                        }
                        replyTo = Messenger(handler)
                    }
                )
                assertTrue("Reader comparison timed out", replied.await(TIMEOUT_SECONDS, TimeUnit.SECONDS))
                return requireNotNull(result.get())
            } finally {
                handler.removeCallbacksAndMessages(null)
            }
        }

        fun close() {
            if (bound) context.unbindService(connection)
        }

        private companion object {
            const val TIMEOUT_SECONDS = 20L
        }
    }
}
