package com.gurbakir.mobile.address

import androidx.compose.ui.text.input.KeyboardType

internal fun postalCodeKeyboardType(mode: PostalCodeInputMode): KeyboardType = when (mode) {
    PostalCodeInputMode.NUMERIC -> KeyboardType.Number
    PostalCodeInputMode.TEXT -> KeyboardType.Text
}
