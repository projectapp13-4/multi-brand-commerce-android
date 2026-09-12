package com.gurbakir.mobile.address

import androidx.compose.ui.text.input.KeyboardType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PostalCodeInputModeTest {
    @Test
    fun `postal input maps finite territory policy to keyboard semantics`() {
        assertEquals(KeyboardType.Number, postalCodeKeyboardType(PostalCodeInputMode.NUMERIC))
        assertEquals(KeyboardType.Text, postalCodeKeyboardType(PostalCodeInputMode.TEXT))
    }
}
