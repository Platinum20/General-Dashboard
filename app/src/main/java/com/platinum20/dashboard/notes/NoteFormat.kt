package com.platinum20.dashboard.notes

/**
 * I formati delle note lunghe: grassetto `**così**`, corsivo `_così_` (si legge anche il vecchio `*così*`), barrato
 * `~~così~~` e titoletto `## ` a inizio riga. Restano nel testo come segni, così le note vecchie si aprono uguali, ma
 * nell'editor non si vedono mai (1.577, Antonio: niente asterischi né cancelletti): si scrive come in un editor vero,
 * e [afterEdit] fa in modo che cancellare o andare a capo non rompa mai un segno. Fuori dall'editor si usa [plain].
 */
object NoteFormat {
    enum class Kind { BOLD, ITALIC, STRIKE, HEADING, MARK, HIGHLIGHT, LINK, URL, COLOR }

    /** Un pezzo di testo da disegnare in un modo: [end] escluso. */
    data class Span(val start: Int, val end: Int, val kind: Kind)

    /** Un formato con i suoi due segni: il testo formattato va da [contentStart] a [contentEnd] (escluso). */
    private data class Format(val kind: Kind, val open: IntRange, val close: IntRange?) {
        val contentStart get() = open.last + 1
        val contentEnd get() = close?.first ?: Int.MAX_VALUE
    }

    const val BOLD = "**"
    const val ITALIC = "_"
    const val STRIKE = "~~"
    const val HIGHLIGHT = "=="
    const val HEADING = "## "

    // La fine può avere spazi: mentre si scrive in grassetto la parola appena finita non deve far ricomparire i segni.
    private val boldRx = Regex("""\*\*(?=\S)(.+?)\*\*""")
    private val strikeRx = Regex("""~~(?=\S)(.+?)~~""")
    private val italicRx = Regex("""(?<![_\w])_(?=[^\s_])(.+?)_(?![_\w])""")
    private val oldItalicRx = Regex("""(?<![*\w])\*(?=[^\s*])(.+?)(?<=[^\s*])\*(?![*\w])""")
    private val headingRx = Regex("""(?m)^## (.*)$""")
    private val highlightRx = Regex("""==(?=\S)(.+?)==""")

    /**
     * I colori (1.590): `{r:testo}` colora il testo, la lettera maiuscola `{R:testo}` lo sfondo. r rosso, a arancio,
     * g giallo, v verde, b blu, p viola, f rosa. Il colore si legge dal carattere due prima del testo ([colorAt]).
     */
    const val COLORS = "ragvbpf"
    private val colorRx = Regex("""\{([ragvbpfRAGVBPF]):(?=\S)([^\{\}\n]+?)\}""")

    /** Un link col suo testo (1.584): `[testo](https://…)` o `[Titolo](nota:12)` per un'altra nota. */
    private val linkRx = Regex("""\[([^\]\n]+)]\(([^)\s]+)\)""")
    private val urlRx = Regex("""https?://[^\s)\]]+""")
    const val NOTE_LINK = "nota:"
    const val APP_LINK = "app:"

    /** Un formato acceso senza ancora testo (G toccato senza niente di scelto): scrivendo, il testo nasce formattato. */
    private val emptyRx = Regex("""\*\*\*\*|~~~~|====|(?<![_\w])__(?![_\w])""")

