package com.gurbakir.mobile.accountdeletion

import androidx.annotation.StringRes

enum class DeletionPageId { PRIVACY, ACCOUNT_DELETION_REQUEST }

data class DeletionPageDescriptor(val id: DeletionPageId, @param:StringRes val titleResourceId: Int)

fun interface DeletionPageSource {
    fun pages(): List<DeletionPageDescriptor>
}

enum class DeletionPageLaunchResult { OPENED, NO_BROWSER, REJECTED }

data class DeletionPageFeedback(val pageId: DeletionPageId, val type: DeletionPageFeedbackType)

enum class DeletionPageFeedbackType { OPENING, RETURNED, NO_BROWSER, REJECTED }
