package com.platinum20.dashboard

import com.platinum20.dashboard.notes.TaskItem
import com.platinum20.dashboard.widget.DashboardWidget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

/** La riga dei promemoria nel widget della schermata Home. */
class WidgetLinesTest {
    private val zone = ZoneId.of("Europe/Rome")
    private fun ms(h: Int, m: Int = 0, day: Int = 23) = LocalDateTime.of(2026, 10, day, h, m).atZone(zone).toInstant().toEpochMilli()
    private val now = ms(12)
    private fun task(id: Long, text: String, due: Long?) = TaskItem(id, text, createdAt = 0, dueAt = due)

    @Test fun senzaPromemoriaDiOggiNessunaRiga() {
        assertNull(DashboardWidget.tasksLine(emptyList(), now, zone))
        // Quelli di domani o senza scadenza non contano.
        assertNull(DashboardWidget.tasksLine(listOf(task(1, "Spesa", null), task(2, "Dentista", ms(9, day = 24))), now, zone))
    }

    @Test fun ilProssimoConLOra() {
        val line = DashboardWidget.tasksLine(listOf(task(1, "Palestra", ms(18, 30)), task(2, "Chiamare Marco", ms(15))), now, zone)
        assertEquals("📌 2 promemoria oggi · Chiamare Marco alle 15:00", line)
    }

    @Test fun scadutiContati() {
        val one = DashboardWidget.tasksLine(listOf(task(1, "Bollette", ms(9))), now, zone)
        assertEquals("📌 1 promemoria scaduto · Bollette alle 09:00", one)
        val mixed = DashboardWidget.tasksLine(listOf(task(1, "Bollette", ms(9)), task(2, "Palestra", ms(18))), now, zone)
        assertEquals("📌 2 promemoria oggi (1 scaduto) · Palestra alle 18:00", mixed)
        // Uno scaduto ieri: si conta, ma senza "alle" perché non è di oggi.
        val old = DashboardWidget.tasksLine(listOf(task(1, "Pacco", ms(10, day = 22)), task(2, "Pane", ms(8))), now, zone)
        assertEquals("📌 2 promemoria scaduti · Pacco", old)
    }
}
