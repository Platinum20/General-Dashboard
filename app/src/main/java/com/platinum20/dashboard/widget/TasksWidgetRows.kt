package com.platinum20.dashboard.widget

import com.platinum20.dashboard.notes.TaskItem
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Cosa mostra il widget "Da fare": righe da spuntare, quanti restano e la frase quando è vuoto. */
data class TasksWidgetContent(
    val rows: List<Row>,
    /** Promemoria di oggi o senza scadenza che non stanno nel widget ("+ altri 3"). */
    val more: Int,
    /** Solo se [rows] è vuota: "Niente da fare oggi" e, se c'è, il prossimo promemoria. */
    val empty: String?,
) {
    data class Row(val id: Long, val text: String, val time: String?, val late: Boolean)
}

/** Le righe del widget "Da fare", separate da RemoteViews per poterle provare. */
object TasksWidgetRows {
    const val MAX_ROWS = 6
    private val HOUR = DateTimeFormatter.ofPattern("HH:mm")
    private val DAY = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ITALIAN)

    /**
     * Prima gli scaduti e quelli di oggi (dal più vecchio), poi quelli senza scadenza,
     * al massimo [maxRows]. Quelli dei prossimi giorni restano in Note: il widget è per oggi.
     */
    fun build(open: List<TaskItem>, now: Long, zone: ZoneId, maxRows: Int): TasksWidgetContent {
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val endOfToday = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val dated = open.filter { it.dueAt != null && it.dueAt < endOfToday }.sortedBy { it.dueAt }
        val undated = open.filter { it.dueAt == null }
        val candidates = dated + undated
        val limit = maxRows.coerceIn(1, MAX_ROWS)
        val rows = candidates.take(limit).map { task ->
            val due = task.dueAt
            TasksWidgetContent.Row(
                id = task.id,
                text = task.text,
                time = due?.let { timeLabel(it, today, zone) },
                late = due != null && due < now,
            )
        }
        val empty = if (rows.isEmpty()) emptyText(open, endOfToday, today, zone) else null
        return TasksWidgetContent(rows, candidates.size - rows.size, empty)
    }

    /** "15:00" per oggi, "ieri" o "lun 19 ott" per gli scaduti dei giorni prima. */
    private fun timeLabel(due: Long, today: LocalDate, zone: ZoneId): String {
        val time = Instant.ofEpochMilli(due).atZone(zone)
        return when (time.toLocalDate()) {
            today -> time.format(HOUR)
            today.minusDays(1) -> "ieri"
            else -> time.format(DAY)
        }
    }

    private fun emptyText(open: List<TaskItem>, endOfToday: Long, today: LocalDate, zone: ZoneId): String {
        val next = open.filter { it.dueAt != null && it.dueAt >= endOfToday }.minByOrNull { it.dueAt!! }
            ?: return "Niente da fare oggi"
        val time = Instant.ofEpochMilli(next.dueAt!!).atZone(zone)
        val day = if (time.toLocalDate() == today.plusDays(1)) "domani" else time.format(DAY)
        return "Niente da fare oggi · prossimo: ${next.text}, $day alle ${time.format(HOUR)}"
    }

    /** Quante righe stanno nell'altezza del widget (dp): intestazione, margini e "+ altri" a parte, 36dp a riga. */
    fun rowsForHeight(heightDp: Int): Int = ((heightDp - 70) / 36).coerceIn(1, MAX_ROWS)

    /** Il testo sotto le righe quando non stanno tutte. */
    fun moreText(more: Int): String? = when {
        more <= 0 -> null
        more == 1 -> "+ un altro in Note"
        else -> "+ altri $more in Note"
    }
}
