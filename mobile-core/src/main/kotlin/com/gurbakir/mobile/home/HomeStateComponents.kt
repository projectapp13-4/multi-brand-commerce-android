@file:Suppress("FunctionNaming")

package com.gurbakir.mobile.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.gurbakir.foundation.ui.LocalBrandSpacing
import com.gurbakir.mobile.core.R
import com.gurbakir.mobile.ui.CommerceSkeleton
import com.gurbakir.mobile.ui.CommerceStatePanel

@Composable
internal fun HomeSectionSkeleton(tag: String) {
    val spacing = LocalBrandSpacing.current
    Column(
        modifier = Modifier.fillMaxWidth().testTag(tag),
        verticalArrangement = Arrangement.spacedBy(spacing.normalDp.dp)
    ) {
        CommerceSkeleton(
            modifier = Modifier.fillMaxWidth(SKELETON_TITLE_WIDTH_FRACTION).height(SKELETON_TITLE_HEIGHT)
        )
        CommerceSkeleton(modifier = Modifier.fillMaxWidth().height(SKELETON_MEDIA_HEIGHT))
    }
}

@Composable
internal fun HomeSectionError(failure: HomeLoadFailure, onRetry: () -> Unit, tag: String, partial: Boolean = false) {
    if (!partial) {
        CommerceStatePanel(
            title = stringResource(R.string.home_error_title),
            body = stringResource(failure.messageResourceId()),
            primaryActionLabel = if (failure.retryable) stringResource(R.string.retry) else null,
            onPrimaryAction = if (failure.retryable) onRetry else null,
            testTag = tag
        )
        return
    }
    Column(
        modifier = Modifier.fillMaxWidth().testTag(tag),
        verticalArrangement = Arrangement.spacedBy(LocalBrandSpacing.current.normalDp.dp)
    ) {
        Text(text = stringResource(R.string.home_error_partial), style = MaterialTheme.typography.bodyLarge)
        if (failure.retryable) {
            Button(onClick = onRetry) {
                Text(stringResource(R.string.retry))
            }
        }
    }
}

@Composable
internal fun HomeSlowLoadingPanel(onRetry: () -> Unit, tag: String) {
    CommerceStatePanel(
        title = stringResource(R.string.home_slow_loading_title),
        body = stringResource(R.string.home_slow_loading_body),
        primaryActionLabel = stringResource(R.string.retry),
        onPrimaryAction = onRetry,
        testTag = tag
    )
}

private fun HomeLoadFailure.messageResourceId(): Int = when (category) {
    HomeLoadFailureCategory.CONNECTION -> R.string.home_error_connection
    HomeLoadFailureCategory.CONFIGURATION -> R.string.home_error_configuration
    HomeLoadFailureCategory.SERVICE -> R.string.home_error_service
}

private const val SKELETON_TITLE_WIDTH_FRACTION = 0.45f
private val SKELETON_TITLE_HEIGHT = 24.dp
private val SKELETON_MEDIA_HEIGHT = 180.dp
