package com.gurbakir.storefront

import com.gurbakir.storefront.graphql.fragment.CartLineFields
import com.gurbakir.storefront.graphql.fragment.CartSnapshotFields
import java.net.URI

internal fun CartSnapshotFields.toMappedCart(
    warningCodes: Set<String>,
    mediaPolicy: StorefrontMediaPolicy
): StorefrontResult<MappedCartSnapshot> {
    val checkoutUri = checkoutUrl.toCheckoutUri()
    val subtotal = cost.subtotalAmount.toStorefrontMoney()
    val total = cost.totalAmount.toStorefrontMoney()
    val mappedLines = lines.nodes.mapNotNull { it.cartLineFields.toDomainLine(mediaPolicy) }
    return when {
        checkoutUri == null -> graphQlFailure("INVALID_CHECKOUT_URL")

        subtotal == null || total == null -> graphQlFailure("INVALID_CART_MONEY")

        subtotal.currencyCode != total.currencyCode -> graphQlFailure("CART_CURRENCY_MISMATCH")

        mappedLines.size != lines.nodes.size -> graphQlFailure("UNSUPPORTED_CART_LINE")

        mappedLines.any { !it.hasCartCurrency(total.currencyCode) } -> graphQlFailure("CART_CURRENCY_MISMATCH")

        lines.pageInfo.hasNextPage && lines.pageInfo.endCursor == null ->
            graphQlFailure("MISSING_CART_LINE_CURSOR")

        else ->
            StorefrontResult.Success(
                MappedCartSnapshot(
                    cart =
                        CartReference(
                            id = SensitiveCartId.from(id),
                            checkoutUrl = SensitiveCheckoutUrl.from(checkoutUri),
                            totalQuantity = totalQuantity,
                            lines = mappedLines,
                            hasMoreLines = lines.pageInfo.hasNextPage,
                            warningCodes = warningCodes,
                            subtotal = subtotal,
                            total = total,
                            customerAssociated = buyerIdentity.customer != null
                        ),
                    endCursor = lines.pageInfo.endCursor?.let(::Cursor)
                )
            )
    }
}

internal fun CartLineSummary.hasCartCurrency(currencyCode: String): Boolean =
    unitPrice?.currencyCode == currencyCode && totalPrice?.currencyCode == currencyCode

internal fun CartLineFields.toDomainLine(mediaPolicy: StorefrontMediaPolicy): CartLineSummary? {
    val variant = merchandise.onProductVariant
    val unitPrice = cost.amountPerQuantity.toStorefrontMoney()
    val totalPrice = cost.totalAmount.toStorefrontMoney()
    val quantityRule = variant?.quantityRule
    val requiredFieldsPresent = variant != null && unitPrice != null && totalPrice != null
    val currencyMatches = unitPrice?.currencyCode == totalPrice?.currencyCode
    val lineSupported = requiredFieldsPresent && currencyMatches && quantityRule?.isValid() == true
    return if (!lineSupported) {
        null
    } else {
        CartLineSummary(
            id = SensitiveCartLineId.from(id),
            merchandiseId = requireNotNull(variant).id,
            quantity = quantity,
            productId = variant.product.id,
            productTitle = variant.product.title,
            variantTitle = variant.title,
            availableForSale = variant.availableForSale,
            currentlyNotInStock = variant.currentlyNotInStock,
            quantityRule =
                CartQuantityRule(
                    minimum = requireNotNull(quantityRule).minimum,
                    maximum = quantityRule.maximum,
                    increment = quantityRule.increment
                ),
            canRemove = onCartLine?.instructions?.canRemove == true,
            canUpdateQuantity = onCartLine?.instructions?.canUpdateQuantity == true,
            image = variant.image?.homeImageFields?.toStorefrontMedia(mediaPolicy),
            unitPrice = requireNotNull(unitPrice),
            totalPrice = requireNotNull(totalPrice)
        )
    }
}

private fun CartLineFields.QuantityRule.isValid(): Boolean {
    val safeMaximum = maximum
    return minimum > 0 && increment > 0 && (safeMaximum == null || safeMaximum >= minimum)
}

internal fun String.toSafeHttpsUri(): URI? = runCatching {
    URI(this).takeIf { uri ->
        uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null
    }
}.getOrNull()

internal fun String.toApprovedMediaUri(mediaPolicy: StorefrontMediaPolicy): URI? =
    runCatching { URI(this) }.getOrNull()?.takeIf(mediaPolicy::accepts)

internal fun List<ShopifyUserError>.toCartFailure(): StorefrontResult.Failure {
    val reason = when {
        any { it.code == "INVALID_MERCHANDISE_LINE" || it.code == "MERCHANDISE_NOT_APPLICABLE" } ->
            InvalidCartReason.MERCHANDISE_UNAVAILABLE

        else -> null
    }
    return if (reason == null) {
        StorefrontResult.Failure(StorefrontFailure.UserErrors(this))
    } else {
        StorefrontResult.Failure(StorefrontFailure.InvalidCart(reason))
    }
}

internal fun <T> graphQlFailure(code: String): StorefrontResult<T> =
    StorefrontResult.Failure(StorefrontFailure.GraphQl(setOf(code)))

private fun String.toCheckoutUri(): URI? = toSafeHttpsUri()?.takeIf { it.fragment == null }
