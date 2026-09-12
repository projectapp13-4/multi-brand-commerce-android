package com.gurbakir.mobile.address

fun interface AddressPostalCodePolicy {
    fun accepts(postalCode: String): Boolean
}

enum class PostalCodeInputMode {
    NUMERIC,
    TEXT
}

data class AddressTerritoryPolicy(
    val supportedTerritoryCode: String,
    val postalCodeInputMode: PostalCodeInputMode,
    val postalCodePolicy: AddressPostalCodePolicy = AddressPostalCodePolicy { true }
) {
    init {
        require(supportedTerritoryCode.isNotBlank())
    }

    fun supports(territoryCode: String?): Boolean = territoryCode == supportedTerritoryCode

    fun acceptsPostalCode(postalCode: String): Boolean = postalCodePolicy.accepts(postalCode)
}
