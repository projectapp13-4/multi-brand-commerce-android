package com.gurbakir.checkout

import com.shopify.checkoutsheetkit.CheckoutExpiredException
import com.shopify.checkoutsheetkit.CheckoutSheetKitException
import com.shopify.checkoutsheetkit.ConfigurationException
import com.shopify.checkoutsheetkit.HttpException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CheckoutFailureMappingTest {
    @Test
    fun `maps SDK failures without exposing descriptions`() {
        assertEquals(
            CheckoutFailure.EXPIRED_OR_COMPLETED_CART,
            CheckoutExpiredException(isRecoverable = false).toProjectFailure()
        )
        assertEquals(
            CheckoutFailure.CONFIGURATION,
            ConfigurationException(isRecoverable = false).toProjectFailure()
        )
        assertEquals(
            CheckoutFailure.NETWORK,
            HttpException(statusCode = SERVICE_UNAVAILABLE_STATUS, isRecoverable = true).toProjectFailure()
        )
        assertEquals(
            CheckoutFailure.RECOVERABLE,
            CheckoutSheetKitException(
                errorDescription = "sensitive detail must not cross the adapter",
                isRecoverable = true
            ).toProjectFailure()
        )
    }

    private companion object {
        private const val SERVICE_UNAVAILABLE_STATUS = 503
    }
}
