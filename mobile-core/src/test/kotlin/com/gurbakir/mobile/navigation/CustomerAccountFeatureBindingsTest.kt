package com.gurbakir.mobile.navigation

import com.gurbakir.foundation.config.ApplicationCapabilities
import com.gurbakir.foundation.config.ApplicationComposition
import com.gurbakir.foundation.config.CapabilityState
import com.gurbakir.foundation.config.CustomerAccountCapability
import com.gurbakir.foundation.config.CustomerAccountConfiguration
import com.gurbakir.foundation.config.PrimaryNavigationDestination
import com.gurbakir.foundation.config.PrimaryNavigationSpec
import com.gurbakir.mobile.accountdeletion.DeletionPageLaunchResult
import com.gurbakir.mobile.order.TrackingUrlPolicy
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class CustomerAccountFeatureBindingsTest {
    @Test
    fun `account bindings are required exactly when enabled`() {
        val enabled = composition(true)
        val disabled = composition(false)
        validateCustomerAccountBindings(enabled, bindings())
        validateCustomerAccountBindings(disabled, null)
        assertThrows<IllegalArgumentException> { validateCustomerAccountBindings(enabled, null) }
        assertThrows<IllegalArgumentException> { validateCustomerAccountBindings(disabled, bindings()) }
    }

    @Test
    fun `deep link bases reject unsafe URLs without echoing them`() {
        for (bad in listOf(
            "",
            "/relative",
            "http://unsafe.example/orders",
            "https://user:password@unsafe.example/orders",
            "https://safe.example/path?secret=value",
            "https://safe.example/path#secret"
        )) {
            val common =
                assertThrows<IllegalArgumentException> {
                    MobileDeepLinkConfiguration(bad, "https://safe.example/products")
                }
            val product =
                assertThrows<IllegalArgumentException> {
                    MobileDeepLinkConfiguration("https://safe.example/collections", bad)
                }
            val order = assertThrows<IllegalArgumentException> { bindings(bad) }
            if (bad.isNotEmpty()) {
                listOf(common, product, order).forEach {
                    assertFalse(it.message.orEmpty().contains(bad))
                }
            }
        }
    }

    private fun bindings(base: String = "https://safe.example/orders") = CustomerAccountFeatureBindings(
        base,
        TrackingUrlPolicy(setOf("tracking.example")),
        { _, _ -> DeletionPageLaunchResult.REJECTED }
    )

    private fun composition(account: Boolean): ApplicationComposition {
        val capabilities = ApplicationCapabilities(
            CapabilityState.DISABLED,
            CapabilityState.DISABLED,
            if (account) {
                CustomerAccountCapability.Enabled(
                    CustomerAccountConfiguration("", "", "", "", "", "", "", "", emptySet())
                )
            } else {
                CustomerAccountCapability.Disabled
            }
        )
        val destinations = listOf(PrimaryNavigationDestination.HOME, PrimaryNavigationDestination.CATEGORIES) +
            if (account) listOf(PrimaryNavigationDestination.ACCOUNT) else emptyList()
        return ApplicationComposition(capabilities, PrimaryNavigationSpec.create(destinations, capabilities))
    }
}
