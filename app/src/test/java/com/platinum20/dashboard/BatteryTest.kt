package com.platinum20.dashboard

import com.platinum20.dashboard.work.ShiftWorker
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** I giri in sottofondo che aprono pagine nascoste non devono passare troppo spesso (batteria). */
class BatteryTest {
    private val minute = 60_000L

    @Test
    fun shiftsAreReadAtMostHourly() {
        val now = 10_000 * minute
        assertFalse(ShiftWorker.due(readAt = now - 15 * minute, now = now))
        assertFalse(ShiftWorker.due(readAt = now - 30 * minute, now = now))
        assertTrue(ShiftWorker.due(readAt = now - 60 * minute, now = now))
        assertTrue(ShiftWorker.due(readAt = 0, now = now))
    }

    /** Le letture restano sotto l'ora, sennò con il giro ogni 15 minuti diventerebbero ogni ora e un quarto. */
    @Test
    fun shiftReadStaysUnderAnHour() {
        assertTrue(ShiftWorker.READ_EVERY_MS < 60 * minute)
        assertTrue(ShiftWorker.READ_EVERY_MS > 30 * minute)
    }
}
