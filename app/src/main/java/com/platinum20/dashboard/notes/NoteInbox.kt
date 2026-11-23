package com.platinum20.dashboard.notes

/**
 * Le cose che arrivano nelle note da fuori dall'editor (1.606): un minuto dal player, una frase dalle News,
 * le righe utili di una mail, quello condiviso da un'altra app. Sempre in fondo alla nota, mai sopra il testo.
 */
object NoteInbox {
    /** Il testo di [text] con [lines] in fondo, su righe nuove. Pura. */
    fun appended(text: String, lines: List<String>): String {
        val add = lines.filter { it.isNotBlank() }
        if (add.isEmpty()) return text
        val base = text.trimEnd('\n', ' ')
        return if (base.isEmpty()) add.joinToString("\n") else base + "\n" + add.joinToString("\n")
    }

    /** La nota (non in archivio, non una cosa salvata) che ha proprio questo titolo, se c'è. Pura. */
    fun byTitle(notes: List<Note>, title: String): Note? = notes.firstOrNull {
        it.archivedAt == null && it.plainTitle.equals(title.trim(), ignoreCase = true)
    }

    /** Le note modificate per ultime, per "Aggiungi a …": quelle scritte a mano, non le cose salvate. Pura. */
    fun recent(notes: List<Note>, max: Int = 3, isSaved: (Note) -> Boolean = { false }): List<Note> = notes
        .filter { it.archivedAt == null && it.plainTitle.isNotBlank() && !isSaved(it) }
        .sortedByDescending { it.updatedAt }
        .take(max)

    /** Un'etichetta che apre una pagina dell'app, come quelle messe con "@": `[titolo](app:pagina)`. Pura. */
    fun appLink(title: String, route: String): String {
        val clean = title.replace('[', '(').replace(']', ')').replace('\n', ' ').trim().ifEmpty { "Apri" }
        val safe = route.replace(" ", "%20").replace("(", "%28").replace(")", "%29")
        return "[$clean](${NoteFormat.APP_LINK}$safe)"
    }

    /** La nota delle frasi salvate dal lettore (idea 6). */
    const val NEWS_NOTE = "Dalle news"

    /** Una frase dal lettore: citazione e, sotto, l'etichetta che riapre l'articolo. Pura. */
    fun quoteLines(phrase: String, articleTitle: String, route: String): List<String> {
        val one = phrase.replace(Regex("\\s+"), " ").trim()
        if (one.isEmpty()) return emptyList()
        return listOf(NoteLines.QUOTE + one, "📰 " + appLink(articleTitle.ifBlank { "Articolo" }, route))
    }

    /** Mette [lines] in fondo alla nota [id]; risponde se la nota c'era. */
    fun appendTo(id: Long, lines: List<String>): Boolean {
        val note = NotesStore.note(id) ?: return false
        NotesStore.saveNote(id, appended(note.text, lines))
        return true
    }

    /** Mette [lines] in fondo alla nota che si chiama [title], creandola se non c'è. Risponde il suo id. */
    fun appendToTitled(title: String, lines: List<String>): Long? {
        val existing = byTitle(NotesStore.data.value.notes, title)
        if (existing != null) {
            NotesStore.saveNote(existing.id, appended(existing.text, lines))
            return existing.id
        }
        return NotesStore.saveNote(null, appended(title.trim(), lines))
    }
}
