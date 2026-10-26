package com.platinum20.dashboard

import android.provider.CalendarContract
import com.platinum20.dashboard.calendar.CalendarDays
import com.platinum20.dashboard.calendar.CalendarEvent
import com.platinum20.dashboard.calendar.EventDetail
import com.platinum20.dashboard.calendar.EventDraft
import com.platinum20.dashboard.ui.screens.agendaDayTitle
import com.platinum20.dashboard.ui.screens.eventWhen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

class CalendarDaysTest {
    private val zone = ZoneId.of("Europe/Rome")
    private val day = LocalDate.of(2026, 10, 22)
    private fun ms(t: LocalDateTime) = t.atZone(zone).toInstant().toEpochMilli()
    private fun utc(d: LocalDate) = d.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    private fun event(id: Long, from: LocalDateTime, to: LocalDateTime, allDay: Boolean = false) =
        CalendarEvent(id, "E$id", ms(from), ms(to), allDay, location = "", color = 0)

    @Test
    fun eventoNelGiorno() {
        assertTrue(CalendarDays.occursOn(ms(day.atTime(10, 0)), ms(day.atTime(11, 0)), false, day, zone))
        assertFalse(CalendarDays.occursOn(ms(day.atTime(10, 0)), ms(day.atTime(11, 0)), false, day.plusDays(1), zone))
    }

    @Test
    fun eventoCheFinisceAMezzanotteNonToccaIlGiornoDopo() {
        val begin = ms(day.atTime(22, 0))
        val end = ms(day.plusDays(1).atStartOfDay())
        assertTrue(CalendarDays.occursOn(begin, end, false, day, zone))
        assertFalse(CalendarDays.occursOn(begin, end, false, day.plusDays(1), zone))
    }

    @Test
    fun tuttoIlGiornoSiLeggeInUtc() {
        // Un evento di tutto il giorno del 22 è salvato da mezzanotte UTC del 22 a mezzanotte UTC del 23.
        val begin = utc(day)
        val end = utc(day.plusDays(1))
        assertTrue(CalendarDays.occursOn(begin, end, true, day, zone))
        assertFalse(CalendarDays.occursOn(begin, end, true, day.plusDays(1), zone))
        assertFalse(CalendarDays.occursOn(begin, end, true, day.minusDays(1), zone))
    }

    @Test
    fun eventoDiDurataZero() {
        val at = ms(day.atTime(9, 0))
        assertTrue(CalendarDays.occursOn(at, at, false, day, zone))
    }

    @Test
    fun raggruppaPerGiornoPrimaTuttoIlGiorno() {
        val events = listOf(
            event(1, day.atTime(15, 0), day.atTime(16, 0)),
            CalendarEvent(2, "Ferie", utc(day), utc(day.plusDays(3)), allDay = true, location = "", color = 0),
            event(3, day.plusDays(1).atTime(9, 0), day.plusDays(1).atTime(10, 0)),
            event(1, day.atTime(15, 0), day.atTime(16, 0)), // doppione
        )
        val grouped = CalendarDays.group(events, day, days = 5, zone = zone)
        assertEquals(listOf(day, day.plusDays(1), day.plusDays(2)), grouped.keys.toList())
        assertEquals(listOf(2L, 1L), grouped.getValue(day).map { it.eventId })
        assertEquals(listOf(2L, 3L), grouped.getValue(day.plusDays(1)).map { it.eventId })
        assertEquals(listOf(2L), grouped.getValue(day.plusDays(2)).map { it.eventId })
    }

    @Test
    fun festivitaDaDueCalendariUnaSola() {
        val santi = LocalDate.of(2026, 11, 1)
        val events = listOf(
            CalendarEvent(10, "Tutti i Santi", utc(santi), utc(santi.plusDays(1)), allDay = true, location = "", color = 0),
            CalendarEvent(20, "Tutti i santi ", utc(santi), utc(santi.plusDays(1)), allDay = true, location = "", color = 1),
            CalendarEvent(30, "Riunione", ms(santi.atTime(9, 0)), ms(santi.atTime(10, 0)), allDay = false, location = "", color = 0),
            CalendarEvent(31, "Riunione", ms(santi.atTime(15, 0)), ms(santi.atTime(16, 0)), allDay = false, location = "", color = 0),
        )
        // Stesso nome nello stesso giorno: uno solo. Due riunioni a orari diversi restano due.
        assertEquals(listOf(10L, 30L, 31L), CalendarDays.group(events, santi, days = 1, zone = zone).getValue(santi).map { it.eventId })
    }

    @Test
    fun durate() {
        assertEquals(3_600_000L, CalendarDays.durationMs("P3600S"))
        assertEquals(5_400_000L, CalendarDays.durationMs("PT1H30M"))
        assertEquals(86_400_000L, CalendarDays.durationMs("P1D"))
        assertEquals(7 * 86_400_000L, CalendarDays.durationMs("P1W"))
        assertEquals(3_600_000L, CalendarDays.durationMs(null))
        assertEquals(3_600_000L, CalendarDays.durationMs("boh"))
    }

    @Test
    fun orariNellAgenda() {
        fun label(from: LocalDateTime, to: LocalDateTime) = CalendarDays.timeLabel(ms(from), ms(to), false, day, zone)
        assertEquals("10:00 – 11:30", label(day.atTime(10, 0), day.atTime(11, 30)))
        assertEquals("Dalle 22:00", label(day.atTime(22, 0), day.plusDays(1).atTime(2, 0)))
        assertEquals("Fino alle 09:00", label(day.minusDays(1).atTime(20, 0), day.atTime(9, 0)))
        assertEquals("Tutto il giorno", label(day.minusDays(1).atTime(20, 0), day.plusDays(1).atTime(9, 0)))
        assertEquals("22:00 – 00:00", label(day.atTime(22, 0), day.plusDays(1).atStartOfDay()))
    }

