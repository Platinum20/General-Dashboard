package com.platinum20.dashboard.notes

/**
 * Le liste delle note in Home "Da fare" (Antonio, 2026-11-23, idea 4): una nota con delle spunte aperte (la spesa,
 * la valigia) diventa una riga sola "Spesa · 3 da fare su 5", che si apre e si spunta lì, senza aprire la nota.
 * Funzioni pure: si provano senza telefono.
 */
object NoteLists {
    /** Una voce della lista: [index] = riga nel testo intero della nota (0 = titolo), per [Checklist.toggle]. */
    data class Entry(val index: Int, val text: String, val done: Boolean)

    data class Row(val noteId: Long, val title: String, val entries: List<Entry>, val pinned: Boolean) {
        val open: Int get() = entries.count { !it.done }
        val total: Int get() = entries.size
    }

    /** Una nota come lista, null se non ha voci da spuntare. Le voci senza testo non contano. */
    fun of(note: Note): Row? {
        val entries = Checklist.lines(note.text).filterIsInstance<Checklist.Item>()
            .map { Entry(it.index, NoteFormat.plain(it.text).trim(), it.done) }
            .filter { it.text.isNotEmpty() }
        if (entries.isEmpty()) return null
        return Row(note.id, note.title.ifBlank { "Lista" }, entries, note.pinned)
    }

    /**
     * Le liste da mostrare in "Da fare": note attive (non archiviate) con almeno una voce da fare, le fissate prima,
     * poi dall'ultima toccata. [keep] = liste già in vista che restano anche finite (spuntando l'ultima voce la riga non
     * deve sparire sotto il dito).
     */
    fun rows(notes: List<Note>, keep: Set<Long> = emptySet()): List<Row> = notes
        .asSequence()
        .filter { it.archivedAt == null }
        .sortedWith(compareByDescending<Note> { it.pinned }.thenByDescending { it.updatedAt })
        .mapNotNull { n -> of(n)?.takeIf { it.open > 0 || n.id in keep } }
        .toList()

    /** La riga sotto il titolo: "3 da fare su 5 · dalla nota", "Tutto fatto · 5 di 5". */
    fun line(row: Row): String =
        if (row.open == 0) "Tutto fatto · ${row.total} di ${row.total}" else "${row.open} da fare su ${row.total} · dalla nota"

    /** "Ricomincia" (idea 15): tutte le spunte via, il testo resta uguale. */
    fun restart(text: String): String {
        var t = text
        Checklist.lines(text).filterIsInstance<Checklist.Item>().filter { it.done }.forEach { t = Checklist.toggle(t, it.index) }
        return t
    }

    /** Quante spunte toglie "Ricomincia": il sottotitolo del fumetto. */
    fun doneCount(text: String): Int = Checklist.lines(text).count { it is Checklist.Item && it.done }

    /** "Duplica" (idea 15): stessa nota con il titolo "Valigia (2)", le spunte da rifare. */
    fun duplicate(text: String): String {
        val title = text.lineSequence().firstOrNull().orEmpty()
        val rest = text.substringAfter('\n', "")
        val copy = NoteText.copyTitle(title).ifBlank { "Lista (2)" }
        return restart(if (rest.isEmpty()) copy else "$copy\n$rest")
    }
}
