package com.gurbakir.storefront

import java.math.BigDecimal
import java.net.URI

interface StorefrontHomeGateway {
    suspend fun loadHomeCollection(handle: String): StorefrontResult<HomeCollectionSummary?>

    suspend fun loadHomeProduct(handle: String): StorefrontResult<HomeProductSummary?>
}

interface StorefrontApi :
    StorefrontGateway,
    StorefrontHomeGateway

data class StorefrontMedia(val uri: URI, val altText: String?, val width: Int?, val height: Int?)

data class StorefrontMoney(val amount: BigDecimal, val currencyCode: String)

data class HomeCollectionSummary(
    val id: String,
    val handle: String,
    val sourceTitle: String,
    val media: StorefrontMedia
)

data class HomeProductSummary(
    val id: String,
    val handle: String,
    val title: String,
    val availableForSale: Boolean,
    val media: StorefrontMedia?,
    val price: StorefrontMoney
)
