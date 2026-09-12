package com.gurbakir.mobile.product

import com.gurbakir.storefront.StorefrontMedia
import com.gurbakir.storefront.StorefrontMoney
import com.gurbakir.storefront.StorefrontProductDetail
import com.gurbakir.storefront.StorefrontProductMedia
import com.gurbakir.storefront.StorefrontProductOption
import com.gurbakir.storefront.StorefrontProductOptionValue
import com.gurbakir.storefront.StorefrontProductVariant
import com.gurbakir.storefront.StorefrontSelectedOption
import java.math.BigDecimal
import java.net.URI

internal fun productFixture(): StorefrontProductDetail = StorefrontProductDetail(
    id = "gid://shopify/Product/1",
    handle = "copper-pan",
    title = "Copper pan",
    description = "Handmade product description.",
    availableForSale = true,
    options =
        listOf(
            option("1", "Size", "Small", "Large"),
            option("2", "Color", "Red", "Blue")
        ),
    variants =
        listOf(
            variant("11", "Small", "Red", available = true, amount = "100.00"),
            variant("12", "Small", "Blue", available = false, amount = "110.00"),
            variant("13", "Large", "Red", available = true, amount = "120.00")
                .copy(currentlyNotInStock = true)
        ),
    media =
        listOf(
            StorefrontProductMedia("gid://shopify/MediaImage/21", media("primary")),
            StorefrontProductMedia("gid://shopify/MediaImage/22", media("detail"))
        )
)

private fun option(id: String, name: String, vararg values: String): StorefrontProductOption = StorefrontProductOption(
    id = "gid://shopify/ProductOption/$id",
    name = name,
    values =
        values.mapIndexed { index, value ->
            StorefrontProductOptionValue("gid://shopify/ProductOptionValue/$id$index", value)
        }
)

private fun variant(
    id: String,
    size: String,
    color: String,
    available: Boolean,
    amount: String
): StorefrontProductVariant = StorefrontProductVariant(
    id = "gid://shopify/ProductVariant/$id",
    title = "$size / $color",
    availableForSale = available,
    currentlyNotInStock = false,
    price = StorefrontMoney(BigDecimal(amount), "TRY"),
    compareAtPrice = null,
    image = if (id == "13") media("large-red") else null,
    selectedOptions =
        listOf(
            StorefrontSelectedOption("Size", size),
            StorefrontSelectedOption("Color", color)
        )
)

private fun media(name: String): StorefrontMedia = StorefrontMedia(
    URI("https://cdn.shopify.com/s/files/1/$name.jpg"),
    name,
    600,
    800
)
