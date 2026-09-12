package com.gurbakir.storefront

import com.gurbakir.storefront.graphql.CartBuyerIdentityUpdateMutation
import com.gurbakir.storefront.graphql.CartCreateMutation
import com.gurbakir.storefront.graphql.CartLinesAddMutation
import com.gurbakir.storefront.graphql.CartLinesRemoveMutation
import com.gurbakir.storefront.graphql.CartLinesUpdateMutation

private const val MAX_CART_LINES = 250

internal fun validateCreateLines(lines: List<CartLineInput>): StorefrontFailure.UserErrors? {
    val errors = buildList {
        if (lines.isEmpty() || lines.size > MAX_CART_LINES) {
            add(ShopifyUserError("CLIENT_INVALID_LINE_COUNT", listOf("lines")))
        }
        lines.forEachIndexed { index, line ->
            if (line.merchandiseId.isBlank()) {
                add(ShopifyUserError("CLIENT_MISSING_MERCHANDISE_ID", listOf("lines", "$index", "merchandiseId")))
            }
            if (line.quantity <= 0) {
                add(ShopifyUserError("CLIENT_INVALID_QUANTITY", listOf("lines", "$index", "quantity")))
            }
        }
    }
    return errors.takeIf { it.isNotEmpty() }?.let(StorefrontFailure::UserErrors)
}

internal fun validateUpdateLines(lines: List<CartLineUpdate>): StorefrontFailure.UserErrors? {
    val errors = buildList {
        if (lines.isEmpty() || lines.size > MAX_CART_LINES) {
            add(ShopifyUserError("CLIENT_INVALID_LINE_COUNT", listOf("lines")))
        }
        lines.forEachIndexed { index, line ->
            if (line.quantity <= 0) {
                add(ShopifyUserError("CLIENT_INVALID_QUANTITY", listOf("lines", "$index", "quantity")))
            }
        }
    }
    return errors.takeIf { it.isNotEmpty() }?.let(StorefrontFailure::UserErrors)
}

internal fun validateRemoveLines(lineIds: List<SensitiveCartLineId>): StorefrontFailure.UserErrors? =
    if (lineIds.isEmpty() || lineIds.size > MAX_CART_LINES || lineIds.distinct().size != lineIds.size) {
        StorefrontFailure.UserErrors(
            listOf(ShopifyUserError("CLIENT_INVALID_LINE_IDS", listOf("lineIds")))
        )
    } else {
        null
    }

internal fun List<CartCreateMutation.UserError>.toCreateProjectErrors(): List<ShopifyUserError> = map { error ->
    ShopifyUserError(error.code?.rawValue, error.field.orEmpty())
}

internal fun List<CartLinesAddMutation.UserError>.toAddProjectErrors(): List<ShopifyUserError> = map { error ->
    ShopifyUserError(error.code?.rawValue, error.field.orEmpty())
}

internal fun List<CartLinesUpdateMutation.UserError>.toUpdateProjectErrors(): List<ShopifyUserError> = map { error ->
    ShopifyUserError(error.code?.rawValue, error.field.orEmpty())
}

internal fun List<CartLinesRemoveMutation.UserError>.toRemoveProjectErrors(): List<ShopifyUserError> = map { error ->
    ShopifyUserError(error.code?.rawValue, error.field.orEmpty())
}

internal fun List<CartBuyerIdentityUpdateMutation.UserError>.toBuyerIdentityProjectErrors(): List<ShopifyUserError> =
    map { error ->
        ShopifyUserError(error.code?.rawValue, error.field.orEmpty())
    }
