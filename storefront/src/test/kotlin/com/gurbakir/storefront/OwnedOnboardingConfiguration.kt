package com.gurbakir.storefront

import com.gurbakir.foundation.config.ControlledPublicToken
import com.gurbakir.foundation.config.StorefrontConfiguration
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.Properties

internal data class OwnedOnboardingConfiguration(
    val storefront: StorefrontConfiguration,
    val menuHandle: String,
    val homeType: String,
    val homeHandle: String
)

internal fun loadOwnedOnboardingConfiguration(): OwnedOnboardingConfiguration {
    val application = System.getProperty("onboarding.application")
    val profile = requireNotNull(System.getProperty("onboarding.profile")) {
        "An explicit enrolled profile is required."
    }
    require(application == "gurbakir") { "An explicit enrolled application is required." }
    require(profile in setOf("development", "staging")) { "An explicit enrolled profile is required." }
    val root = File(requireNotNull(System.getProperty("gurbakir.repoRoot")))
    fun load(file: File): Properties = Properties().apply {
        require(file.isFile) { "Required onboarding configuration is missing." }
        file.reader(StandardCharsets.UTF_8).use(::load)
    }
    val projection = load(File(root, "config/onboarding/generated/gurbakir/$profile.properties"))
    val local = load(File(root, "config/local/gurbakir/$profile.properties"))
    require(projection.getProperty("onboarding.schemaVersion") == "1")
    require(projection.getProperty("onboarding.application") == "gurbakir")
    require(projection.getProperty("onboarding.profile") == profile)
    require(projection.getProperty("shopify.storefrontDomain") == "gurbakir.com") {
        "Owned Storefront proofs cannot target an unapproved host."
    }
    require(projection.getProperty("shopify.storefrontApiVersion") == "2026-07") {
        "Owned Storefront proofs require the pinned API version."
    }
    return OwnedOnboardingConfiguration(
        storefront = StorefrontConfiguration(
            domain = projection.getProperty("shopify.storefrontDomain"),
            apiVersion = projection.getProperty("shopify.storefrontApiVersion"),
            publicToken = ControlledPublicToken.from(local.getProperty("shopify.storefrontPublicToken", ""))
        ),
        menuHandle = projection.getProperty("shopify.catalogMenuHandle"),
        homeType = projection.getProperty("shopify.homeRootType"),
        homeHandle = projection.getProperty("shopify.homeRootHandle")
    )
}
