package com.platinum20.dashboard.today

import com.platinum20.dashboard.calendar.CalendarEvent
import com.platinum20.dashboard.notes.Countdown
import com.platinum20.dashboard.notes.Expenses
import com.platinum20.dashboard.notes.FixedExpense
import com.platinum20.dashboard.notes.Parcel
import com.platinum20.dashboard.notes.TaskItem
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** Una riga di "Prossimi giorni": il giorno, l'ora se c'è, che cosa. */
data class UpcomingItem(
    val day: LocalDate,
    val kind: BriefKind,
    val text: String,
    /** "09:30" per impegni e promemoria con orario, null per le cose di tutto il giorno. */
    val time: String? = null,
    /** L'evento del calendario, per aprirne la scheda con un tocco. */
    val event: CalendarEvent? = null,
)

/**
 * "Prossimi giorni" in Oggi: da domani alla settimana dopo, tutto in un posto solo (impegni del calendario,
 * promemoria con data, compleanni e date, pacchi, addebiti, anche quelli trovati in notifiche, SMS e mail).
 * "Adesso" sopra resta su oggi; il meteo dei giorni dopo sta nel dettaglio Meteo. Funzione pura.
 */
object Upcoming {
    /** Quanti giorni guarda avanti, domani compreso. */
    const val DAYS = 7

    /** Quante righe al massimo: è un riassunto, il resto è in Note > Prossimi giorni. */
    const val MAX = 8

    private val hour = DateTimeFormatter.ofPattern("HH:mm")

    fun build(
        today: LocalDate,
        events: Map<LocalDate, List<CalendarEvent>>,
        tasks: List<TaskItem>,
        countdowns: List<Countdown>,
        parcels: List<Parcel> = emptyList(),
        fixedExpenses: List<FixedExpense> = emptyList(),
        zone: ZoneId = ZoneId.systemDefault(),
        days: Int = DAYS,
    ): List<UpcomingItem> {
        val out = mutableListOf<UpcomingItem>()
        // Un evento di più giorni compare solo il primo giorno: non riempie la settimana.
        val seen = mutableSetOf<Long>()
        for (d in 1..days) {
            val day = today.plusDays(d.toLong())
            val allDay = mutableListOf<UpcomingItem>()
            val timed = mutableListOf<Pair<Long, UpcomingItem>>()
            countdowns.filter { it.daysLeft(today) == d.toLong() }.forEach {
                allDay += UpcomingItem(day, BriefKind.DATE, it.title + (it.yearsAtNext(today)?.let { y -> " ($y anni)" } ?: ""))
            }
            events[day].orEmpty().filter { seen.add(it.eventId) }.forEach {
                if (it.allDay) allDay += UpcomingItem(day, BriefKind.EVENT, it.title, event = it)
                else timed += it.begin to UpcomingItem(day, BriefKind.EVENT, it.title, it.begin.at(zone), event = it)
            }
            tasks.filter { !it.done && it.dueAt != null && it.dueAt.day(zone) == day }.forEach {
                timed += it.dueAt!! to UpcomingItem(day, BriefKind.TASK, it.text, it.dueAt.at(zone))
            }
            parcels.filter { !it.delivered && it.expected == day }.forEach {
                allDay += UpcomingItem(day, BriefKind.PARCEL, "Arriva: ${it.title}")
            }
            fixedExpenses.filter { it.nextDate(today) == day }.forEach {
                allDay += UpcomingItem(day, BriefKind.MONEY, "Addebito: ${it.text.ifEmpty { it.category.label }} ${Expenses.format(it.cents)}")
            }
            out += allDay + timed.sortedBy { it.first }.map { it.second }
        }
        return out
    }

    /** "Domani", poi "Mer 28": il giorno della settimana con il numero non si confonde con oggi. */
    fun dayLabel(day: LocalDate, today: LocalDate): String =
        if (day == today.plusDays(1)) "Domani"
        else day.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ITALIAN).replaceFirstChar { it.uppercase() } + " ${day.dayOfMonth}"

    private fun Long.at(zone: ZoneId): String = Instant.ofEpochMilli(this).atZone(zone).format(hour)
    private fun Long.day(zone: ZoneId): LocalDate = Instant.ofEpochMilli(this).atZone(zone).toLocalDate()
}
