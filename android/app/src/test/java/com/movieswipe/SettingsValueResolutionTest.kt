package com.movieswipe

import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsValueResolutionTest {
    @Test
    fun backendValuesReplaceFirstRunDefaultsButNotRealLocalEdits() {
        assertEquals(
            "http://radarr.test",
            preferBackendSetting("http://localhost:7878", "http://radarr.test", "http://localhost:7878"),
        )
        assertEquals(
            "real-key",
            preferBackendSetting("YOUR_RADARR_API_KEY", "real-key", "YOUR_RADARR_API_KEY"),
        )
        assertEquals(
            "http://custom-radarr",
            preferBackendSetting("http://custom-radarr", "http://radarr.test", "http://localhost:7878"),
        )
    }

    @Test
    fun settingsSaveMessageDoesNotReportRejectedRequestsAsSaved() {
        assertEquals("Settings saved", settingsSaveMessage(true))
        assertEquals("Saved locally (backend rejected settings)", settingsSaveMessage(false))
    }

    @Test
    fun placeholderSettingsAreNotPushedToANewBackend() {
        assertEquals(null, configuredSetting("YOUR_RADARR_API_KEY", "YOUR_RADARR_API_KEY"))
        assertEquals(null, configuredSetting("http://localhost:7878", "http://localhost:7878"))
        assertEquals("real-key", configuredSetting("real-key", "YOUR_RADARR_API_KEY"))
    }
}
