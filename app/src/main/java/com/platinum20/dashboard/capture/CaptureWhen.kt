package com.platinum20.dashboard.capture

import com.platinum20.dashboard.calendar.QuickEvent
import com.platinum20.dashboard.calendar.QuickEventDraft
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * "Calendario" scelto a mano nel "+" su un testo senza giorno (1.536, Antonio): invece del vecchio dialogo
 * "Aggiungi" sale il fumetto di vetro che chiede solo quando, con giorni e ore in chip. Il testo non si riscrive.
 * Funzioni pure.
 */
internal object CaptureWhen {
    /** Ore proposte nel fumetto; le altre con "Altra ora". */
    val HOURS = listOf(9, 12, 15, 18, 21)

    /** Giorni in chip: oggi e i sei dopo. */
    const val DAYS = 7

    /** Serve chiedere il quando: Calendario scelto a mano e nel testo non c'è un giorno. */
    fun needed(chosen: CaptureDestination?, text: String, now: LocalDateTime = LocalDateTime.now()): Boolean =
        chosen == CaptureDestination.CALENDAR && text.isNotBlank() && QuickEvent.parse(text, now) == null &&
            CaptureGuess.guess(text, now).let { CaptureDestination.of(it, text, now) != CaptureDestination.CALENDAR }

    private val dayFormat = DateTimeFormatter.ofPattern("EEE d", Locale.ITALIAN)

    /** "Oggi", "Domani", poi "Dom 22". */
    fun dayLabel(day: LocalDate, today: LocalDate): String = when (day) {
        today -> "Oggi"
        today.plusDays(1) -> "Domani"
        else -> day.format(dayFormat).replaceFirstChar { it.uppercase() }
    }

    fun days(today: LocalDate): List<LocalDate> = (0 until DAYS).map { today.plusDays(it.toLong()) }

    /** Le ore da proporre per [day]: oggi solo quelle non ancora passate. */
    fun hours(day: LocalDate, now: LocalDateTime): List<LocalTime> =
        HOURS.map { LocalTime.of(it, 0) }.filter { day != now.toLocalDate() || it.isAfter(now.toLocalTime()) }

    fun hourLabel(time: LocalTime): String = "%d:%02d".format(time.hour, time.minute)

    /** L'evento: [time] null = tutto il giorno; se no dura un'ora come quelli scritti a parole. */
    fun draft(text: String, day: LocalDate, time: LocalTime?): QuickEventDraft {
        val title = text.trim().replaceFirstChar { it.uppercase() }
        if (time == null) return QuickEventDraft(title, day.atStartOfDay(), day.atStartOfDay(), allDay = true)
        val start = day.atTime(time)
        return QuickEventDraft(title, start, start.plusMinutes(QuickEvent.DEFAULT_MINUTES), allDay = false)
    }
}
