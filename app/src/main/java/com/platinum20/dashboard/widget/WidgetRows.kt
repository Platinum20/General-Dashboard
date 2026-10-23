package com.platinum20.dashboard.widget

import com.platinum20.dashboard.notes.TaskItem
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Le righe di testo del widget, separate da RemoteViews per poterle provare. */
object WidgetRows {
    /** Riga con i promemoria scaduti o in scadenza oggi, null se non ce ne sono. */
    fun tasksLine(openTasks: List<TaskItem>, now: Instant, zone: ZoneId): String? {
        val today = now.atZone(zone).toLocalDate()
        val endOfToday = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val nowMs = now.toEpochMilli()
        val due = openTasks.filter { (it.dueAt ?: Long.MAX_VALUE) < endOfToday }.sortedBy { it.dueAt }
        if (due.isEmpty()) return null
        val late = due.count { (it.dueAt ?: 0) < nowMs }
        val next = due.firstOrNull { (it.dueAt ?: 0) >= nowMs } ?: due.first()
        val count = when {
            late == due.size -> if (late == 1) "1 promemoria scaduto" else "$late promemoria scaduti"
            late > 0 -> "${due.size} promemoria oggi ($late ${if (late == 1) "scaduto" else "scaduti"})"
            due.size == 1 -> "1 promemoria oggi"
            else -> "${due.size} promemoria oggi"
        }
        return "📌 $count · ${next.text} ${timeOf(next, today, zone)}".trim()
    }

    private fun timeOf(task: TaskItem, today: LocalDate, zone: ZoneId): String {
        val due = task.dueAt ?: return ""
        val time = Instant.ofEpochMilli(due).atZone(zone)
        return if (time.toLocalDate() == today) "alle " + time.format(DateTimeFormatter.ofPattern("HH:mm")) else ""
    }

    /** Sotto la temperatura: "Sereno · 22°/10° · pioggia 40%" (la pioggia solo dal 30%). */
    fun weatherLine(description: String, max: String?, min: String?, rainChance: Int?): String =
        description +
            (if (max != null && min != null) " · $max/$min" else "") +
            (rainChance?.takeIf { it >= 30 }?.let { " · pioggia $it%" } ?: "")

    /** Quante notizie stanno nel widget: tre se non c'è altro, due se sopra ci sono le righe di Adesso o i promemoria. */
    fun newsCount(briefLines: Int, hasTasks: Boolean): Int = if (briefLines == 0 && !hasTasks) 3 else 2
}
