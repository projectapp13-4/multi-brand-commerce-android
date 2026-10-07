package com.gurbakir.mobile.address

import android.content.ContentResolver
import android.content.Context
import android.os.ParcelFileDescriptor
import android.os.UserManager
import android.provider.Settings
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

internal class AddressVisibleImeEnvironmentRule : TestRule {
    override fun apply(base: Statement, description: Description): Statement = object : Statement() {
        override fun evaluate() {
            withVisibleIme(base, description)
        }
    }

    private fun withVisibleIme(base: Statement, description: Description) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val users = context.getSystemService(Context.USER_SERVICE) as UserManager
        check(users.isSystemUser) { "The visible-IME fixture requires the managed device's system user" }
        val resolver = context.contentResolver
        val original = snapshot(resolver)
        check(original.showWithHardwareKeyboard == null || INTEGER.matches(original.showWithHardwareKeyboard)) {
            "The original visible-IME flag must be absent or an integer before changing it"
        }
        var primaryFailure: Throwable? = null
        try {
            shell("settings --user 0 put secure $SHOW_WITH_HARDWARE_KEYBOARD 1")
            check(Settings.Secure.getString(resolver, SHOW_WITH_HARDWARE_KEYBOARD) == "1") {
                "The visible-IME environment flag was not confirmed"
            }
            checkOtherSettings(resolver, original)
            Log.i(
                LOG_TAG,
                "case=${description.methodName} previousFlag=${original.showWithHardwareKeyboard ?: "absent"} " +
                    "enabledConfirmed=true defaultImeUnchanged=true fontScaleUnchanged=true"
            )
            base.evaluate()
        } catch (failure: Throwable) {
            primaryFailure = failure
            throw failure
        } finally {
            restore(resolver, original, primaryFailure)
        }
    }

    private fun restore(resolver: ContentResolver, original: ImeEnvironment, primaryFailure: Throwable?) {
        val cleanupFailures = mutableListOf<Throwable>()
        collectFailure(cleanupFailures) {
            val previous = original.showWithHardwareKeyboard
            val command = if (previous == null) {
                "settings --user 0 delete secure $SHOW_WITH_HARDWARE_KEYBOARD"
            } else {
                "settings --user 0 put secure $SHOW_WITH_HARDWARE_KEYBOARD $previous"
            }
            shell(command)
        }
        collectFailure(cleanupFailures) {
            check(
                Settings.Secure.getString(resolver, SHOW_WITH_HARDWARE_KEYBOARD) == original.showWithHardwareKeyboard
            ) {
                "The original visible-IME flag's value and presence were not restored"
            }
        }
        collectFailure(cleanupFailures) {
            check(Settings.Secure.getString(resolver, Settings.Secure.DEFAULT_INPUT_METHOD) == original.defaultIme) {
                "The selected default IME changed during the visible-IME fixture"
            }
        }
        collectFailure(cleanupFailures) {
            check(Settings.System.getString(resolver, Settings.System.FONT_SCALE) == original.fontScale) {
                "The device font scale changed during the visible-IME fixture"
            }
        }
        val cleanupFailure = cleanupFailures.firstOrNull()
        if (cleanupFailure == null) {
            Log.i(LOG_TAG, "restoredConfirmed=true defaultImeUnchanged=true fontScaleUnchanged=true")
            return
        }
        val preservedFailure = primaryFailure ?: cleanupFailure
        cleanupFailures.filter { it !== preservedFailure }.forEach(preservedFailure::addSuppressed)
        if (primaryFailure == null) throw cleanupFailure
    }

    private fun checkOtherSettings(resolver: ContentResolver, original: ImeEnvironment) {
        check(Settings.Secure.getString(resolver, Settings.Secure.DEFAULT_INPUT_METHOD) == original.defaultIme) {
            "The visible-IME setup changed the selected default IME"
        }
        check(Settings.System.getString(resolver, Settings.System.FONT_SCALE) == original.fontScale) {
            "The visible-IME setup changed the device font scale"
        }
    }

    private fun snapshot(resolver: ContentResolver): ImeEnvironment = ImeEnvironment(
        showWithHardwareKeyboard = Settings.Secure.getString(resolver, SHOW_WITH_HARDWARE_KEYBOARD),
        defaultIme = Settings.Secure.getString(resolver, Settings.Secure.DEFAULT_INPUT_METHOD),
        fontScale = Settings.System.getString(resolver, Settings.System.FONT_SCALE)
    )

    private fun shell(command: String) {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(command)).bufferedReader().use {
            it.readText()
        }
    }

    private fun collectFailure(failures: MutableList<Throwable>, action: () -> Unit) {
        try {
            action()
        } catch (failure: Throwable) {
            failures += failure
        }
    }

    private companion object {
        const val SHOW_WITH_HARDWARE_KEYBOARD = "show_ime_with_hard_keyboard"
        const val LOG_TAG = "W4_IME_ENV"
        val INTEGER = Regex("-?[0-9]+")
    }
}

private data class ImeEnvironment(
    val showWithHardwareKeyboard: String?,
    val defaultIme: String?,
    val fontScale: String?
)
