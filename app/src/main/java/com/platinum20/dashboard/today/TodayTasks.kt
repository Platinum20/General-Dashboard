package com.platinum20.dashboard.today

import com.platinum20.dashboard.notes.TaskItem
import java.time.LocalDateTime
import java.time.ZoneId

/** Le righe della card "Da fare" di Oggi e quanti promemoria restano solo in Note. */
data class TodayTasks(val shown: List<TaskItem>, val more: Int)

object TodayTaskPicker {
    const val MAX_ROWS = 5
    const val MAX_UNDATED = 2

    /**
     * Prima gli scaduti e quelli di oggi, dal più vicino; poi al massimo [MAX_UNDATED] senza scadenza.
     * Quelli di domani la sera li mostra la card "Domani"; le scadenze più lontane restano in Note.
     */
    fun pick(open: List<TaskItem>, now: LocalDateTime, zone: ZoneId = ZoneId.systemDefault()): TodayTasks {
        val limit = now.toLocalDate().plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val dated = open.filter { it.dueAt != null && it.dueAt < limit }.sortedBy { it.dueAt }.take(MAX_ROWS)
        val undated = open.filter { it.dueAt == null }.take(minOf(MAX_UNDATED, MAX_ROWS - dated.size))
        val shown = dated + undated
        return TodayTasks(shown, open.size - shown.size)
    }
}
