package com.platinum20.dashboard.notes

/**
 * I formati delle note lunghe (1.529): grassetto `**così**`, corsivo `*così*`, barrato `~~così~~` e titoletto `## ` a
 * inizio riga. Restano nel testo come segni (si condividono e si copiano ovunque); l'editor li disegna e li sbiadisce,
 * le righe della linea del tempo li tolgono ([plain]).
 */
object NoteFormat {
    enum class Kind { BOLD, ITALIC, STRIKE, HEADING, MARK }

    /** Un pezzo di testo da disegnare in un modo: [end] escluso. */
    data class Span(val start: Int, val end: Int, val kind: Kind)

    const val BOLD = "**"
    const val ITALIC = "*"
    const val STRIKE = "~~"
    const val HEADING = "## "

    private val boldRx = Regex("""\*\*(?=\S)(.+?)(?<=\S)\*\*""")
    private val strikeRx = Regex("""~~(?=\S)(.+?)(?<=\S)~~""")
    private val italicRx = Regex("""(?<![*\w])\*(?=[^\s*])(.+?)(?<=[^\s*])\*(?![*\w])""")
    private val headingRx = Regex("""(?m)^## (.*)$""")

    /** Dove disegnare ogni formato e quali segni sbiadire. */
    fun spans(text: String): List<Span> {
        val out = mutableListOf<Span>()
        fun wrapped(rx: Regex, kind: Kind, mark: Int) {
            rx.findAll(text).forEach { m ->
                out += Span(m.range.first, m.range.first + mark, Kind.MARK)
                out += Span(m.range.first + mark, m.range.last + 1 - mark, kind)
                out += Span(m.range.last + 1 - mark, m.range.last + 1, Kind.MARK)
            }
        }
        headingRx.findAll(text).forEach { m ->
            out += Span(m.range.first, m.range.first + HEADING.length, Kind.MARK)
            out += Span(m.range.first + HEADING.length, m.range.last + 1, Kind.HEADING)
        }
        wrapped(boldRx, Kind.BOLD, BOLD.length)
        wrapped(strikeRx, Kind.STRIKE, STRIKE.length)
        // Il corsivo non deve prendere le stelle del grassetto: si cerca nel testo con il grassetto coperto.
        val masked = boldRx.replace(text) { " ".repeat(it.value.length) }
        italicRx.findAll(masked).forEach { m ->
            out += Span(m.range.first, m.range.first + 1, Kind.MARK)
            out += Span(m.range.first + 1, m.range.last, Kind.ITALIC)
            out += Span(m.range.last, m.range.last + 1, Kind.MARK)
        }
        return out.filter { it.end > it.start }.sortedBy { it.start }
    }

    /** Il testo senza segni, per le righe e i titoli fuori dall'editor. */
    fun plain(text: String): String {
        var t = headingRx.replace(text) { it.groupValues[1] }
        t = boldRx.replace(t) { it.groupValues[1] }
        t = strikeRx.replace(t) { it.groupValues[1] }
        t = italicRx.replace(t) { it.groupValues[1] }
        return t
    }

    /** Il risultato di un tocco su un formato: testo nuovo e selezione nuova. */
    data class Edit(val text: String, val start: Int, val end: Int)

    /**
     * Mette o toglie [mark] attorno alla selezione: se è già dentro (o la selezione comprende i segni) li toglie,
     * altrimenti li aggiunge. Con la selezione vuota mette i due segni e il cursore in mezzo.
     */
    fun toggle(text: String, start: Int, end: Int, mark: String): Edit {
        val s = minOf(start, end).coerceIn(0, text.length)
        val e = maxOf(start, end).coerceIn(0, text.length)
        val inner = text.substring(s, e)
        // La selezione comprende già i segni.
        if (inner.length >= 2 * mark.length && inner.startsWith(mark) && inner.endsWith(mark) && !(mark == ITALIC && inner.startsWith(BOLD) && !inner.startsWith("***"))) {
            val bare = inner.substring(mark.length, inner.length - mark.length)
            return Edit(text.substring(0, s) + bare + text.substring(e), s, s + bare.length)
        }
        // I segni stanno appena fuori dalla selezione.
        val before = s >= mark.length && text.regionMatches(s - mark.length, mark, 0, mark.length)
        val after = text.regionMatches(e, mark, 0, mark.length)
        val boldAround = mark == ITALIC && (s >= 2 && text.regionMatches(s - 2, BOLD, 0, 2)) && !(s >= 3 && text.regionMatches(s - 3, "***", 0, 3))
        if (before && after && !boldAround) {
            val t = text.substring(0, s - mark.length) + inner + text.substring(e + mark.length)
            return Edit(t, s - mark.length, e - mark.length)
        }
        // Gli spazi ai bordi restano fuori, altrimenti il formato non si riconosce.
        val lead = inner.length - inner.trimStart().length
        val trail = inner.length - inner.trimEnd().length
        val a = s + lead
        val b = maxOf(a, e - trail)
        val t = text.substring(0, a) + mark + text.substring(a, b) + mark + text.substring(b)
        return Edit(t, a + mark.length, b + mark.length)
    }

    /** Mette o toglie "## " all'inizio di ogni riga toccata dalla selezione. */
    fun heading(text: String, start: Int, end: Int): Edit {
        val s = minOf(start, end).coerceIn(0, text.length)
        val e = maxOf(start, end).coerceIn(0, text.length)
        val first = text.lastIndexOf('\n', s - 1) + 1
        val lastEnd = text.indexOf('\n', e).let { if (it < 0) text.length else it }
        val lines = text.substring(first, lastEnd).split('\n')
        val off = lines.all { it.startsWith(HEADING) }
        val changed = lines.joinToString("\n") { if (off) it.removePrefix(HEADING) else if (it.startsWith(HEADING)) it else HEADING + it }
        val delta = changed.length - (lastEnd - first)
        val t = text.substring(0, first) + changed + text.substring(lastEnd)
        val shift = if (off) -HEADING.length else if (text.startsWith(HEADING, first)) 0 else HEADING.length
        return Edit(t, (s + shift).coerceIn(first, t.length), (e + delta).coerceIn(0, t.length))
    }

    /** Il formato che c'è già dove sta la selezione: i chip accesi. */
    fun active(text: String, start: Int, end: Int): Set<Kind> {
        val s = minOf(start, end)
        val e = maxOf(start, end)
        return spans(text).filter { it.kind != Kind.MARK && it.start <= s && it.end >= e && it.end > it.start }.map { it.kind }.toSet()
    }
}
