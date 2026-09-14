package com.gurbakir.mobile.home

import javax.inject.Inject
import kotlinx.coroutines.delay

enum class HomeEditorialFreshness {
    FRESH,
    EXPIRED,
    CLOCK_INVALID
}

object HomeEditorialClockPolicy {
    private const val MAX_BACKWARD_CLOCK_SKEW_MILLIS = 5L * 60L * 1_000L

    fun deadline(acceptedAtMillis: Long, ttlMillis: Long): Long? {
        if (acceptedAtMillis < 0L || ttlMillis <= 0L) return null
        return runCatching { Math.addExact(acceptedAtMillis, ttlMillis) }.getOrNull()
    }

    fun freshness(acceptedAtMillis: Long, expiresAtMillis: Long, nowMillis: Long): HomeEditorialFreshness = when {
        acceptedAtMillis < 0L || nowMillis < 0L || expiresAtMillis <= acceptedAtMillis ->
            HomeEditorialFreshness.CLOCK_INVALID

        nowMillis < acceptedAtMillis &&
            acceptedAtMillis - nowMillis > MAX_BACKWARD_CLOCK_SKEW_MILLIS ->
            HomeEditorialFreshness.CLOCK_INVALID

        maxOf(nowMillis, acceptedAtMillis) >= expiresAtMillis -> HomeEditorialFreshness.EXPIRED

        else -> HomeEditorialFreshness.FRESH
    }
}

interface HomeEditorialClock {
    fun nowMillis(): Long

    suspend fun awaitUntil(deadlineMillis: Long)
}

class SystemHomeEditorialClock @Inject constructor() : HomeEditorialClock {
    override fun nowMillis(): Long = System.currentTimeMillis()

    override suspend fun awaitUntil(deadlineMillis: Long) {
        val remaining = deadlineMillis - nowMillis()
        if (remaining > 0L) delay(remaining)
    }
}
