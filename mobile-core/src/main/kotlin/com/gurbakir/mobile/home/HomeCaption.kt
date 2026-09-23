package com.gurbakir.mobile.home

internal fun String.normalizeHomeCaptionLineEndings(): String = replace("\r\n", "\n").replace('\r', '\n')

internal fun String.isBoundedHomeCaption(maximumCodePoints: Int): Boolean =
    isNotBlank() && codePointCount(0, length) in 1..maximumCodePoints && none { it.isISOControl() && it != '\n' }
