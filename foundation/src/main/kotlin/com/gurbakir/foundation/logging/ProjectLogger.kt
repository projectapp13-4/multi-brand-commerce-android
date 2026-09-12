package com.gurbakir.foundation.logging

import android.util.Log

private const val MAXIMUM_ATTRIBUTE_LENGTH = 256

@JvmInline
value class LogEvent private constructor(val value: String) {
    companion object {
        private val validPattern = Regex("^[a-z0-9_.-]{3,80}$")

        fun of(value: String): LogEvent {
            require(value.matches(validPattern)) { "Log events must be stable low-cardinality identifiers." }
            return LogEvent(value)
        }
    }
}

enum class SafeAttribute {
    CORRELATION_ID,
    ENVIRONMENT,
    FAILURE_TYPE,
    HTTP_STATUS,
    OPERATION,
    RESULT
}

interface ProjectLogger {
    fun info(event: LogEvent, attributes: Map<SafeAttribute, String> = emptyMap())

    fun warn(event: LogEvent, attributes: Map<SafeAttribute, String> = emptyMap())
}

class AndroidProjectLogger(
    private val tag: String = "Gurbakir",
    private val redactionPolicy: RedactionPolicy = RedactionPolicy()
) : ProjectLogger {
    override fun info(event: LogEvent, attributes: Map<SafeAttribute, String>) {
        Log.i(tag, format(event, attributes))
    }

    override fun warn(event: LogEvent, attributes: Map<SafeAttribute, String>) {
        Log.w(tag, format(event, attributes))
    }

    private fun format(event: LogEvent, attributes: Map<SafeAttribute, String>): String = buildString {
        append(event.value)
        attributes.toSortedMap(compareBy { it.name }).forEach { (key, value) ->
            append(' ')
            append(key.name.lowercase())
            append('=')
            append(redactionPolicy.redact(value.sanitizeForSingleLogLine()))
        }
    }
}

class RedactionPolicy {
    private val sensitivePatterns =
        listOf(
            Regex("(?i)\\bBearer\\s+[A-Za-z0-9._~+/=-]+"),
            Regex("(?i)\\b(shpat|shpca|shpss)_[A-Za-z0-9_]+"),
            Regex("\\bAIza[0-9A-Za-z_-]{35}\\b"),
            Regex("\\beyJ[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]{8,}\\b"),
            Regex("(?i)(access_token|refresh_token|id_token|code|state|client_secret|password)=([^&\\s]+)"),
            Regex("(?i)\\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}\\b")
        )

    fun redact(value: String): String = sensitivePatterns.fold(value) { redacted, pattern ->
        pattern.replace(redacted) { match ->
            val key = if (match.groups.size > 1) match.groups[1]?.value else null
            if (key == null) "<redacted>" else "$key=<redacted>"
        }
    }
}

private fun String.sanitizeForSingleLogLine(): String = take(MAXIMUM_ATTRIBUTE_LENGTH).map { character ->
    if (character.isISOControl()) ' ' else character
}.joinToString(separator = "")
