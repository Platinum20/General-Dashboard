package com.platinum20.dashboard

import com.platinum20.dashboard.ui.screens.DueDay
import com.platinum20.dashboard.ui.screens.DueRow
import com.platinum20.dashboard.ui.screens.withoutEmptyToday
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

/** Card Agenda in Home (Antonio, 2026-11-26): se oggi non ha niente, niente OGGI; si parte dal primo giorno con qualcosa. */
class AgendaOggiVuotoTest {
    private val today = LocalDate.of(2026, 11, 26)
    private val header = DueRow("today", today, Long.MIN_VALUE, today = true)
    private val tomorrow = DueDay(today.plusDays(1), false, listOf(DueRow("x", today.plusDays(1), 0L)))

    @Test fun oggiVuotoSparisce() {
        val groups = listOf(DueDay(today, false, listOf(header)), tomorrow)
        assertEquals(listOf(tomorrow), groups.withoutEmptyToday(today))
    }

    @Test fun oggiConQualcosaResta() {
        val oggi = DueDay(today, false, listOf(header, DueRow("y", today, 1L)))
        assertEquals(listOf(oggi, tomorrow), listOf(oggi, tomorrow).withoutEmptyToday(today))
    }
}
