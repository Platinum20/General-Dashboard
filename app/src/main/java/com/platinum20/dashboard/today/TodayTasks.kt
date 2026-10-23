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
     * Prima gli scaduti e quelli di oggi (dalle 18 anche di domani), dal più vicino;
     * poi al massimo [MAX_UNDATED] senza scadenza. Le scadenze più lontane restano in Note.
     * Con [tomorrowCard] i promemoria di domani li mostra già la card "Domani": qui non si ripetono.
     */
    fun pick(
        open: List<TaskItem>,
        now: LocalDateTime,
        zone: ZoneId = ZoneId.systemDefault(),
        tomorrowCard: Boolean = false,
    ): TodayTasks {
        val lastDay = if (now.hour >= 18 && !tomorrowCard) now.toLocalDate().plusDays(1) else now.toLocalDate()
        val limit = lastDay.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val dated = open.filter { it.dueAt != null && it.dueAt < limit }.sortedBy { it.dueAt }.take(MAX_ROWS)
        val undated = open.filter { it.dueAt == null }.take(minOf(MAX_UNDATED, MAX_ROWS - dated.size))
        val shown = dated + undated
        return TodayTasks(shown, open.size - shown.size)
    }
}
