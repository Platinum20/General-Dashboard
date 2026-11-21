package com.platinum20.dashboard.notes

import com.platinum20.dashboard.capture.SavedLinks

/** Quello che si dice di una nota lunga senza aprirla: quante parole, quanto ci vuole a leggerla. */
object NoteText {
    /** Sotto questa soglia il tempo di lettura non dice niente: si legge in un attimo. */
    private const val READING_FROM = 200
    private const val WORDS_PER_MINUTE = 200

    fun words(text: String): Int =
        SavedLinks.withoutLink(text).split(Regex("\\s+")).count { word -> word.any { it.isLetterOrDigit() } }

    /** "230 parole · 2 min" (minuti di lettura), null per una nota vuota. */
    fun stats(text: String): String? {
        val n = words(text)
        if (n == 0) return null
        val count = if (n == 1) "1 parola" else "%,d parole".format(java.util.Locale.ITALIAN, n)
        if (n < READING_FROM) return count
        val minutes = (n + WORDS_PER_MINUTE - 1) / WORDS_PER_MINUTE
        return "$count · $minutes min"
    }

    /**
     * La riga sopra una nota aperta, con l'orario vero: solo il momento più recente, perché la riga intera stia su una
     * riga (1.552): "Scritta oggi, 10:05" oppure "Modificata 14 nov, 09:44" (la modifica conta da un minuto dopo).
     */
    fun moment(createdAt: Long, updatedAt: Long, now: Long = System.currentTimeMillis(), zone: java.time.ZoneId = java.time.ZoneId.systemDefault()): String {
        val today = java.time.Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val edited = updatedAt - createdAt >= 60_000
        val t = java.time.Instant.ofEpochMilli(if (edited) updatedAt else createdAt).atZone(zone)
        return (if (edited) "Modificata " else "Scritta ") + day(t.toLocalDate(), today) + ", " + hour(t)
    }

    private fun hour(t: java.time.ZonedDateTime): String = "%02d:%02d".format(t.hour, t.minute)

    private fun day(d: java.time.LocalDate, today: java.time.LocalDate): String = when (d) {
        today -> "oggi"
        today.minusDays(1) -> "ieri"
        else -> d.format(
            java.time.format.DateTimeFormatter.ofPattern(if (d.year == today.year) "d MMM" else "d MMM yyyy", java.util.Locale.ITALIAN),
        )
    }

    /** Sulla linea del tempo, dopo il titolo: "2/5" per le liste, niente per il testo normale. */
    fun badge(text: String): String? = Checklist.progress(text)?.let { (done, total) -> "$done/$total" }

    /** Aggiunge in fondo alla nota il testo dettato (1.528): a capo se l'ultima riga ha già qualcosa. */
    fun append(text: String, heard: String): String {
        val add = heard.trim().replaceFirstChar { it.uppercase() }
        if (add.isEmpty()) return text
        return when {
            text.isBlank() -> add
            text.endsWith("\n") -> text + add
            text.endsWith(" ") -> text + add
            else -> "$text $add"
        }
    }

    /** Il titolo (prima riga) e il resto: nell'editor stanno in due campi con le foto in mezzo (1.529). */
    fun split(text: String): Pair<String, String> {
        val cut = text.indexOf('\n')
        return if (cut < 0) text to "" else text.substring(0, cut) to text.substring(cut + 1)
    }

    /** L'inverso di [split]: una nota con solo il titolo non porta un a capo in fondo. */
    fun join(title: String, body: String): String = if (body.isEmpty()) title else "$title\n$body"

    /** Inserisce il testo dettato dove sta il cursore, con lo spazio giusto prima e dopo; restituisce testo e cursore. */
    fun insert(text: String, at: Int, heard: String): Pair<String, Int> {
        val add = heard.trim()
        if (add.isEmpty()) return text to at
        val i = at.coerceIn(0, text.length)
        val before = text.substring(0, i)
        val after = text.substring(i)
        val sentence = before.isBlank() || before.trimEnd().lastOrNull() in listOf('.', '!', '?', '\n') || before.endsWith("\n")
        val word = if (sentence) add.replaceFirstChar { it.uppercase() } else add
        val pre = if (before.isEmpty() || before.endsWith(" ") || before.endsWith("\n")) "" else " "
        val post = if (after.isEmpty() || after.startsWith(" ") || after.startsWith("\n")) "" else " "
        val piece = pre + word + post
        return before + piece + after to i + pre.length + word.length
    }
}
