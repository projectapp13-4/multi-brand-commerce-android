package com.tumiart.mobile.brand

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.gurbakir.foundation.ui.BrandFontFamilies
import com.tumiart.mobile.R

internal val TumiArtFontFamilies = BrandFontFamilies(
    functional = FontFamily(
        Font(R.font.karla_regular, FontWeight.Normal),
        Font(R.font.karla_medium, FontWeight.Medium),
        Font(R.font.karla_semibold, FontWeight.SemiBold)
    ),
    editorial = FontFamily(Font(R.font.newsreader_regular, FontWeight.Normal))
)
