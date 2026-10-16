package com.gurbakir.mobile.catalog

data class CatalogConfiguration(
    val menuHandle: String,
    val presentation: CatalogPresentation = CatalogPresentation.Standard
)