    private fun formats(text: String): List<Format> {
        val out = mutableListOf<Format>()
        headingRx.findAll(text).forEach { m -> out += Format(Kind.HEADING, m.range.first until m.range.first + HEADING.length, null) }
        fun wrapped(rx: Regex, kind: Kind, mark: Int, where: String) {
            rx.findAll(where).forEach { m ->
                out += Format(kind, m.range.first until m.range.first + mark, m.range.last + 1 - mark..m.range.last)
            }
        }
        wrapped(boldRx, Kind.BOLD, BOLD.length, text)
        wrapped(strikeRx, Kind.STRIKE, STRIKE.length, text)
        wrapped(highlightRx, Kind.HIGHLIGHT, HIGHLIGHT.length, text)
        colorRx.findAll(text).forEach { m -> out += Format(Kind.COLOR, m.range.first..m.range.first + 2, m.range.last..m.range.last) }
        linkRx.findAll(text).forEach { m ->
            val label = m.groups[1]!!.range
            out += Format(Kind.LINK, m.range.first..m.range.first, label.last + 1..m.range.last)
        }
        // Il corsivo non deve prendere le stelle del grassetto: si cerca con i soli segni del grassetto coperti.
        val masked = StringBuilder(text)
        out.filter { it.kind == Kind.BOLD }.forEach { f -> (f.open + f.close!!).forEach { masked.setCharAt(it, '\u0001') } }
        wrapped(italicRx, Kind.ITALIC, 1, masked.toString())
        wrapped(oldItalicRx, Kind.ITALIC, 1, masked.toString())
        return out
    }

    /** I segni di un formato acceso e ancora vuoto ([emptyRx]) che non fanno parte di un formato vero. */
    private fun empties(text: String, marks: Set<Int>): List<IntRange> =
        emptyRx.findAll(text).map { it.range }.filter { r -> r.none { it in marks } }.toList()

    /** L'indirizzo del link (o dell'indirizzo scritto così com'è) dove sta [offset]; null = nessuno. */
    fun linkAt(text: String, offset: Int): Pair<String, String>? {
        linkRx.findAll(text).forEach { m ->
            if (offset >= m.range.first && offset <= m.range.last + 1) return m.groupValues[2] to m.groupValues[1]
        }
        urlRx.findAll(text).forEach { m ->
            if (offset >= m.range.first && offset <= m.range.last + 1) return m.value to m.value
        }
        return null
    }

    /** Gli indirizzi scritti così come sono, fuori dai link col testo: si colorano come link. */
    private fun bareUrls(text: String): List<IntRange> {
        val inLinks = linkRx.findAll(text).map { it.range }.toList()
        return urlRx.findAll(text).map { it.range }.filter { r -> inLinks.none { r.first in it } }.toList()
    }

    /**
     * Il tasto Link della barra Aa: il testo scelto diventa un link a [url]. Senza testo scelto, l'indirizzo va dove
     * sta il cursore. Un link già lì si toglie (resta il testo).
     */
    fun link(text: String, start: Int, end: Int, url: String?): Edit {
        val s = minOf(start, end).coerceIn(0, text.length)
        val e = maxOf(start, end).coerceIn(0, text.length)
        linkRx.findAll(text).firstOrNull { s >= it.range.first && s <= it.range.last }?.let { m ->
            val label = m.groupValues[1]
            val t = text.substring(0, m.range.first) + label + text.substring(m.range.last + 1)
            return Edit(t, m.range.first, m.range.first + label.length)
        }
        val u = url?.trim().orEmpty()
        if (u.isEmpty()) return Edit(text, start, end)
        if (s == e) return Edit(text.substring(0, s) + u + text.substring(s), s + u.length)
        val label = text.substring(s, e).trim().replace('\n', ' ')
        val piece = "[$label]($u)"
        return Edit(text.substring(0, s) + piece + text.substring(e), s + piece.length)
    }

    /** Un link messo con "@" (1.589): verso un'altra nota o una pagina di Canvas. Si comporta come un pezzo unico. */
    fun isMention(url: String): Boolean = url.startsWith(NOTE_LINK) || url.startsWith(APP_LINK)

    /** Gli indirizzi dei link "@" di un testo, per "Citato in" (2026-11-23). */
    fun mentionUrls(text: String): List<String> = linkRx.findAll(text).map { it.groupValues[2] }.filter(::isMention).toList()

    /** I posti dei link (con "@" o no) e degli indirizzi nudi: lì dentro non si cercano date, etichette e codici. */
    fun linkRanges(text: String): List<IntRange> = linkRx.findAll(text).map { it.range }.toList() + urlRx.findAll(text).map { it.range }

