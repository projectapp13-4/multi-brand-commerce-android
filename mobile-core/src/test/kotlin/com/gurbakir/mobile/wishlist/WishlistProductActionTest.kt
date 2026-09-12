package com.gurbakir.mobile.wishlist

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class WishlistProductActionTest {
    @Test
    fun `absent state or action removes product wishlist controls`() {
        val absent: WishlistMembershipUiState? = null
        assertNull(absent.productAction("product", { _, _ -> error("Disabled action invoked") }))
        assertNull(WishlistMembershipUiState().productAction("product", null))
    }

    @Test
    fun `available product action forwards the desired state and exact product`() {
        var received: Pair<String, Boolean>? = null
        val action = requireNotNull(
            WishlistMembershipUiState().productAction("product-123") { id, saved ->
                received =
                    id to saved
            }
        )
        action.onSetSaved(true)
        assertEquals("product-123" to true, received)
        assertEquals(false, action.saved)
        assertEquals(false, action.updating)
    }
}
