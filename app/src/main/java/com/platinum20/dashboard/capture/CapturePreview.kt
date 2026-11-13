package com.platinum20.dashboard.capture

import com.platinum20.dashboard.calendar.QuickEvent
import com.platinum20.dashboard.notes.QuickParse
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * La riga che comparirà salvando (1.528, Antonio scelta B): sopra la barra del "+", mentre si scrive o si detta,
 * si vede dove va e com'è, con la stessa forma delle righe del Calendario: ora a sinistra, nome, una riga sotto.
 */
internal data class CapturePreviewRow(
    /** "VA NEL CALENDARIO", "VA IN PER DOPO", ... */
    val destination: String,
    /** L'ora ("18:00"), il giorno corto ("Ven 13") o vuota. */
    val left: String,
    val name: String,
    val detail: String,
    val mode: CaptureMode,
)

internal object CapturePreview {
    private val hour = DateTimeFormatter.ofPattern("H:mm")
    private val shortDay = DateTimeFormatter.ofPattern("EEE d", Locale.ITALIAN)
    private val longDay = DateTimeFormatter.ofPattern("EEE d MMMM", Locale.ITALIAN)

    /** Sotto i tre caratteri non si mostra niente: "ci" non è ancora una cosa da salvare. */
    const val MIN_CHARS = 3

    fun of(text: String, now: LocalDateTime = LocalDateTime.now()): CapturePreviewRow? {
        val clean = text.trim()
        if (clean.length < MIN_CHARS) return null
        // Una domanda non si salva: risponde il "+" (Chiedi al "+").
        if (Ask.isQuestion(clean)) return null
        val mode = CaptureGuess.guess(clean, now)
        val today = now.toLocalDate()
        return when (mode) {
            CaptureMode.EVENT -> QuickEvent.parse(clean, now)?.let { e ->
                CapturePreviewRow(
                    CALENDAR,
                    if (e.allDay) cap(e.start.toLocalDate().format(shortDay)) else e.start.format(hour),
                    e.title.ifBlank { clean },
                    dayLine(e.start.toLocalDate(), today) + if (e.allDay) " · tutto il giorno" else " · " + e.start.format(hour) + "–" + e.end.format(hour),
                    mode,
                )
            } ?: CapturePreviewRow(CALENDAR, "", clean, "Scrivi anche quando", mode)
            CaptureMode.TASK -> QuickParse.parse(clean, now).let { e ->
                val due = e.due
                if (due == null) CapturePreviewRow(LATER, "", e.text.ifBlank { clean }, "Da fare, senza data", mode)
                else CapturePreviewRow(
                    CALENDAR,
                    due.format(hour),
                    e.text.ifBlank { clean },
                    dayLine(due.toLocalDate(), today) + (e.repeat?.let { " · " + it.label.lowercase() } ?: " · avviso all'ora"),
                    mode,
                )
            }
            CaptureMode.DATE -> CaptureSave.dateOf(clean, now)?.let { d ->
                CapturePreviewRow(
                    CALENDAR,
                    cap(d.date.format(shortDay)),
                    d.title,
                    dayLine(d.date, today) + if (d.yearly) " · ogni anno" else "",
                    mode,
                )
            } ?: CapturePreviewRow(CALENDAR, "", clean, "Scrivi anche il giorno: es. 12 marzo", mode)
            CaptureMode.HABIT -> CaptureSave.habitOf(clean, now)?.let { h ->
                CapturePreviewRow(
                    HABITS,
                    "",
                    h.title,
                    (if (h.perWeek > 0) "${h.perWeek} volte a settimana" else "Ogni giorno") +
                        (h.remindAt?.let { " · avviso alle " + it.format(hour) } ?: ""),
                    mode,
                )
            } ?: CapturePreviewRow(HABITS, "", clean, "Ogni giorno", mode)
            CaptureMode.PARCEL -> CapturePreviewRow(
                CALENDAR,
                "",
                clean.lineSequence().first().take(60),
                CaptureSave.preview(mode, clean, now) ?: "Pacco in arrivo",
                mode,
            )
            CaptureMode.NOTE -> CapturePreviewRow(LATER, "", clean.lineSequence().first().take(80), "Nota", mode)
        }
    }

    const val CALENDAR = "Va nel Calendario"
    const val LATER = "Va in Per dopo"
    const val HABITS = "Va nelle abitudini"

    private fun dayLine(day: LocalDate, today: LocalDate): String = when (day) {
        today -> "Oggi"
        today.plusDays(1) -> "Domani"
        else -> cap(day.format(longDay))
    }

    private fun cap(s: String) = s.replaceFirstChar { it.uppercase() }
}
