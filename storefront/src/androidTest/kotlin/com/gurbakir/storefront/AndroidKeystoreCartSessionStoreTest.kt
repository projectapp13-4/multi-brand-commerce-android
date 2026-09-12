package com.gurbakir.storefront

import android.content.Context
import android.util.Base64
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.gurbakir.foundation.config.ProtectedStoreIdentity
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidKeystoreCartSessionStoreTest {
    private val identity = ProtectedStoreIdentity("gate3_test_secure_cart", "gate3.test.cart.v1")
    private lateinit var context: Context
    private lateinit var store: AndroidKeystoreCartSessionStore

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        store = AndroidKeystoreCartSessionStore(context, identity)
        runBlocking { store.clear() }
    }

    @After
    fun tearDown() {
        runBlocking { store.clear() }
    }

    @Test
    fun encryptedCartRestoresThroughANewStoreInstanceAndCanBeCleared() = runBlocking {
        val cartValue = UUID.randomUUID().toString()
        val expiresAt = Instant.ofEpochSecond(2_000_000_000L)
        store.write(
            PersistedCart(
                SensitiveCartId.from(cartValue),
                expiresAt,
                CartOwnership.ANONYMOUS
            )
        )

        val restored = AndroidKeystoreCartSessionStore(context, identity).read()

        assertTrue(restored?.id?.use { it == cartValue } == true)
        assertEquals(expiresAt, restored?.expiresAt)
        store.clear()
        assertNull(AndroidKeystoreCartSessionStore(context, identity).read())
    }

    @Test
    fun corruptedCiphertextFailsClosedAndRemovesStoredPayload() = runBlocking {
        store.write(
            PersistedCart(
                SensitiveCartId.from(UUID.randomUUID().toString()),
                Instant.ofEpochSecond(2_000_000_000L),
                CartOwnership.ANONYMOUS
            )
        )
        val preferences = context.getSharedPreferences(
            identity.preferencesName,
            Context.MODE_PRIVATE
        )
        assertTrue(
            preferences.edit()
                .putString("ciphertext", Base64.encodeToString(byteArrayOf(1, 2, 3), Base64.NO_WRAP))
                .commit()
        )

        assertNull(AndroidKeystoreCartSessionStore(context, identity).read())
        assertFalse(preferences.contains("iv"))
        assertFalse(preferences.contains("ciphertext"))
    }
}
