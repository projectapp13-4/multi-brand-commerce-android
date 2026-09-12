package com.gurbakir.mobile.checkout

import com.gurbakir.storefront.CartCompletionResolution
import com.gurbakir.storefront.CartCoordinator
import com.gurbakir.storefront.SensitiveCartId
import javax.inject.Inject

interface CheckoutCartCompleter {
    suspend fun complete(cartId: SensitiveCartId): CartCompletionResolution
}

class CoordinatedCheckoutCartCompleter @Inject constructor(private val coordinator: CartCoordinator) :
    CheckoutCartCompleter {
    override suspend fun complete(cartId: SensitiveCartId): CartCompletionResolution =
        coordinator.completion.complete(cartId)
}
