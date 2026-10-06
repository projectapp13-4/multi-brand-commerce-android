package com.gurbakir.mobile.account

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import com.gurbakir.account.CustomerAccountGateway
import com.gurbakir.account.CustomerAccountResult
import com.gurbakir.account.CustomerIdentity
import com.gurbakir.account.oauth.CustomerAccountAuthorizationCoordinator
import com.gurbakir.account.oauth.CustomerAccountAuthorizationGrant
import com.gurbakir.account.oauth.CustomerAccountLogoutClient
import com.gurbakir.account.oauth.CustomerAccountTokenClient
import com.gurbakir.account.oauth.CustomerLogoutResult
import com.gurbakir.account.oauth.CustomerTokenPayload
import com.gurbakir.account.oauth.CustomerTokenResult
import com.gurbakir.account.oauth.UnconfiguredCustomerAccountDiscoveryClient
import com.gurbakir.account.session.AndroidKeystoreCustomerSessionStore
import com.gurbakir.account.session.CustomerAccountSessionCoordinator
import com.gurbakir.account.session.CustomerSession
import com.gurbakir.account.session.SensitiveToken
import com.gurbakir.foundation.config.CustomerAccountConfiguration
import com.gurbakir.foundation.config.ProtectedStoreIdentity
import com.gurbakir.foundation.config.REQUIRED_CUSTOMER_ACCOUNT_SCOPES
import com.gurbakir.mobile.cart.CartActionResult
import com.gurbakir.mobile.cart.CartCheckoutResolution
import com.gurbakir.mobile.cart.CartRepository
import com.gurbakir.mobile.cart.CartState
import com.gurbakir.mobile.cart.CartStatus
import com.gurbakir.storefront.SensitiveCartLineId
import java.security.KeyStore
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Actual encrypted storage, with only the native commit receipt boundary controlled. */
internal class ProtectedSessionReceiptFixture(private val context: Context) {
    enum class ReceiptMode { HEALTHY, REFUSE_BEFORE_APPLY, APPLY_THEN_REFUSE }

    private val nonce = UUID.randomUUID().toString().replace("-", "")
    private val identity = ProtectedStoreIdentity("session_receipt_$nonce", "session.receipt.$nonce")
    private val preferences = context.getSharedPreferences(identity.preferencesName, Context.MODE_PRIVATE)
    private val faultPreferences = ReceiptPreferences(preferences)
    private val faultContext = object : ContextWrapper(context) {
        override fun getApplicationContext(): Context = this

        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
            if (name == identity.preferencesName) faultPreferences else super.getSharedPreferences(name, mode)
    }
    val store = AndroidKeystoreCustomerSessionStore(faultContext, identity)
    val healthyStore = AndroidKeystoreCustomerSessionStore(context, identity)
    val identityCalls = AtomicInteger()
    val renewalCalls = AtomicInteger()
    val logoutCalls = AtomicInteger()
    private val now = Instant.parse("2026-10-06T00:00:00Z")
    private val configuration = CustomerAccountConfiguration(
        clientId = "synthetic-receipt-client",
        issuer = "https://shop.example/customer-account",
        authorizationEndpoint = "https://shop.example/authentication/oauth/authorize",
        tokenEndpoint = "https://shop.example/authentication/oauth/token",
        logoutEndpoint = "https://shop.example/authentication/logout",
        graphqlEndpoint = "https://shop.example/customer/api/2026-07/graphql",
        redirectUri = "shop.123456.example://oauth/callback",
        userAgent = "Synthetic-Android",
        scopes = REQUIRED_CUSTOMER_ACCOUNT_SCOPES
    )
    val coordinator = CustomerAccountSessionCoordinator(
        configuration = configuration,
        tokenClient = object : CustomerAccountTokenClient {
            override suspend fun exchange(grant: CustomerAccountAuthorizationGrant): CustomerTokenResult =
                error("This receipt control never authorizes a customer")

            override suspend fun refresh(refreshToken: SensitiveToken): CustomerTokenResult {
                renewalCalls.incrementAndGet()
                return CustomerTokenResult.Success(
                    CustomerTokenPayload(
                        SensitiveToken.from("receipt-renewed-$nonce"),
                        null,
                        null,
                        now.plusSeconds(3_600)
                    )
                )
            }
        },
        sessionStore = store,
        logoutClient = CustomerAccountLogoutClient {
            logoutCalls.incrementAndGet()
            CustomerLogoutResult.Success
        },
        clock = Clock.fixed(now, ZoneOffset.UTC)
    )
    val controller = DefaultAccountController(
        CustomerAccountAuthorizationCoordinator(configuration, UnconfiguredCustomerAccountDiscoveryClient()),
        coordinator,
        object : CustomerAccountGateway {
            override suspend fun loadIdentity(): CustomerAccountResult<CustomerIdentity> {
                identityCalls.incrementAndGet()
                return CustomerAccountResult.Success(
                    CustomerIdentity("gid://shopify/Customer/receipt-$nonce", "Synthetic receipt control")
                )
            }
        },
        EmptyCart()
    )

