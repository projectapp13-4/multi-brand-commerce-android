package com.gurbakir.mobile.product

import com.gurbakir.storefront.StorefrontProductDetail
import com.gurbakir.storefront.StorefrontProductOption
import com.gurbakir.storefront.StorefrontProductVariant

data class ProductOptionValueState(
    val name: String,
    val selected: Boolean,
    val existsForCurrentSelection: Boolean,
    val hasAvailableMatch: Boolean
)

internal object VariantSelectionResolver {
    fun selectionForVariant(variant: StorefrontProductVariant): Map<String, String> =
        variant.selectedOptions.associate { it.name to it.value }

    fun selectedVariant(product: StorefrontProductDetail, selection: Map<String, String>): StorefrontProductVariant? {
        if (selection.size != product.options.size) return null
        return product.variants.singleOrNull { variant -> variant.matches(selection) }
    }

    fun valueStates(
        product: StorefrontProductDetail,
        option: StorefrontProductOption,
        selection: Map<String, String>
    ): List<ProductOptionValueState> {
        val otherSelections = selection - option.name
        return option.values.map { value ->
            val candidate = otherSelections + (option.name to value.name)
            val matches = product.variants.filter { variant -> variant.matchesPartial(candidate) }
            ProductOptionValueState(
                name = value.name,
                selected = selection[option.name] == value.name,
                existsForCurrentSelection = matches.isNotEmpty(),
                hasAvailableMatch = matches.any { it.availableForSale }
            )
        }
    }

    fun canSelect(
        product: StorefrontProductDetail,
        optionName: String,
        value: String,
        currentSelection: Map<String, String>
    ): Boolean =
        product.options.any { option -> option.name == optionName && option.values.any { it.name == value } } &&
            product.variants.any { variant ->
                variant.matchesPartial((currentSelection - optionName) + (optionName to value))
            }

    fun restoreSelection(product: StorefrontProductDetail, restored: Map<String, String>): Map<String, String> =
        restored
            .takeIf { selection ->
                selection.isNotEmpty() &&
                    selection.keys.all { key -> product.options.any { it.name == key } } &&
                    product.variants.any { it.matchesPartial(selection) }
            }
            .orEmpty()

    private fun StorefrontProductVariant.matches(selection: Map<String, String>): Boolean =
        selectedOptions.size == selection.size && matchesPartial(selection)

    private fun StorefrontProductVariant.matchesPartial(selection: Map<String, String>): Boolean {
        val values = selectedOptions.associate { it.name to it.value }
        return selection.all { (name, value) -> values[name] == value }
    }
}
