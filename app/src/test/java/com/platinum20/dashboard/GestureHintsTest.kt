package com.platinum20.dashboard

import com.platinum20.dashboard.ui.screens.GestureHints
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GestureHintsTest {
    @Test fun ilSuggerimentoCompareTreVolte() {
        assertTrue(GestureHints.shouldShow(count = 0, legacySeen = false))
        assertTrue(GestureHints.shouldShow(count = 2, legacySeen = false))
        assertFalse(GestureHints.shouldShow(count = 3, legacySeen = false))
    }

    @Test fun chiLoAvevaGiaVistoNonLoRivede() = assertFalse(GestureHints.shouldShow(count = 0, legacySeen = true))
}
