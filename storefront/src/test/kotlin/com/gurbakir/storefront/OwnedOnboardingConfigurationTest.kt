package com.gurbakir.storefront

import java.io.File
import java.nio.charset.StandardCharsets
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class OwnedOnboardingConfigurationTest {
    @TempDir
    lateinit var root: File

    @Test
    fun `resolves an explicitly selected enrolled application without a Gurbakir path fallback`() {
        val projection = File(root, "config/onboarding/generated/future/development.properties")
        val local = File(root, "config/local/future/development.properties")
        requireNotNull(projection.parentFile).mkdirs()
        requireNotNull(local.parentFile).mkdirs()
        projection.writeText(
            """
            onboarding.schemaVersion=1
            onboarding.sourceRegistrySha256=${"a".repeat(64)}
            onboarding.application=future
            onboarding.profile=development
            shopify.storefrontDomain=future.invalid
            shopify.storefrontApiVersion=2026-07
            shopify.catalogMenuHandle=future-menu
            shopify.homeRootType=mobile_home
            shopify.homeRootHandle=future-home
            """.trimIndent() + "\n",
            StandardCharsets.UTF_8
        )
        local.writeText("shopify.storefrontPublicToken=fixture-public-token\n", StandardCharsets.UTF_8)

        val properties =
            mapOf(
                "onboarding.application" to "future",
                "onboarding.profile" to "development",
                "onboarding.repoRoot" to root.absolutePath,
                "onboarding.projectionPath" to projection.relativeTo(root).invariantSeparatorsPath,
                "onboarding.localConfigurationPath" to local.relativeTo(root).invariantSeparatorsPath,
                "onboarding.registrySha256" to "a".repeat(64)
            )
        val previous = properties.mapValues { (key) -> System.getProperty(key) }
        try {
            properties.forEach(System::setProperty)
            val configuration = loadOwnedOnboardingConfiguration()
            assertEquals("future.invalid", configuration.storefront.domain)
            assertEquals("future-menu", configuration.menuHandle)
            assertEquals("future-home", configuration.homeHandle)
        } finally {
            previous.forEach { (key, value) ->
                if (value == null) System.clearProperty(key) else System.setProperty(key, value)
            }
        }
    }
}