    var receiptMode: ReceiptMode
        get() = faultPreferences.receiptMode
        set(value) {
            faultPreferences.receiptMode = value
        }

    val rejectedCommits: Int get() = faultPreferences.rejectedCommits.get()
    val ciphertextPresent: Boolean get() = preferences.contains("ciphertext")

    suspend fun seed(expired: Boolean = false) {
        healthyStore.write(
            CustomerSession(
                SensitiveToken.from("receipt-original-$nonce"),
                SensitiveToken.from("receipt-refresh-$nonce"),
                null,
                if (expired) now.minusSeconds(1) else now.plusSeconds(3_600)
            )
        )
    }

    fun corruptCiphertext(): Boolean = preferences.edit().putString("ciphertext", "not-valid-base64!").commit()

    suspend fun hasOriginalToken(): Boolean = healthyStore.read()?.accessToken?.use {
        it == "receipt-original-$nonce"
    } == true

    suspend fun hasRenewedToken(): Boolean = healthyStore.read()?.accessToken?.use {
        it == "receipt-renewed-$nonce"
    } == true

    suspend fun close() {
        receiptMode = ReceiptMode.HEALTHY
        try {
            healthyStore.clear()
        } finally {
            KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(identity.keyAlias)
        }
    }

    private class ReceiptPreferences(private val delegate: SharedPreferences) : SharedPreferences by delegate {
        var receiptMode = ReceiptMode.HEALTHY
        val rejectedCommits = AtomicInteger()

        override fun edit(): SharedPreferences.Editor {
            val editor = delegate.edit()
            return object : SharedPreferences.Editor by editor {
                override fun putString(key: String, value: String?): SharedPreferences.Editor {
                    editor.putString(key, value)
                    return this
                }

                override fun remove(key: String): SharedPreferences.Editor {
                    editor.remove(key)
                    return this
                }

                override fun commit(): Boolean = when (receiptMode) {
                    ReceiptMode.HEALTHY -> editor.commit()

                    ReceiptMode.REFUSE_BEFORE_APPLY -> {
                        rejectedCommits.incrementAndGet()
                        false
                    }

                    ReceiptMode.APPLY_THEN_REFUSE -> {
                        check(editor.commit()) { "Healthy native commit failed in receipt fixture" }
                        rejectedCommits.incrementAndGet()
                        false
                    }
                }
            }
        }
    }

    private class EmptyCart : CartRepository {
        override val state: StateFlow<CartState> = MutableStateFlow(CartState(CartStatus.EMPTY))
        override suspend fun refresh() = Unit
        override suspend fun add(merchandiseId: String, quantity: Int): CartActionResult =
            error("No merchandise mutation belongs to a receipt control")
        override suspend fun update(lineId: SensitiveCartLineId, quantity: Int): CartActionResult =
            error("No merchandise mutation belongs to a receipt control")
        override suspend fun remove(lineId: SensitiveCartLineId): CartActionResult =
            error("No merchandise mutation belongs to a receipt control")
        override suspend fun discard(): CartActionResult = error("A receipt control must retain cart work")
        override suspend fun prepareCheckout(): CartCheckoutResolution =
            error("No checkout belongs to a receipt control")
    }
}
