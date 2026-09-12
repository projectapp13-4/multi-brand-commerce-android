package com.gurbakir.storefront

import com.gurbakir.storefront.graphql.ProductDetailPageQuery

private const val MAX_DESCRIPTION_LENGTH = 12_000
private const val MAX_PRODUCT_TEXT_LENGTH = 255
private val PRODUCT_HANDLE = Regex("^[a-z0-9][a-z0-9-]{0,254}$")

internal data class ProductCore(
    val id: String,
    val handle: String,
    val title: String,
    val description: String,
    val availableForSale: Boolean,
    val options: List<StorefrontProductOption>
)

internal data class MappedProductPage(
    val core: ProductCore,
    val declaredVariantCount: Int?,
    val variants: List<StorefrontProductVariant>,
    val media: List<StorefrontProductMedia>,
    val mediaNodeCount: Int,
    val variantEndCursor: Cursor?,
    val hasNextVariantPage: Boolean,
    val mediaEndCursor: Cursor?,
    val hasNextMediaPage: Boolean
)

internal fun ProductDetailPageQuery.Product.toMappedPage(
    requestedId: String,
    mediaPolicy: StorefrontMediaPolicy
): StorefrontResult<MappedProductPage> = try {
    StorefrontResult.Success(toMappedPageOrThrow(requestedId, mediaPolicy))
} catch (failure: ProductMappingFailure) {
    graphQlFailure(failure.code)
}

private fun ProductDetailPageQuery.Product.toMappedPageOrThrow(
    requestedId: String,
    mediaPolicy: StorefrontMediaPolicy
): MappedProductPage {
    mappingCheck(
        id == requestedId && StorefrontProductIds.isProductGid(id) && handle.matches(PRODUCT_HANDLE),
        "INVALID_PRODUCT_IDENTITY"
    )
    val mappedMedia =
        media.nodes.mapNotNull { node ->
            node.onMediaImage?.let { image ->
                image.image?.homeImageFields?.toStorefrontMedia(mediaPolicy)?.let { safeImage ->
                    StorefrontProductMedia(image.id, safeImage)
                }
            }
        }
    return MappedProductPage(
        core =
            ProductCore(
                id = id,
                handle = handle,
                title = title.mappedRequiredText("INVALID_PRODUCT_TITLE"),
                description = description.safeDescription(),
                availableForSale = availableForSale,
                options = options.map(ProductDetailPageQuery.Option::toDomainOption)
            ),
        declaredVariantCount = variantsCount?.count,
        variants = variants.nodes.map { variant -> variant.toDomainVariant(mediaPolicy) },
        media = mappedMedia,
        mediaNodeCount = media.nodes.size,
        variantEndCursor = variants.pageInfo.endCursor?.let(::Cursor),
        hasNextVariantPage = variants.pageInfo.hasNextPage,
        mediaEndCursor = media.pageInfo.endCursor?.let(::Cursor),
        hasNextMediaPage = media.pageInfo.hasNextPage
    )
}

private fun ProductDetailPageQuery.Option.toDomainOption(): StorefrontProductOption {
    val mappedValues =
        optionValues.map { value ->
            StorefrontProductOptionValue(value.id, value.name.mappedRequiredText("INVALID_OPTION_VALUE"))
        }
    mappingCheck(
        id.isNotBlank() && mappedValues.isNotEmpty() && mappedValues.distinctBy { it.name }.size == mappedValues.size,
        "INVALID_PRODUCT_OPTION"
    )
    return StorefrontProductOption(id, name.mappedRequiredText("INVALID_OPTION_NAME"), mappedValues)
}

private fun ProductDetailPageQuery.Node.toDomainVariant(mediaPolicy: StorefrontMediaPolicy): StorefrontProductVariant {
    val amount = price.amount.toBigDecimalOrNull() ?: mappingFailure("INVALID_VARIANT_PRICE")
    val currency = price.currencyCode.rawValue
    val compareAmount = compareAtPrice?.amount?.toBigDecimalOrNull()
    val compareCurrency = compareAtPrice?.currencyCode?.rawValue
    mappingCheck(StorefrontProductIds.isVariantGid(id), "INVALID_VARIANT_IDENTITY")
    mappingCheck(
        compareAtPrice == null || (compareAmount != null && compareCurrency == currency),
        "INVALID_COMPARE_AT_PRICE"
    )
    return StorefrontProductVariant(
        id = id,
        title = title.mappedRequiredText("INVALID_VARIANT_TITLE"),
        availableForSale = availableForSale,
        currentlyNotInStock = currentlyNotInStock,
        price = StorefrontMoney(amount, currency),
        compareAtPrice = compareAmount?.let { StorefrontMoney(it, requireNotNull(compareCurrency)) },
        image = image?.homeImageFields?.toStorefrontMedia(mediaPolicy),
        selectedOptions =
            selectedOptions.map { option ->
                StorefrontSelectedOption(
                    option.name.mappedRequiredText("INVALID_SELECTED_OPTION"),
                    option.value.mappedRequiredText("INVALID_SELECTED_OPTION")
                )
            }
    )
}

private fun String.mappedRequiredText(code: String): String =
    safeRequiredText(MAX_PRODUCT_TEXT_LENGTH) ?: mappingFailure(code)

private fun String.safeRequiredText(maxLength: Int): String? =
    trim().takeIf { value -> value.isNotEmpty() && value.length <= maxLength && value.none(Char::isISOControl) }

private fun String.safeDescription(): String =
    filter { character -> !character.isISOControl() || character == '\n' || character == '\t' }
        .trim()
        .take(MAX_DESCRIPTION_LENGTH)
