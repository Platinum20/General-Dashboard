package com.platinum20.dashboard.notes

import com.platinum20.dashboard.search.SearchMatch
import java.text.Normalizer

/** Dove sta un risultato della ricerca, nell'ordine in cui compaiono i gruppi. */
enum class SearchKind(val label: String) {
    TASK("Promemoria"),
    NOTE("Note"),
    PARCEL("Pacchi"),
    DATE("Date"),
    HABIT("Abitudini"),
}

/**
 * Un risultato. [line] = per le note, la riga dove c'è la parola (Antonio, 2026-11-23, idea 9), già accorciata;
 * [inPhoto] = la parola sta nel testo letto da una foto della nota (idea 30). Null quando basta il titolo.
 */
data class SearchHit(
    val kind: SearchKind,
    val id: Long,
    val title: String,
    val line: String? = null,
    val inPhoto: Boolean = false,
    /** Quanto vale per la ricerca ([com.platinum20.dashboard.search.SearchMatch.score]): sceglie il risultato migliore. */
    val score: Int = 0,
)

/**
 * Cerca in tutte le Note insieme: promemoria (anche completati), note libere, pacchi (nome e codice), date e abitudini. Senza badare ad accenti
 * e maiuscole. Funzione pura: si prova senza telefono.
 */
object NotesSearch {
    private val MARKS = Regex("""\p{M}+""")

    fun normalize(text: String): String =
        MARKS.replace(Normalizer.normalize(text, Normalizer.Form.NFD), "").lowercase().trim()

    /**
     * [photoText] = il testo letto da ogni foto, per percorso ([NotePhotoText]): una nota si trova anche per quello
     * scritto nelle sue foto (la lavagna, lo scontrino della garanzia).
     */
    fun search(data: NotesData, query: String, photoText: Map<String, String> = emptyMap()): List<SearchHit> {
        if (normalize(query).isEmpty()) return emptyList()
        // Le parole in qualunque ordine e con un errore di battitura (2026-11-26, punto 1): vedi SearchMatch.
        fun score(title: String, vararg more: String) = SearchMatch.score(query, title, *more)
        return buildList {
            // Prima quelli ancora da fare, poi i completati.
            // Le date di tutto il giorno sono promemoria (1.361), ma si cercano con la loro riga "12 marzo · compie…".
            data.tasks.sortedBy { it.done }.forEach { t ->
                val s = score(t.text, t.notes, t.place)
                if (s > 0) add(SearchHit(if (t.allDay) SearchKind.DATE else SearchKind.TASK, t.id, t.text, score = s))
            }
            data.notes.sortedByDescending { it.updatedAt }.forEach { n -> noteHit(n, query, photoText)?.let(::add) }
            data.parcels.forEach { p -> score(p.title, p.code).takeIf { it > 0 }?.let { add(SearchHit(SearchKind.PARCEL, p.id, p.title, score = it)) } }
            data.habits.forEach { h -> score(h.title).takeIf { it > 0 }?.let { add(SearchHit(SearchKind.HABIT, h.id, h.title, score = it)) } }
        }
    }

    /** Le righe di una nota da guardare: senza segni dei formati, senza i blocchi (foto, audio, righe divisorie). */
    private fun noteLines(n: Note): List<String> = n.text.lines().drop(1).mapNotNull { line ->
        when (NoteLines.kindOf(line).first) {
            NoteLines.Kind.IMAGE, NoteLines.Kind.AUDIO, NoteLines.Kind.DIVIDER -> null
            NoteLines.Kind.TABLE -> line.split('|').map { it.trim() }.filter { it.isNotEmpty() }.joinToString(" · ")
            else -> NoteLines.plainLine(line)
        }?.takeIf { it.isNotBlank() }
    }

    /** Le foto della nota: quelle in cima e quelle messe nel testo. */
    fun photosOf(n: Note): List<String> = (n.photos + n.text.lines().mapNotNull(NoteLines::imagePath)).distinct()

    private fun noteHit(n: Note, query: String, photoText: Map<String, String>): SearchHit? {
        val title = n.title.ifEmpty { "Nota" }
        val lines = noteLines(n)
        // La riga con più parole cercate, anche quando c'è nel titolo (una riga sola sotto, come nella card).
        fun bestLine(candidates: List<String>) = candidates.map { it to SearchMatch.count(it, query) }
            .filter { it.second > 0 }.maxByOrNull { it.second }?.first
        val head = if (n.emoji != null) n.title else n.plainTitle
        val s = SearchMatch.score(query, head, lines.joinToString("\n"))
        if (s > 0) return SearchHit(SearchKind.NOTE, n.id, title, bestLine(lines)?.let { snippet(it, query) }, score = s)
        val photoLines = photosOf(n).asSequence().mapNotNull { photoText[it] }.flatMap { it.lineSequence() }.map { it.trim() }.filter { it.isNotEmpty() }.toList()
        if (photoLines.isEmpty()) return null
        val sp = SearchMatch.score(query, head, lines.joinToString("\n"), photoLines.joinToString("\n"))
        if (sp == 0) return null
        return SearchHit(SearchKind.NOTE, n.id, title, bestLine(photoLines)?.let { snippet(it, query) }, inPhoto = true, score = sp)
    }

    /**
     * La riga accorciata attorno alla parola, così la parola si vede anche in una riga lunga: al più [max] lettere,
     * "…" dove è tagliata.
     */
    fun snippet(line: String, query: String, max: Int = 70): String {
        val text = line.trim()
        if (text.length <= max) return text
        val at = SearchMatch.ranges(text, query).firstOrNull()?.first ?: 0
        val start = (at - max / 3).coerceIn(0, text.length - max)
        val end = start + max
        return (if (start > 0) "…" else "") + text.substring(start, end).trim() + (if (end < text.length) "…" else "")
    }

    /**
     * Dove sta la parola cercata nel testo vero, senza badare ad accenti e maiuscole: per colorarla nel risultato.
     * Null se non c'è.
     */
    fun matchRange(text: String, query: String): IntRange? {
        val q = normalize(query)
        if (q.isEmpty()) return null
        // Ogni lettera del testo con quello che diventa normalizzata (una "è" = "e", un segno da solo = niente).
        val pieces = text.map { MARKS.replace(Normalizer.normalize(it.toString(), Normalizer.Form.NFD), "").lowercase() }
        val flat = StringBuilder()
        val owner = mutableListOf<Int>()
        pieces.forEachIndexed { i, p -> p.forEach { c -> flat.append(c); owner += i } }
        val found = flat.indexOf(q)
        if (found < 0) return null
        return owner[found]..owner[found + q.length - 1]
    }
}
