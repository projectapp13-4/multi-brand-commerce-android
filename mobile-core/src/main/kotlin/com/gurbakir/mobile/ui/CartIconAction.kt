@file:Suppress("FunctionNaming")

package com.gurbakir.mobile.ui

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.gurbakir.mobile.core.R

@Composable
internal fun CartIconAction(onClick: () -> Unit, quantity: Int = 0, testTag: String? = null) {
    val description = if (quantity > 0) {
        stringResource(R.string.cart_title_with_quantity, quantity)
    } else {
        stringResource(R.string.cart_title)
    }
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(CART_TOUCH_TARGET).then(if (testTag == null) Modifier else Modifier.testTag(testTag))
    ) {
        BadgedBox(
            badge = {
                if (quantity > 0) {
                    Badge(modifier = Modifier.clearAndSetSemantics {}) {
                        Text(if (quantity > MAX_BADGE_QUANTITY) "99+" else quantity.toString())
                    }
                }
            }
        ) {
            Icon(painterResource(R.drawable.ic_account_cart), contentDescription = description)
        }
    }
}

private const val MAX_BADGE_QUANTITY = 99
private val CART_TOUCH_TARGET = 48.dp