    /** Il posto dell'icona davanti a un link "@" (1.596), al posto della "[": uno spazio stretto senza "a capo". */
    const val MENTION_OPEN = "\u202F"

    /** Il posto in fondo a un link "@", al posto di "](…)": un po' d'aria prima della fine della bolla. */
    const val MENTION_CLOSE = "\u202F"

    /** I segni dei link "@" che l'editor disegna come posti (icona davanti, aria in fondo), per [NoteLines.Shown]. */
    fun mentionSlots(text: String): Map<Int, String> = linkRx.findAll(text).filter { isMention(it.groupValues[2]) }
        .flatMap { m -> listOf(m.range.first to MENTION_OPEN, m.groups[1]!!.range.last + 1 to MENTION_CLOSE) }.toMap()

    /**
     * L'emoji scritta all'inizio del nome di un link "@" (1.597), con lo spazio dopo: l'editor non la scrive, la usa come
     * icona della bolla. Così ogni bolla ha una sola icona, sempre allo stesso posto.
     */
    fun mentionEmojis(text: String): List<Pair<IntRange, String>> = linkRx.findAll(text).filter { isMention(it.groupValues[2]) }.mapNotNull { m ->
        val label = m.groups[1]!!
        val sp = label.value.indexOf(' ')
        if (sp <= 0 || sp >= label.value.length - 1) return@mapNotNull null
        val head = label.value.substring(0, sp)
        if (!isEmoji(head)) null else (label.range.first..label.range.first + sp) to head
    }.toList()

    /** Solo emoji (con i loro modificatori), niente lettere o numeri. */
    fun isEmoji(s: String): Boolean {
        if (s.isEmpty()) return false
        val cps = s.codePoints().toArray()
        fun symbol(cp: Int) = Character.getType(cp) == Character.OTHER_SYMBOL.toInt()
        return symbol(cps[0]) && cps.all { cp -> symbol(cp) || cp == 0xFE0F || cp == 0x200D || cp in 0x1F3FB..0x1F3FF || cp in 0xE0020..0xE007F }
    }

    /** Dove stanno i link "@" nel testo, da "[" a ")". */
    fun mentions(text: String): List<IntRange> = linkRx.findAll(text).filter { isMention(it.groupValues[2]) }.map { it.range }.toList()

    /**
     * Cancellato un solo carattere di un link "@" o di un vocale (1.589): se ne va tutto il link, e lo spazio dopo se c'era.
     * [old] prima, [new] dopo, [cursor] dove sta il cursore dopo; null = non era un link "@".
     */
    fun deleteMention(old: String, new: String, cursor: Int): Edit? {
        if (new.length != old.length - 1 || cursor !in 0..new.length) return null
        if (!new.regionMatches(0, old, 0, cursor) || !new.regionMatches(cursor, old, cursor + 1, old.length - cursor - 1)) return null
        val r = linkRx.findAll(old).filter { isMention(it.groupValues[2]) || it.groupValues[2].startsWith(NoteAudio.SCHEME) }
            .map { it.range }.firstOrNull { cursor in it } ?: return null
        val end = if (old.getOrNull(r.last + 1) == ' ') r.last + 2 else r.last + 1
        return Edit(old.removeRange(r.first, end), r.first)
    }

    /** Un'altra nota dentro questa (scrivendo "@"): `[Titolo](nota:ID)` al posto di [from]..[to]. */
    fun noteLink(text: String, from: Int, to: Int, title: String, id: Long): Edit = mentionLink(text, from, to, title, "$NOTE_LINK$id")

    /** "@" verso qualsiasi cosa dell'app (1.588): `[Titolo](app:pagina)`; l'indirizzo non ha spazi né parentesi. */
    fun mentionLink(text: String, from: Int, to: Int, title: String, url: String): Edit {
        val clean = title.replace('[', '(').replace(']', ')').replace('\n', ' ').trim().ifEmpty { "Nota" }
        val safe = url.replace(" ", "%20").replace("(", "%28").replace(")", "%29")
        val piece = "[$clean]($safe) "
        return Edit(text.substring(0, from) + piece + text.substring(to), from + piece.length)
    }

