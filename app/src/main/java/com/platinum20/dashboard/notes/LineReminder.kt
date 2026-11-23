package com.platinum20.dashboard.notes

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * "Ricordamelo" su una riga della nota (idea 16, Antonio 2026-11-23): la riga diventa un promemoria con l'ora, e
 * nelle note del promemoria c'è il link alla nota ([NoteFormat.mentionLink]), così la notifica apre la nota.
 * Funzioni pure: le ore proposte e la riga sotto il cursore.
 */
object LineReminder {
    val EVENING: LocalTime = LocalTime.of(19, 0)
    val MORNING: LocalTime = LocalTime.of(9, 0)

    /** Stasera alle 19; dopo le 18:30 non ha senso (è già sera): null. */
    fun tonight(now: LocalDateTime): LocalDateTime? =
        now.toLocalDate().atTime(EVENING).takeIf { now.toLocalTime().isBefore(EVENING.minusMinutes(30)) }

    fun tomorrowMorning(now: LocalDateTime): LocalDateTime = now.toLocalDate().plusDays(1).atTime(MORNING)

    /** La riga della nota dove sta il cursore, senza segni; null se è vuota o è un blocco (tabella, foto). */
    fun lineAt(body: String, cursor: Int): String? {
        if (body.isBlank()) return null
        val line = NoteLines.lineAt(body, cursor.coerceIn(0, body.length))
        if (line.isBlock || line.kind == NoteLines.Kind.DIVIDER) return null
        return NoteLines.plainLine(body.substring(line.start, line.end)).takeIf { it.isNotEmpty() }
    }

    /** Le note del promemoria: solo il link alla nota di partenza. */
    fun link(title: String, noteId: Long): String {
        val clean = title.replace('[', '(').replace(']', ')').replace('\n', ' ').trim().ifEmpty { "Nota" }
        return "[$clean](${NoteFormat.NOTE_LINK}$noteId)"
    }

    private val linkRx = Regex("""^\s*\[[^\]\n]*]\(nota:(\d+)\)\s*$""")

    /** La nota da aprire dalla notifica di un promemoria nato da una riga; null per gli altri. */
    fun noteOf(notes: String): Long? = linkRx.find(notes)?.groupValues?.get(1)?.toLongOrNull()

    /** "stasera alle 19", "domani alle 9", "lun 1 dic alle 15:30": per il fumetto "Te lo ricordo …". */
    fun whenText(at: LocalDateTime, today: LocalDate): String {
        val hour = if (at.minute == 0) "${at.hour}" else "%d:%02d".format(at.hour, at.minute)
        val day = when (at.toLocalDate()) {
            today -> if (at.hour >= 18) "stasera" else "oggi"
            today.plusDays(1) -> "domani"
            else -> at.format(java.time.format.DateTimeFormatter.ofPattern("EEE d MMM", java.util.Locale.ITALIAN))
        }
        return "$day alle $hour"
    }
}
