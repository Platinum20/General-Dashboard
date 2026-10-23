package com.platinum20.dashboard.capture

import com.platinum20.dashboard.calendar.QuickEvent
import com.platinum20.dashboard.notes.Carrier
import java.time.LocalDateTime

/**
 * Il "+" capisce da solo che cosa si sta scrivendo, così non serve scegliere prima il tipo:
 * un codice di tracciamento è un pacco, "12,50 pizza" una spesa, "venerdì alle 20 cena da Luca"
 * un evento, un testo lungo una nota, "ogni giorno leggere" un'abitudine, "compleanno di Marta 12 marzo"
 * una data da ricordare. Nel dubbio resta un promemoria.
 */
internal object CaptureGuess {
    private const val B0 = "(?<![\\p{L}\\d])"
    private const val B1 = "(?![\\p{L}\\d])"

    // Un importo vero: con l'euro accanto oppure con i centesimi dopo la virgola.
    // "12.30" da solo no: è più facile che sia un orario.
    private val money = Regex(
        "$B0(?:€\\s*\\d{1,5}(?:[.,]\\d{1,2})?|\\d{1,5}(?:[.,]\\d{1,2})?\\s*(?:€|euro|eur)$B1|\\d{1,5},\\d{2}$B1)",
        RegexOption.IGNORE_CASE,
    )
    private val clock = Regex("${B0}(?:alle|ore|dalle|verso le)\\s+\\d", RegexOption.IGNORE_CASE)

    // Cose che si fanno a un'ora con qualcuno o da qualche parte: vanno in calendario.
    private val eventWords = Regex(
        "$B0(?:cena|pranzo|colazione|aperitivo|riunione|appuntamento|incontro|colloquio|visita|festa|" +
            "partita|calcetto|cinema|teatro|concerto|meeting|call|lezione|esame|matrimonio|compleanno|viaggio|volo|treno)$B1",
        RegexOption.IGNORE_CASE,
    )

    fun guess(text: String, now: LocalDateTime = LocalDateTime.now()): CaptureMode {
        val t = text.trim()
        if (t.isEmpty()) return CaptureMode.TASK
        if (Carrier.findCode(t) != null) return CaptureMode.PARCEL
        if (t.contains('\n') || t.length > 120) return CaptureMode.NOTE
        if (money.containsMatchIn(t) && !clock.containsMatchIn(t)) return CaptureMode.EXPENSE
        // Senza un'ora: con l'ora "ogni giorno alle 8 pillola" resta un promemoria, "festa di compleanno alle 20" un evento.
        if (!CaptureSave.clock.containsMatchIn(t)) {
            if (CaptureSave.yearlyWords.containsMatchIn(t) && CaptureSave.dateOf(t, now) != null) return CaptureMode.DATE
            if (CaptureSave.perWeek.containsMatchIn(t) || CaptureSave.everyDay.containsMatchIn(t)) return CaptureMode.HABIT
        }
        if (eventWords.containsMatchIn(t) && QuickEvent.parse(t, now) != null) return CaptureMode.EVENT
        return CaptureMode.TASK
    }
}
