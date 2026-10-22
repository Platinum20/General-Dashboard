package com.platinum20.dashboard.notes

import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit

/** Le scelte "Rimanda" nella notifica di un promemoria. */
enum class Snooze(private val baseLabel: String) {
    HOUR("Tra 1 ora"),
    TOMORROW("Domani 9:00");

    /** Il testo del pulsante: a notte fonda "domani" vuol dire stamattina. */
    fun label(now: LocalDateTime): String =
        if (this == TOMORROW && isSmallHours(now)) "Stamattina 9:00" else baseLabel

    fun until(now: LocalDateTime): LocalDateTime = when (this) {
        HOUR -> now.truncatedTo(ChronoUnit.MINUTES).plusHours(1)
        TOMORROW -> {
            // Chi rimanda alle 00:30 di venerdì intende venerdì mattina, non sabato.
            val day = if (isSmallHours(now)) now.toLocalDate() else now.toLocalDate().plusDays(1)
            day.atTime(MORNING)
        }
    }

    private companion object {
        val MORNING: LocalTime = LocalTime.of(9, 0)
        /** Prima di questa ora la notte appartiene ancora al giorno prima. */
        val NIGHT_END: LocalTime = LocalTime.of(5, 0)

        fun isSmallHours(now: LocalDateTime) = now.toLocalTime().isBefore(NIGHT_END)
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