    private fun markSet(fs: List<Format>): Set<Int> = fs.flatMap { f -> f.open.toList() + (f.close?.toList() ?: emptyList()) }.toSet()

    /** Dove disegnare ogni formato e quali sono i segni. */
    fun spans(text: String): List<Span> {
        val out = mutableListOf<Span>()
        formats(text).forEach { f ->
            out += Span(f.open.first, f.open.last + 1, Kind.MARK)
            out += Span(f.contentStart, f.close?.first ?: headingEnd(text, f), f.kind)
            f.close?.let { out += Span(it.first, it.last + 1, Kind.MARK) }
        }
        bareUrls(text).forEach { out += Span(it.first, it.last + 1, Kind.URL) }
        return out.filter { it.end > it.start }.sortedBy { it.start }
    }

    private fun headingEnd(text: String, f: Format): Int = text.indexOf('\n', f.open.first).let { if (it < 0) text.length else it }

    /** I segni da non disegnare mai: tutti, ovunque sia il cursore. In ordine, senza sovrapposizioni, [IntRange.last] incluso. */
    fun hidden(text: String): List<IntRange> {
        val marks = markSet(formats(text))
        val all = (marks + empties(text, marks).flatMap { it.toList() }).sorted()
        val out = mutableListOf<IntRange>()
        all.forEach { i -> if (out.isNotEmpty() && out.last().last + 1 == i) out[out.size - 1] = out.last().first..i else out += i..i }
        return out
    }

    /** Dove cade un punto del testo vero nel testo disegnato senza i segni [hidden], e viceversa. */
    class Collapse(private val hidden: List<IntRange>) {
        fun toShown(offset: Int): Int = offset - hidden.sumOf { r -> (minOf(offset, r.last + 1) - r.first).coerceAtLeast(0) }

        fun toText(shown: Int): Int {
            var o = shown
            for (r in hidden) if (r.first <= o) o += r.last - r.first + 1 else break
            return o
        }
    }

    /** Il testo senza segni, per le righe e i titoli fuori dall'editor (e per copiare e condividere). */
    fun plain(text: String): String {
        val gone = hidden(text).flatMap { it.toList() }.toSet()
        if (gone.isEmpty()) return text
        return buildString { text.forEachIndexed { i, c -> if (i !in gone) append(c) } }
    }

    /** Il risultato di un tocco su un formato o di una modifica: testo nuovo e selezione nuova. */
    data class Edit(val text: String, val start: Int, val end: Int = start)

    private fun kindOf(mark: String) = when (mark) {
        BOLD -> Kind.BOLD
        STRIKE -> Kind.STRIKE
        HIGHLIGHT -> Kind.HIGHLIGHT
        else -> Kind.ITALIC
    }

    private fun markOf(kind: Kind) = when (kind) {
        Kind.BOLD -> BOLD
        Kind.STRIKE -> STRIKE
        Kind.HIGHLIGHT -> HIGHLIGHT
        else -> ITALIC
    }

    /** Il testo senza i caratteri [drop]; [map] porta un punto del testo vecchio nel nuovo. */
    private class Dropped(text: String, private val drop: Set<Int>) {
        val text: String = buildString { text.forEachIndexed { i, c -> if (i !in drop) append(c) } }
        fun map(o: Int): Int = o - drop.count { it < o }
    }

