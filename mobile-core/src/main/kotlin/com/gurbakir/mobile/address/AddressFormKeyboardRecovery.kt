package com.gurbakir.mobile.address

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.InterceptPlatformTextInput
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.PlatformTextInputInterceptor
import androidx.compose.ui.platform.PlatformTextInputMethodRequest
import com.gurbakir.account.CustomerAddressField

@Composable
@OptIn(ExperimentalComposeUiApi::class)
@Suppress("FunctionNaming") // Compose Unit-returning wrapper follows Composable naming.
internal fun AddressFieldInputSession(
    recovery: AddressKeyboardRecoveryState,
    content: @Composable () -> Unit
) {
    val interceptor = remember(recovery) {
        PlatformTextInputInterceptor { request, nextHandler ->
            val session = recovery.beginSession()
            try {
                nextHandler.startInputMethod(
                    PlatformTextInputMethodRequest { attributes ->
                        val connection = request.createInputConnection(attributes)
                        recovery.connectionCreated(session)
                        connection
                    }
                )
            } finally {
                recovery.endSession(session)
            }
        }
    }
    InterceptPlatformTextInput(interceptor, content)
}

@Composable
@Suppress("FunctionNaming") // Compose Unit-returning effect follows Composable naming.
internal fun AddressRecoveryKeyboardEffect(
    state: AddressFormUiState,
    field: CustomerAddressField,
    focus: AddressFieldFocus
) {
    val recovery = focus.keyboardRecovery
    val intent = recovery.intent
    val session = recovery.session
    val controller = LocalSoftwareKeyboardController.current
    val windowInfo = LocalWindowInfo.current
    val windowFocused = windowInfo.isWindowFocused
    val latestState by rememberUpdatedState(state)
    LaunchedEffect(
        state.focusRequest,
        state.loaded,
        state.phase,
        windowFocused,
        focus.focused,
        focus.coordinates,
        intent,
        session,
        session?.connectionCreated,
        controller
    ) {
        if (intent != null && recovery.intent === intent) {
            val request = latestState.focusRequest
            val ownsRecovery = request == null || (request.id == intent.requestId && request.field == field)
            val canRecover =
                latestState.loaded && latestState.phase == AddressFormPhase.READY && windowInfo.isWindowFocused &&
                    focus.focused && focus.coordinates?.isAttached == true && ownsRecovery
            if (!canRecover || controller == null) {
                recovery.cancel(intent)
            } else if (session != null && recovery.consumeReady(intent, session)) {
                controller.show()
            }
        }
    }
}

internal class AddressKeyboardRecoveryState {
    var session by mutableStateOf<AddressInputSession?>(null)
        private set
    var intent by mutableStateOf<AddressKeyboardRecoveryIntent?>(null)
        private set

    fun arm(requestId: Long) {
        intent = AddressKeyboardRecoveryIntent(requestId, session)
    }

    fun cancel(expected: AddressKeyboardRecoveryIntent? = intent) {
        if (intent === expected) intent = null
    }

    fun clearOwnership() {
        intent = null
        session = null
    }

    fun consumeReady(expected: AddressKeyboardRecoveryIntent, connected: AddressInputSession): Boolean {
        val ready =
            intent === expected && session === connected &&
                connected.connectionCreated && expected.session === connected
        if (ready) intent = null
        return ready
    }

    fun beginSession(): AddressInputSession {
        val started = AddressInputSession()
        session = started
        val pending = intent
        if (pending != null) {
            if (pending.session == null) pending.session = started else cancel(pending)
        }
        return started
    }

    fun connectionCreated(expected: AddressInputSession) {
        if (session === expected) expected.connectionCreated = true
    }

    fun endSession(expected: AddressInputSession) {
        if (session === expected) session = null
        val pending = intent
        if (pending?.session === expected) cancel(pending)
    }
}

internal class AddressInputSession {
    var connectionCreated by mutableStateOf(false)
}

internal class AddressKeyboardRecoveryIntent(val requestId: Long, session: AddressInputSession?) {
    var session by mutableStateOf(session)
}
