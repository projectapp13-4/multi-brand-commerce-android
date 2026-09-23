package com.gurbakir.storefront

import java.io.File
import java.nio.charset.StandardCharsets
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
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
        projection.writeText(validProjection(), StandardCharsets.UTF_8)
        local.writeText("shopify.storefrontPublicToken=fixture-public-token\n", StandardCharsets.UTF_8)

        withOnboardingProperties(projection, local) {
            val configuration = loadOwnedOnboardingConfiguration()
            assertEquals("future.invalid", configuration.storefront.domain)
            assertEquals("USD", configuration.marketCurrencyCode)
            assertEquals("future-menu", configuration.menuHandle)
            assertEquals("future-home", configuration.homeHandle)
            assertEquals(HomeContentContractId.GATE7_V1, configuration.homeContractId)
        }
    }

    @Test
    fun `rejects files outside the strict onboarding properties grammar`() {
        val projection = File(root, "config/onboarding/generated/future/development.properties")
        val local = File(root, "config/local/future/development.properties")
        requireNotNull(projection.parentFile).mkdirs()
        requireNotNull(local.parentFile).mkdirs()
        projection.writeText(validProjection(), StandardCharsets.UTF_8)

        val invalidLocalFiles =
            listOf(
                "# comment\nshopify.storefrontPublicToken=fixture-public-token\n",
                "shopify.storefrontPublicToken=first\nshopify.storefrontPublicToken=second\n",
                "shopify.storefrontPublicToken=fixture\\\n-public-token\n",
                "shopify.storefrontPublicToken=fixture-public-token\r\n"
            )
        for (contents in invalidLocalFiles) {
            local.writeText(contents, StandardCharsets.UTF_8)
            withOnboardingProperties(projection, local) {
                assertThrows<IllegalArgumentException> { loadOwnedOnboardingConfiguration() }
            }
        }
    }

    @Test
    fun `rejects an oversized local configuration`() {
        val projection = File(root, "config/onboarding/generated/future/development.properties")
        val local = File(root, "config/local/future/development.properties")
        requireNotNull(projection.parentFile).mkdirs()
        requireNotNull(local.parentFile).mkdirs()
        projection.writeText(validProjection(), StandardCharsets.UTF_8)
        local.writeBytes(ByteArray(262_145) { 'a'.code.toByte() })

        withOnboardingProperties(projection, local) {
            assertThrows<IllegalArgumentException> { loadOwnedOnboardingConfiguration() }
        }
    }

    private fun validProjection(): String =
        """
        onboarding.schemaVersion=1
        onboarding.sourceRegistrySha256=${"a".repeat(64)}
        onboarding.application=future
        onboarding.profile=development
        app.brandDisplayName=Future 🚀
        app.marketCurrencyCode=USD
        shopify.storefrontDomain=future.invalid
        shopify.storefrontApiVersion=2026-07
        shopify.catalogMenuHandle=future-menu
        shopify.homeRootType=mobile_home
        shopify.homeRootHandle=future-home
        shopify.homeContentSchemaVersion=1
        shopify.homeDefinitionContract=gate7-v1
        """.trimIndent() + "\n"

    private fun withOnboardingProperties(projection: File, local: File, block: () -> Unit) {
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
            block()
        } finally {
            previous.forEach { (key, value) ->
                if (value == null) System.clearProperty(key) else System.setProperty(key, value)
            }
        }
    }
}
