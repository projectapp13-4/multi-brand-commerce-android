@file:Suppress("FunctionNaming")

package com.gurbakir.mobile.wishlist

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

internal data class WishlistMembershipBinding(
    val state: WishlistMembershipUiState,
    val onSetSaved: (String, Boolean) -> Unit
)

@Composable
internal fun wishlistMembership(): WishlistMembershipBinding {
    val viewModel: WishlistMembershipViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    return WishlistMembershipBinding(state, viewModel::setSaved)
}
