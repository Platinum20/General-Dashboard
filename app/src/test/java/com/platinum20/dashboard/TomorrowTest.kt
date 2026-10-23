package com.platinum20.dashboard

import com.platinum20.dashboard.calendar.CalendarEvent
import com.platinum20.dashboard.notes.Countdown
import com.platinum20.dashboard.notes.ExpenseCategory
import com.platinum20.dashboard.notes.FixedExpense
import com.platinum20.dashboard.notes.TaskItem
import com.platinum20.dashboard.today.Tomorrow
import com.platinum20.dashboard.today.TomorrowKind
import com.platinum20.dashboard.weather.CurrentWeather
import com.platinum20.dashboard.weather.DailyPoint
import com.platinum20.dashboard.weather.Forecast
import com.platinum20.dashboard.weather.HourlyPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset

class TomorrowTest {
    private val zone: ZoneId = ZoneOffset.UTC
    private val today = LocalDate.of(2026, 10, 22)
    private val tomorrow = today.plusDays(1)

    private fun ms(day: LocalDate, hour: Int, minute: Int = 0) = day.atTime(hour, minute).toInstant(ZoneOffset.UTC).toEpochMilli()

    private fun forecast(rainHour: Int? = null): Forecast {
        val start = today.atTime(18, 0)
        return Forecast(
            place = "Roma", latitude = 0.0, longitude = 0.0, fetchedAt = 0L,
            current = CurrentWeather(start, 15.0, 14.0, 70, 3, false, 5.0, 180, 0.0, 1013.0),
            hourly = (0 until 24).map { i ->
                val t = start.plusHours(i.toLong())
                HourlyPoint(t, 14.0, 3, if (t.toLocalDate() == tomorrow && t.hour == rainHour) 70 else 10, true)
            },
            daily = listOf(today, tomorrow).map { d ->
                DailyPoint(d, 61, 21.0, 12.0, 70, 3.0, null, null, 10.0, 3.0)
            },
        )
    }

    private val dentist = CalendarEvent(1, "Dentista", ms(tomorrow, 8, 30), ms(tomorrow, 9, 30), false, "", 0)

    @Test
    fun alleDiciassetteNienteCard() {
        assertNull(Tomorrow.targetDay(today.atTime(17, 59)))
        assertNull(Tomorrow.build(today.atTime(17, 59), forecast(), listOf(dentist), emptyList(), emptyList(), zone = zone))
        assertNull(Tomorrow.targetDay(today.atTime(3, 0)))
    }

    @Test
    fun orariDiConfine() {
        assertEquals(tomorrow, Tomorrow.targetDay(today.atTime(18, 0)))
        assertEquals(tomorrow, Tomorrow.targetDay(tomorrow.atTime(2, 59)))
    }

    @Test
    fun allaSeraMeteoImpegniEFrase() {
        val plan = Tomorrow.build(today.atTime(19, 0), forecast(rainHour = 10), listOf(dentist), emptyList(), emptyList(), zone = zone)
        assertNotNull(plan)
        plan!!
        assertEquals(tomorrow, plan.day)
        assertEquals("dalle 10", plan.weather?.rainFrom)
        assertEquals(21.0, plan.weather!!.max, 0.0)
        assertEquals("Domani 8:30 Dentista, pioggia dalle 10", plan.headline)
        assertEquals(TomorrowKind.EVENT, plan.lines.first().kind)
        assertEquals("8:30 · Dentista", plan.lines.first().text)
    }

    @Test
    fun senzaPioggiaNelleOreDelGiorno() {
        val plan = Tomorrow.build(today.atTime(19, 0), forecast(rainHour = 5), emptyList(), emptyList(), emptyList(), zone = zone)!!
        assertNull(plan.weather?.rainFrom)
        assertNull(plan.headline)
    }

    @Test
    fun promemoriaDateEAddebitiDiDomani() {
        val tasks = listOf(
            TaskItem(1, "Ritirare la giacca", dueAt = ms(tomorrow, 17)),
            TaskItem(2, "Oggi, non domani", dueAt = ms(today, 21)),
            TaskItem(3, "Già fatto", done = true, dueAt = ms(tomorrow, 9)),
        )
        val dates = listOf(Countdown(1, "Marco", LocalDate.of(1990, 10, 23), yearly = true))
        val fixed = listOf(FixedExpense(1, 999, "Netflix", ExpenseCategory.entries.first(), 23, YearMonth.of(2026, 1)))
        val plan = Tomorrow.build(today.atTime(20, 0), null, null, tasks, dates, fixed, zone)!!
        assertEquals(
            listOf("17:00 · Ritirare la giacca", "Marco (compie 36 anni)"),
            plan.lines.filter { it.kind != TomorrowKind.MONEY }.map { it.text },
        )
        assertEquals(1, plan.lines.count { it.kind == TomorrowKind.MONEY })
        assertNull(plan.weather)
    }

    @Test
    fun nessunDatoCardNascosta() {
        assertNull(Tomorrow.build(today.atTime(20, 0), null, emptyList(), emptyList(), emptyList(), zone = zone))
    }

    @Test
    fun dopoMezzanotteParlaDelGiornoCheIniziaETantiImpegni() {
        val events = (0 until 4).map { CalendarEvent(it.toLong(), "E$it", ms(tomorrow, 9 + it), ms(tomorrow, 10 + it), false, "", 0) }
        val plan = Tomorrow.build(tomorrow.atTime(1, 0), null, events, emptyList(), emptyList(), zone = zone)!!
        assertEquals(tomorrow, plan.day)
        assertEquals(4, plan.eventCount)
        assertEquals(listOf("9:00 · E0", "10:00 · E1", "…e altri 2 impegni"), plan.lines.map { it.text })
    }

    @Test
    fun fraseColPrimoPromemoriaSenzaCalendario() {
        // V21: senza eventi del calendario la frase parte dal primo promemoria con l'orario.
        val tasks = listOf(
            TaskItem(1, "Ritirare la giacca", dueAt = ms(tomorrow, 17)),
            TaskItem(2, "Chiamare il commercialista", dueAt = ms(tomorrow, 9)),
        )
        val plan = Tomorrow.build(today.atTime(19, 0), null, null, tasks, emptyList(), zone = zone)!!
        assertEquals("Domani", plan.label)
        assertEquals("Domani 9:00 Chiamare il commercialista", plan.headline)
    }

    @Test
    fun dopoMezzanotteSiChiamaOggi() {
        val plan = Tomorrow.build(tomorrow.atTime(1, 0), forecast(rainHour = 10), listOf(dentist), emptyList(), emptyList(), zone = zone)!!
        assertEquals("Oggi", plan.label)
        assertEquals("Oggi 8:30 Dentista, pioggia dalle 10", plan.headline)
    }
}
