package com.platinum20.dashboard.today

import com.platinum20.dashboard.calendar.CalendarEvent
import com.platinum20.dashboard.notes.Countdown
import com.platinum20.dashboard.notes.Expenses
import com.platinum20.dashboard.notes.FixedExpense
import com.platinum20.dashboard.notes.TaskItem
import com.platinum20.dashboard.weather.Forecast
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId

/** Il meteo del giorno dopo, già pronto da mostrare. */
data class TomorrowWeather(
    val code: Int,
    val max: Double,
    val min: Double,
    val rainChance: Int,
    /** "dalle 10" se nelle ore del giorno la pioggia è probabile, altrimenti null. */
    val rainFrom: String?,
)

enum class TomorrowKind { EVENT, TASK, DATE, MONEY }

data class TomorrowLine(val kind: TomorrowKind, val text: String)

/** La card "Domani" della sera: meteo, impegni e cose da fare del giorno dopo. */
data class TomorrowPlan(
    val day: LocalDate,
    /** "Domani" la sera, "Oggi" dopo mezzanotte (il giorno di cui si parla è già iniziato). */
    val label: String,
    val weather: TomorrowWeather?,
    /** La frase più importante, per la prima riga ("Domani 8:30 dentista, pioggia dalle 10"). */
    val headline: String?,
    val eventCount: Int,
    val lines: List<TomorrowLine>,
)

object Tomorrow {
    /** Dalle 18 si guarda a domani; fino alle 3 di notte "domani" è il giorno che sta per iniziare. */
    const val FROM_HOUR = 18
    const val UNTIL_HOUR = 3
    const val MAX_LINES = 5

    /** Il giorno di cui parlare a quest'ora, null se è troppo presto per la card. */
    fun targetDay(now: LocalDateTime): LocalDate? = when {
        now.hour >= FROM_HOUR -> now.toLocalDate().plusDays(1)
        now.hour < UNTIL_HOUR -> now.toLocalDate()
        else -> null
    }

    /**
     * [events] sono gli impegni del giorno [targetDay] (domani la sera, oggi dopo mezzanotte).
     * Restituisce null fuori orario o se non c'è niente da dire.
     */
    fun build(
        now: LocalDateTime,
        forecast: Forecast?,
        events: List<CalendarEvent>?,
        tasks: List<TaskItem>,
        countdowns: List<Countdown>,
        fixed: List<FixedExpense> = emptyList(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): TomorrowPlan? {
        val day = targetDay(now) ?: return null
        val weather = forecast?.let { weatherFor(it, day) }
        val lines = mutableListOf<TomorrowLine>()

        val sorted = events.orEmpty().sortedWith(compareBy({ !it.allDay }, { it.begin }))
        sorted.take(2).forEach { e ->
            val at = if (e.allDay) "tutto il giorno" else time(e.begin, zone)
            lines += TomorrowLine(TomorrowKind.EVENT, "$at · ${e.title}")
        }
        if (sorted.size > 2) lines += TomorrowLine(TomorrowKind.EVENT, if (sorted.size == 3) "…e un altro impegno" else "…e altri ${sorted.size - 2} impegni")

        tasks.filter { !it.done }
            .filter { t -> t.dueAt?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() == day } == true }
            .sortedBy { it.dueAt }
            .forEach { t -> lines += TomorrowLine(TomorrowKind.TASK, "${time(t.dueAt!!, zone)} · ${t.text}") }

        countdowns.filter { it.nextDate(day) == day }.forEach { c ->
            val years = c.yearsAtNext(day)?.let { " (compie $it anni)" }.orEmpty()
            lines += TomorrowLine(TomorrowKind.DATE, c.title + years)
        }

        fixed.filter { it.dateIn(YearMonth.from(day)) == day }.forEach { f ->
            lines += TomorrowLine(TomorrowKind.MONEY, "Addebito ${f.text} · ${Expenses.format(f.cents)}")
        }

        if (weather == null && lines.isEmpty()) return null
        val label = if (day == now.toLocalDate()) "Oggi" else "Domani"
        val firstTask = tasks.filter { !it.done && it.dueAt != null }
            .filter { Instant.ofEpochMilli(it.dueAt!!).atZone(zone).toLocalDate() == day }
            .minByOrNull { it.dueAt!! }
        return TomorrowPlan(
            day = day,
            label = label,
            weather = weather,
            headline = headline(label, sorted.firstOrNull { !it.allDay }, firstTask, weather, zone),
            eventCount = sorted.size,
            lines = lines.take(MAX_LINES),
        )
    }

    private fun weatherFor(forecast: Forecast, day: LocalDate): TomorrowWeather? {
        val d = forecast.daily.firstOrNull { it.date == day } ?: return null
        // Le ore utili del giorno (dalle 7 alle 22): la notte non conta per uscire.
        val rainHour = forecast.hourly.firstOrNull {
            it.time.toLocalDate() == day && it.time.hour in 7..22 && it.precipitationProbability >= 50
        }
        return TomorrowWeather(
            code = d.code,
            max = d.max,
            min = d.min,
            rainChance = d.precipitationProbability,
            rainFrom = rainHour?.let { "dalle ${it.time.hour}" },
        )
    }

    /** Il primo impegno (o, senza calendario, il primo promemoria con l'orario) e la pioggia. */
    private fun headline(label: String, first: CalendarEvent?, task: TaskItem?, weather: TomorrowWeather?, zone: ZoneId): String? {
        val parts = mutableListOf<String>()
        when {
            first != null -> parts += "${time(first.begin, zone)} ${first.title}"
            task != null -> parts += "${time(task.dueAt!!, zone)} ${task.text}"
        }
        weather?.rainFrom?.let { parts += "pioggia $it" }
        if (parts.isEmpty()) return null
        return "$label " + parts.joinToString(", ")
    }

    private fun time(ms: Long, zone: ZoneId): String = Instant.ofEpochMilli(ms).atZone(zone).toLocalTime().let {
        "%d:%02d".format(it.hour, it.minute)
    }
}
