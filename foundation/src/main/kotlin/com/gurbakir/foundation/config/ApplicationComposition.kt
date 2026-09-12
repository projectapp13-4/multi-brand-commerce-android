package com.gurbakir.foundation.config

import java.util.Collections

enum class ApplicationCapability { SEARCH, WISHLIST, CUSTOMER_ACCOUNT }

enum class CapabilityState { ENABLED, DISABLED }

sealed interface CustomerAccountCapability {
    data object Disabled : CustomerAccountCapability

    data class Enabled(val configuration: CustomerAccountConfiguration) : CustomerAccountCapability
}

data class ApplicationCapabilities(
    val search: CapabilityState,
    val wishlist: CapabilityState,
    val customerAccount: CustomerAccountCapability
) {
    fun isEnabled(capability: ApplicationCapability): Boolean = when (capability) {
        ApplicationCapability.SEARCH -> search == CapabilityState.ENABLED
        ApplicationCapability.WISHLIST -> wishlist == CapabilityState.ENABLED
        ApplicationCapability.CUSTOMER_ACCOUNT -> customerAccount is CustomerAccountCapability.Enabled
    }
}

enum class PrimaryNavigationDestination { HOME, CATEGORIES, SEARCH, WISHLIST, ACCOUNT }

class PrimaryNavigationSpec private constructor(destinations: List<PrimaryNavigationDestination>) {
    val destinations: List<PrimaryNavigationDestination> = Collections.unmodifiableList(ArrayList(destinations))

    internal fun validate(capabilities: ApplicationCapabilities) {
        require(destinations.isNotEmpty()) { "Primary navigation must not be empty." }
        require(destinations.distinct().size == destinations.size) { "Primary destinations must be unique." }
        val expected = buildSet {
            add(PrimaryNavigationDestination.HOME)
            add(PrimaryNavigationDestination.CATEGORIES)
            if (capabilities.isEnabled(ApplicationCapability.SEARCH)) add(PrimaryNavigationDestination.SEARCH)
            if (capabilities.isEnabled(ApplicationCapability.WISHLIST)) add(PrimaryNavigationDestination.WISHLIST)
            if (capabilities.isEnabled(
                    ApplicationCapability.CUSTOMER_ACCOUNT
                )
            ) {
                add(PrimaryNavigationDestination.ACCOUNT)
            }
        }
        require(destinations.toSet() == expected) { "Primary destinations must match application capabilities." }
    }

    companion object {
        fun create(
            destinations: List<PrimaryNavigationDestination>,
            capabilities: ApplicationCapabilities
        ): PrimaryNavigationSpec = PrimaryNavigationSpec(destinations).also { it.validate(capabilities) }
    }
}

data class ApplicationComposition(
    val capabilities: ApplicationCapabilities,
    val primaryNavigation: PrimaryNavigationSpec
) {
    init {
        primaryNavigation.validate(capabilities)
    }
}
