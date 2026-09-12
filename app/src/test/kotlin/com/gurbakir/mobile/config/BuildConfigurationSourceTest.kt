package com.gurbakir.mobile.config

import com.gurbakir.foundation.config.EnvironmentId
import com.gurbakir.foundation.config.ProtectedPersistenceConfiguration
import com.gurbakir.foundation.config.ProtectedStoreIdentity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class BuildConfigurationSourceTest {
    @Test
    fun `development environment maps to exact legacy protected identities`() {
        assertEquals(EnvironmentId.DEVELOPMENT, resolveEnvironmentId("development"))
        assertEquals(
            ProtectedPersistenceConfiguration(
                cart =
                    ProtectedStoreIdentity(
                        "gurbakir_secure_cart_development",
                        "gurbakir.cart.development.v1"
                    ),
                customerSession =
                    ProtectedStoreIdentity(
                        "gurbakir_secure_customer_session_development",
                        "gurbakir.customer.session.development.v1"
                    )
            ),
            protectedPersistenceFor(EnvironmentId.DEVELOPMENT)
        )
    }

    @Test
    fun `staging environment maps to exact legacy protected identities`() {
        assertEquals(EnvironmentId.STAGING, resolveEnvironmentId("staging"))
        assertEquals(
            ProtectedPersistenceConfiguration(
                cart =
                    ProtectedStoreIdentity(
                        "gurbakir_secure_cart_staging",
                        "gurbakir.cart.staging.v1"
                    ),
                customerSession =
                    ProtectedStoreIdentity(
                        "gurbakir_secure_customer_session_staging",
                        "gurbakir.customer.session.staging.v1"
                    )
            ),
            protectedPersistenceFor(EnvironmentId.STAGING)
        )
    }

    @Test
    fun `unknown environment fails closed`() {
        assertThrows<IllegalStateException> { resolveEnvironmentId("production") }
        assertThrows<IllegalStateException> { resolveEnvironmentId("") }
    }
}
