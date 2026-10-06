package com.gurbakir.mobile.address

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.platform.LocalWindowInfo
import com.gurbakir.account.CustomerAddressField
import kotlinx.coroutines.flow.first

@Composable
@Suppress("FunctionNaming") // Compose Unit-returning effect follows Composable naming.
internal fun AddressValidationFocusEffect(
    state: AddressFormUiState,
    actions: AddressFormActions,
    fields: Map<CustomerAddressField, AddressFieldFocus>,
    listState: LazyListState,
    fieldStartIndex: Int
) {
    val latestState by rememberUpdatedState(state)
    val latestActions by rememberUpdatedState(actions)
    val windowFocused = LocalWindowInfo.current.isWindowFocused
    LaunchedEffect(state.focusRequest, state.loaded, state.phase, fieldStartIndex, windowFocused) {
        val request = state.focusRequest ?: return@LaunchedEffect
        if (!state.loaded || state.phase != AddressFormPhase.READY || !windowFocused) return@LaunchedEffect
        val fieldOffset = ADDRESS_FORM_FIELDS.indexOf(request.field)
        if (fieldOffset < 0) return@LaunchedEffect
        val field = fields.getValue(request.field)
        val itemIndex = fieldStartIndex + fieldOffset
        listState.scrollToItem(itemIndex)
        snapshotFlow {
            field.coordinates?.isAttached == true &&
                listState.layoutInfo.visibleItemsInfo.any { it.key == request.field.name && it.index == itemIndex }
        }.first { it }
        if (latestState.focusRequest != request) return@LaunchedEffect
        if (!field.requester.requestFocus(FocusDirection.Enter)) return@LaunchedEffect
        snapshotFlow { field.focused }.first { it }
        if (latestState.focusRequest == request) latestActions.onFocusHandled(request.id)
    }
}

internal class AddressFieldFocus {
    val requester = FocusRequester()
    var coordinates by mutableStateOf<LayoutCoordinates?>(null)
    var focused by mutableStateOf(false)
}
