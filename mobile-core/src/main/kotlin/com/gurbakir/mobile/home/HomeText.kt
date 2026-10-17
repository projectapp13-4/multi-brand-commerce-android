package com.gurbakir.mobile.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

@Composable
internal fun HomeText.resolve(): String = when (this) {
    is HomeText.Packaged -> stringResource(resourceId)
    is HomeText.Remote -> value
}
