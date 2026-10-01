@file:Suppress("FunctionNaming")

package com.gurbakir.mobile.home

import androidx.compose.runtime.Composable
import com.gurbakir.mobile.ui.CartIconAction

@Composable
internal fun HomeTopBarActions(actions: HomeActions, cartQuantity: Int) {
    CartIconAction(onClick = actions.openCart, quantity = cartQuantity, testTag = HomeTestTags.CART)
}
