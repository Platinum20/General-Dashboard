package com.platinum20.dashboard.capture

import com.platinum20.dashboard.calendar.QuickEvent
import com.platinum20.dashboard.notes.QuickParse
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Cosa dice la barra del +: a campo vuoto il segnaposto è un esempio che cambia, mentre si scrive
 * il simbolo a destra dice cosa verrà salvato (e il riassunto lo legge TalkBack). Non una domanda:
 * il tipo lo sceglie Canvas.
 */
internal object CaptureHints {
    /** Alta quanto il +, sempre: la barra non cresce né con gli esempi né col testo lungo. */
    const val BAR_DP = 60

    /** Ogni quanto cambia l'esempio nel campo vuoto. */
    const val EXAMPLE_MS = 3_000L

    /** Uno per tipo, nell'ordine in cui girano. */
    val examples = listOf(
        "domani alle 9 chiamare il dentista",
        "venerdì alle 20 cena da Luca",
        "compleanno di Marta 12 marzo",
        "ogni giorno leggere 20 pagine",
    )

    /** Il segnaposto: l'esempio così com'è, in grigio si capisce da sé che è un esempio. */
    fun example(index: Int): String = examples[Math.floorMod(index, examples.size)]

    private val day = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ITALIAN)
    private val hour = DateTimeFormatter.ofPattern("H:mm")

    /** "Promemoria · domani 9:00": in una riga sola, cosa diventerà. */
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
