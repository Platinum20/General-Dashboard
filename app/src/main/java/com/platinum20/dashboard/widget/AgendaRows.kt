package com.platinum20.dashboard.widget

import com.platinum20.dashboard.ui.screens.DueDay
import com.platinum20.dashboard.ui.screens.RowText
import com.platinum20.dashboard.ui.screens.timelineEntries
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Una riga del widget Calendario. */
sealed interface AgendaRow {
    /** L'intestazione di un giorno: "Oggi", "Domani", "Mer 11". */
    data class Day(val label: String) : AgendaRow

    /** Una cosa del giorno, la stessa riga del calendario dell'app: evento, turno, treno, promemoria, spesa, nota. */
    data class Item(
        val kind: Kind,
        val id: Long,
        val title: String,
        /** L'ora (la colonna a sinistra, come nell'app). */
        val time: String?,
        val late: Boolean = false,
        val color: Int? = null,
        val begin: Long = 0,
        val end: Long = 0,
        /** Spuntata oggi: resta sotto Oggi, segnata fatta (come nell'app). */
        val done: Boolean = false,
    ) : AgendaRow {
        val key: String get() = "${kind.name}:$id:$begin"
    }

    /** Testo grigio: "Niente in programma oggi", "+ altre 2 oggi". */
    data class Note(val text: String) : AgendaRow

    enum class Kind { TASK, DATE, EVENT, PARCEL, NOTE, SIM }
}

/**
 * Cosa mostra il widget Calendario: lo stesso Oggi del calendario dell'app ([dueDays] con `withToday`): tutte
 * le cose di oggi (turni, treni, eventi anche finiti, promemoria, note, cose spuntate) in ordine d'orario, niente
 * nascosto; spese escluse (vanno nella sezione dei soldi); poi i prossimi giorni finché c'è posto. Le parole vengono da [timelineEntries].
 */
object AgendaRows {
    const val DAYS = 7
    const val ITEM_DP = 36
    const val DAY_DP = 26
    const val NOTE_DP = 30
    /** Intestazione (etichetta + giorno + tasto) e margini del widget. */
    const val HEADER_DP = 76

    /** [days] = [dueDays] con `withToday = true`: il primo giorno è oggi. */
    internal fun build(
        days: List<DueDay>,
        now: Long,
        zone: ZoneId,
        heightDp: Int,
    ): List<AgendaRow> {
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        var budget = heightDp - HEADER_DP
        val rows = mutableListOf<AgendaRow>()

        rows += AgendaRow.Day(TODAY)
        budget -= DAY_DP
        val todayItems = todayItems(days, today, zone)
        val later = days.filter { d -> !d.past && !d.late && d.day != null && d.day.isAfter(today) && d.day.isBefore(today.plusDays(DAYS.toLong())) }
        if (todayItems.isEmpty()) {
            rows += AgendaRow.Note(if (later.isNotEmpty()) "Niente in programma oggi" else "Settimana libera")
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

        for (group in later) {
            val items = items(group, today, zone)
            if (items.isEmpty()) continue
            if (budget < DAY_DP + ITEM_DP) break
            rows += AgendaRow.Day(dayLabel(group.day!!, today))
            budget -= DAY_DP
            for (item in items) {
                if (budget < ITEM_DP) break
                rows += item
                budget -= ITEM_DP
            }
        }
        return rows
    }

    /** Tutte le cose di oggi, come sotto OGGI nell'app. */
    internal fun todayItems(days: List<DueDay>, today: LocalDate, zone: ZoneId): List<AgendaRow.Item> =
        days.firstOrNull { it.day == today && !it.past && !it.late }?.let { items(it, today, zone) }.orEmpty()

    /** Il segno OGGI del calendario. I soldi non stanno nel widget: vanno nella loro sezione (Antonio, 2026-11-10). */
    const val TODAY = "Oggi"

    private fun items(group: DueDay, today: LocalDate, zone: ZoneId): List<AgendaRow.Item> =
        timelineEntries(listOf(group), today, zone).mapNotNull { (entry, row) ->
            // Spese e addebiti restano fuori: i soldi vanno nella loro sezione (Antonio, 2026-11-10).
            if (row.spend != null || row.expense != null || row.charge != null) return@mapNotNull null
            val (kind, id) = when {
                row.event != null -> AgendaRow.Kind.EVENT to row.event.eventId
                row.task != null -> (if (row.task.allDay) AgendaRow.Kind.DATE else AgendaRow.Kind.TASK) to row.task.id
                row.parcel != null -> AgendaRow.Kind.PARCEL to row.parcel.id
                row.note != null -> AgendaRow.Kind.NOTE to row.note.id
                row.simRenewal -> AgendaRow.Kind.SIM to 0L
                else -> return@mapNotNull null
            }
            AgendaRow.Item(
                kind, id, entry.text,
                time = entry.time,
                late = entry.late,
                color = entry.color.takeIf { it != 0 },
                begin = row.event?.begin ?: 0,
                end = row.event?.end ?: 0,
                done = entry.done,
            )
        }

    /** "Domani" o "Gio 12": le stesse parole del calendario dell'app (punto 15). */
    fun dayLabel(day: LocalDate, today: LocalDate): String = RowText.day(day, today)

    fun moreText(n: Int): String = if (n == 1) "+ un'altra cosa oggi" else "+ altre $n cose oggi"
}
