package com.movieswipe

fun preferBackendSetting(localValue: String, backendValue: String, defaultValue: String): String =
    if (localValue.isBlank() || localValue == defaultValue) backendValue else localValue

fun settingsSaveMessage(responseSuccessful: Boolean): String =
    if (responseSuccessful) "Settings saved" else "Saved locally (backend rejected settings)"
