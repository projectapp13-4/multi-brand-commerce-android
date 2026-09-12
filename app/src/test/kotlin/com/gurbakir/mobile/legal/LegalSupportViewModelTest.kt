package com.gurbakir.mobile.legal

import com.gurbakir.mobile.R
import java.time.LocalDate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class LegalSupportViewModelTest {
    private val page =
        LegalPageMetadata(
            id = LegalPageId.SUPPORT,
            titleResourceId = R.string.legal_support_page_support,
            summaryResourceId = R.string.legal_support_page_support_summary,
            canonicalUrl = "https://gurbakir.com/pages/contact",
            baselineVersion = LEGAL_BASELINE_VERSION,
            adoptedAt = LocalDate.of(2026, 8, 11),
            sourceEffectiveDate = null,
            source = LegalPageSource.MERCHANT_PAGE
        )

    @Test
    fun `successful external launch returns focus without inventing acceptance state`() {
        val viewModel = LegalSupportViewModel(FakeRepository(listOf(page)))

        viewModel.onLaunchResult(page.id, OwnedPageLaunchResult.OPENED)

        assertEquals(page.id, viewModel.state.value.pendingReturnPageId)
        assertEquals(LegalPageFeedbackType.OPENING, viewModel.state.value.feedback?.type)

        viewModel.onActivityResumed()

        assertNull(viewModel.state.value.pendingReturnPageId)
        assertEquals(page.id, viewModel.state.value.focusRequestPageId)
        assertEquals(LegalPageFeedbackType.RETURNED, viewModel.state.value.feedback?.type)

        viewModel.onFocusRequestHandled(page.id)

        assertNull(viewModel.state.value.focusRequestPageId)
    }

    @Test
    fun `unavailable browser remains on local index and requests link focus`() {
        val viewModel = LegalSupportViewModel(FakeRepository(listOf(page)))

        viewModel.onLaunchResult(page.id, OwnedPageLaunchResult.NO_BROWSER)

        assertNull(viewModel.state.value.pendingReturnPageId)
        assertEquals(page.id, viewModel.state.value.focusRequestPageId)
        assertEquals(LegalPageFeedbackType.NO_BROWSER, viewModel.state.value.feedback?.type)
        assertEquals(listOf(page), viewModel.state.value.pages)
    }

    private class FakeRepository(private val value: List<LegalPageMetadata>) : LegalSupportRepository {
        override fun pages(): List<LegalPageMetadata> = value
    }
}
