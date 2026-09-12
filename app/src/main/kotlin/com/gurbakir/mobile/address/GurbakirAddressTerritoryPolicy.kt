package com.gurbakir.mobile.address

private val TURKISH_POSTAL_CODE = Regex("^[0-9]{5}$")

internal val GurbakirAddressTerritoryPolicy = AddressTerritoryPolicy(
    supportedTerritoryCode = "TR",
    postalCodeInputMode = PostalCodeInputMode.NUMERIC,
    postalCodePolicy = AddressPostalCodePolicy(TURKISH_POSTAL_CODE::matches)
)
