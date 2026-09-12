package com.gurbakir.mobile.accountdeletion

import android.content.Context
import com.gurbakir.mobile.legal.LegalPageId
import com.gurbakir.mobile.legal.LegalPageMetadata
import com.gurbakir.mobile.legal.LegalSupportRepository
import com.gurbakir.mobile.legal.OwnedPageLaunchResult
import com.gurbakir.mobile.legal.OwnedPageLauncher
import com.gurbakir.mobile.legal.OwnedPagePolicy
import javax.inject.Inject

// App-owned adapter: retained with legal metadata and launcher when the deletion feature moves to core.
internal class GurbakirDeletionPages @Inject constructor(repository: LegalSupportRepository) : DeletionPageSource {
    private val metadata = repository.pages().filter { it.id == LegalPageId.PRIVACY || it.id == LegalPageId.SUPPORT }
    private val launcher = OwnedPageLauncher(OwnedPagePolicy(metadata))

    override fun pages(): List<DeletionPageDescriptor> = metadata.map { page ->
        DeletionPageDescriptor(
            id = if (page.id == LegalPageId.PRIVACY) DeletionPageId.PRIVACY else DeletionPageId.SUPPORT,
            titleResourceId = page.titleResourceId
        )
    }

    fun open(context: Context, id: DeletionPageId): DeletionPageLaunchResult =
        launch(id) { page -> launcher.open(context, page) }

    internal fun launch(
        id: DeletionPageId,
        openPage: (LegalPageMetadata) -> OwnedPageLaunchResult
    ): DeletionPageLaunchResult {
        val legalId = when (id) {
            DeletionPageId.PRIVACY -> LegalPageId.PRIVACY
            DeletionPageId.SUPPORT -> LegalPageId.SUPPORT
        }
        val page = metadata.firstOrNull { it.id == legalId } ?: return DeletionPageLaunchResult.REJECTED
        return when (openPage(page)) {
            OwnedPageLaunchResult.OPENED -> DeletionPageLaunchResult.OPENED
            OwnedPageLaunchResult.NO_BROWSER -> DeletionPageLaunchResult.NO_BROWSER
            OwnedPageLaunchResult.REJECTED -> DeletionPageLaunchResult.REJECTED
        }
    }
}
