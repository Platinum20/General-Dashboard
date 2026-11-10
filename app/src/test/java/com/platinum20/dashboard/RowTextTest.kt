package com.platinum20.dashboard

import com.platinum20.dashboard.calendar.CalendarEvent
import com.platinum20.dashboard.notes.Repeat
import com.platinum20.dashboard.notes.TaskItem
import com.platinum20.dashboard.ui.screens.RowText
import com.platinum20.dashboard.ui.screens.tripStops
import com.platinum20.dashboard.widget.AgendaRows
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/** App e widget leggono le righe con le stesse parole (punto 15). */
class RowTextTest {
    private val zone = ZoneId.of("Europe/Rome")
    private val today = LocalDate.of(2026, 11, 10)
    private val start = today.atTime(14, 0).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun turnoUgualeInAppEWidget() {
        val shift = CalendarEvent(-4, "Turno · Debora, Chiara", start, start + 5 * 3_600_000L, false, "", 0)
        assertEquals("Turno su Debora e Chiara", RowText.event(shift))
        assertEquals(RowText.event(shift), tripStops(shift, today, zone)!!.from)
    }

    @Test
    fun dataSuUnaRigaColleEta() {
        val marta = TaskItem(1, "Compleanno di Marta", createdAt = 0, dueAt = today.plusDays(5).atStartOfDay(zone).toInstant().toEpochMilli(),
            allDay = true, repeat = Repeat.YEARLY, firstYear = 1992)
        assertEquals("Compleanno di Marta · 34 anni", RowText.dateOneLine(marta, today.plusDays(5), zone))
        assertEquals("Domani", AgendaRows.dayLabel(today.plusDays(1), today))
    }
}
