package com.platinum20.dashboard.notes

import java.text.Normalizer

/** Un esempio tappabile in Date: apre il dialogo già compilato. */
data class DateIdea(val title: String, val yearly: Boolean)

/** Esempi per riempire la scheda Date quando ha poche voci, così la pagina non resta mezza vuota. */
object DateIdeas {
    /** Da quante date in su gli esempi spariscono. */
    const val UNTIL = 3

    val all = listOf(
        DateIdea("Compleanno di ", yearly = true),
        DateIdea("Anniversario", yearly = true),
        DateIdea("Scadenza passaporto", yearly = false),
        DateIdea("Revisione auto", yearly = false),
        DateIdea("Vacanze", yearly = false),
    )

    /** Gli esempi da mostrare: nessuno da [UNTIL] date in su, e mai uno che c'è già. */
    fun suggest(existing: List<Countdown>, max: Int = 4): List<DateIdea> {
        if (existing.size >= UNTIL) return emptyList()
        val titles = existing.map { key(it.title) }
        return all.filter { idea -> titles.none { it.startsWith(key(idea.title)) } }.take(max)
    }

    private fun key(text: String): String =
        Normalizer.normalize(text.trim().lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}"), "")
}
