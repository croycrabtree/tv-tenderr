package com.movieswipe

fun historyUndoLabel(action: String): String? = when (action) {
    "keep", "super_keep" -> "Un-keep"
    "block" -> "Restore"
    "clean" -> "Re-monitor"
    "added" -> "Remove"
    "hidden" -> "Show Again"
    else -> null
}
