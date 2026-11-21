package com.platinum20.dashboard.notes

/**
 * Le righe di una nota (1.553, Antonio scelta A di /mnt/project-files/editor-note-2/): ogni riga sceglie da sola cosa
 * essere, testo, punto ("- "), voce da spuntare ("[ ] " / "[x] ") o titoletto ("## "), e nella stessa nota si mescolano.
 * Restano segni nel testo, così le note si copiano e si condividono ovunque; l'editor li nasconde e disegna cerchi e
 * pallini. Funzioni pure: si provano senza telefono.
 */
object NoteLines {
    enum class Kind { TEXT, BULLET, CHECK, HEADING }

    /** Una riga: [start]..[end] ([end] = dove sta il "\n" o la fine), il segno davanti lungo [prefix]. */
    data class Line(val index: Int, val start: Int, val end: Int, val kind: Kind, val done: Boolean, val prefix: Int) {
        val contentStart: Int get() = start + prefix
        val isList: Boolean get() = kind == Kind.BULLET || kind == Kind.CHECK
    }

    /** Il risultato di una modifica: testo nuovo e cursore (o selezione) nuovo. */
    data class Edit(val text: String, val start: Int, val end: Int = start)

    const val CHECK = "[ ] "
    const val DONE = "[x] "
    const val BULLET = "- "
    const val HEADING = "## "

    private val checkRx = Regex("""^\s*\[([ xX]?)](?: |$)""")
    private val bulletRx = Regex("""^\s*[-•*] """)

    /** Che riga è e quanto è lungo il suo segno. */
    fun kindOf(line: String): Triple<Kind, Boolean, Int> {
        checkRx.find(line)?.let { m -> return Triple(Kind.CHECK, m.groupValues[1].equals("x", ignoreCase = true), m.value.length) }
        bulletRx.find(line)?.let { m -> return Triple(Kind.BULLET, false, m.value.length) }
        if (line.startsWith(HEADING)) return Triple(Kind.HEADING, false, HEADING.length)
        return Triple(Kind.TEXT, false, 0)
    }

    fun parse(text: String): List<Line> {
        val out = mutableListOf<Line>()
        var start = 0
        var index = 0
        while (true) {
            val nl = text.indexOf('\n', start)
            val end = if (nl < 0) text.length else nl
            val (kind, done, prefix) = kindOf(text.substring(start, end))
            out += Line(index, start, end, kind, done, prefix)
            if (nl < 0) break
            start = nl + 1
            index++
        }
        return out
    }

    /** La riga dove cade [offset] (il "\n" in fondo appartiene alla riga che chiude). */
    fun lineAt(text: String, offset: Int): Line {
        val lines = parse(text)
        return lines.firstOrNull { offset >= it.start && offset <= it.end } ?: lines.last()
    }

    /** Il testo della riga senza il segno davanti. */
    fun content(text: String, line: Line): String = text.substring(line.contentStart, line.end)

    private fun prefixFor(kind: Kind, done: Boolean = false): String = when (kind) {
        Kind.TEXT -> ""
        Kind.BULLET -> BULLET
        Kind.CHECK -> if (done) DONE else CHECK
        Kind.HEADING -> HEADING
    }

    /**
     * I tasti Punti / Spunta / Titolo: le righe toccate dalla selezione diventano [kind]; se lo sono già tutte tornano
     * testo. La selezione segue il testo, come se il segno non ci fosse.
     */
    fun setKind(text: String, selStart: Int, selEnd: Int, kind: Kind): Edit {
        val s = minOf(selStart, selEnd).coerceIn(0, text.length)
        val e = maxOf(selStart, selEnd).coerceIn(0, text.length)
        val lines = parse(text)
        val touched = lines.filter { it.end >= s && it.start <= e }
        val target = if (touched.all { it.kind == kind }) Kind.TEXT else kind
        val sb = StringBuilder()
        // Per ogni riga toccata: dove cominciava, vecchia lunghezza del segno, nuova lunghezza.
        val shifts = mutableListOf<Triple<Int, Int, Int>>()
        var at = 0
        touched.forEach { l ->
            sb.append(text, at, l.start)
            val keep = if (target == Kind.CHECK && l.kind == Kind.CHECK) prefixFor(Kind.CHECK, l.done) else prefixFor(target)
            sb.append(keep).append(text, l.contentStart, l.end)
            shifts += Triple(l.start, l.prefix, keep.length)
            at = l.end
        }
        sb.append(text, at, text.length)
        fun map(o: Int): Int {
            var delta = 0
            for ((start, old, new) in shifts) {
                if (o < start) break
                if (o < start + old) return start + delta + new
                delta += new - old
            }
            return o + delta
        }
        return Edit(sb.toString(), map(s), map(e))
    }

