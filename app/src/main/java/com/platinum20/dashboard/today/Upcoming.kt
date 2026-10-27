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
 * "Prossimi giorni" in Oggi: le prossime [MAX] cose da domani in poi (fino a [DAYS] giorni), tutto in un posto solo (impegni del calendario,
 * promemoria con data, compleanni e date, pacchi, addebiti, anche quelli trovati in notifiche, SMS e mail).
 * "Adesso" sopra resta su oggi; il meteo dei giorni dopo sta nel dettaglio Meteo. Funzione pura.
 */
object Upcoming {
    /** Quanti giorni guarda avanti, domani compreso: più di un anno, come il calendario in Note > Prossimi giorni. */
    const val DAYS = 400

    /** Quante righe al massimo (Antonio: 8): è un riassunto, il resto è in Note > Prossimi giorni. */
    const val MAX = 8

    /** Lo spazio massimo fra due giorni sulla linea della card. */
    const val MAX_GAP_DP = 20f

    /**
     * Lo spazio in più (dp) fra due righe della card: zero se sono giorni di fila, poi 6 dp per ogni giorno
     * vuoto in mezzo (4 dp), fino a [MAX_GAP_DP]: anche mesi vuoti restano corti. Così la linea mostra quanto tempo passa fra una cosa e l'altra.
     */
    fun gapDp(from: LocalDate, to: LocalDate): Float =
        (4f * (java.time.temporal.ChronoUnit.DAYS.between(from, to) - 1)).coerceIn(0f, MAX_GAP_DP)

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

    /**
     * "Oggi", "Domani", "Ieri", poi "Mer 28": il giorno della settimana con il numero non si confonde con oggi.
     * Con il mese ("Dom 4 ott") nel passato e fuori dal mese corrente; in un altro anno il giorno, il mese e l'anno
     * ("9 feb 2027"). Uguale in Oggi e in Note.
     */
    fun dayLabel(day: LocalDate, today: LocalDate): String = when {
        day == today -> "Oggi"
        day == today.plusDays(1) -> "Domani"
        day == today.minusDays(1) -> "Ieri"
        day.year != today.year -> "${day.dayOfMonth} " + day.month.getDisplayName(TextStyle.SHORT, Locale.ITALIAN) + " ${day.year}"
        else -> day.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ITALIAN).replaceFirstChar { it.uppercase() } + " ${day.dayOfMonth}" +
            if (day.isBefore(today) || day.month != today.month || day.isAfter(today.plusDays(27))) " " + day.month.getDisplayName(TextStyle.SHORT, Locale.ITALIAN) else ""
    }

    private fun Long.at(zone: ZoneId): String = Instant.ofEpochMilli(this).atZone(zone).format(hour)
    private fun Long.day(zone: ZoneId): LocalDate = Instant.ofEpochMilli(this).atZone(zone).toLocalDate()
}
