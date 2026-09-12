package com.gurbakir.mobile.home

import javax.inject.Inject
import kotlinx.coroutines.delay

class HomeLoadingClock
@Inject
constructor() {
    suspend fun awaitSlowLoading() {
        delay(SLOW_LOADING_DELAY_MILLIS)
    }

    private companion object {
        const val SLOW_LOADING_DELAY_MILLIS = 8_000L
    }
}
