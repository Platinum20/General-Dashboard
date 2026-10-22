package com.platinum20.dashboard

import com.platinum20.dashboard.notes.Habit
import com.platinum20.dashboard.notes.Repeat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class HabitTest {
    private val today = LocalDate.of(2026, 10, 22)

    @Test fun serieFinoAOggi() =
        assertEquals(3, Habit(1, "Corsa", setOf(today, today.minusDays(1), today.minusDays(2), today.minusDays(4))).streak(today))

    @Test fun oggiNonAncoraFattoTieneLaSerieDiIeri() =
        assertEquals(2, Habit(1, "Corsa", setOf(today.minusDays(1), today.minusDays(2))).streak(today))

    @Test fun unBucoAzzeraLaSerie() = assertEquals(0, Habit(1, "Corsa", setOf(today.minusDays(2))).streak(today))

    @Test fun fattoOggi() {
        val h = Habit(1, "Corsa", setOf(today))
        assertTrue(h.isDone(today))
        assertFalse(h.isDone(today.minusDays(1)))
    }

    @Test fun ultimiSetteGiorniDalPiuVecchio() {
        val days = Habit(1, "Corsa", setOf(today, today.minusDays(6))).lastDays(today)
        assertEquals(7, days.size)
        assertEquals(today.minusDays(6) to true, days.first())
        assertEquals(today to true, days.last())
        assertEquals(2, days.count { it.second })
    }
}

class RepeatTest {
    @Test fun ogniMeseDal31VaAFineFebbraio() =
        assertEquals(LocalDateTime.of(2027, 2, 28, 9, 0), Repeat.MONTHLY.next(LocalDateTime.of(2027, 1, 31, 9, 0)))

    @Test fun ogniAnnoDal29Febbraio() =
        assertEquals(LocalDateTime.of(2029, 2, 28, 10, 0), Repeat.YEARLY.next(LocalDateTime.of(2028, 2, 29, 10, 0)))

    @Test fun ogniSettimana() =
        assertEquals(LocalDateTime.of(2026, 10, 29, 18, 0), Repeat.WEEKLY.next(LocalDateTime.of(2026, 10, 22, 18, 0)))

    @Test fun primaVoltaDopoAdessoSaltaQuellePassate() {
        val from = LocalDateTime.of(2026, 10, 1, 8, 0)
        assertEquals(LocalDateTime.of(2026, 10, 23, 8, 0), Repeat.DAILY.nextAfter(from, LocalDateTime.of(2026, 10, 22, 9, 0)))
    }
}
