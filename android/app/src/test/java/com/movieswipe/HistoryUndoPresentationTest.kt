package com.movieswipe

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HistoryUndoPresentationTest {
    @Test
    fun everyActionExposesTheExpectedUndoLabel() {
        assertEquals("Un-keep", historyUndoLabel("keep"))
        assertEquals("Un-keep", historyUndoLabel("super_keep"))
        assertEquals("Restore", historyUndoLabel("block"))
        assertEquals("Re-monitor", historyUndoLabel("clean"))
        assertEquals("Remove", historyUndoLabel("added"))
        assertEquals("Show Again", historyUndoLabel("hidden"))
        assertNull(historyUndoLabel("skip"))
    }
}
