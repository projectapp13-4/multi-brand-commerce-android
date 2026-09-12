package com.gurbakir.storefront

internal class ProductAccumulator {
    var variantAfter: Cursor? = null
        private set
    var mediaAfter: Cursor? = null
        private set
    private var core: ProductCore? = null
    private var declaredVariantCount: Int? = null
    private val variants = linkedMapOf<String, StorefrontProductVariant>()
    private val media = linkedMapOf<String, StorefrontProductMedia>()

    fun accept(page: MappedProductPage): StorefrontProductDetail? {
        validateStableCore(page)
        variants.appendUnique(page.variants, StorefrontProductVariant::id)
        media.appendUnique(page.media, StorefrontProductMedia::id)
        variantAfter =
            advanceCursor(
                current = variantAfter,
                endCursor = page.variantEndCursor,
                hasNextPage = page.hasNextVariantPage,
                nodeCount = page.variants.size,
                failureCode = "INVALID_PRODUCT_VARIANT_CURSOR"
            )
        mediaAfter =
            advanceCursor(
                current = mediaAfter,
                endCursor = page.mediaEndCursor,
                hasNextPage = page.hasNextMediaPage,
                nodeCount = page.mediaNodeCount,
                failureCode = "INVALID_PRODUCT_MEDIA_CURSOR"
            )
        return if (!page.hasNextVariantPage && !page.hasNextMediaPage) {
            buildProduct(core, declaredVariantCount, variants.values.toList(), media.values.toList())
        } else {
            null
        }
    }

    private fun validateStableCore(page: MappedProductPage) {
        val initialCore = core
        if (initialCore == null) {
            core = page.core
            declaredVariantCount = page.declaredVariantCount
        } else {
            mappingCheck(
                initialCore == page.core && declaredVariantCount == page.declaredVariantCount,
                "UNSTABLE_PRODUCT_DETAIL_PAGE"
            )
        }
    }
}

private fun buildProduct(
    core: ProductCore?,
    declaredVariantCount: Int?,
    variants: List<StorefrontProductVariant>,
    media: List<StorefrontProductMedia>
): StorefrontProductDetail {
    val requiredCore = core ?: mappingFailure("MISSING_PRODUCT_DETAIL")
    mappingCheck(
        variants.isNotEmpty() && declaredVariantCount?.let { it == variants.size } != false,
        "INCOMPLETE_PRODUCT_VARIANTS"
    )
    mappingCheck(
        variants.map { it.price.currencyCode }.distinct().size == 1,
        "INCONSISTENT_PRODUCT_CURRENCY"
    )
    val expectedOptions = requiredCore.options.associate { option ->
        option.name to option.values.map { it.name }.toSet()
    }
    val validVariants = variants.all { variant ->
        variant.selectedOptions.size == expectedOptions.size &&
            variant.selectedOptions.map { it.name }.distinct().size == expectedOptions.size &&
            variant.selectedOptions.all { selected -> selected.value in expectedOptions[selected.name].orEmpty() }
    }
    mappingCheck(
        validVariants && requiredCore.availableForSale == variants.any { it.availableForSale },
        "INCONSISTENT_PRODUCT_VARIANTS"
    )
    return StorefrontProductDetail(
        id = requiredCore.id,
        handle = requiredCore.handle,
        title = requiredCore.title,
        description = requiredCore.description,
        availableForSale = requiredCore.availableForSale,
        options = requiredCore.options,
        variants = variants,
        media = media
    )
}

private fun advanceCursor(
    current: Cursor?,
    endCursor: Cursor?,
    hasNextPage: Boolean,
    nodeCount: Int,
    failureCode: String
): Cursor? {
    val validEnd = endCursor?.takeIf { it.value.isNotBlank() }
    mappingCheck(!((hasNextPage || nodeCount > 0) && validEnd == null), failureCode)
    mappingCheck(!(hasNextPage && validEnd == current), failureCode)
    return validEnd ?: current
}

private fun <T> MutableMap<String, T>.appendUnique(values: List<T>, idOf: (T) -> String) {
    values.forEach { value ->
        mappingCheck(put(idOf(value), value) == null, "DUPLICATE_PRODUCT_DETAIL_NODE")
    }
}

internal class ProductMappingFailure(val code: String) : IllegalArgumentException(code)

internal fun mappingFailure(code: String): Nothing = throw ProductMappingFailure(code)

internal fun mappingCheck(condition: Boolean, code: String) {
    if (!condition) mappingFailure(code)
}
