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

    /** Sulla linea del tempo, dopo il titolo: "2/5" per le liste, niente per il testo normale. */
    fun badge(text: String): String? = Checklist.progress(text)?.let { (done, total) -> "$done/$total" }
}