    @Test
    fun nuovoEventoAllaProssimaOraPiena() {
        val now = day.atTime(14, 25)
        val draft = EventDraft.new(calendarId = 7, day = day, now = now)
        assertEquals(day.atTime(15, 0), draft.start)
        assertEquals(day.atTime(16, 0), draft.end)
        val other = EventDraft.new(calendarId = 7, day = day.plusDays(2), now = now)
        assertEquals(day.plusDays(2).atTime(9, 0), other.start)
    }

    @Test
    fun cosaMancaPerSalvare() {
        val base = EventDraft(calendarId = 1, title = "Dentista", start = day.atTime(10, 0), end = day.atTime(11, 0))
        assertNull(base.problem())
        assertEquals("Scrivi un titolo", base.copy(title = "  ").problem())
        assertEquals("La fine deve essere dopo l'inizio", base.copy(end = day.atTime(9, 0)).problem())
        assertNull(base.copy(allDay = true, end = day.atTime(9, 0)).problem())
        assertEquals("La fine è prima dell'inizio", base.copy(allDay = true, end = day.minusDays(1).atStartOfDay()).problem())
        // Le serie si salvano senza toccare gli orari.
        assertNull(base.copy(recurring = true, end = day.atTime(9, 0)).problem())
    }

    @Test
    fun valoriPerUnEventoConOrario() {
        val draft = EventDraft(calendarId = 3, title = " Dentista ", location = "Via Roma", start = day.atTime(10, 0), end = day.atTime(11, 0))
        val values = draft.toValues(zone)
        assertEquals("Dentista", values[CalendarContract.Events.TITLE])
        assertEquals(3L, values[CalendarContract.Events.CALENDAR_ID])
        assertEquals(ms(day.atTime(10, 0)), values[CalendarContract.Events.DTSTART])
        assertEquals(ms(day.atTime(11, 0)), values[CalendarContract.Events.DTEND])
        assertEquals(0, values[CalendarContract.Events.ALL_DAY])
        assertEquals("Europe/Rome", values[CalendarContract.Events.EVENT_TIMEZONE])
    }

    @Test
    fun valoriPerTuttoIlGiorno() {
        val draft = EventDraft(calendarId = 3, title = "Ferie", allDay = true, start = day.atStartOfDay(), end = day.plusDays(2).atStartOfDay())
        val values = draft.toValues(zone)
        assertEquals(utc(day), values[CalendarContract.Events.DTSTART])
        // La fine è la mezzanotte del giorno dopo l'ultimo.
        assertEquals(utc(day.plusDays(3)), values[CalendarContract.Events.DTEND])
        assertEquals(1, values[CalendarContract.Events.ALL_DAY])
        assertEquals("UTC", values[CalendarContract.Events.EVENT_TIMEZONE])
    }

    @Test
    fun modificaNonCambiaCalendarioESerieNonCambiaOrari() {
        val draft = EventDraft(id = 9, calendarId = 3, title = "Palestra", start = day.atTime(18, 0), end = day.atTime(19, 0), recurring = true)
        val values = draft.toValues(zone)
        assertFalse(CalendarContract.Events.CALENDAR_ID in values)
        assertFalse(CalendarContract.Events.DTSTART in values)
        assertEquals("Palestra", values[CalendarContract.Events.TITLE])
    }

    @Test
    fun daEventoABozzaEIndietro() {
        val detail = EventDetail(
            id = 4, title = "Ferie", begin = utc(day), end = utc(day.plusDays(3)), allDay = true,
            location = "", description = "", recurring = false, calendarId = 2, calendarName = "Casa", writable = true, color = 0,
        )
        val draft = EventDraft.from(detail, zone)
        assertEquals(day.atStartOfDay(), draft.start)
        assertEquals(day.plusDays(2).atStartOfDay(), draft.end)
        val values = draft.toValues(zone)
        assertEquals(detail.begin, values[CalendarContract.Events.DTSTART])
        assertEquals(detail.end, values[CalendarContract.Events.DTEND])
    }

    @Test
    fun titoliDeiGiorni() {
        assertEquals("Oggi · giovedì 22 ottobre", agendaDayTitle(day, day))
        assertEquals("Domani · venerdì 23 ottobre", agendaDayTitle(day.plusDays(1), day))
        assertEquals("sabato 24 ottobre", agendaDayTitle(day.plusDays(2), day))
    }

    @Test
    fun quandoNellaScheda() {
        assertEquals("Giovedì 22 ottobre · 10:00 – 11:30", eventWhen(ms(day.atTime(10, 0)), ms(day.atTime(11, 30)), false, zone))
        assertEquals("Giovedì 22 ottobre · tutto il giorno", eventWhen(utc(day), utc(day.plusDays(1)), true, zone))
        assertEquals("Giovedì 22 ottobre – sabato 24 ottobre", eventWhen(utc(day), utc(day.plusDays(3)), true, zone))
        assertEquals(
            "Giovedì 22 ottobre 22:00 – ven 23 ott 02:00",
            eventWhen(ms(day.atTime(22, 0)), ms(day.plusDays(1).atTime(2, 0)), false, zone),
        )
    }
}
