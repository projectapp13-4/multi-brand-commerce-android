package com.gurbakir.storefront

import com.gurbakir.foundation.config.ControlledPublicToken
import com.gurbakir.foundation.config.StorefrontConfiguration
import java.io.File
import java.util.Properties
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test

class OwnedStorefrontReadProofTest {
    @Test
    fun `generated Apollo client reads only the owner verified shop and catalog`() {
        assumeTrue(System.getProperty("gurbakir.runOwnedStorefrontProof") == "true")
        val configuration = loadConfiguration()

        assertEquals("gurbakir.com", configuration.domain)
        assertEquals("2026-07", configuration.apiVersion)
        assertEquals("<redacted>", configuration.publicToken.toString())
        assertTrue(configuration.validationIssues().isEmpty())

        val client = StorefrontApolloClientFactory.createClient(configuration)
        val mediaPolicy = StorefrontMediaPolicy(configuration.domain)
        try {
            val gateway = ApolloStorefrontGateway(client, mediaPolicy)
            val catalogGateway = ApolloStorefrontCatalogGateway(client, mediaPolicy)
            val searchGateway = ApolloStorefrontSearchGateway(client, mediaPolicy)
            val productGateway = ApolloStorefrontProductGateway(client, mediaPolicy)
            val shopResult = kotlinx.coroutines.runBlocking { gateway.loadShopSummary() }
            val shopSuccess = assertInstanceOf(StorefrontResult.Success::class.java, shopResult)
            val shop = assertInstanceOf(ShopSummary::class.java, shopSuccess.value)
            assertEquals("Gür Bakır", shop.name)
            assertEquals("gurbakir.com", shop.primaryDomain)

            val catalogResult = kotlinx.coroutines.runBlocking { gateway.loadCatalogPage(after = null) }
            val catalogSuccess = assertInstanceOf(StorefrontResult.Success::class.java, catalogResult)
            val catalog = assertInstanceOf(CatalogPage::class.java, catalogSuccess.value)
            assertFalse(catalog.products.isEmpty())

            verifyCollectionCatalog(catalogGateway)
            verifyProductSearch(searchGateway, catalog.products.first().title)
            verifyProductDetail(productGateway, catalog.products.first().id)
        } finally {
            client.close()
        }
    }

    private fun loadConfiguration(): StorefrontConfiguration {
        val repoRoot = requireNotNull(System.getProperty("gurbakir.repoRoot"))
        val properties =
            Properties().apply {
                File(repoRoot, "config/local.properties").inputStream().use(::load)
            }
        return StorefrontConfiguration(
            domain = properties.getProperty("shopify.storefrontDomain", "").trim(),
            apiVersion = properties.getProperty("shopify.storefrontApiVersion", "").trim(),
            publicToken =
                ControlledPublicToken.from(
                    properties.getProperty("shopify.storefrontPublicToken", "")
                )
        )
    }

    private fun verifyCollectionCatalog(gateway: StorefrontCatalogGateway) {
        val collectionResult =
            kotlinx.coroutines.runBlocking {
                gateway.loadCollectionCatalogPage(
                    CollectionCatalogPageRequest(
                        handle = "ozel-urunlerimiz",
                        sort = CollectionCatalogSort.COLLECTION_DEFAULT
                    )
                )
            }
        val collectionSuccess = assertInstanceOf(StorefrontResult.Success::class.java, collectionResult)
        val collection = assertInstanceOf(CollectionCatalogPage::class.java, collectionSuccess.value)
        assertEquals("ozel-urunlerimiz", collection.handle)
        assertFalse(collection.products.isEmpty())
        assertTrue(collection.productTypeFilter?.values?.any { it.value == "Şişe" } == true)

        val filteredResult =
            kotlinx.coroutines.runBlocking {
                gateway.loadCollectionCatalogPage(
                    CollectionCatalogPageRequest(
                        handle = "ozel-urunlerimiz",
                        sort = CollectionCatalogSort.PRICE_HIGH_TO_LOW,
                        productTypes = setOf("Şişe")
                    )
                )
            }
        val filtered =
            assertInstanceOf(
                CollectionCatalogPage::class.java,
                assertInstanceOf(StorefrontResult.Success::class.java, filteredResult).value
            )
        assertFalse(filtered.products.isEmpty())
    }

    private fun verifyProductSearch(gateway: StorefrontSearchGateway, productTitle: String) {
        val result =
            kotlinx.coroutines.runBlocking {
                gateway.searchProducts(ProductSearchPageRequest(productTitle))
            }
        val page =
            assertInstanceOf(
                ProductSearchPage::class.java,
                assertInstanceOf(StorefrontResult.Success::class.java, result).value
            )
        assertTrue(page.totalCount > 0)
        assertFalse(page.products.isEmpty())
        assertTrue(page.products.any { it.title == productTitle })
    }

    private fun verifyProductDetail(gateway: StorefrontProductGateway, productId: String) {
        val result =
            kotlinx.coroutines.runBlocking {
                gateway.loadProductDetail(ProductDetailRequest(productId))
            }
        assertTrue(result is StorefrontResult.Success) { "Product detail proof failed: $result" }
        val product =
            assertInstanceOf(
                StorefrontProductDetail::class.java,
                (result as StorefrontResult.Success).value
            )
        assertEquals(productId, product.id)
        assertFalse(product.variants.isEmpty())
        assertTrue(product.variants.all { it.selectedOptions.size == product.options.size })
        assertEquals(product.availableForSale, product.variants.any { it.availableForSale })
    }
}
