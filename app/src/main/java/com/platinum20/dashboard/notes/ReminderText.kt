package com.platinum20.dashboard.notes

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Il testo della notifica di un promemoria (1.402, Antonio): il titolo è la cosa, il testo è il quando.
 * Nella tendina si legge solo la prima riga: prima erano sette notifiche che dicevano "Promemoria".
 */
object ReminderText {
    private val clock = DateTimeFormatter.ofPattern("HH:mm")

    fun title(task: TaskItem): String = task.text

    fun detail(task: TaskItem, today: LocalDate = LocalDate.now(), zone: ZoneId = ZoneId.systemDefault()): String {
        val time = task.dueAt?.takeIf { !task.allDay }?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalTime().format(clock) }
        val whenText = when {
            task.alert == Alert.DAY_BEFORE -> "Domani" + (time?.let { " alle $it" } ?: "")
            task.allDay || time == null -> "Oggi"
            else -> "Adesso"
        }
        val years = task.yearsAtNext(today, zone)?.let { "$it anni" }
        return listOfNotNull(whenText, years, task.place.takeIf { it.isNotBlank() }).joinToString(" · ")
    }
}
