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
    val application = requireNotNull(System.getProperty("onboarding.application")) {
        "An explicit enrolled application is required."
    }
    val profile = requireNotNull(System.getProperty("onboarding.profile")) {
        "An explicit enrolled profile is required."
    }
    val root = File(requireNotNull(System.getProperty("onboarding.repoRoot"))).canonicalFile
    fun resolve(property: String): File {
        val relative = requireNotNull(System.getProperty(property)) { "$property is required." }
        require(relative.isNotBlank() && '\\' !in relative) { "$property must be a repository-relative path." }
        val resolved = File(root, relative).canonicalFile
        require(resolved.toPath().startsWith(root.toPath())) { "$property escapes the repository root." }
        return resolved
    }
    fun load(file: File): Properties = Properties().apply {
        require(file.isFile) { "Required onboarding configuration is missing." }
        file.reader(StandardCharsets.UTF_8).use(::load)
    }
    val projection = load(resolve("onboarding.projectionPath"))
    val local = load(resolve("onboarding.localConfigurationPath"))
    require(projection.getProperty("onboarding.schemaVersion") == "1")
    require(
        projection.getProperty("onboarding.sourceRegistrySha256") ==
            requireNotNull(System.getProperty("onboarding.registrySha256"))
    )
    require(projection.getProperty("onboarding.application") == application)
    require(projection.getProperty("onboarding.profile") == profile)
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
