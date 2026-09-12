package com.gurbakir.storefront

import java.time.Instant

object StorefrontDebugFixtures {
    fun persistedCart(rawCartId: String, expiresAt: Instant, ownership: CartOwnership): PersistedCart = PersistedCart(
        id = SensitiveCartId.from(rawCartId),
        expiresAt = expiresAt,
        ownership = ownership
    )
}
