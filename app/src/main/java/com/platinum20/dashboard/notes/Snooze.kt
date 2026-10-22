package com.platinum20.dashboard.notes

import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit

/** Le scelte "Rimanda" nella notifica di un promemoria. */
enum class Snooze(val label: String) {
    HOUR("Tra 1 ora"),
    TOMORROW("Domani 9:00");

    fun until(now: LocalDateTime): LocalDateTime = when (this) {
        HOUR -> now.truncatedTo(ChronoUnit.MINUTES).plusHours(1)
        TOMORROW -> now.toLocalDate().plusDays(1).atTime(LocalTime.of(9, 0))
    }
}

/**
 * Il promemoria spostato a [until]. Per un ricorrente si ricorda la scadenza originale,
 * così spuntandolo la volta dopo resta quella di sempre.
 */
fun TaskItem.snoozed(until: Long): TaskItem = copy(
    dueAt = until,
    snoozedFrom = if (repeat != null) snoozedFrom ?: dueAt else null,
)
