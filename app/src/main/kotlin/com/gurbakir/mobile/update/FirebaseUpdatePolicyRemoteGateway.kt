package com.gurbakir.mobile.update

import com.gurbakir.firebase.RemoteConfigResult
import com.gurbakir.firebase.RemoteFeatureFlags

internal class FirebaseUpdatePolicyRemoteGateway(private val remoteFeatureFlags: RemoteFeatureFlags) :
    UpdatePolicyRemoteGateway {
    override suspend fun refresh(): UpdatePolicyRefreshResult = when (val result = remoteFeatureFlags.refresh()) {
        RemoteConfigResult.LocalDefaults -> UpdatePolicyRefreshResult.LocalDefaults

        is RemoteConfigResult.Fetched -> {
            val snapshot = remoteFeatureFlags.policySnapshot()
            UpdatePolicyRefreshResult.Fetched(
                snapshot = UpdatePolicySnapshot(
                    maintenanceMessageEnabled = snapshot.maintenanceMessageEnabled,
                    checkoutPreloadEnabled = snapshot.checkoutPreloadEnabled,
                    optionalUpdateMessageEnabled = snapshot.optionalUpdateMessageEnabled,
                    recommendedVersionCode = snapshot.recommendedVersionCode,
                    policyRevision = snapshot.policyRevision
                ),
                fetchedAtEpochMillis = result.fetchedAtEpochMillis
            )
        }
    }
}
