package com.gurbakir.storefront

import com.gurbakir.storefront.graphql.fragment.CartLineFields
import com.gurbakir.storefront.graphql.fragment.CartSnapshotFields

internal fun CartSnapshotFields.SubtotalAmount.toStorefrontMoney(): StorefrontMoney? =
    amount.toBigDecimalOrNull()?.let { StorefrontMoney(it, currencyCode.rawValue) }

internal fun CartSnapshotFields.TotalAmount.toStorefrontMoney(): StorefrontMoney? =
    amount.toBigDecimalOrNull()?.let { StorefrontMoney(it, currencyCode.rawValue) }

internal fun CartLineFields.AmountPerQuantity.toStorefrontMoney(): StorefrontMoney? =
    amount.toBigDecimalOrNull()?.let { StorefrontMoney(it, currencyCode.rawValue) }

internal fun CartLineFields.TotalAmount.toStorefrontMoney(): StorefrontMoney? =
    amount.toBigDecimalOrNull()?.let { StorefrontMoney(it, currencyCode.rawValue) }
