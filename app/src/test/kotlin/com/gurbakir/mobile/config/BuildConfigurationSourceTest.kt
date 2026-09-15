package com.gurbakir.mobile.config

import com.gurbakir.foundation.config.EnvironmentId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class BuildConfigurationSourceTest {
    @Test
    fun `development environment maps from the exact registry enum`() {
        assertEquals(EnvironmentId.DEVELOPMENT, resolveEnvironmentId("DEVELOPMENT"))
    }

    @Test
    fun `staging environment maps from the exact registry enum`() {
        assertEquals(EnvironmentId.STAGING, resolveEnvironmentId("STAGING"))
    }

    @Test
    fun `unknown environment fails closed`() {
        assertThrows<IllegalStateException> { resolveEnvironmentId("production") }
        assertThrows<IllegalStateException> { resolveEnvironmentId("development") }
        assertThrows<IllegalStateException> { resolveEnvironmentId("") }
    }
}
