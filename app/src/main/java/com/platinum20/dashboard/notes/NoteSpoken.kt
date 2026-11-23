package com.platinum20.dashboard.notes

/**
 * Le righe di una nota come si dicono a voce o si cercano su Spotify (1.606): senza segni di lista, link, formati,
 * foto, audio e righe-blocco. Pure, provate nei test.
 */
object NoteSpoken {
    private val linkRx = Regex("""\[([^\]\n]+)]\([^)\s]+\)""")
    private val urlRx = Regex("""https?://\S+""")

    /** Il testo leggibile di ogni riga del corpo, vuote e blocchi esclusi. */
    fun lines(body: String): List<String> = NoteLines.parse(body).mapNotNull { line ->
        when (line.kind) {
            NoteLines.Kind.DIVIDER, NoteLines.Kind.AUDIO, NoteLines.Kind.IMAGE, NoteLines.Kind.BOARD,
            NoteLines.Kind.BOOKMARK, NoteLines.Kind.PAGE -> null
            NoteLines.Kind.TABLE -> {
                val raw = body.substring(line.start, line.end)
                NoteTable.cells(raw).filter { c -> c.isNotBlank() && !c.all { it == '-' || it == ':' } }
                    .joinToString(", ").ifBlank { null }
            }
            NoteLines.Kind.COLUMNS -> body.substring(line.start, line.end).split('‖').map { it.trim() }.filter { it.isNotEmpty() }.joinToString(", ")
            else -> clean(NoteLines.content(body, line))
        }?.takeIf { it.isNotBlank() }
    }

    /** Una riga senza link (resta il loro nome), indirizzi e segni dei formati. */
    fun clean(text: String): String {
        val noLinks = linkRx.replace(text) { it.groupValues[1] }
        return NoteFormat.plain(urlRx.replace(noLinks, "")).replace(Regex("\\s+"), " ").trim()
    }

    /** "Ascolta" (idea 29): il titolo e poi una parte per riga, come un articolo del lettore. */
    fun script(title: String, body: String): List<String> =
        (listOf(clean(title)) + lines(body)).map { it.trim() }.filter { it.isNotBlank() }

    private val songRx = Regex("""^(.{1,80}?)\s+[-–—]\s+(.{1,80})$""")

    /**
     * I brani scritti come "Artista – Titolo" (o "Titolo - Artista"), uno per riga (idea 18): la ricerca da fare su
     * Spotify. Servono almeno due righe così, se no la nota non è un elenco di canzoni.
     */
    fun songs(body: String): List<String> {
        val found = lines(body).mapNotNull { l ->
            val m = songRx.matchEntire(l) ?: return@mapNotNull null
            val a = m.groupValues[1].trim()
            val b = m.groupValues[2].trim()
            if (a.isEmpty() || b.isEmpty() || a.any { it == ':' } || b.count { it == ' ' } > 10) null else "$a $b"
        }.distinct()
        return if (found.size >= 2) found.take(MAX_SONGS) else emptyList()
    }

    /** Una playlist Spotify ne prende 100 alla volta: oltre, una nota non è più un elenco da ascoltare. */
    const val MAX_SONGS = 100
}
