package com.platinum20.dashboard.today

import com.platinum20.dashboard.calendar.CalendarEvent
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
    /** Il promemoria, per spuntarlo dalla card senza entrare. */
    val task: TaskItem? = null,
    /** Il pacco o l'addebito della riga: toccandola si apre la sua scheda (Antonio). */
    val parcelId: Long? = null,
    val chargeId: Long? = null,
    /** Il rinnovo dell'offerta SIM: tocco = pagina SIM. */
    val simRenewal: Boolean = false,
)

/**
 * "Prossimi giorni" in Oggi: le prossime [MAX] cose da domani in poi (fino a [DAYS] giorni), tutto in un posto solo (impegni del calendario,
 * promemoria con data, compleanni e date, pacchi, addebiti, anche quelli trovati in notifiche, SMS e mail).
 * "Adesso" sopra resta su oggi; il meteo dei giorni dopo sta nel dettaglio Meteo. Funzione pura.
 */
object Upcoming {
    /** Quanti giorni guarda avanti, domani compreso: più di un anno, come il calendario in Note > Prossimi giorni. */
    const val DAYS = 400

    /** Quante righe al massimo (Antonio: 5): è un riassunto, il resto è in Note > Prossimi giorni. */
    const val MAX = 5

    /** Il nome della riga del rinnovo SIM, uguale in Oggi e in Note. */
    const val SIM_RENEWAL = "Rinnovo offerta SIM"

    private val monthName = DateTimeFormatter.ofPattern("LLLL", java.util.Locale.ITALIAN)

    /**
     * Il segno del mese sopra una riga: il mese di [day] in maiuscolo ("NOVEMBRE", con l'anno se non è
     * quello di [today]), solo se la riga prima ([previous]) era in un altro mese. Vale in tutti e due i versi.
     */
    fun monthBreak(previous: LocalDate?, day: LocalDate, today: LocalDate): String? {
        if (previous == null || java.time.YearMonth.from(previous) == java.time.YearMonth.from(day)) return null
        return monthLabel(day, today)
    }

    /** Il nome del mese di [day] per il segno: "NOVEMBRE", con l'anno se non è quello di [today]. */
    fun monthLabel(day: LocalDate, today: LocalDate): String {
        val name = day.format(monthName).uppercase(java.util.Locale.ITALIAN)
        return if (day.year == today.year) name else "$name ${day.year}"
    }

    private val hour = DateTimeFormatter.ofPattern("HH:mm")

    fun build(
        today: LocalDate,
        events: Map<LocalDate, List<CalendarEvent>>,
        /** Promemoria e date di tutto il giorno insieme (1.361). */
        tasks: List<TaskItem>,
        parcels: List<Parcel> = emptyList(),
        fixedExpenses: List<FixedExpense> = emptyList(),
        zone: ZoneId = ZoneId.systemDefault(),
        days: Int = DAYS,
        /** Il giorno in cui si rinnova l'offerta della SIM (WindTre), se collegata. */
        simRenewal: LocalDate? = null,
    ): List<UpcomingItem> {
        val out = mutableListOf<UpcomingItem>()
        // Un evento di più giorni compare solo il primo giorno: non riempie la settimana.
        val seen = mutableSetOf<Long>()
        for (d in 1..days) {
            val day = today.plusDays(d.toLong())
            val allDay = mutableListOf<UpcomingItem>()
            val timed = mutableListOf<Pair<Long, UpcomingItem>>()
            tasks.filter { !it.done && it.allDay && it.daysLeft(today, zone) == d.toLong() }.forEach {
                allDay += UpcomingItem(day, BriefKind.DATE, it.titleWithYears(today, zone), task = it)
            }
            events[day].orEmpty().filter { seen.add(it.eventId) }.forEach {
                if (it.allDay) allDay += UpcomingItem(day, BriefKind.EVENT, it.title, event = it)
                else timed += it.begin to UpcomingItem(day, BriefKind.EVENT, it.title, it.begin.at(zone), event = it)
            }
            tasks.filter { !it.done && !it.allDay && it.dueAt != null && it.dueAt.day(zone) == day }.forEach {
                timed += it.dueAt!! to UpcomingItem(day, BriefKind.TASK, it.text, it.dueAt.at(zone), task = it)
            }
            parcels.filter { !it.delivered && it.expected == day }.forEach {
                allDay += UpcomingItem(day, BriefKind.PARCEL, "Arriva: ${it.title}", parcelId = it.id)
            }
            fixedExpenses.filter { it.nextDate(today) == day }.forEach {
                allDay += UpcomingItem(day, BriefKind.MONEY, "Addebito: ${it.text.ifEmpty { it.category.label }} ${Expenses.format(it.cents)}", chargeId = it.id)
            }
            if (simRenewal == day) allDay += UpcomingItem(day, BriefKind.MONEY, SIM_RENEWAL, simRenewal = true)
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

    /**
     * Il giorno quando il mese è già scritto sopra (il segno del mese, o il mese di oggi): solo "Dom 1",
     * senza mese né anno (Antonio). [shown] = il mese che si legge in quel punto della linea.
     */
    fun dayLabel(day: LocalDate, today: LocalDate, shown: java.time.YearMonth?): String = when {
        day == today || day == today.plusDays(1) || day == today.minusDays(1) -> dayLabel(day, today)
        java.time.YearMonth.from(day) == shown ->
            day.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ITALIAN).replaceFirstChar { it.uppercase() } + " ${day.dayOfMonth}"
        else -> dayLabel(day, today)
    }

    private fun Long.at(zone: ZoneId): String = Instant.ofEpochMilli(this).atZone(zone).format(hour)
    private fun Long.day(zone: ZoneId): LocalDate = Instant.ofEpochMilli(this).atZone(zone).toLocalDate()
}
