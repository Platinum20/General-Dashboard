package com.platinum20.dashboard.today

import com.platinum20.dashboard.calendar.CalendarEvent
import com.platinum20.dashboard.notes.Countdown
import com.platinum20.dashboard.notes.TaskItem
import java.time.LocalDate

/** Una riga della card "La giornata". */
sealed interface DayItem {
    data class Event(val event: CalendarEvent) : DayItem
    data class Task(val task: TaskItem) : DayItem
    data class Date(val countdown: Countdown, val daysLeft: Long) : DayItem
}

/**
 * "La giornata": impegni, promemoria e date in una lista sola, invece di tre card che
 * rispondono tutte a "cosa c'è oggi". Prima le cose di tutto il giorno (eventi interi
 * e le date di oggi), poi impegni e promemoria in ordine di orario (gli scaduti vengono
 * per primi da soli), poi i promemoria senza ora e infine le date dei prossimi giorni.
 */
object DayTimeline {
    fun build(
        events: List<CalendarEvent>,
        tasks: List<TaskItem>,
        dates: List<Countdown>,
        today: LocalDate,
    ): List<DayItem> {
        val withDays = dates.map { DayItem.Date(it, it.daysLeft(today)) }.sortedBy { it.daysLeft }
        val allDay = events.filter { it.allDay }.map { DayItem.Event(it) } + withDays.filter { it.daysLeft == 0L }
        val timed = events.filter { !it.allDay }.map { it.begin to DayItem.Event(it) } +
            tasks.filter { it.dueAt != null }.map { it.dueAt!! to DayItem.Task(it) }
        return allDay +
            timed.sortedBy { it.first }.map { it.second } +
            tasks.filter { it.dueAt == null }.map { DayItem.Task(it) } +
            withDays.filter { it.daysLeft > 0L }
    }
}
