package com.gurbakir.mobile.product

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class VariantSelectionResolverTest {
    @Test
    fun `every existing combination resolves exactly and missing combination never resolves`() {
        val product = productFixture()

        product.variants.forEach { variant ->
            val selection = VariantSelectionResolver.selectionForVariant(variant)
            assertEquals(variant.id, VariantSelectionResolver.selectedVariant(product, selection)?.id)
        }
        assertNull(
            VariantSelectionResolver.selectedVariant(
                product,
                mapOf("Size" to "Large", "Color" to "Blue")
            )
        )
    }

    @Test
    fun `partial choice disables only impossible values and labels sellable matches separately`() {
        val product = productFixture()
        val color = product.options.single { it.name == "Color" }
        val states =
            VariantSelectionResolver.valueStates(
                product,
                color,
                mapOf("Size" to "Large")
            ).associateBy { it.name }

        assertTrue(requireNotNull(states["Red"]).existsForCurrentSelection)
        assertTrue(requireNotNull(states["Red"]).hasAvailableMatch)
        assertFalse(requireNotNull(states["Blue"]).existsForCurrentSelection)
        assertFalse(requireNotNull(states["Blue"]).hasAvailableMatch)
        assertFalse(
            VariantSelectionResolver.canSelect(
                product,
                "Color",
                "Blue",
                mapOf("Size" to "Large")
            )
        )
    }

    @Test
    fun `complete unavailable variant is inspectable but never creates purchase intent`() {
        val state =
            ProductDetailUiState(
                product = productFixture(),
                selectedOptions = mapOf("Size" to "Small", "Color" to "Blue")
            )

        assertEquals("gid://shopify/ProductVariant/12", state.selectedVariant?.id)
        assertNull(state.purchaseIntent)
    }
}
