package com.platinum20.dashboard.capture

import com.platinum20.dashboard.calendar.QuickEvent
import com.platinum20.dashboard.notes.QuickParse
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Le parole sopra la barra del +: prima di scrivere un esempio che cambia (solo le prime volte),
 * mentre si scrive un riassunto di cosa verrà salvato. Non una domanda: il tipo lo sceglie Canvas.
 */
internal object CaptureHints {
    /** Il campo vuoto dice solo questo: ci sta sempre, anche sui telefoni stretti. */
    const val PLACEHOLDER = "Scrivi qualcosa…"

    /** Dopo cinque salvataggi si sa già come funziona: gli esempi spariscono. */
    const val EXAMPLE_SAVES = 5

    /** Uno per tipo, nell'ordine in cui girano. */
    val examples = listOf(
        "pizza 12,50",
        "domani alle 9 chiamare il dentista",
        "venerdì alle 20 cena da Luca",
        "compleanno di Marta 12 marzo",
        "ogni giorno leggere 20 pagine",
    )

    fun showExamples(saves: Int): Boolean = saves < EXAMPLE_SAVES

    fun example(index: Int): String = "Prova: " + examples[Math.floorMod(index, examples.size)]

    private val day = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ITALIAN)
    private val hour = DateTimeFormatter.ofPattern("H:mm")

    /** "Spesa · 12,50 € · Cibo", "Promemoria · domani 9:00": in una riga sola, cosa diventerà. */
    fun summary(mode: CaptureMode, text: String, now: LocalDateTime = LocalDateTime.now()): String? {
        if (text.isBlank()) return null
        val detail = when (mode) {
            CaptureMode.TASK -> QuickParse.parse(text, now).let { e ->
                e.due?.let { whenText(it, now) + (e.repeat?.let { r -> " · ${r.label.lowercase()}" } ?: "") }
            }
            CaptureMode.EVENT -> QuickEvent.parse(text, now)?.let { e ->
                if (e.allDay) "${dayOf(e.start, now)} · tutto il giorno" else whenText(e.start, now)
            } ?: "scrivi anche quando"
            CaptureMode.NOTE -> null
            else -> CaptureSave.preview(mode, text, now)
        }
        return listOfNotNull(mode.label, detail).joinToString(" · ")
    }

    private fun dayOf(at: LocalDateTime, now: LocalDateTime): String = when (at.toLocalDate()) {
        now.toLocalDate() -> "oggi"
        now.toLocalDate().plusDays(1) -> "domani"
        else -> at.format(day)
    }

    private fun whenText(at: LocalDateTime, now: LocalDateTime): String = "${dayOf(at, now)} ${at.format(hour)}"
}