    /**
     * Il tasto G / C / B: con del testo scelto mette il formato, o lo toglie se è già tutto formattato così. Senza niente
     * scelto: dentro una parola formattata la "spegne", altrimenti accende il formato e il testo che si scrive nasce così.
     */
    fun toggle(text: String, start: Int, end: Int, mark: String): Edit {
        val kind = kindOf(mark)
        val write = markOf(kind)
        val fs = formats(text)
        val marks = markSet(fs)
        val same = fs.filter { it.kind == kind }
        var s = minOf(start, end).coerceIn(0, text.length)
        var e = maxOf(start, end).coerceIn(0, text.length)
        // I bordi della scelta non contano segni e spazi.
        while (s < e && (s in marks || text[s].isWhitespace())) s++
        while (e > s && (e - 1 in marks || text[e - 1].isWhitespace())) e--

        if (s == e) {
            // Già acceso e vuoto qui: si spegne.
            empties(text, marks).firstOrNull { it.first + it.count() / 2 == s && kindOf(text.substring(it.first, it.first + it.count() / 2)) == kind }?.let { r ->
                return Edit(text.removeRange(r), r.first)
            }
            same.firstOrNull { it.contentStart <= s && s <= it.contentEnd }?.let { f ->
                val d = Dropped(text, (f.open + f.close!!).toSet())
                return Edit(d.text, d.map(s))
            }
            val at = minOf(start, end).coerceIn(0, text.length)
            return Edit(text.substring(0, at) + write + write + text.substring(at), at + write.length)
        }

        // Tutta la scelta è già formattata così: il formato si toglie solo da lì, il resto lo tiene.
        same.firstOrNull { it.contentStart <= s && e <= it.contentEnd }?.let { f ->
            val o = text.substring(f.open.first, f.open.last + 1)
            val c = text.substring(f.close!!.first, f.close.last + 1)
            val left = text.substring(f.contentStart, s)
            val right = text.substring(e, f.contentEnd)
            val gap = right.takeWhile { it.isWhitespace() }
            val rest = right.substring(gap.length)
            val head = text.substring(0, f.open.first) + (if (left.isNotEmpty()) o + left + c else "")
            val mid = text.substring(s, e) + gap
            val t = head + mid + (if (rest.isNotEmpty()) o + rest + c else "") + text.substring(f.close.last + 1)
            return Edit(t, head.length, head.length + e - s)
        }

        // Più righe già tutte con questo formato (2026-11-26): si toglie da tutte. Prima G le riformattava e non si spegneva mai.
        if ('\n' in text.substring(s, e)) {
            var at = s
            val cores = text.substring(s, e).split('\n').mapIndexedNotNull { i, seg ->
                val from = at
                at += seg.length + 1
                val mark = if (i > 0 || from == 0 || text[from - 1] == '\n') lineMark(seg) else 0
                var cs = from + mark
                var ce = from + seg.length
                while (cs < ce && (cs in marks || text[cs].isWhitespace())) cs++
                while (ce > cs && (ce - 1 in marks || text[ce - 1].isWhitespace())) ce--
                if (cs < ce) cs to ce else null
            }
            val covering = cores.map { (cs, ce) -> same.firstOrNull { it.contentStart <= cs && ce <= it.contentEnd } }
            if (cores.isNotEmpty() && covering.all { it != null }) {
                val d = Dropped(text, covering.filterNotNull().flatMap { it.open + it.close!! }.toSet())
                return Edit(d.text, d.map(s), d.map(e))
            }
        }

        var a = s
        var b = e
        // Il corsivo vale per parole intere, se no i segni non si riconoscono.
        if (kind == Kind.ITALIC) {
            while (a > 0 && text[a - 1].isLetterOrDigit()) a--
            while (b < text.length && text[b].isLetterOrDigit()) b++
        }
        // Formati uguali dentro o a cavallo: si uniscono in uno solo.
        var grown = true
        while (grown) {
            grown = false
            same.filter { it.open.first < b && it.close!!.last >= a }.forEach { f ->
                if (f.open.first < a) { a = f.open.first; grown = true }
                if (f.close!!.last + 1 > b) { b = f.close.last + 1; grown = true }
            }
        }
        val inner = same.filter { it.open.first >= a && it.close!!.last < b }.flatMap { (it.open + it.close!!) }.toSet()
        val d = Dropped(text, inner)
        val na = d.map(a)
        val nb = d.map(b)
        val t = d.text
        // Riga per riga: un formato non va mai a capo.
        val sb = StringBuilder(t.substring(0, na))
        var first = -1
        var last = na
        t.substring(na, nb).split('\n').forEachIndexed { i, whole ->
            if (i > 0) sb.append('\n')
            // Il segno della riga (punto, spunta, numero, titolo) resta fuori dal formato: dentro, la voce non era più voce.
            val mark = if (i > 0 || na == 0 || t[na - 1] == '\n') lineMark(whole) else 0
            sb.append(whole, 0, mark)
            val seg = whole.substring(mark)
            val lead = seg.length - seg.trimStart().length
            val core = seg.trim()
            if (core.isEmpty()) { sb.append(seg); return@forEachIndexed }
            sb.append(seg, 0, lead)
            sb.append(write)
            if (first < 0) first = sb.length
            sb.append(core)
            last = sb.length
            sb.append(write)
            sb.append(seg, lead + core.length, seg.length)
        }
        sb.append(t, nb, t.length)
        return Edit(sb.toString(), if (first < 0) na else first, last)
    }

