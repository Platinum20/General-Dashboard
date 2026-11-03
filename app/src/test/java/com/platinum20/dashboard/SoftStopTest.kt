package com.platinum20.dashboard

import com.platinum20.dashboard.ui.screens.softStopAtNow
import com.platinum20.dashboard.ui.screens.softStopMs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SoftStopTest {
    @Test
    fun aFlingThatWouldPassNowBrakesOnIt() {
        // OGGI è 300 px sopra il bordo, il lancio andrebbe 900 px: si frena di 300 px.
        assertEquals(300f, softStopAtNow(-300f, 2000f, 900f, unlocked = false))
    }

    @Test
    fun shortFlingsFutureFlingsAndUnlockedPastAreLeftAlone() {
        assertNull(softStopAtNow(-300f, 2000f, 200f, unlocked = false))
        assertNull(softStopAtNow(-300f, -2000f, 900f, unlocked = false))
        assertNull(softStopAtNow(-300f, 2000f, 900f, unlocked = true))
        assertNull(softStopAtNow(null, 2000f, 900f, unlocked = false))
    }

    @Test
    fun theBrakeLastsLikeAConstantDeceleration() {
        assertEquals(300, softStopMs(300f, 2000f))
        assertEquals(180, softStopMs(10f, 9000f))
        assertEquals(600, softStopMs(3000f, 1000f))
    }
}
