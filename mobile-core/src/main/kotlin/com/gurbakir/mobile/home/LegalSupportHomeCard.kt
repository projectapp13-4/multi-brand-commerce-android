@file:Suppress("FunctionNaming")

package com.gurbakir.mobile.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.gurbakir.foundation.ui.LocalBrandSpacing
import com.gurbakir.mobile.core.R

@Composable
internal fun LegalSupportHomeCard(onOpen: () -> Unit) {
    val spacing = LocalBrandSpacing.current
    Card(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth().testTag(HomeTestTags.LEGAL_SUPPORT)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(spacing.generousDp.dp),
            verticalArrangement = Arrangement.spacedBy(spacing.normalDp.dp)
        ) {
            Text(
                text = stringResource(R.string.legal_support_home_title),
                style = MaterialTheme.typography.titleLarge
            )
            Text(stringResource(R.string.legal_support_home_summary))
            Text(
                text = stringResource(R.string.legal_support_home_action),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}
