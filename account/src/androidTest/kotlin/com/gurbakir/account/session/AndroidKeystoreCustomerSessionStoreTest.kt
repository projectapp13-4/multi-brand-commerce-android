package com.gurbakir.account.session

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
class AndroidKeystoreCustomerSessionStoreTest {
    private val identity =
        ProtectedStoreIdentity("gate3_test_secure_customer_session", "gate3.test.customer.session.v1")
    private lateinit var context: Context
    private lateinit var store: AndroidKeystoreCustomerSessionStore

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        store = AndroidKeystoreCustomerSessionStore(context, identity)
        runBlocking { store.clear() }
    }

    @After
    fun tearDown() {
        runBlocking { store.clear() }
    }

    @Test
    fun encryptedSessionRestoresThroughANewStoreInstanceAndCanBeCleared() = runBlocking {
        val accessValue = UUID.randomUUID().toString()
        val refreshValue = UUID.randomUUID().toString()
        val idValue = UUID.randomUUID().toString()
        val expiresAt = Instant.ofEpochSecond(2_000_000_000L)
        store.write(
            CustomerSession(
                accessToken = SensitiveToken.from(accessValue),
                refreshToken = SensitiveToken.from(refreshValue),
                idToken = SensitiveToken.from(idValue),
                expiresAt = expiresAt
            )
        )

        val restored = AndroidKeystoreCustomerSessionStore(context, identity).read()

        assertTrue(restored?.accessToken?.use { it == accessValue } == true)
        assertTrue(restored?.refreshToken?.use { it == refreshValue } == true)
        assertTrue(restored?.idToken?.use { it == idValue } == true)
        assertEquals(expiresAt, restored?.expiresAt)
        store.clear()
        assertNull(AndroidKeystoreCustomerSessionStore(context, identity).read())
    }

    @Test
    fun corruptedCiphertextFailsClosedAndRemovesStoredPayload() = runBlocking {
        store.write(
            CustomerSession(
                accessToken = SensitiveToken.from(UUID.randomUUID().toString()),
                refreshToken = null,
                idToken = null,
                expiresAt = Instant.ofEpochSecond(2_000_000_000L)
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

        assertNull(AndroidKeystoreCustomerSessionStore(context, identity).read())
        assertFalse(preferences.contains("iv"))
        assertFalse(preferences.contains("ciphertext"))
    }
}
