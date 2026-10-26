package com.platinum20.dashboard

import com.platinum20.dashboard.calendar.CalendarEvent
import com.platinum20.dashboard.notes.Countdown
import com.platinum20.dashboard.notes.Habit
import com.platinum20.dashboard.notes.Parcel
import com.platinum20.dashboard.notes.TaskItem
import com.platinum20.dashboard.today.BriefAction
import com.platinum20.dashboard.today.BriefKind
import com.platinum20.dashboard.today.Briefing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import com.platinum20.dashboard.weather.CurrentWeather
import com.platinum20.dashboard.weather.DailyPoint
import com.platinum20.dashboard.weather.Forecast
import com.platinum20.dashboard.weather.HourlyPoint
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull

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
        assertEquals("Scaduto: Bolletta", lines.first().text)
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

    private fun rainyTomorrow(day: LocalDate) = Forecast(
        place = "Roma", latitude = 0.0, longitude = 0.0, fetchedAt = 0L,
        current = CurrentWeather(day.atTime(19, 0), 15.0, 14.0, 70, 0, false, 5.0, 180, 0.0, 1013.0),
        hourly = emptyList(),
        daily = listOf(day, day.plusDays(1)).map { DailyPoint(it, 61, 22.0, 10.0, 60, 3.0, null, null, 10.0, 3.0) },
    )

    @Test
    fun laCardDiOggiRestaSuOggi() {
        // Nel widget la sera arriva anche domani; nella card no: lì c'è "Prossime cose".
        val evening = LocalDateTime.of(2026, 10, 22, 19, 0)
        val tomorrow = evening.toLocalDate().plusDays(1).atTime(9, 30)
        fun build(widget: Boolean) = Briefing.build(
            now = evening,
            forecast = rainyTomorrow(evening.toLocalDate()),
            today = emptyList(),
            tomorrow = listOf(event("Treno", tomorrow, tomorrow.plusHours(2))),
            tasks = emptyList(),
            countdowns = listOf(Countdown(2, "Compleanno di Marta", LocalDate.of(2026, 10, 23))),
            parcels = listOf(Parcel(1, "Cuffie", expected = LocalDate.of(2026, 10, 23))),
            zone = zone,
            withTomorrow = widget,
        )
        val widget = build(widget = true)
        assertTrue(widget.any { it.kind == BriefKind.WEATHER })
        assertTrue(widget.any { it.text == "Domani alle 09:30: Treno" })
        val card = build(widget = false)
        assertFalse(card.any { it.text.startsWith("Domani") })
        assertFalse(card.any { it.kind == BriefKind.WEATHER })
        assertEquals(listOf("Niente altro in programma oggi"), card.map { it.text })
    }

    @Test
    fun rigaMeteoDiDomaniPortaIlCodiceDellaPioggia() {
        val evening = LocalDateTime.of(2026, 10, 22, 19, 0)
        val line = Briefing.build(
            now = evening,
            forecast = rainyTomorrow(evening.toLocalDate()),
            today = emptyList(),
            tomorrow = emptyList(),
            tasks = emptyList(),
            countdowns = emptyList(),
            zone = zone,
        ).first { it.kind == BriefKind.WEATHER }
        assertEquals("Domani 22° / 10°, pioggia 60%", line.text)
        assertEquals(61, line.weatherCode)
    }

    @Test
    fun righeConAzioneRapida() {
        // Voce 17: abitudini, pacchi e promemoria si chiudono da Adesso.
        val evening = LocalDateTime.of(2026, 10, 22, 19, 0)
        val lines = Briefing.build(
            evening, null, emptyList(), emptyList(),
            tasks = listOf(TaskItem(7, "Bolletta", dueAt = ms(evening.minusHours(2)))),
            countdowns = emptyList(), zone = zone,
            habits = listOf(Habit(1, "Acqua"), Habit(2, "Leggere 20 minuti")),
            parcels = listOf(Parcel(3, "Cuffie", expected = evening.toLocalDate())),
            max = 6,
        )
        val task = lines.first { it.kind == BriefKind.TASK }
        assertEquals("Scaduto: Bolletta", task.text)
        assertEquals(BriefAction.TASK_DONE, task.action)
        assertEquals(listOf(7L), task.targetIds)
        val parcel = lines.first { it.kind == BriefKind.PARCEL }
        assertEquals(BriefAction.PARCEL_ARRIVED, parcel.action)
        assertEquals(listOf(3L), parcel.targetIds)
        val habits = lines.first { it.kind == BriefKind.HABIT }
        assertEquals(BriefAction.HABITS, habits.action)
        assertEquals(listOf(1L, 2L), habits.targetIds)
    }

    @Test
    fun abitudineSegnataLaRigaScende() {
        val evening = LocalDateTime.of(2026, 10, 22, 19, 0)
        fun habitLine(habits: List<Habit>) = Briefing.build(
            evening, null, emptyList(), emptyList(), emptyList(), emptyList(), zone, habits = habits,
        ).firstOrNull { it.kind == BriefKind.HABIT }
        val both = listOf(Habit(1, "Acqua"), Habit(2, "Lettura"))
        assertEquals(BriefAction.HABITS, habitLine(both)?.action)
        val oneDone = listOf(Habit(1, "Acqua", setOf(evening.toLocalDate())), Habit(2, "Lettura"))
        assertEquals(BriefAction.HABIT_DONE, habitLine(oneDone)?.action)
        assertEquals(listOf(2L), habitLine(oneDone)?.targetIds)
        val allDone = both.map { it.copy(done = setOf(evening.toLocalDate())) }
        assertEquals(null, habitLine(allDone))
    }

    @Test
    fun piuPacchiNessunArrivato() {
        val lines = Briefing.build(
            morning, null, emptyList(), null, emptyList(), emptyList(), zone,
            parcels = listOf(Parcel(1, "Cuffie", expected = morning.toLocalDate()), Parcel(2, "Libro", expected = morning.toLocalDate())),
        )
        assertEquals(null, lines.first { it.kind == BriefKind.PARCEL }.action)
    }

    @Test
    fun nomiDelleAbitudiniNonTagliati() {
        assertEquals("Ancora da fare oggi: Acqua", Briefing.habitsText(listOf("Acqua")))
        assertEquals("Ancora da fare oggi: Acqua, Lettura", Briefing.habitsText(listOf("Acqua", "Lettura")))
        assertEquals("Ancora da fare: 2 abitudini", Briefing.habitsText(listOf("Bere 2 litri d'acqua", "Camminare 30 minuti")))
        assertEquals("Ancora da fare: 1 abitudine", Briefing.habitsText(listOf("Meditare dieci minuti prima di dormire")))
        assertEquals("Ancora da fare: 3 abitudini", Briefing.habitsText(listOf("A", "B", "C")))
    }

    @Test
    fun pioggiaSullImpegno() {
        // Integrazione meteo + agenda: la riga dell'impegno dice se a quell'ora piove.
        val start = morning.plusHours(3)
        val forecast = rainyTomorrow(morning.toLocalDate()).copy(
            hourly = listOf(
                HourlyPoint(start, 15.0, 61, 70, true),
                HourlyPoint(start.plusHours(1), 15.0, 3, 10, true),
            ),
        )
        fun line(at: LocalDateTime) = Briefing.build(
            now = morning, forecast = forecast,
            today = listOf(event("Calcetto", at, at.plusHours(1))),
            tomorrow = null, tasks = emptyList(), countdowns = emptyList(), zone = zone,
        ).first { it.kind == BriefKind.EVENT }.text
        assertEquals("Alle 12:00: Calcetto · pioggia 70%", line(start))
        assertEquals("Alle 13:30: Calcetto", line(start.plusMinutes(90)))
    }

    @Test
    fun laCardElencaTuttaLaGiornata() {
        // Nella card di Oggi ogni impegno e ogni promemoria ha la sua riga; nel widget solo il prossimo.
        val allDay = CalendarEvent(3, "Tutti i Santi", ms(morning.withHour(0)), ms(morning.withHour(0).plusDays(1)), allDay = true, location = "", color = 0)
        fun build(card: Boolean) = Briefing.build(
            now = morning,
            forecast = null,
            today = listOf(allDay, event("Riunione", morning.plusHours(2), morning.plusHours(3)), event("Palestra", morning.plusHours(9), morning.plusHours(10))),
            tomorrow = null,
            tasks = listOf(TaskItem(1, "Bolletta", dueAt = ms(morning.plusHours(4))), TaskItem(2, "Latte", dueAt = ms(morning.plusHours(6)))),
            countdowns = emptyList(),
            budgetCents = 100,
            expenses = listOf(com.platinum20.dashboard.notes.Expense(1, 500, "pizza", com.platinum20.dashboard.notes.ExpenseCategory.entries.first(), date = morning.toLocalDate())),
            zone = zone,
            withTomorrow = !card,
            wholeDay = card,
            max = Briefing.CARD_MAX,
        )
        val card = build(card = true).map { it.text }
        assertTrue(card.contains("Oggi: Tutti i Santi"))
        assertTrue(card.contains("Alle 11:00: Riunione"))
        assertTrue(card.contains("Alle 18:00: Palestra"))
        assertTrue(card.contains("Entro le 13:00: Bolletta"))
        assertTrue(card.contains("Entro le 15:00: Latte"))
        // Il budget sta nella card Spese, non qui.
        assertFalse(card.any { it.contains("Budget") || it.contains("Spese") })
        val widget = build(card = false).map { it.text }
        assertTrue(widget.contains("2 promemoria entro stasera"))
        assertFalse(widget.contains("Alle 18:00: Palestra"))
    }
}
