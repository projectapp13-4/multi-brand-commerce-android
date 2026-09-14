package com.gurbakir.storefront

import com.gurbakir.foundation.config.StorefrontConfiguration
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test

class OwnedStorefrontCartProofTest {
    @Test
    fun `owned non production store supports a bounded synthetic cart lifecycle`() {
        assumeTrue(System.getProperty("gurbakir.runOwnedCartProof") == "true")
        val configuration = loadOwnedOnboardingConfiguration().storefront
        assertEquals("gurbakir.com", configuration.domain)
        assertEquals("2026-07", configuration.apiVersion)
        assertTrue(configuration.validationIssues().isEmpty())

        val client = StorefrontApolloClientFactory.createClient(configuration)
        val gateway = ApolloStorefrontGateway(client, StorefrontMediaPolicy(configuration.domain))
        var activeCart: CartReference? = null
        try {
            val variant = runBlocking { firstAvailableVariant(gateway) }
            val created = runBlocking {
                gateway.createCart(listOf(CartLineInput(variant.id, quantity = 1)))
            }.requireCart("create")
            activeCart = created
            assertEquals(1, created.totalQuantity)
            assertFalse(created.lines.isEmpty())
            assertEquals(configuration.domain, created.checkoutUrl.use { it.host })
            assertFalse(created.toString().contains("?key="))

            val emptied = runBlocking {
                gateway.removeCartLines(created.id, created.lines.map(CartLineSummary::id))
            }.requireCart("initial-remove")
            activeCart = emptied
            assertEquals(0, emptied.totalQuantity)

            val added = runBlocking {
                gateway.addCartLines(emptied.id, listOf(CartLineInput(variant.id, quantity = 1)))
            }.requireCart("add")
            activeCart = added
            assertEquals(1, added.totalQuantity)

            val lineToUpdate = added.lines.first()
            val updated = runBlocking {
                gateway.updateCartLines(
                    added.id,
                    listOf(CartLineUpdate(lineToUpdate.id, lineToUpdate.quantity + 1))
                )
            }.requireCart("update")
            activeCart = updated
            assertTrue(updated.totalQuantity >= added.totalQuantity)

            val restored = runBlocking { gateway.loadCart(updated.id) }.requireCart("restore")
            activeCart = restored
            assertEquals(updated.totalQuantity, restored.totalQuantity)

            val removed = runBlocking {
                gateway.removeCartLines(restored.id, restored.lines.map(CartLineSummary::id))
            }.requireCart("remove")
            activeCart = removed
            assertEquals(0, removed.totalQuantity)
            assertTrue(removed.lines.isEmpty())
        } finally {
            activeCart?.takeIf { it.lines.isNotEmpty() }?.let { cart ->
                runBlocking {
                    gateway.removeCartLines(
                        cart.id,
                        cart.lines.map(CartLineSummary::id)
                    )
                }
            }
            client.close()
        }
    }

    private suspend fun firstAvailableVariant(gateway: StorefrontGateway): ProductVariantSummary {
        var cursor: Cursor? = null
        repeat(MAXIMUM_CATALOG_PAGES) {
            val page = gateway.loadCatalogPage(cursor)
            val catalog = assertInstanceOf(StorefrontResult.Success::class.java, page).value as CatalogPage
            catalog.products.asSequence()
                .flatMap { it.variants.asSequence() }
                .firstOrNull(ProductVariantSummary::availableForSale)
                ?.let { return it }
            if (!catalog.hasNextPage || catalog.endCursor == null) {
                error("The owned non-production catalog has no available synthetic cart variant.")
            }
            cursor = catalog.endCursor
        }
        error("No available variant was found within the bounded proof page limit.")
    }

    private fun StorefrontResult<CartReference>.requireCart(step: String): CartReference = when (this) {
        is StorefrontResult.Success -> value
        is StorefrontResult.Failure -> fail("Owned cart $step failed: ${error.safeSummary()}")
    }

    private fun StorefrontFailure.safeSummary(): String = when (this) {
        is StorefrontFailure.Configuration -> "configuration:${missingKeys.sorted().joinToString(",")}"

        is StorefrontFailure.GraphQl -> "graphql:${errorCodes.sorted().joinToString(",")}"

        is StorefrontFailure.InvalidCart -> "invalid-cart:$reason"

        is StorefrontFailure.SecurePersistence -> "secure-persistence"

        is StorefrontFailure.Transport -> "transport:retryable=$retryable"

        is StorefrontFailure.UserErrors ->
            "user-errors:${errors.map { it.code ?: "UNCLASSIFIED" }.sorted().joinToString(",")}"
    }

    private companion object {
        const val MAXIMUM_CATALOG_PAGES = 10
    }
}
