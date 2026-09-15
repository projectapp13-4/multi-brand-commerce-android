package com.gurbakir.storefront

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test

class OwnedHomeContentReadbackTest {
    @Test
    fun `configured client reads the selected owned Home root`() {
        assumeTrue(System.getProperty("onboarding.runOwnedHomeReadback") == "true")
        val owned = loadOwnedOnboardingConfiguration()
        val client = StorefrontApolloClientFactory.createClient(owned.storefront)
        try {
            val gateway = ApolloStorefrontGateway(client, StorefrontMediaPolicy(owned.storefront.domain))
            val result = runBlocking {
                gateway.loadHomeDocument(HomeDocumentSelector(owned.homeType, owned.homeHandle))
            }
            val success = assertInstanceOf(StorefrontResult.Success::class.java, result)
            assertNotNull(success.value, "Selected Home root must be Storefront-readable")
        } finally {
            client.close()
        }
    }
}
