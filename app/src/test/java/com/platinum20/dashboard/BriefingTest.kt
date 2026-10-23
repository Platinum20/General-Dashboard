package com.platinum20.dashboard

import com.platinum20.dashboard.calendar.CalendarEvent
import com.platinum20.dashboard.notes.Countdown
import com.platinum20.dashboard.notes.Habit
import com.platinum20.dashboard.notes.Parcel
import com.platinum20.dashboard.notes.TaskItem
import com.platinum20.dashboard.today.BriefKind
import com.platinum20.dashboard.today.Briefing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class BriefingTest {
    private val zone = ZoneId.of("Europe/Rome")
    private fun ms(t: LocalDateTime) = t.atZone(zone).toInstant().toEpochMilli()
    private fun event(title: String, from: LocalDateTime, to: LocalDateTime) =
        CalendarEvent(1, title, ms(from), ms(to), allDay = false, location = "", color = 0)

    private val morning = LocalDateTime.of(2026, 10, 22, 9, 0)

    @Test
    fun prossimoImpegnoVicino() {
        val lines = Briefing.build(
            now = morning,
            forecast = null,
            today = listOf(event("Dentista", morning.plusMinutes(25), morning.plusMinutes(85))),
            tomorrow = null,
            tasks = emptyList(),
            countdowns = emptyList(),
            zone = zone,
        )
        assertEquals("Tra 25 min: Dentista", lines.first().text)
    }

    @Test
    fun scadutiInCima() {
        val lines = Briefing.build(
            now = morning,
            forecast = null,
            today = listOf(event("Riunione", morning.plusHours(3), morning.plusHours(4))),
            tomorrow = null,
            tasks = listOf(TaskItem(1, "Bolletta", dueAt = ms(morning.minusHours(2)))),
            countdowns = emptyList(),
            zone = zone,
        )
        assertEquals(BriefKind.TASK, lines.first().kind)
        assertEquals("1 promemoria scaduto", lines.first().text)
    }

    @Test
    fun laSeraGuardaADomani() {
        val evening = LocalDateTime.of(2026, 10, 22, 21, 0)
        val tomorrow = evening.toLocalDate().plusDays(1).atTime(9, 30)
        val lines = Briefing.build(
            now = evening,
            forecast = null,
            today = emptyList(),
            tomorrow = listOf(event("Treno", tomorrow, tomorrow.plusHours(2))),
            tasks = emptyList(),
            countdowns = listOf(Countdown(2, "Compleanno di Marta", LocalDate.of(2026, 10, 23))),
            zone = zone,
        )
        val texts = lines.map { it.text }
        assertTrue(texts.contains("Domani: Compleanno di Marta"))
        assertTrue(texts.contains("Domani alle 09:30: Treno"))
    }

    @Test
    fun conLaCardDomaniNienteRipetizioni() {
        val evening = LocalDateTime.of(2026, 10, 22, 21, 0)
        val tomorrow = evening.toLocalDate().plusDays(1)
        val lines = Briefing.build(
            now = evening,
            forecast = null,
            today = emptyList(),
            tomorrow = listOf(event("Treno", tomorrow.atTime(9, 30), tomorrow.atTime(11, 30))),
            tasks = emptyList(),
            countdowns = listOf(Countdown(2, "Compleanno di Marta", tomorrow)),
            zone = zone,
            parcels = listOf(Parcel(3, "Cuffie", expected = tomorrow)),
            tomorrowCard = true,
        )
        assertTrue(lines.none { it.text.startsWith("Domani") })
    }

    @Test
    fun compleannoConGliAnni() {
        val lines = Briefing.build(
            now = morning,
            forecast = null,
            today = emptyList(),
            tomorrow = null,
            tasks = emptyList(),
            countdowns = listOf(Countdown(3, "Compleanno di Marta", morning.toLocalDate().withYear(1996), yearly = true)),
            zone = zone,
        )
        assertTrue(lines.map { it.text }.contains("Oggi: Compleanno di Marta (30 anni)"))
    }

    @Test
    fun giornataLibera() {
        val lines = Briefing.build(morning, null, emptyList(), null, emptyList(), emptyList(), zone)
        assertEquals(listOf(BriefKind.FREE), lines.map { it.kind })
    }

    @Test
    fun senzaCalendarioNessunaFraseInventata() {
        val lines = Briefing.build(morning, null, null, null, emptyList(), emptyList(), zone)
        assertTrue(lines.isEmpty())
    }

    @Test
    fun seraAbitudiniEPacchi() {
        val evening = LocalDateTime.of(2026, 10, 22, 19, 0)
        val lines = Briefing.build(
            evening, null, emptyList(), emptyList(), emptyList(), emptyList(), zone,
            habits = listOf(Habit(1, "Acqua"), Habit(2, "Lettura", setOf(evening.toLocalDate()))),
            parcels = listOf(Parcel(3, "Cuffie", expected = evening.toLocalDate().plusDays(1))),
        )
        assertTrue(lines.any { it.kind == BriefKind.HABIT && it.text.endsWith("Acqua") })
        assertTrue(lines.any { it.kind == BriefKind.PARCEL && it.text == "Domani arriva: Cuffie" })
    }
}
