package com.gurbakir.storefront

import com.gurbakir.foundation.config.ControlledPublicToken
import com.gurbakir.foundation.config.StorefrontConfiguration
import java.io.File
import java.util.Properties
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test

class OwnedCatalogDiscoveryProofTest {
    @Test
    fun `configured client reads the selected owned Catalog Menu and distinguishes a missing Menu`() {
        assumeTrue(System.getProperty("gurbakir.runOwnedStorefrontProof") == "true")
        val owned = loadOwnedConfiguration()

        assertEquals("gurbakir.com", owned.storefront.domain)
        assertEquals("2026-07", owned.storefront.apiVersion)
        assertEquals("<redacted>", owned.storefront.publicToken.toString())
        assertTrue(owned.storefront.validationIssues().isEmpty(), "Owned Storefront configuration must be complete")
        assertTrue(CatalogDiscoveryRequest(owned.menuHandle).isValid(), "Catalog Menu selector must be valid")

        val client = StorefrontApolloClientFactory.createClient(owned.storefront)
        try {
            verifyOwnedShop(client, owned.storefront.domain)
            val gateway = ApolloStorefrontCatalogGateway(client, StorefrontMediaPolicy(owned.storefront.domain))
            val selectedResult = runBlocking {
                gateway.loadCatalogDiscovery(CatalogDiscoveryRequest(owned.menuHandle))
            }
            assertTrue(selectedResult is StorefrontResult.Success, "Selected Catalog Menu read must succeed")
            val selectedMenu = (selectedResult as StorefrontResult.Success).value
            assertTrue(selectedMenu != null, "Selected Catalog Menu must exist")
            checkNotNull(selectedMenu)
            assertEquals(owned.menuHandle, selectedMenu.handle)
            assertTrue(
                selectedMenu.items.haveBoundedShape(),
                "Selected Catalog Menu must remain within discovery bounds"
            )
            assertTrue(
                selectedMenu.items.anyUsableUnfilteredCollection(),
                "Selected Catalog Menu must expose at least one usable unfiltered Collection"
            )

            assertTrue(
                missingMenuCandidates.any { candidate ->
                    runBlocking {
                        gateway.loadCatalogDiscovery(CatalogDiscoveryRequest(candidate))
                    } == StorefrontResult.Success(null)
                },
                "A bounded nonexistent Menu handle must return a successful null result"
            )
        } finally {
            client.close()
        }
    }

    private fun loadOwnedConfiguration(): OwnedCatalogConfiguration {
        val repoRoot = requireNotNull(System.getProperty("gurbakir.repoRoot"))
        val localFile = File(repoRoot, "config/local.properties")
        assertTrue(localFile.isFile, "Ignored owned Storefront configuration must exist")
        val properties = Properties().apply { localFile.inputStream().use(::load) }
        return OwnedCatalogConfiguration(
            storefront =
                StorefrontConfiguration(
                    domain = properties.getProperty("shopify.storefrontDomain", "").trim(),
                    apiVersion = properties.getProperty("shopify.storefrontApiVersion", "").trim(),
                    publicToken = ControlledPublicToken.from(
                        properties.getProperty("shopify.storefrontPublicToken", "")
                    )
                ),
            menuHandle = properties.getProperty("shopify.catalogMenuHandle", "").trim()
        )
    }

    private fun verifyOwnedShop(client: com.apollographql.apollo.ApolloClient, expectedDomain: String) {
        val result =
            runBlocking { ApolloStorefrontGateway(client, StorefrontMediaPolicy(expectedDomain)).loadShopSummary() }
        assertTrue(result is StorefrontResult.Success, "Owned shop identity read must succeed")
        val shop = (result as StorefrontResult.Success).value
        assertEquals(expectedDomain, shop.primaryDomain)
    }

    private fun List<CatalogDiscoveryNode>.haveBoundedShape(): Boolean {
        var count = 0

        fun visit(nodes: List<CatalogDiscoveryNode>, depth: Int): Boolean {
            if (nodes.isNotEmpty() && depth > CatalogDiscoveryBounds.MAX_DEPTH) return false
            return nodes.all { node ->
                count += 1
                count <= CatalogDiscoveryBounds.MAX_NODES && visit(node.children, depth + 1)
            }
        }

        return visit(this, depth = 1)
    }

    private fun List<CatalogDiscoveryNode>.anyUsableUnfilteredCollection(): Boolean = any { node ->
        (!node.hasCollectionFilters && node.target is CatalogDiscoveryTarget.Collection) ||
            node.children.anyUsableUnfilteredCollection()
    }

    private data class OwnedCatalogConfiguration(val storefront: StorefrontConfiguration, val menuHandle: String)

    private companion object {
        val missingMenuCandidates =
            listOf(
                "codex-gate6-missing-menu",
                "codex-gate6-no-such-menu",
                "codex-gate6-absent-navigation"
            )
    }
}
