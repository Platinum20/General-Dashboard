package com.platinum20.dashboard

import com.platinum20.dashboard.ui.screens.PressRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PressRuleTest {
    @Test
    fun scrollCancelsThePress() {
        assertEquals(PressRule.Outcome.CANCEL, PressRule.outcome(lifted = false, moved = true, taken = false))
        assertEquals(PressRule.Outcome.CANCEL, PressRule.outcome(lifted = false, moved = false, taken = true))
    }

    @Test
    fun liftedIsATapAndStillIsHold() {
        assertEquals(PressRule.Outcome.UP, PressRule.outcome(lifted = true, moved = false, taken = false))
        assertEquals(PressRule.Outcome.HOLD, PressRule.outcome(lifted = false, moved = false, taken = false))
    }

    @Test
    fun onlyTheInnermostTakesTheFinger() {
        assertTrue(PressRule.claim(9_001L))
        assertFalse(PressRule.claim(9_001L))
        assertTrue(PressRule.claim(9_002L))
    }
}

class RowFlashTest {
    @Test
    fun aRowLightsUpOnlyRightAfterItChanged() {
        val at = 10_000L
        assertTrue(com.platinum20.dashboard.ui.screens.RowFlash.isFresh(at, at + 100))
        assertTrue(com.platinum20.dashboard.ui.screens.RowFlash.isFresh(at, at + 3_000))
        assertFalse(com.platinum20.dashboard.ui.screens.RowFlash.isFresh(at, at + 3_001))
        assertFalse(com.platinum20.dashboard.ui.screens.RowFlash.isFresh(null, at))
    }
}

class FlingMotionTest {
    @Test
    fun aFlingLeavesAtTheFingersPace() {
        val m = com.platinum20.dashboard.ui.screens.Motion
        // 600 px a 3000 px/s = 200 ms.
        assertEquals(200, m.flingMs(600f, 3000f))
        // Lancio velocissimo: mai sotto il minimo; lento o fermo: mai oltre il massimo.
        assertEquals(m.FLING_MIN_MS, m.flingMs(600f, 50_000f))
        assertEquals(m.FLING_MAX_MS, m.flingMs(600f, 100f))
        assertEquals(m.FLING_MAX_MS, m.flingMs(600f, 0f))
        assertEquals(200, m.flingMs(-600f, -3000f))
    }
}

class NewRowsFlashTest {
    private val flash = com.platinum20.dashboard.ui.screens.RowFlash

    @Test
    fun onlyRowsAddedAfterTheFirstListLightUp() {
        assertEquals(emptyList<String>(), flash.newKeys(null, listOf("task-1", "task-2")))
        assertEquals(listOf("task-3"), flash.newKeys(setOf("task-1", "task-2"), listOf("task-1", "task-3", "task-2")))
    }

    @Test
    fun aWholeNewChunkOfCalendarDoesNotFlash() {
        val many = (1..10).map { "event-$it" }
        assertEquals(emptyList<String>(), flash.newKeys(setOf("task-1"), listOf("task-1") + many))
    }
}
