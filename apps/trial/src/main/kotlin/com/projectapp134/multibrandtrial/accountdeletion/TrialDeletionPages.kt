package com.projectapp134.multibrandtrial.accountdeletion

import android.content.Context
import com.gurbakir.mobile.accountdeletion.DeletionPageDescriptor
import com.gurbakir.mobile.accountdeletion.DeletionPageId
import com.gurbakir.mobile.accountdeletion.DeletionPageLaunchResult
import com.gurbakir.mobile.accountdeletion.DeletionPageSource
import com.projectapp134.multibrandtrial.R
import com.projectapp134.multibrandtrial.legal.TrialLegalContract
import com.projectapp134.multibrandtrial.legal.TrialLegalEntry
import com.projectapp134.multibrandtrial.legal.TrialLegalLaunchResult
import com.projectapp134.multibrandtrial.legal.TrialLegalPageLauncher
import com.projectapp134.multibrandtrial.legal.TrialLegalPagePolicy
import com.projectapp134.multibrandtrial.legal.TrialLegalRole

class TrialDeletionPages(private val legal: TrialLegalContract) : DeletionPageSource {
    private val launcher = TrialLegalPageLauncher(TrialLegalPagePolicy(legal))

    override fun pages(): List<DeletionPageDescriptor> =
        listOf(DeletionPageDescriptor(DeletionPageId.PRIVACY, R.string.trial_legal_privacy))

    fun open(context: Context, id: DeletionPageId): DeletionPageLaunchResult = launch(id) { entry ->
        when (launcher.open(context, entry)) {
            TrialLegalLaunchResult.OPENED -> DeletionPageLaunchResult.OPENED
            TrialLegalLaunchResult.NO_BROWSER -> DeletionPageLaunchResult.NO_BROWSER
            TrialLegalLaunchResult.REJECTED -> DeletionPageLaunchResult.REJECTED
        }
    }

    internal fun launch(
        id: DeletionPageId,
        openPage: (TrialLegalEntry) -> DeletionPageLaunchResult
    ): DeletionPageLaunchResult = when (id) {
        DeletionPageId.PRIVACY ->
            legal.entries.singleOrNull { it.role == TrialLegalRole.PRIVACY }?.let(openPage)
                ?: DeletionPageLaunchResult.REJECTED

        DeletionPageId.ACCOUNT_DELETION_REQUEST -> DeletionPageLaunchResult.REJECTED
    }
}
