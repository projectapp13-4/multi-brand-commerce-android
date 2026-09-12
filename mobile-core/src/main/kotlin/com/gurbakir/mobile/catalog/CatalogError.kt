@file:Suppress("FunctionNaming")

package com.gurbakir.mobile.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.gurbakir.foundation.ui.LocalBrandSpacing
import com.gurbakir.mobile.core.R

@Composable
internal fun CatalogError(failure: CatalogLoadFailure, onRetry: () -> Unit, tag: String, partial: Boolean = false) {
    val spacing = LocalBrandSpacing.current
    Column(
        modifier = Modifier.fillMaxWidth().testTag(tag),
        verticalArrangement = Arrangement.spacedBy(spacing.normalDp.dp)
    ) {
        Text(
            if (partial) {
                stringResource(R.string.categories_partial_error)
            } else {
                stringResource(failure.messageResourceId())
            }
        )
        if (failure.retryable) {
            Button(onClick = onRetry) { Text(stringResource(R.string.retry)) }
        }
    }
}

private fun CatalogLoadFailure.messageResourceId(): Int = when (category) {
    CatalogLoadFailureCategory.CONNECTION -> R.string.catalog_error_connection
    CatalogLoadFailureCategory.CONFIGURATION -> R.string.catalog_error_configuration
    CatalogLoadFailureCategory.SERVICE -> R.string.catalog_error_service
}
