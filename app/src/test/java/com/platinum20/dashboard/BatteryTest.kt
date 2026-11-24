package com.platinum20.dashboard

import com.platinum20.dashboard.work.ShiftWorker
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** I giri in sottofondo che aprono pagine nascoste non devono passare troppo spesso (batteria). */
class BatteryTest {
    private val minute = 60_000L

    @Test
    fun shiftsAreReadEveryTwoHours() {
        val now = 10_000 * minute
        assertFalse(ShiftWorker.due(readAt = now - 15 * minute, now = now))
        assertFalse(ShiftWorker.due(readAt = now - 60 * minute, now = now))
        assertTrue(ShiftWorker.due(readAt = now - 120 * minute, now = now))
        assertTrue(ShiftWorker.due(readAt = 0, now = now))
    }

    /** Sotto le 2 ore, sennò con il controllo ogni 15 minuti diventerebbero 2 ore e un quarto. */
    @Test
    fun shiftReadStaysUnderTwoHours() {
        assertTrue(ShiftWorker.READ_EVERY_MS < 120 * minute)
        assertTrue(ShiftWorker.READ_EVERY_MS > 90 * minute)
    }
}
