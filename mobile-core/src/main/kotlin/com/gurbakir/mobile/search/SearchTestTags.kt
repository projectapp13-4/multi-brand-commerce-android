package com.gurbakir.mobile.search

object SearchTestTags {
    const val ROOT = "search-root"
    const val GRID = "search-grid"
    const val INPUT = "search-input"
    const val SUBMIT = "search-submit"
    const val TOO_SHORT = "search-too-short"
    const val LOADING = "search-loading"
    const val EMPTY = "search-empty"
    const val ERROR = "search-error"
    const val RESULT_COUNT = "search-result-count"
    const val LOAD_MORE = "search-load-more"
    const val NEXT_ERROR = "search-next-error"
    const val HISTORY = "search-history"
    const val HISTORY_TOGGLE = "search-history-toggle"
    const val HISTORY_SETTINGS = "search-history-settings"
    const val HISTORY_SETTINGS_PANEL = "search-history-settings-panel"
    const val HISTORY_CLEAR = "search-history-clear"
    const val HISTORY_UNAVAILABLE = "search-history-unavailable"

    fun historyItem(normalizedQuery: String): String = "search-history-item-${normalizedQuery.hashCode()}"

    fun historyRemove(normalizedQuery: String): String = "search-history-remove-${normalizedQuery.hashCode()}"
}
