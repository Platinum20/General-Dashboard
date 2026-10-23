package com.platinum20.dashboard

import com.platinum20.dashboard.calendar.CalendarEvent
import com.platinum20.dashboard.news.NewsItem
import com.platinum20.dashboard.news.NewsSource
import com.platinum20.dashboard.search.EverywhereSearch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class EverywhereSearchTest {
    private val today = LocalDate.of(2026, 10, 23)
    private fun ev(id: Long, title: String, begin: Long, location: String = "") =
        CalendarEvent(id, title, begin, begin + 3_600_000, allDay = false, location = location, color = 0)

    @Test
    fun impegniPrimaIProssimiPoiIPassati() {
        val byDay = mapOf(
            today.minusDays(3) to listOf(ev(1, "Cena con Lùca", 100)),
            today.plusDays(5) to listOf(ev(2, "Calcetto", 500, location = "Campo di Luca")),
            today.plusDays(1) to listOf(ev(3, "Pranzo Luca", 300)),
            today.plusDays(2) to listOf(ev(4, "Dentista", 400)),
        )
        assertEquals(listOf(3L, 2L, 1L), EverywhereSearch.events(byDay, "luca", today).map { it.second.eventId })
        assertTrue(EverywhereSearch.events(byDay, " ", today).isEmpty())
    }

    @Test
    fun eventoDiPiuGiorniUnaVolta() {
        val trip = ev(9, "Viaggio a Lisbona", 100)
        val byDay = mapOf(today to listOf(trip), today.plusDays(1) to listOf(trip))
        assertEquals(1, EverywhereSearch.events(byDay, "lisbona", today).size)
    }

    @Test
    fun notizieRecentiPrima() {
        val src = NewsSource("ansa", "ANSA", "")
        fun n(t: String, s: String, at: Long) = NewsItem(t, "https://x/$t", s, null, Instant.ofEpochSecond(at), src)
        val found = EverywhereSearch.news(
            listOf(n("Sciopero dei treni", "", 1), n("Meteo", "Allerta per lo sciopero", 5), n("Calcio", "", 9)),
            "SCIOPERO",
        )
        assertEquals(listOf("Meteo", "Sciopero dei treni"), found.map { it.title })
    }
}
