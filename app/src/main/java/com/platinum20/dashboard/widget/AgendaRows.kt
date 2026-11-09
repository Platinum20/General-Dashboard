package com.platinum20.dashboard.widget

import com.platinum20.dashboard.calendar.CalendarEvent
import com.platinum20.dashboard.notes.TaskItem
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Una riga del widget Calendario. */
sealed interface AgendaRow {
    /** L'intestazione di un giorno: "DOMANI", "MER 11 NOV". */
    data class Day(val label: String) : AgendaRow

    /** Una cosa: promemoria (si spunta), data di tutto il giorno o evento del calendario. */
    data class Item(
        val kind: Kind,
        val id: Long,
        val title: String,
        val time: String?,
        val late: Boolean = false,
        val color: Int? = null,
        val begin: Long = 0,
        val end: Long = 0,
    ) : AgendaRow {
        val key: String get() = "${kind.name}:$id:$begin"
    }

    /** Testo grigio: "Giornata libera", "+ altre 2 oggi". */
    data class Note(val text: String) : AgendaRow

    enum class Kind { TASK, DATE, EVENT }
}

/**
 * Cosa mostra il widget Calendario: come la card della Home, prima tutto oggi (scaduti, eventi, promemoria,
 * date, da fare senza data) e poi i prossimi giorni finché c'è posto.
 */
object AgendaRows {
    const val DAYS = 7
    const val ITEM_DP = 36
    const val DAY_DP = 26
    const val NOTE_DP = 30
    /** Intestazione (etichetta + giorno + tasto) e margini del widget. */
    const val HEADER_DP = 76

    private val HOUR = DateTimeFormatter.ofPattern("HH:mm")
    private val SHORT = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ITALIAN)

    fun build(
        open: List<TaskItem>,
        events: Map<LocalDate, List<CalendarEvent>>,
        now: Long,
        zone: ZoneId,
        heightDp: Int,
    ): List<AgendaRow> {
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        var budget = heightDp - HEADER_DP
        val rows = mutableListOf<AgendaRow>()

        val todayItems = itemsOf(today, open, events, now, zone, isToday = true)
        if (todayItems.isEmpty()) {
            rows += AgendaRow.Note(freeText(open, events, today, zone))
            budget -= NOTE_DP
        } else {
            val fit = (budget / ITEM_DP).coerceAtLeast(1)
            if (todayItems.size <= fit) {
                rows += todayItems
                budget -= todayItems.size * ITEM_DP
            } else {
                // Non ci stanno tutte: l'ultima riga dice quante ne restano, i prossimi giorni non si vedono.
                val shown = ((budget - NOTE_DP) / ITEM_DP).coerceAtLeast(1)
                rows += todayItems.take(shown)
                rows += AgendaRow.Note(moreText(todayItems.size - shown))
                return rows
            }
        }

        for (offset in 1 until DAYS) {
            val day = today.plusDays(offset.toLong())
            val items = itemsOf(day, open, events, now, zone, isToday = false)
            if (items.isEmpty()) continue
            if (budget < DAY_DP + ITEM_DP) break
            rows += AgendaRow.Day(dayLabel(day, today))
            budget -= DAY_DP
            for (item in items) {
                if (budget < ITEM_DP) break
                rows += item
                budget -= ITEM_DP
            }
        }
        return rows
    }

    private fun itemsOf(
        day: LocalDate,
        open: List<TaskItem>,
        events: Map<LocalDate, List<CalendarEvent>>,
        now: Long,
        zone: ZoneId,
        isToday: Boolean,
    ): List<AgendaRow.Item> {
        // Ordine dentro il giorno: prima quello di tutto il giorno, poi per orario (come nel calendario).
        val timed = mutableListOf<Pair<Long, AgendaRow.Item>>()
        events[day].orEmpty().forEach { e ->
            val time = if (e.allDay) null else Instant.ofEpochMilli(e.begin).atZone(zone).let { t ->
                // Un evento iniziato ieri che continua oggi: si legge "fino alle 10".
                if (t.toLocalDate() == day) t.format(HOUR) else null
            }
            timed += (if (e.allDay) Long.MIN_VALUE else e.begin) to
                AgendaRow.Item(AgendaRow.Kind.EVENT, e.eventId, e.title, time, color = e.color, begin = e.begin, end = e.end)
        }
        open.forEach { t ->
            val next = t.nextDay(day, zone) ?: return@forEach
            if (next != day) return@forEach
            if (t.allDay) {
                timed += Long.MIN_VALUE to AgendaRow.Item(AgendaRow.Kind.DATE, t.id, t.titleWithYears(day, zone), null)
            } else {
                val due = t.dueAt ?: return@forEach
                timed += due to AgendaRow.Item(AgendaRow.Kind.TASK, t.id, t.text, Instant.ofEpochMilli(due).atZone(zone).format(HOUR), late = due < now)
            }
        }
        val sorted = timed.sortedBy { it.first }.map { it.second }
        if (!isToday) return sorted
        // Oggi apre con gli scaduti dei giorni prima e chiude con le cose da fare senza data.
        val overdue = open.filter { !it.allDay && it.dueAt != null && it.day(zone)!!.isBefore(day) }
            .sortedBy { it.dueAt }
            .map { AgendaRow.Item(AgendaRow.Kind.TASK, it.id, it.text, lateLabel(it.day(zone)!!, day), late = true) }
        val undated = open.filter { !it.allDay && it.dueAt == null }
            .map { AgendaRow.Item(AgendaRow.Kind.TASK, it.id, it.text, null) }
        return overdue + sorted + undated
    }

    /** "ieri" o "lun 2 nov" per uno scaduto. */
    private fun lateLabel(due: LocalDate, today: LocalDate): String =
        if (due == today.minusDays(1)) "ieri" else due.format(SHORT)

    /** "DOMANI" o "MER 11 NOV". */
    fun dayLabel(day: LocalDate, today: LocalDate): String =
        (if (day == today.plusDays(1)) "domani" else day.format(SHORT)).uppercase(Locale.ITALIAN).replace(".", "")

    fun moreText(n: Int): String = if (n == 1) "+ un'altra cosa oggi" else "+ altre $n cose oggi"

    /** Oggi è vuoto: lo dice e, se c'è, nomina la prossima cosa. */
    private fun freeText(open: List<TaskItem>, events: Map<LocalDate, List<CalendarEvent>>, today: LocalDate, zone: ZoneId): String {
        val anyLater = (1 until DAYS).any { o ->
            val d = today.plusDays(o.toLong())
            events[d].orEmpty().isNotEmpty() || open.any { it.nextDay(d, zone) == d }
        }
        return if (anyLater) "Niente in programma oggi" else "Settimana libera"
    }
}
