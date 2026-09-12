@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@file:Suppress("FunctionNaming")

package com.gurbakir.mobile.legal

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.gurbakir.foundation.ui.LocalBrandSpacing
import com.gurbakir.mobile.R
import com.gurbakir.mobile.localization.effectiveForegroundLocale
import com.gurbakir.mobile.ui.DestinationLevel
import com.gurbakir.mobile.ui.DestinationScaffold
import com.gurbakir.mobile.ui.DestinationTitleAlignment
import com.gurbakir.mobile.ui.centeredDestinationContent
import com.gurbakir.mobile.ui.consumeDestinationInsets
import com.gurbakir.mobile.ui.withDestinationSpacing
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

data class LegalSupportActions(
    val onBack: () -> Unit,
    val onOpen: (LegalPageMetadata) -> Unit,
    val onFocusRequestHandled: (LegalPageId) -> Unit = {}
)

@Composable
fun LegalSupportScreen(state: LegalSupportUiState, actions: LegalSupportActions) {
    val spacing = LocalBrandSpacing.current
    val focusRequesters = remember(state.pages) { state.pages.associate { it.id to FocusRequester() } }
    val listState = rememberLazyListState()
    LaunchedEffect(state.focusRequestPageId) {
        state.focusRequestPageId?.let { pageId ->
            val pageIndex = state.pages.indexOfFirst { it.id == pageId }
            if (pageIndex >= 0) {
                listState.scrollToItem(1 + pageIndex)
                withFrameNanos {}
            }
            focusRequesters[pageId]?.requestFocus()
            actions.onFocusRequestHandled(pageId)
        }
    }

    DestinationScaffold(
        title = stringResource(R.string.legal_support_title),
        level = DestinationLevel.SECONDARY,
        modifier = Modifier.testTag(LegalSupportTestTags.ROOT),
        titleAlignment = DestinationTitleAlignment.CENTER,
        onNavigateUp = actions.onBack
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier =
                Modifier.fillMaxSize()
                    .centeredDestinationContent(720.dp)
                    .consumeDestinationInsets(padding)
                    .testTag(LegalSupportTestTags.CONTENT),
            contentPadding = padding.withDestinationSpacing(),
            verticalArrangement = Arrangement.spacedBy(spacing.generousDp.dp)
        ) {
            item {
                LegalSupportHeader()
            }
            items(state.pages, key = { it.id.name }) { page ->
                LegalPageCard(
                    page = page,
                    feedback = state.feedback?.takeIf { it.pageId == page.id },
                    focusRequester = requireNotNull(focusRequesters[page.id]),
                    onOpen = { actions.onOpen(page) }
                )
            }
        }
    }
}

@Composable
private fun LegalSupportHeader() {
    val spacing = LocalBrandSpacing.current
    Column(verticalArrangement = Arrangement.spacedBy(spacing.normalDp.dp)) {
        Text(
            text = stringResource(R.string.legal_support_heading),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.semantics { heading() }
        )
        Text(stringResource(R.string.legal_support_intro))
        Text(
            text = stringResource(R.string.legal_support_external_context),
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = stringResource(R.string.legal_support_offline),
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text =
                stringResource(
                    R.string.legal_support_baseline,
                    LEGAL_BASELINE_VERSION,
                    LEGAL_BASELINE_ADOPTION_DATE.localized()
                ),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.testTag(LegalSupportTestTags.BASELINE)
        )
    }
}

@Composable
private fun LegalPageCard(
    page: LegalPageMetadata,
    feedback: LegalPageFeedback?,
    focusRequester: FocusRequester,
    onOpen: () -> Unit
) {
    val spacing = LocalBrandSpacing.current
    Card(modifier = Modifier.fillMaxWidth().testTag(LegalSupportTestTags.page(page.id))) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(spacing.generousDp.dp),
            verticalArrangement = Arrangement.spacedBy(spacing.normalDp.dp)
        ) {
            Text(
                text = stringResource(page.titleResourceId),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() }
            )
            Text(stringResource(page.summaryResourceId))
            Text(
                text =
                    when (page.source) {
                        LegalPageSource.SHOPIFY_POLICY ->
                            stringResource(R.string.legal_support_source_shopify)

                        LegalPageSource.MERCHANT_PAGE ->
                            stringResource(R.string.legal_support_source_merchant)
                    },
                style = MaterialTheme.typography.labelMedium
            )
            Text(
                text =
                    page.sourceEffectiveDate?.let {
                        stringResource(R.string.legal_support_source_updated, it.localized())
                    } ?: stringResource(R.string.legal_support_source_adopted, page.adoptedAt.localized()),
                style = MaterialTheme.typography.labelMedium
            )
            feedback?.let { LegalPageFeedbackMessage(it, page) }
            Button(
                onClick = onOpen,
                modifier =
                    Modifier.fillMaxWidth()
                        .focusRequester(focusRequester)
                        .focusable()
                        .testTag(LegalSupportTestTags.open(page.id))
            ) {
                Text(stringResource(R.string.legal_support_open, stringResource(page.titleResourceId)))
            }
        }
    }
}

@Composable
private fun LegalPageFeedbackMessage(feedback: LegalPageFeedback, page: LegalPageMetadata) {
    Text(
        text = feedback.text(page),
        color =
            if (
                feedback.type == LegalPageFeedbackType.NO_BROWSER ||
                feedback.type == LegalPageFeedbackType.REJECTED
            ) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        modifier =
            Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                .testTag(LegalSupportTestTags.FEEDBACK)
    )
}

@Composable
private fun LegalPageFeedback.text(page: LegalPageMetadata): String {
    val pageTitle = stringResource(page.titleResourceId)
    return when (type) {
        LegalPageFeedbackType.OPENING -> stringResource(R.string.legal_support_feedback_opening, pageTitle)
        LegalPageFeedbackType.RETURNED -> stringResource(R.string.legal_support_feedback_returned, pageTitle)
        LegalPageFeedbackType.NO_BROWSER -> stringResource(R.string.legal_support_feedback_no_browser, pageTitle)
        LegalPageFeedbackType.REJECTED -> stringResource(R.string.legal_support_feedback_rejected, pageTitle)
    }
}

@Composable
private fun LocalDate.localized(): String = formatLegalDate(this, effectiveForegroundLocale(LocalConfiguration.current))

internal fun formatLegalDate(date: LocalDate, locale: Locale): String =
    DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale).format(date)

object LegalSupportTestTags {
    const val ROOT = "legal-support-root"
    const val CONTENT = "legal-support-content"
    const val BASELINE = "legal-support-baseline"
    const val FEEDBACK = "legal-support-feedback"

    fun page(id: LegalPageId): String = "legal-support-page-${id.name.lowercase()}"

    fun open(id: LegalPageId): String = "legal-support-open-${id.name.lowercase()}"
}
