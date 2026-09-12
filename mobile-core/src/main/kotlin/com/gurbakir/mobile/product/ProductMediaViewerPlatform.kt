@file:Suppress("FunctionNaming")

package com.gurbakir.mobile.product

import android.os.Build
import android.view.ViewTreeObserver
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat

@Composable
internal fun DialogSystemBarAppearance() {
    val view = LocalView.current
    val window = (view.parent as? DialogWindowProvider)?.window
    DisposableEffect(window, view) {
        if (window == null) {
            onDispose {}
        } else {
            val controller = WindowCompat.getInsetsController(window, view)
            val previousLightStatusBars = controller.isAppearanceLightStatusBars
            val previousLightNavigationBars = controller.isAppearanceLightNavigationBars
            val previousContrastEnforced =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    window.isNavigationBarContrastEnforced
                } else {
                    false
                }
            controller.isAppearanceLightStatusBars = false
            controller.isAppearanceLightNavigationBars = false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                window.isNavigationBarContrastEnforced = false
            }
            onDispose {
                controller.isAppearanceLightStatusBars = previousLightStatusBars
                controller.isAppearanceLightNavigationBars = previousLightNavigationBars
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    window.isNavigationBarContrastEnforced = previousContrastEnforced
                }
            }
        }
    }
}

@Composable
internal fun RequestDialogFocus(focusRequester: FocusRequester) {
    val view = LocalView.current
    DisposableEffect(view, focusRequester) {
        val observer = view.viewTreeObserver
        val listener =
            ViewTreeObserver.OnWindowFocusChangeListener { hasFocus ->
                if (hasFocus) {
                    view.post { focusRequester.requestFocus() }
                }
            }
        observer.addOnWindowFocusChangeListener(listener)
        if (view.hasWindowFocus()) {
            view.post { focusRequester.requestFocus() }
        }
        onDispose {
            if (observer.isAlive) {
                observer.removeOnWindowFocusChangeListener(listener)
            }
        }
    }
}
