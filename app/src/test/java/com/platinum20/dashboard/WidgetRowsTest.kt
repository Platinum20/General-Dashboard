package com.platinum20.dashboard

import com.platinum20.dashboard.notes.TaskItem
import com.platinum20.dashboard.widget.WidgetRows
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WidgetRowsTest {
    private val zone = ZoneId.of("Europe/Rome")
    private val now = LocalDateTime.of(2026, 10, 23, 10, 0).atZone(zone).toInstant()
    private fun at(day: Int, hour: Int, minute: Int = 0) =
        LocalDateTime.of(2026, 10, day, hour, minute).atZone(zone).toInstant().toEpochMilli()
    private fun task(id: Long, text: String, dueAt: Long?) = TaskItem(id = id, text = text, dueAt = dueAt)

    @Test
    fun senzaPromemoriaDiOggiNessunaRiga() {
        assertNull(WidgetRows.tasksLine(emptyList(), now, zone))
        assertNull(WidgetRows.tasksLine(listOf(task(1, "Senza data", null), task(2, "Domani", at(24, 9))), now, zone))
    }

    @Test
    fun unoOggi() {
        assertEquals(
            "📌 1 promemoria oggi · Chiamare il dentista alle 15:30",
            WidgetRows.tasksLine(listOf(task(1, "Chiamare il dentista", at(23, 15, 30))), now, zone),
        )
    }

    @Test
    fun scadutiEDiOggiMostraIlProssimo() {
        val tasks = listOf(
            task(1, "Pagare bolletta", at(22, 18)),
            task(2, "Ritirare pacco", at(23, 17)),
            task(3, "Palestra", at(23, 12)),
        )
        assertEquals("📌 3 promemoria oggi (1 scaduto) · Palestra alle 12:00", WidgetRows.tasksLine(tasks, now, zone))
    }

    @Test
    fun tuttiScaduti() {
        val tasks = listOf(task(1, "Pagare bolletta", at(22, 18)), task(2, "Mail", at(23, 8)))
        // Quello di ieri non ha l'orario: "alle 18:00" farebbe pensare a stasera.
        assertEquals("📌 2 promemoria scaduti · Pagare bolletta", WidgetRows.tasksLine(tasks, now, zone))
        assertEquals("📌 1 promemoria scaduto · Mail alle 08:00", WidgetRows.tasksLine(listOf(tasks[1]), now, zone))
    }

    @Test
    fun meteo() {
        assertEquals("Sereno · 22°/10°", WidgetRows.weatherLine("Sereno", "22°", "10°", 10))
        assertEquals("Rovesci · 18°/9° · pioggia 60%", WidgetRows.weatherLine("Rovesci", "18°", "9°", 60))
        assertEquals("Nuvoloso", WidgetRows.weatherLine("Nuvoloso", null, null, null))
    }

    @Test
    fun quanteNotizie() {
        assertEquals(3, WidgetRows.newsCount(0, hasTasks = false))
        assertEquals(2, WidgetRows.newsCount(1, hasTasks = false))
        assertEquals(2, WidgetRows.newsCount(0, hasTasks = true))
    }
}
