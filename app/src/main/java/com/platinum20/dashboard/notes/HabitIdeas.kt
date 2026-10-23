package com.platinum20.dashboard.notes

import java.text.Normalizer

/** Un esempio tappabile in Abitudini: apre il dialogo già compilato. */
data class HabitIdea(val title: String, val perWeek: Int = 0) {
    /** "Ogni giorno" o "3 volte a settimana", come nel dialogo. */
    val frequency: String get() = if (perWeek in 1..6) "$perWeek volte a settimana" else "Ogni giorno"
}

/** Esempi per la scheda Abitudini quando ne ha poche, così la pagina non resta mezza vuota. */
object HabitIdeas {
    /** Da quante abitudini in su gli esempi spariscono. */
    const val UNTIL = 3

    val all = listOf(
        HabitIdea("Leggere 20 minuti"),
        HabitIdea("Palestra", perWeek = 3),
        HabitIdea("Niente telefono a letto"),
        HabitIdea("Bere 2 litri d'acqua"),
        HabitIdea("Camminare 30 minuti"),
    )

    /** Gli esempi da mostrare: nessuno da [UNTIL] abitudini in su, e mai uno simile a una che c'è già. */
    fun suggest(existing: List<Habit>, max: Int = 3): List<HabitIdea> {
        if (existing.size >= UNTIL) return emptyList()
        val titles = existing.map { key(it.title) }
        return all.filter { idea ->
            val k = key(idea.title)
            // "Palestra 3 volte" o "palestra" contano come la stessa idea.
            titles.none { it.startsWith(k) || k.startsWith(it) }
        }.take(max)
    }

    private fun key(text: String): String =
        Normalizer.normalize(text.trim().lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}"), "")
}
