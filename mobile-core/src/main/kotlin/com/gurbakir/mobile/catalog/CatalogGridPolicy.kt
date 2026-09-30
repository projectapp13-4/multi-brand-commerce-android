package com.gurbakir.mobile.catalog

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.gurbakir.mobile.ui.LARGE_TEXT_FONT_SCALE

internal fun catalogProductCardMinimumWidth(fontScale: Float): Dp =
    if (fontScale >= LARGE_TEXT_FONT_SCALE) 240.dp else 160.dp
