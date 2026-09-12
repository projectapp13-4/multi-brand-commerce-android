package com.gurbakir.mobile

import androidx.compose.ui.unit.dp
import com.gurbakir.mobile.cart.shouldStackCartQuantityControls
import com.gurbakir.mobile.home.homeProductRangeColumnCount
import com.gurbakir.mobile.product.isExpandedProductLayout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AdaptiveLayoutPolicyTest {
    @Test
    fun homeProductRangeAddsColumnsAtRefinedWindowThresholds() {
        assertEquals(2, homeProductRangeColumnCount(399.dp))
        assertEquals(2, homeProductRangeColumnCount(599.dp))
        assertEquals(3, homeProductRangeColumnCount(600.dp))
        assertEquals(3, homeProductRangeColumnCount(959.dp))
        assertEquals(4, homeProductRangeColumnCount(960.dp))
    }

    @Test
    fun expandedProductUsesAvailableWindowWidthRatherThanPhysicalDisplayWidth() {
        assertFalse(isExpandedProductLayout(839.dp))
        assertTrue(isExpandedProductLayout(840.dp))
    }

    @Test
    fun cartQuantityControlsStackBeforeTwoHundredPercentText() {
        assertFalse(shouldStackCartQuantityControls(1.3f))
        assertTrue(shouldStackCartQuantityControls(1.5f))
        assertTrue(shouldStackCartQuantityControls(2f))
    }
}
