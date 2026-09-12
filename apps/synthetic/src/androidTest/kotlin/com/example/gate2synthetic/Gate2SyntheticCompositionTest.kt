package com.example.gate2synthetic

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.gate2synthetic.config.Gate2SyntheticConfiguration
import com.gurbakir.account.session.AndroidKeystoreCustomerSessionStore
import com.gurbakir.account.session.CustomerSession
import com.gurbakir.account.session.SensitiveToken
import com.gurbakir.foundation.config.CustomerAccountCapability
import com.gurbakir.mobile.update.UpdatePolicyRefreshResult
import com.gurbakir.storefront.AndroidKeystoreCartSessionStore
import com.gurbakir.storefront.CartOwnership
import com.gurbakir.storefront.StorefrontDebugFixtures
import dagger.hilt.android.EntryPointAccessors
import java.io.File
import java.security.KeyStore
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Gate2SyntheticCompositionTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val configurationEntryPoint =
        EntryPointAccessors.fromApplication(context, Gate2SyntheticConfigurationEntryPoint::class.java)
    private val persistenceEntryPoint =
        EntryPointAccessors.fromApplication(context, Gate2SyntheticPersistenceEntryPoint::class.java)

    @Test
    fun hiltGraphUsesEveryApprovedAppOwnedConfigurationInput() = runBlocking {
        assertSame(Gate2SyntheticConfiguration.app, configurationEntryPoint.appConfiguration())
        assertSame(Gate2SyntheticConfiguration.home, configurationEntryPoint.homeConfiguration())
        assertSame(Gate2SyntheticConfiguration.catalog, configurationEntryPoint.catalogConfiguration())
        assertSame(Gate2SyntheticConfiguration.addressPolicy, configurationEntryPoint.addressPolicy())
        assertEquals(Gate2SyntheticConfiguration.searchPartition, configurationEntryPoint.searchPartition())
        assertEquals(Gate2SyntheticConfiguration.wishlistPartition, configurationEntryPoint.wishlistPartition())
        assertEquals(1, configurationEntryPoint.currentVersionCode().value)
        assertEquals(UpdatePolicyRefreshResult.LocalDefaults, configurationEntryPoint.updatePolicyGateway().refresh())
        assertTrue(configurationEntryPoint.deletionPageSource().pages().isNotEmpty())
        assertEquals(
            CustomerAccountCapability.Disabled,
            configurationEntryPoint.appConfiguration().applicationComposition.capabilities.customerAccount
        )
    }

    @Test
    fun roomAndEncryptedStoresUseOnlyTheSyntheticApplicationSandbox() = runBlocking {
        val database = persistenceEntryPoint.database()
        val databasePath = File(requireNotNull(database.openHelper.writableDatabase.path)).canonicalFile
        assertEquals("gate2-synthetic-local.db", databasePath.name)
        assertEquals(2, database.openHelper.readableDatabase.version)
        assertTrue(databasePath.isFile)
        assertFalse(context.getDatabasePath("gurbakir-local.db").exists())

        val cartStore = persistenceEntryPoint.cartSessionStore()
        val customerStore = persistenceEntryPoint.customerSessionStore()
        assertTrue(cartStore is AndroidKeystoreCartSessionStore)
        assertTrue(customerStore is AndroidKeystoreCustomerSessionStore)
        assertSame(cartStore, persistenceEntryPoint.cartSessionStore())
        assertSame(customerStore, persistenceEntryPoint.customerSessionStore())

        val cart =
            StorefrontDebugFixtures.persistedCart(
                rawCartId = "gid://shopify/Cart/synthetic?key=synthetic",
                expiresAt = Instant.parse("2030-01-01T00:00:00Z"),
                ownership = CartOwnership.ANONYMOUS
            )
        val session =
            CustomerSession(
                accessToken = SensitiveToken.from("synthetic-test-token"),
                refreshToken = null,
                idToken = null,
                expiresAt = Instant.parse("2030-01-01T00:00:00Z")
            )
        cartStore.clear()
        customerStore.clear()
        cartStore.write(cart)
        customerStore.write(session)

        val recreatedCart =
            AndroidKeystoreCartSessionStore(
                context,
                Gate2SyntheticConfiguration.app.protectedPersistence.cart
            ).read()
        val recreatedSession =
            AndroidKeystoreCustomerSessionStore(
                context,
                Gate2SyntheticConfiguration.app.protectedPersistence.customerSession
            ).read()
        assertTrue(recreatedCart?.id?.use { it == "gid://shopify/Cart/synthetic?key=synthetic" } == true)
        assertEquals(cart.expiresAt, recreatedCart?.expiresAt)
        assertEquals(cart.ownership, recreatedCart?.ownership)
        assertTrue(recreatedSession?.accessToken?.use { it == "synthetic-test-token" } == true)
        assertEquals(session.expiresAt, recreatedSession?.expiresAt)

        assertSyntheticProtectedStatePresent()
        cartStore.clear()
        customerStore.clear()
        assertNull(cartStore.read())
        assertNull(customerStore.read())

        assertGurbakirProtectedStateAbsent()
    }

    private fun assertSyntheticProtectedStatePresent() {
        val preferencesDirectory = File(context.applicationInfo.dataDir, "shared_prefs")
        listOf(
            "gate2_synthetic_secure_cart_development.xml",
            "gate2_synthetic_secure_customer_session_development.xml"
        ).forEach { name -> assertTrue(File(preferencesDirectory, name).exists()) }

        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        assertTrue(keyStore.containsAlias("gate2.synthetic.cart.development.v1"))
        assertTrue(keyStore.containsAlias("gate2.synthetic.customer.session.development.v1"))
    }

    private fun assertGurbakirProtectedStateAbsent() {
        val preferencesDirectory = File(context.applicationInfo.dataDir, "shared_prefs")
        listOf(
            "gurbakir_secure_cart_development.xml",
            "gurbakir_secure_customer_session_development.xml"
        ).forEach { name -> assertFalse(File(preferencesDirectory, name).exists()) }

        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        assertFalse(keyStore.containsAlias("gurbakir.cart.development.v1"))
        assertFalse(keyStore.containsAlias("gurbakir.customer.session.development.v1"))
    }
}