    /**
     * Dopo che la tastiera ha cambiato il testo: Invio in una lista aggiunge un'altra voce uguale, Invio su una voce
     * vuota chiude la lista, cancellare il segno trasforma la voce in testo. Null = la modifica va bene così.
     */
    fun afterEdit(old: String, new: String, cursor: Int): Edit? {
        // Invio: un "\n" in più appena prima del cursore.
        if (new.length == old.length + 1 && cursor >= 1 && cursor <= new.length && new[cursor - 1] == '\n' &&
            new.regionMatches(0, old, 0, cursor - 1) && new.regionMatches(cursor, old, cursor - 1, old.length - cursor + 1)
        ) {
            val p = cursor - 1
            val line = lineAt(old, p)
            if (!line.isList || p < line.contentStart) return null
            if (content(old, line).isBlank()) return Edit(old.removeRange(line.start, line.end), line.start)
            val indent = old.substring(line.start, line.contentStart).takeWhile { it == ' ' || it == '\t' }
            val pre = indent + if (line.kind == Kind.CHECK) CHECK else old.substring(line.start + indent.length, line.contentStart)
            return Edit(new.substring(0, cursor) + pre + new.substring(cursor), cursor + pre.length)
        }
        // Un carattere cancellato dentro il segno (indietro a inizio voce): la voce torna testo.
        if (new.length == old.length - 1 && cursor in 0..new.length &&
            new.regionMatches(0, old, 0, cursor) && new.regionMatches(cursor, old, cursor + 1, old.length - cursor - 1)
        ) {
            val line = lineAt(old, cursor)
            if (line.isList && cursor >= line.start && cursor < line.contentStart) {
                return Edit(old.removeRange(line.start, line.contentStart), line.start)
            }
        }
        return null
    }

    /** Spunta o toglie la spunta alla riga [index]; il segno diventa sempre "[ ] " / "[x] ". */
    fun toggle(text: String, index: Int): String {
        val line = parse(text).getOrNull(index)?.takeIf { it.kind == Kind.CHECK } ?: return text
        return text.substring(0, line.start) + prefixFor(Kind.CHECK, !line.done) + text.substring(line.contentStart)
    }

    /** Cambia il tipo di una riga sola (fumetto del tieni premuto). */
    fun setLineKind(text: String, index: Int, kind: Kind): String {
        val line = parse(text).getOrNull(index) ?: return text
        return setKind(text, line.start, line.start, kind).let { if (line.kind == kind) text else it.text }
    }

    /** Sposta la riga [index] su ([up]) o giù di una. */
    fun move(text: String, index: Int, up: Boolean): String {
        val all = text.split('\n').toMutableList()
        val to = if (up) index - 1 else index + 1
        if (index !in all.indices || to !in all.indices) return text
        all[index] = all[to].also { all[to] = all[index] }
        return all.joinToString("\n")
    }

    fun remove(text: String, index: Int): String {
        val all = text.split('\n').toMutableList()
        if (index !in all.indices) return text
        all.removeAt(index)
        return all.joinToString("\n")
    }

    private fun checks(text: String) = parse(text).filter { it.kind == Kind.CHECK }

    fun hasDone(text: String): Boolean = checks(text).any { it.done }

    /** Tutte le voci spuntate (e almeno una c'è). */
    fun allDone(text: String): Boolean = checks(text).let { it.isNotEmpty() && it.all { l -> l.done } }

    /** "Togli tutte le spunte": per rifare la stessa lista (la valigia). */
    fun uncheckAll(text: String): String {
        var t = text
        checks(text).filter { it.done }.forEach { t = toggle(t, it.index) }
        return t
    }

