package com.example.gate2synthetic

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.gate2synthetic.config.Gate2SyntheticConfiguration
import com.gurbakir.account.session.AndroidKeystoreCustomerSessionStore
import com.gurbakir.account.session.CustomerSession
import com.gurbakir.account.session.SensitiveToken
import com.gurbakir.storefront.AndroidKeystoreCartSessionStore
import com.gurbakir.storefront.CartOwnership
import com.gurbakir.storefront.StorefrontDebugFixtures
import java.io.File
import java.security.KeyStore
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

private const val PROCESS_PROOF_ARGUMENT = "gate3ProcessProof"
private const val PROCESS_PROOF_SEED = "seed"
private const val PROCESS_PROOF_VERIFY = "verify"
private const val SYNTHETIC_CART_ID = "gid://shopify/Cart/gate3-process-proof?key=synthetic"
private const val SYNTHETIC_ACCESS_TOKEN = "gate3-synthetic-process-proof-token"
private val SYNTHETIC_EXPIRY = Instant.parse("2030-01-01T00:00:00Z")

@RunWith(AndroidJUnit4::class)
class Gate3SyntheticProcessPersistenceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val configuration = Gate2SyntheticConfiguration.app.protectedPersistence

    @Test
    fun seedEncryptedStoresForProcessRestartProof() = runBlocking {
        requireProofMode(PROCESS_PROOF_SEED)
        val cartStore = AndroidKeystoreCartSessionStore(context, configuration.cart)
        val customerStore = AndroidKeystoreCustomerSessionStore(context, configuration.customerSession)

        cartStore.clear()
        customerStore.clear()
        cartStore.write(
            StorefrontDebugFixtures.persistedCart(
                rawCartId = SYNTHETIC_CART_ID,
                expiresAt = SYNTHETIC_EXPIRY,
                ownership = CartOwnership.ANONYMOUS
            )
        )
        customerStore.write(
            CustomerSession(
                accessToken = SensitiveToken.from(SYNTHETIC_ACCESS_TOKEN),
                refreshToken = null,
                idToken = null,
                expiresAt = SYNTHETIC_EXPIRY
            )
        )

        assertSyntheticProtectedStatePresent()
        assertGurbakirStateAbsent()
    }

    @Test
    fun verifyEncryptedStoresAfterProcessRestartAndClear() = runBlocking {
        requireProofMode(PROCESS_PROOF_VERIFY)
        val cartStore = AndroidKeystoreCartSessionStore(context, configuration.cart)
        val customerStore = AndroidKeystoreCustomerSessionStore(context, configuration.customerSession)

        try {
            val cart = cartStore.read()
            val session = customerStore.read()
            assertTrue(cart?.id?.use { it == SYNTHETIC_CART_ID } == true)
            assertEquals(SYNTHETIC_EXPIRY, cart?.expiresAt)
            assertEquals(CartOwnership.ANONYMOUS, cart?.ownership)
            assertTrue(session?.accessToken?.use { it == SYNTHETIC_ACCESS_TOKEN } == true)
            assertEquals(SYNTHETIC_EXPIRY, session?.expiresAt)
            assertSyntheticProtectedStatePresent()
            assertGurbakirStateAbsent()
        } finally {
            cartStore.clear()
            customerStore.clear()
        }

        assertNull(AndroidKeystoreCartSessionStore(context, configuration.cart).read())
        assertNull(AndroidKeystoreCustomerSessionStore(context, configuration.customerSession).read())
        assertGurbakirStateAbsent()
    }

    private fun requireProofMode(expected: String) {
        assumeTrue(expected == InstrumentationRegistry.getArguments().getString(PROCESS_PROOF_ARGUMENT))
    }

    private fun assertSyntheticProtectedStatePresent() {
        val preferencesDirectory = File(context.applicationInfo.dataDir, "shared_prefs")
        listOf(
            "gate2_synthetic_secure_cart_development.xml",
            "gate2_synthetic_secure_customer_session_development.xml"
        ).forEach { name -> assertTrue(File(preferencesDirectory, name).isFile) }

        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        assertTrue(keyStore.containsAlias("gate2.synthetic.cart.development.v1"))
        assertTrue(keyStore.containsAlias("gate2.synthetic.customer.session.development.v1"))
    }

    private fun assertGurbakirStateAbsent() {
        val preferencesDirectory = File(context.applicationInfo.dataDir, "shared_prefs")
        listOf(
            "gurbakir_secure_cart_development.xml",
            "gurbakir_secure_cart_staging.xml",
            "gurbakir_secure_customer_session_development.xml",
            "gurbakir_secure_customer_session_staging.xml"
        ).forEach { name -> assertFalse(File(preferencesDirectory, name).exists()) }
        assertFalse(context.getDatabasePath("gurbakir-local.db").exists())

        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        listOf(
            "gurbakir.cart.development.v1",
            "gurbakir.cart.staging.v1",
            "gurbakir.customer.session.development.v1",
            "gurbakir.customer.session.staging.v1"
        ).forEach { alias -> assertFalse(keyStore.containsAlias(alias)) }
    }
}
