package com.gurbakir.foundation.config

import com.gurbakir.foundation.config.PrimaryNavigationDestination.ACCOUNT
import com.gurbakir.foundation.config.PrimaryNavigationDestination.CATEGORIES
import com.gurbakir.foundation.config.PrimaryNavigationDestination.HOME
import com.gurbakir.foundation.config.PrimaryNavigationDestination.SEARCH
import com.gurbakir.foundation.config.PrimaryNavigationDestination.WISHLIST
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ApplicationCompositionTest {
    @Test
    fun `all eight capability combinations require their exact primary set`() {
        val cases = listOf(
            Triple(false, false, false) to listOf(HOME, CATEGORIES),
            Triple(true, false, false) to listOf(HOME, CATEGORIES, SEARCH),
            Triple(false, true, false) to listOf(HOME, CATEGORIES, WISHLIST),
            Triple(false, false, true) to listOf(HOME, CATEGORIES, ACCOUNT),
            Triple(true, true, false) to listOf(HOME, CATEGORIES, SEARCH, WISHLIST),
            Triple(true, false, true) to listOf(HOME, CATEGORIES, SEARCH, ACCOUNT),
            Triple(false, true, true) to listOf(HOME, CATEGORIES, WISHLIST, ACCOUNT),
            Triple(true, true, true) to listOf(HOME, CATEGORIES, SEARCH, WISHLIST, ACCOUNT)
        )
        cases.forEach { (flags, expected) ->
            val capabilities = capabilities(flags.first, flags.second, flags.third)
            assertEquals(flags.first, capabilities.isEnabled(ApplicationCapability.SEARCH))
            assertEquals(flags.second, capabilities.isEnabled(ApplicationCapability.WISHLIST))
            assertEquals(flags.third, capabilities.isEnabled(ApplicationCapability.CUSTOMER_ACCOUNT))
            assertEquals(expected, PrimaryNavigationSpec.create(expected, capabilities).destinations)
            expected.forEach { missing ->
                assertThrows<IllegalArgumentException> {
                    PrimaryNavigationSpec.create(expected - missing, capabilities)
                }
            }
            (PrimaryNavigationDestination.entries - expected.toSet()).forEach { extra ->
                assertThrows<IllegalArgumentException> {
                    PrimaryNavigationSpec.create(expected + extra, capabilities)
                }
            }
            expected.forEach { duplicate ->
                assertThrows<IllegalArgumentException> {
                    PrimaryNavigationSpec.create(expected + duplicate, capabilities)
                }
            }
        }
    }

    @Test
    fun `order is retained including non-first Home and an immutable defensive copy`() {
        val all = capabilities(true, true, true)
        val reordered = mutableListOf(ACCOUNT, CATEGORIES, WISHLIST, HOME, SEARCH)
        val spec = PrimaryNavigationSpec.create(reordered, all)
        reordered.clear()
        assertEquals(listOf(ACCOUNT, CATEGORIES, WISHLIST, HOME, SEARCH), spec.destinations)
        assertThrows<UnsupportedOperationException> { (spec.destinations as MutableList).clear() }
        assertEquals(
            listOf(SEARCH, HOME, CATEGORIES),
            PrimaryNavigationSpec.create(
                listOf(SEARCH, HOME, CATEGORIES),
                capabilities(true, false, false)
            ).destinations
        )
        assertEquals(
            listOf(CATEGORIES, HOME),
            PrimaryNavigationSpec.create(listOf(CATEGORIES, HOME), capabilities(false, false, false)).destinations
        )
        assertThrows<IllegalArgumentException> { PrimaryNavigationSpec.create(emptyList(), all) }
    }

    @Test
    fun `composition rejects a primary spec validated against different capabilities`() {
        val off = capabilities(false, false, false)
        val spec = PrimaryNavigationSpec.create(listOf(HOME, CATEGORIES), off)
        assertThrows<IllegalArgumentException> { ApplicationComposition(capabilities(true, false, false), spec) }
    }

    private fun capabilities(search: Boolean, wishlist: Boolean, account: Boolean) = ApplicationCapabilities(
        search = if (search) CapabilityState.ENABLED else CapabilityState.DISABLED,
        wishlist = if (wishlist) CapabilityState.ENABLED else CapabilityState.DISABLED,
        customerAccount = if (account) {
            CustomerAccountCapability.Enabled(
                invalidAccount()
            )
        } else {
            CustomerAccountCapability.Disabled
        }
    )

    private fun invalidAccount() = CustomerAccountConfiguration("", "", "", "", "", "", "", "", emptySet())
}