    /** "Togli le fatte": le voci spuntate spariscono. */
    fun removeDone(text: String): String =
        text.split('\n').filter { line -> kindOf(line).let { (k, done, _) -> !(k == Kind.CHECK && done) } }.joinToString("\n")

    /** "Fatte in fondo": in ogni lista (righe da spuntare una dopo l'altra) le spuntate scendono, l'ordine resta. */
    fun doneToBottom(text: String): String {
        val all = text.split('\n')
        val out = mutableListOf<String>()
        var block = mutableListOf<String>()
        fun flush() {
            out += block.filter { !kindOf(it).second } + block.filter { kindOf(it).second }
            block = mutableListOf()
        }
        all.forEach { line ->
            if (kindOf(line).first == Kind.CHECK) block += line else { flush(); out += line }
        }
        flush()
        return out.joinToString("\n")
    }

    /** Il testo che si legge: senza segni di riga né di formato (righe fuori dall'editor). */
    fun plainLine(line: String): String = NoteFormat.plain(line.substring(kindOf(line).third)).trim()

    /**
     * La riga sotto il titolo nella card Appunti: con delle voci da spuntare "2 di 4 fatte · Documenti, Ombrello"
     * (quelle che mancano), altrimenti la prima riga scritta.
     */
    fun cardLine(body: String): String? {
        val lines = parse(body)
        val items = lines.filter { it.kind == Kind.CHECK }
        if (items.isNotEmpty()) {
            val done = items.count { it.done }
            val left = items.filter { !it.done }.map { plainLine(body.substring(it.start, it.end)) }.filter { it.isNotEmpty() }
            val head = if (done == items.size) "Tutto fatto · ${items.size} di ${items.size}" else "$done di ${items.size} fatte"
            return if (left.isEmpty()) head else "$head · ${left.joinToString(", ")}"
        }
        return body.lineSequence().map { plainLine(it) }.firstOrNull { it.isNotEmpty() }
    }

    /** Al posto del segno di una voce si disegna un solo spazio sottile: sopra ci va il cerchio o il pallino. */
    const val PLACE = "\u2009"

    /**
     * Il testo come lo disegna l'editor: i segni dei formati [hidden] (da [NoteFormat.hidden]) spariscono, il segno di
     * ogni voce diventa [PLACE]. Il cursore non può finire dentro un segno: da lì va sempre dopo.
     */
    class Shown(val text: String, hidden: List<IntRange> = emptyList()) {
        data class Cut(val start: Int, val end: Int, val with: String)

        val lines: List<Line> = parse(text)
        val cuts: List<Cut> = (lines.filter { it.isList && it.prefix > 0 }.map { Cut(it.start, it.contentStart, PLACE) } +
            hidden.map { Cut(it.first, it.last + 1, "") })
            .sortedBy { it.start }
            .fold(mutableListOf<Cut>()) { acc, c -> if (acc.isNotEmpty() && c.start < acc.last().end) acc else acc.apply { add(c) } }

        val shown: String = buildString {
            var at = 0
            cuts.forEach { c ->
                append(text, at, c.start)
                append(c.with)
                at = c.end
            }
            append(text, at, text.length)
        }

        fun toShown(offset: Int): Int {
            var delta = 0
            for (c in cuts) {
                if (offset <= c.start) break
                if (offset < c.end) return c.start + delta + c.with.length
                delta += c.with.length - (c.end - c.start)
            }
            return (offset + delta).coerceIn(0, shown.length)
        }

        fun toText(offset: Int): Int {
            var delta = 0
            for (c in cuts) {
                val s = c.start + delta
                if (offset < s) break
                if (offset <= s + c.with.length) return c.end
                delta += c.with.length - (c.end - c.start)
            }
            return (offset - delta).coerceIn(0, text.length)
        }
    }

    /** Un cursore finito dentro il segno di una voce va subito dopo il segno. */
    fun outOfPrefix(text: String, offset: Int): Int {
        val line = lineAt(text, offset)
        return if (line.isList && offset >= line.start && offset < line.contentStart) line.contentStart else offset
    }
}
