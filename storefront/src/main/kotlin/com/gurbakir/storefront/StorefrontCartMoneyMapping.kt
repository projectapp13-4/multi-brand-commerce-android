package com.gurbakir.storefront

import com.gurbakir.storefront.graphql.fragment.CartLineFields
import com.gurbakir.storefront.graphql.fragment.CartSnapshotFields

internal fun CartSnapshotFields.SubtotalAmount.toStorefrontMoney(): StorefrontMoney? =
    amount.toNonNegativeCartAmount()?.let { StorefrontMoney(it, currencyCode.rawValue) }

internal fun CartSnapshotFields.TotalAmount.toStorefrontMoney(): StorefrontMoney? =
    amount.toNonNegativeCartAmount()?.let { StorefrontMoney(it, currencyCode.rawValue) }

internal fun CartLineFields.AmountPerQuantity.toStorefrontMoney(): StorefrontMoney? =
    amount.toNonNegativeCartAmount()?.let { StorefrontMoney(it, currencyCode.rawValue) }

internal fun CartLineFields.TotalAmount.toStorefrontMoney(): StorefrontMoney? =
    amount.toNonNegativeCartAmount()?.let { StorefrontMoney(it, currencyCode.rawValue) }

private fun String.toNonNegativeCartAmount() = toBigDecimalOrNull()?.takeIf { it.signum() >= 0 }
