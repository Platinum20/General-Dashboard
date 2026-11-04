package com.platinum20.dashboard.notes

import com.platinum20.dashboard.capture.SavedLinks

/** Quello che si dice di una nota lunga senza aprirla: quante parole, quanto ci vuole a leggerla. */
object NoteText {
    /** Sotto questa soglia il tempo di lettura non dice niente: si legge in un attimo. */
    private const val READING_FROM = 200
    private const val WORDS_PER_MINUTE = 200

    fun words(text: String): Int =
        SavedLinks.withoutLink(text).split(Regex("\\s+")).count { word -> word.any { it.isLetterOrDigit() } }

    /** "230 parole · 2 min di lettura", null per una nota vuota. */
    fun stats(text: String): String? {
        val n = words(text)
        if (n == 0) return null
        val count = if (n == 1) "1 parola" else "%,d parole".format(java.util.Locale.ITALIAN, n)
        if (n < READING_FROM) return count
        val minutes = (n + WORDS_PER_MINUTE - 1) / WORDS_PER_MINUTE
        return "$count · $minutes min di lettura"
    }

    /**
     * La riga sopra una nota aperta, con l'orario vero: "Scritta oggi, 10:05 · modificata 11:20".
     * La modifica si dice solo se è venuta dopo (almeno un minuto); il giorno si ripete solo se cambia.
     */
    fun moment(createdAt: Long, updatedAt: Long, now: Long = System.currentTimeMillis(), zone: java.time.ZoneId = java.time.ZoneId.systemDefault()): String {
        val today = java.time.Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val created = java.time.Instant.ofEpochMilli(createdAt).atZone(zone)
        val first = "Scritta " + day(created.toLocalDate(), today) + ", " + hour(created)
        if (updatedAt - createdAt < 60_000) return first
        val updated = java.time.Instant.ofEpochMilli(updatedAt).atZone(zone)
        val sameDay = updated.toLocalDate() == created.toLocalDate()
        return first + " · modificata " + (if (sameDay) "" else day(updated.toLocalDate(), today) + ", ") + hour(updated)
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
}
