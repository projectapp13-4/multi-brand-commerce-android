package com.gurbakir.firebase

import android.content.Context
import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseApp
import com.google.firebase.installations.FirebaseInstallations
import com.google.firebase.messaging.FirebaseMessaging
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

class ControlledPushTarget private constructor(private val rawValue: String) {
    fun <T> use(block: (String) -> T): T = block(rawValue)

    override fun toString(): String = "<redacted>"

    companion object {
        internal fun from(rawValue: String): ControlledPushTarget? =
            rawValue.takeIf(String::isNotBlank)?.let(::ControlledPushTarget)
    }
}

sealed interface PushRegistrationResult {
    data class Registered(val target: ControlledPushTarget) : PushRegistrationResult {
        override fun toString(): String = "Registered(target=<redacted>)"
    }

    data object Failed : PushRegistrationResult
}

interface PushRegistrationCoordinator {
    fun hasStoredConsent(): Boolean

    suspend fun registerAfterConsent(): PushRegistrationResult

    suspend fun unregister(): Boolean
}

private class FirebasePushRegistrationCoordinator(
    private val consentStore: PushConsentStore,
    private val messaging: FirebaseMessaging = FirebaseMessaging.getInstance(),
    private val installations: FirebaseInstallations = FirebaseInstallations.getInstance()
) : PushRegistrationCoordinator {
    override fun hasStoredConsent(): Boolean = consentStore.read()

    override suspend fun registerAfterConsent(): PushRegistrationResult = runCatching {
        check(!messaging.isAutoInitEnabled)
        messaging.register().awaitProofCompletion()
        val target = installations.id.awaitProofValue()
        val controlledTarget = ControlledPushTarget.from(target)
        val consentStored = controlledTarget != null && consentStore.write(enabled = true)
        if (!consentStored) messaging.unregister().awaitProofCompletion()
        check(consentStored)
        PushRegistrationResult.Registered(checkNotNull(controlledTarget))
    }.getOrDefault(PushRegistrationResult.Failed)

    override suspend fun unregister(): Boolean = runCatching {
        messaging.setAutoInitEnabled(false)
        messaging.unregister().awaitProofCompletion()
        consentStore.write(enabled = false)
    }.getOrDefault(false)
}

private interface PushConsentStore {
    fun read(): Boolean

    fun write(enabled: Boolean): Boolean
}

private class AndroidPushConsentStore(context: Context) : PushConsentStore {
    private val preferences =
        context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    override fun read(): Boolean = preferences.getBoolean(CONSENT_KEY, false)

    override fun write(enabled: Boolean): Boolean = preferences.edit().putBoolean(CONSENT_KEY, enabled).commit()

    private companion object {
        const val PREFERENCES_NAME = "firebase-push-consent"
        const val CONSENT_KEY = "registered_after_explicit_consent"
    }
}

fun createFirebasePushRegistrationCoordinator(context: Context): PushRegistrationCoordinator =
    FirebasePushRegistrationCoordinator(AndroidPushConsentStore(context))

data class FirebaseRuntimeStatus(
    val defaultAppInitialized: Boolean,
    val singleFirebaseApp: Boolean,
    val applicationIdConfigured: Boolean,
    val projectIdConfigured: Boolean,
    val messagingAutoInitEnabled: Boolean
) {
    override fun toString(): String =
        "FirebaseRuntimeStatus(initialized=$defaultAppInitialized, singleApp=$singleFirebaseApp, " +
            "configured=${applicationIdConfigured && projectIdConfigured}, " +
            "messagingAutoInit=$messagingAutoInitEnabled)"
}

class FirebaseRuntimeInspector(context: Context) {
    private val applicationContext = context.applicationContext

    fun inspect(): FirebaseRuntimeStatus = runCatching {
        val apps = FirebaseApp.getApps(applicationContext)
        val defaultApp = FirebaseApp.getInstance()
        FirebaseRuntimeStatus(
            defaultAppInitialized = true,
            singleFirebaseApp = apps.size == 1 && apps.single().name == FirebaseApp.DEFAULT_APP_NAME,
            applicationIdConfigured = defaultApp.options.applicationId.isNotBlank(),
            projectIdConfigured = !defaultApp.options.projectId.isNullOrBlank(),
            messagingAutoInitEnabled = FirebaseMessaging.getInstance().isAutoInitEnabled
        )
    }.getOrElse {
        FirebaseRuntimeStatus(
            defaultAppInitialized = false,
            singleFirebaseApp = false,
            applicationIdConfigured = false,
            projectIdConfigured = false,
            messagingAutoInitEnabled = false
        )
    }
}

private suspend fun Task<*>.awaitProofCompletion(): Unit = suspendCancellableCoroutine { continuation ->
    addOnCompleteListener { task ->
        if (!continuation.isActive) return@addOnCompleteListener
        val failure = task.exception
        if (task.isSuccessful) {
            continuation.resume(Unit)
        } else {
            continuation.resumeWithException(failure ?: IllegalStateException("Firebase task failed"))
        }
    }
}

private suspend fun <T> Task<T>.awaitProofValue(): T = suspendCancellableCoroutine { continuation ->
    addOnCompleteListener { task ->
        if (!continuation.isActive) return@addOnCompleteListener
        val failure = task.exception
        if (task.isSuccessful) {
            continuation.resume(task.result)
        } else {
            continuation.resumeWithException(failure ?: IllegalStateException("Firebase task failed"))
        }
    }
}