    /**
     * Quanto è lungo il segno davanti a una riga intera ([NoteLines.kindOf]) che grassetto e colori lasciano fuori (2026-11-26):
     * scegliendo più voci e toccando G, "- a" diventava "**- a**" e la lista si rompeva. I pezzi unici restano interi.
     */
    private fun lineMark(line: String): Int = NoteLines.kindOf(line).let { (k, _, p) -> if (k == NoteLines.Kind.TEXT) 0 else p }

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

    /** Il formato che c'è già dove sta la selezione (o acceso e vuoto sotto il cursore): i tasti accesi. */
    fun active(text: String, start: Int, end: Int): Set<Kind> {
        val fs = formats(text)
        val marks = markSet(fs)
        var s = minOf(start, end).coerceIn(0, text.length)
        var e = maxOf(start, end).coerceIn(0, text.length)
        while (s < e && s in marks) s++
        while (e > s && e - 1 in marks) e--
        val out = fs.filter { it.kind != Kind.HEADING && it.contentStart <= s && it.contentEnd >= e }.map { it.kind }.toMutableSet()
        if (s == e) empties(text, marks).filter { it.first + it.count() / 2 == s }.forEach { out += kindOf(text.substring(it.first, it.first + it.count() / 2)) }
        return out
    }

    /**
     * Toglie i formati accesi e rimasti vuoti dove il cursore non c'è più (G toccato e poi si è andati altrove).
     * Null = niente da togliere.
     */
    fun tidy(text: String, start: Int, end: Int): Edit? {
        val marks = markSet(formats(text))
        val gone = empties(text, marks).filter { !(start == end && it.first + it.count() / 2 == start) }
        if (gone.isEmpty()) return null
        val d = Dropped(text, gone.flatMap { it.toList() }.toSet())
        return Edit(d.text, d.map(start), d.map(end))
    }

