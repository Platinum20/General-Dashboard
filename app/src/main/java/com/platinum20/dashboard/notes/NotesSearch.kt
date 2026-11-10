package com.platinum20.dashboard.notes

import java.text.Normalizer

/** Dove sta un risultato della ricerca, nell'ordine in cui compaiono i gruppi. */
enum class SearchKind(val label: String) {
    TASK("Promemoria"),
    NOTE("Note"),
    PARCEL("Pacchi"),
    DATE("Date"),
    HABIT("Abitudini"),
}

data class SearchHit(val kind: SearchKind, val id: Long, val title: String)

/**
 * Cerca in tutte le Note insieme: promemoria (anche completati), note libere, pacchi (nome e codice), date e abitudini. Senza badare ad accenti
 * e maiuscole. Funzione pura: si prova senza telefono.
 */
object NotesSearch {
    private val MARKS = Regex("""\p{M}+""")

    fun normalize(text: String): String =
        MARKS.replace(Normalizer.normalize(text, Normalizer.Form.NFD), "").lowercase().trim()

    fun search(data: NotesData, query: String): List<SearchHit> {
        val q = normalize(query)
        if (q.isEmpty()) return emptyList()
        fun hit(vararg texts: String) = texts.any { normalize(it).contains(q) }
        return buildList {
            // Prima quelli ancora da fare, poi i completati.
            // Le date di tutto il giorno sono promemoria (1.361), ma si cercano con la loro riga "12 marzo · compie…".
            data.tasks.sortedBy { it.done }.filter { hit(it.text) }
                .forEach { add(SearchHit(if (it.allDay) SearchKind.DATE else SearchKind.TASK, it.id, it.text)) }
            data.notes.sortedByDescending { it.updatedAt }.filter { hit(it.text) }
                .forEach { add(SearchHit(SearchKind.NOTE, it.id, it.title.ifEmpty { "Nota" })) }
            data.parcels.filter { hit(it.title, it.code) }
                .forEach { add(SearchHit(SearchKind.PARCEL, it.id, it.title)) }
            data.habits.filter { hit(it.title) }
                .forEach { add(SearchHit(SearchKind.HABIT, it.id, it.title)) }
        }
    }
}
