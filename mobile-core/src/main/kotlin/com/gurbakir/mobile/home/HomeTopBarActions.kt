@file:Suppress("FunctionNaming")

package com.gurbakir.mobile.home

import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.gurbakir.mobile.core.R

@Composable
internal fun HomeTopBarActions(actions: HomeActions, cartQuantity: Int, requestActive: Boolean) {
    IconButton(
        onClick = actions.refreshContent,
        enabled = !requestActive,
        modifier = Modifier.testTag(HomeTestTags.REFRESH)
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_refresh),
            contentDescription = stringResource(
                if (requestActive) R.string.home_refreshing_description else R.string.home_refresh_description
            )
        )
    }
    TextButton(
        onClick = actions.openCart,
        modifier = Modifier.testTag(HomeTestTags.CART)
    ) {
        Text(
            if (cartQuantity > 0) {
                stringResource(R.string.cart_title_with_quantity, cartQuantity)
            } else {
                stringResource(R.string.cart_title)
            }
        )
    }
}
