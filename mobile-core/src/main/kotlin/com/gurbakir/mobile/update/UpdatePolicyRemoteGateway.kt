package com.gurbakir.mobile.update

data class UpdatePolicySnapshot(
    val maintenanceMessageEnabled: Boolean = false,
    val checkoutPreloadEnabled: Boolean = false,
    val optionalUpdateMessageEnabled: Boolean = false,
    val recommendedVersionCode: Int = 0,
    val policyRevision: Long = 0L
)

sealed interface UpdatePolicyRefreshResult {
    data class Fetched(val snapshot: UpdatePolicySnapshot, val fetchedAtEpochMillis: Long) : UpdatePolicyRefreshResult
    data object LocalDefaults : UpdatePolicyRefreshResult
}

fun interface UpdatePolicyRemoteGateway {
    suspend fun refresh(): UpdatePolicyRefreshResult
}