    /**
     * Dopo che la tastiera ha cambiato il testo ([old] con la selezione [oldStart]..[oldEnd] → [new] col cursore
     * [cursor]): i segni non si vedono, quindi la tastiera non deve poterli rompere.
     * - Indietro (o Canc) su un segno invisibile cancella la lettera vera accanto; a inizio titoletto lo toglie.
     * - Cancellando un pezzo che contiene segni, se ne vanno solo le lettere: i formati restano interi.
     * - Andando a capo dentro un formato, il formato si chiude e riparte sulla riga nuova.
     * - Uno spazio scritto all'inizio di un formato va fuori dal formato.
     * Null = la modifica va bene così.
     */
    fun afterEdit(old: String, oldStart: Int, oldEnd: Int, new: String, cursor: Int): Edit? {
        if (old == new) return null
        val c = cursor.coerceIn(0, new.length)
        var p = 0
        val maxP = minOf(old.length, new.length, c)
        while (p < maxP && old[p] == new[p]) p++
        var q = 0
        val maxQ = minOf(old.length, new.length) - p
        while (q < maxQ && old[old.length - 1 - q] == new[new.length - 1 - q]) q++
        val a = p
        val oe = old.length - q
        val ins = new.substring(a, new.length - q)

        val fs = formats(old)
        val marks = markSet(fs)
        val empty = empties(old, marks)
        val hiddenAll = marks + empty.flatMap { it.toList() }
        val headings = fs.filter { it.kind == Kind.HEADING }
        val opens = fs.filter { it.kind != Kind.HEADING }.flatMap { it.open.toList() }.toSet() + empty.flatMap { (it.first until it.first + it.count() / 2).toList() }

        if (oe > a && (a until oe).none { it in hiddenAll }) {
            // Niente segni toccati: conta solo l'andare a capo dentro un formato.
            return if ('\n' in ins) breakLines(old, a, oe, ins, fs, empty) else null
        }
        if (oe > a && ins.isEmpty() && (a until oe).all { it in hiddenAll }) {
            val forward = minOf(oldStart, oldEnd) == a && oldStart == oldEnd
            if (!forward) {
                // Indietro all'inizio di un titoletto: la riga torna testo.
                headings.firstOrNull { h -> h.open.first <= a && (h.open.first until a).all { it in hiddenAll } }?.let { h ->
                    return Edit(old.removeRange(h.open), oe - HEADING.length)
                }
                val i = (a - 1 downTo 0).firstOrNull { it !in hiddenAll } ?: return Edit(old, oldStart, oldEnd)
                return Edit(old.removeRange(i, i + 1), i)
            }
            val i = (oe until old.length).firstOrNull { it !in hiddenAll } ?: return Edit(old, oldStart, oldEnd)
            return Edit(old.removeRange(i, i + 1), a)
        }
        if (oe > a) {
            // Un pezzo con dentro dei segni: via le lettere, i segni restano (il titoletto no, se se ne va la riga sopra).
            val sb = StringBuilder(old.substring(0, a))
            for (i in a until oe) {
                if (i !in hiddenAll) continue
                val h = headings.firstOrNull { i in it.open }
                if (h != null && h.open.first > a) continue
                sb.append(old[i])
            }
            sb.append(old, oe, old.length)
            val t0 = sb.toString()
            if ('\n' in ins) {
                val f0 = formats(t0)
                breakLines(t0, a, a, ins, f0, empties(t0, markSet(f0)))?.let { return it }
            }
            return Edit(t0.substring(0, a) + ins + t0.substring(a), a + ins.length)
        }
        // Solo testo in più.
        if ('\n' in ins) return breakLines(old, a, a, ins, fs, empty)
        val lead = ins.takeWhile { it == ' ' || it == '\t' }
        if (lead.isNotEmpty() && a - 1 in opens) {
            var rs = a
            while (rs > 0 && rs - 1 in opens) rs--
            val rest = ins.substring(lead.length)
            val t = old.substring(0, rs) + lead + old.substring(rs, a) + rest + old.substring(a)
            return Edit(t, a + ins.length)
        }
        return null
    }

