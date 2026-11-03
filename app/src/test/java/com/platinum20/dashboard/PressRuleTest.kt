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
