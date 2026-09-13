package com.gurbakir.mobile.home

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class HomeContentRepositoryTest {
    @Test
    fun `test configuration keeps packaged fallback behind a disabled remote source`() {
        assertSame(HomeRemoteSource.Disabled, homeTestConfiguration.remoteSource)
        assertEquals(homeTestPackagedFallback, homeTestConfiguration.packagedFallback)
    }
}