    /**
     * [ins] con dei "\n" al posto di [a]..[oe]: dentro un formato ogni riga nuova lo chiude e lo riapre (dopo il segno
     * della voce, se la riga ne ha uno). Un formato acceso e vuoto lì si spegne.
     */
    private fun breakLines(text: String, a: Int, oe: Int, ins: String, fs: List<Format>, empty: List<IntRange>): Edit? {
        val base = text.removeRange(a, oe)
        empty.firstOrNull { oe == a && it.first + it.count() / 2 == a }?.let { r ->
            val t = base.removeRange(r)
            val at = r.first
            return Edit(t.substring(0, at) + ins + t.substring(at), at + ins.length)
        }
        val around = fs.filter { it.kind != Kind.HEADING && it.contentStart <= a && a <= it.contentEnd && oe <= it.contentEnd }
            .sortedBy { it.contentStart }
        if (around.isEmpty()) return null
        val open = around.joinToString("") { text.substring(it.open.first, it.open.last + 1) }
        val close = around.reversed().joinToString("") { text.substring(it.close!!.first, it.close.last + 1) }
        val parts = ins.split('\n')
        val sb = StringBuilder(parts[0]).append(close)
        parts.drop(1).forEachIndexed { i, part ->
            sb.append('\n')
            val pre = NoteLines.kindOf(part).third
            sb.append(part, 0, pre).append(open).append(part, pre, part.length)
            if (i < parts.size - 2) sb.append(close)
        }
        val piece = sb.toString()
        val t = base.substring(0, a) + piece + base.substring(a)
        val cleaned = tidy(t, a + piece.length, a + piece.length)
        return cleaned ?: Edit(t, a + piece.length)
    }

    /** La lettera del colore di uno [Span] COLOR che comincia a [contentStart] ('r', 'R'…), null se non lo è. */
    fun colorAt(text: String, contentStart: Int): Char? =
        text.getOrNull(contentStart - 3)?.takeIf { it == '{' }?.let { text.getOrNull(contentStart - 2) }?.takeIf { it.lowercaseChar() in COLORS }

    /** Il colore che c'è già dove sta la scelta ('r', 'R'…), null se niente: il fumetto lo accende. */
    fun colorHere(text: String, start: Int, end: Int): Char? {
        val s = minOf(start, end).coerceIn(0, text.length)
        val e = maxOf(start, end).coerceIn(0, text.length)
        return colorRx.findAll(text).firstOrNull { m -> s >= m.range.first && s <= m.range.last + 1 && e <= m.range.last + 1 }?.groupValues?.get(1)?.firstOrNull()
    }

    /**
     * Il colore sul testo scelto (1.590): [code] = la lettera ('r' testo rosso, 'R' sfondo rosso…), null = toglie il colore.
     * Un colore che c'è già dove sta la scelta si cambia o si toglie; il resto prende il colore riga per riga.
     */
    fun color(text: String, start: Int, end: Int, code: Char?): Edit {
        var s = minOf(start, end).coerceIn(0, text.length)
        var e = maxOf(start, end).coerceIn(0, text.length)
        val there = colorRx.findAll(text).firstOrNull { m -> s >= m.range.first && s <= m.range.last + 1 && e <= m.range.last + 1 }
        if (there != null) {
            val inner = there.groupValues[2]
            val piece = if (code == null) inner else "{$code:$inner}"
            val t = text.substring(0, there.range.first) + piece + text.substring(there.range.last + 1)
            val a = there.range.first + if (code == null) 0 else 3
            return Edit(t, a, a + inner.length)
        }
        if (code == null || s == e) return Edit(text, start, end)
        while (s < e && text[s].isWhitespace()) s++
        while (e > s && text[e - 1].isWhitespace()) e--
        if (s == e) return Edit(text, start, end)
        val sb = StringBuilder(text.substring(0, s))
        var first = -1
        var last = s
        text.substring(s, e).split('\n').forEachIndexed { i, whole ->
            if (i > 0) sb.append('\n')
            val mark = if (i > 0 || s == 0 || text[s - 1] == '\n') lineMark(whole) else 0
            sb.append(whole, 0, mark)
            val seg = whole.substring(mark)
            val core = seg.trim()
            if (core.isEmpty()) { sb.append(seg); return@forEachIndexed }
            val lead = seg.length - seg.trimStart().length
            sb.append(seg, 0, lead).append('{').append(code).append(':')
            if (first < 0) first = sb.length
            sb.append(core.replace('{', '(').replace('}', ')'))
            last = sb.length
            sb.append('}').append(seg, lead + core.length, seg.length)
        }
        sb.append(text, e, text.length)
        return Edit(sb.toString(), first, last)
    }
}
