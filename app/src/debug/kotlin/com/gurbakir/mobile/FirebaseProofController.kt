package com.gurbakir.mobile

import com.gurbakir.firebase.ControlledPushTarget
import com.gurbakir.firebase.PushRegistrationCoordinator
import com.gurbakir.firebase.PushRegistrationResult
import com.gurbakir.firebase.RemoteConfigResult
import com.gurbakir.firebase.RemoteFeatureFlags
import javax.inject.Inject

interface FirebaseProofTargetRecorder {
    fun record(target: ControlledPushTarget)

    fun clear()

    companion object {
        const val CACHE_FILE_NAME = "firebase-proof-target"
    }
}

interface FirebaseProofController {
    fun hasStoredPushConsent(): Boolean

    suspend fun refreshRemoteConfig(): RemoteConfigResult

    suspend fun registerPushAfterConsent(): Boolean

    suspend fun unregisterPush(): Boolean
}

class DefaultFirebaseProofController
@Inject
constructor(
    private val remoteFeatureFlags: RemoteFeatureFlags,
    private val pushRegistrationCoordinator: PushRegistrationCoordinator,
    private val proofTargetRecorder: FirebaseProofTargetRecorder
) : FirebaseProofController {
    override fun hasStoredPushConsent(): Boolean = pushRegistrationCoordinator.hasStoredConsent()

    override suspend fun refreshRemoteConfig(): RemoteConfigResult = remoteFeatureFlags.refresh()

    override suspend fun registerPushAfterConsent(): Boolean =
        when (val result = pushRegistrationCoordinator.registerAfterConsent()) {
            is PushRegistrationResult.Registered -> {
                proofTargetRecorder.record(result.target)
                true
            }

            PushRegistrationResult.Failed -> false
        }

    override suspend fun unregisterPush(): Boolean {
        val unregistered = pushRegistrationCoordinator.unregister()
        if (unregistered) proofTargetRecorder.clear()
        return unregistered
    }
}
