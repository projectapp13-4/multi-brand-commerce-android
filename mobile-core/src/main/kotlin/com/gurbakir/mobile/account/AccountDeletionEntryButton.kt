@file:Suppress("FunctionNaming")

package com.gurbakir.mobile.account

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.gurbakir.mobile.core.R

@Composable
internal fun AccountDeletionButton(enabled: Boolean, onAccountDeletion: () -> Unit) {
    AccountMenuRow(
        title = stringResource(R.string.account_deletion_entry),
        iconResource = R.drawable.ic_account_delete,
        enabled = enabled,
        testTag = AccountTestTags.ACCOUNT_DELETION,
        onClick = onAccountDeletion
    )
}
