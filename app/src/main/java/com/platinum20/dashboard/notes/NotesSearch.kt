package com.platinum20.dashboard.notes

import java.text.Normalizer

/** Dove sta un risultato della ricerca, nell'ordine in cui compaiono i gruppi. */
enum class SearchKind(val label: String) {
    TASK("Promemoria"),
    NOTE("Note"),
    EXPENSE("Spese"),
    PARCEL("Pacchi"),
    DATE("Date"),
    HABIT("Abitudini"),
}

data class SearchHit(val kind: SearchKind, val id: Long, val title: String)

/**
 * Cerca in tutte le Note insieme: promemoria (anche completati), note libere, spese
 * (testo e importo), pacchi (nome e codice), date e abitudini. Senza badare ad accenti
 * e maiuscole. Funzione pura: si prova senza telefono.
 */
object NotesSearch {
    private val AMOUNT = Regex("""^\d+(?:[.,]\d{0,2})?$""")
    private val MARKS = Regex("""\p{M}+""")

    fun normalize(text: String): String =
        MARKS.replace(Normalizer.normalize(text, Normalizer.Form.NFD), "").lowercase().trim()

    /** "12,5" trova 12,50 €; "12" trova 12,00 € e 12,90 € ma non 120 €. */
    fun amountMatches(query: String, cents: Long): Boolean {
        val q = query.trim().replace('.', ',')
        if (!AMOUNT.matches(q)) return false
        val shown = "${cents / 100},${"%02d".format(cents % 100)}"
        return if (',' in q) shown.startsWith(q) else shown.startsWith("$q,")
    }

    fun search(data: NotesData, query: String): List<SearchHit> {
        val q = normalize(query)
        if (q.isEmpty()) return emptyList()
        fun hit(vararg texts: String) = texts.any { normalize(it).contains(q) }
        return buildList {
            // Prima quelli ancora da fare, poi i completati.
            data.tasks.sortedBy { it.done }.filter { hit(it.text) }
                .forEach { add(SearchHit(SearchKind.TASK, it.id, it.text)) }
            data.notes.sortedByDescending { it.updatedAt }.filter { hit(it.text) }
                .forEach { add(SearchHit(SearchKind.NOTE, it.id, it.title.ifEmpty { "Nota" })) }
            data.expenses.sortedByDescending { it.date }
                .filter { hit(it.text, it.category.label) || amountMatches(query, it.cents) }
                .forEach { add(SearchHit(SearchKind.EXPENSE, it.id, it.text)) }
            data.parcels.filter { hit(it.title, it.code) }
                .forEach { add(SearchHit(SearchKind.PARCEL, it.id, it.title)) }
            data.countdowns.filter { hit(it.title) }
                .forEach { add(SearchHit(SearchKind.DATE, it.id, it.title)) }
            data.habits.filter { hit(it.title) }
                .forEach { add(SearchHit(SearchKind.HABIT, it.id, it.title)) }
        }
    }
}
