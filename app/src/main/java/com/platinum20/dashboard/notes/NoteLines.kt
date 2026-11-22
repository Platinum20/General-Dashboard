package com.platinum20.dashboard.notes

/**
 * Le righe di una nota (1.555, Antonio scelta A di /mnt/project-files/editor-note-2/): ogni riga sceglie da sola cosa
 * essere, testo, punto ("- "), voce da spuntare ("[ ] " / "[x] ") o titoletto ("## "), e nella stessa nota si mescolano.
 * Restano segni nel testo, così le note si copiano e si condividono ovunque; l'editor li nasconde e disegna cerchi e
 * pallini. Funzioni pure: si provano senza telefono.
 */
object NoteLines {
    enum class Kind { TEXT, BULLET, CHECK, HEADING, NUMBER, QUOTE, CALLOUT, DIVIDER }

    /** Una riga: [start]..[end] ([end] = dove sta il "\n" o la fine), il segno davanti lungo [prefix]. */
    data class Line(val index: Int, val start: Int, val end: Int, val kind: Kind, val done: Boolean, val prefix: Int, val level: Int = 0, val number: Int = 0) {
        val contentStart: Int get() = start + prefix
        val isList: Boolean get() = kind == Kind.BULLET || kind == Kind.CHECK || kind == Kind.NUMBER

        /** Il segno davanti non si vede e l'editor ci disegna sopra (cerchio, numero, barra, emoji, linea). */
        val hasMark: Boolean get() = isList || kind == Kind.QUOTE || kind == Kind.CALLOUT || kind == Kind.DIVIDER
    }

    /** Il risultato di una modifica: testo nuovo e cursore (o selezione) nuovo. */
    data class Edit(val text: String, val start: Int, val end: Int = start)

    const val CHECK = "[ ] "
    const val DONE = "[x] "
    const val BULLET = "- "
    const val HEADING = "## "

    private val checkRx = Regex("""^\s*\[([ xX]?)](?: |$)""")
    private val bulletRx = Regex("""^\s*[-•*] """)
    private val numberRx = Regex("""^\s*(\d{1,3})[.)] """)

    /** Citazione (1.584): "> " davanti. Riquadro "da non dimenticare": "!!" più la sua emoji. Linea: "---" da sola. */
    const val QUOTE = "> "
    const val CALLOUT = "!! "
    const val DIVIDER = "---"
    const val CALLOUT_EMOJI = "💡"
    private val calloutRx = Regex("""^!!(\S{0,8}) """)

    /** L'emoji del riquadro: quella scritta dopo "!!", o la lampadina. */
    fun calloutEmoji(line: String): String = calloutRx.find(line)?.groupValues?.get(1)?.takeIf { it.isNotEmpty() } ?: CALLOUT_EMOJI

    /** Cambia l'emoji del riquadro alla riga [index]. */
    fun setCalloutEmoji(text: String, index: Int, emoji: String): String {
        val l = parse(text).getOrNull(index)?.takeIf { it.kind == Kind.CALLOUT } ?: return text
        return text.substring(0, l.start) + "!!$emoji " + text.substring(l.contentStart)
    }

    /** Un livello di rientro (1.579): due spazi davanti al segno. */
    const val INDENT = "  "
    const val MAX_LEVEL = 4

    /** Quanto è rientrata una riga: due spazi o un tab = un livello. */
    fun levelOf(line: String): Int {
        var spaces = 0
        var tabs = 0
        for (c in line) { if (c == ' ') spaces++ else if (c == '\t') tabs++ else break }
        return minOf(MAX_LEVEL, spaces / 2 + tabs)
    }

    /** Che riga è e quanto è lungo il suo segno. */
    fun kindOf(line: String): Triple<Kind, Boolean, Int> {
        if (line.trim() == DIVIDER) return Triple(Kind.DIVIDER, false, line.length)
        if (line.startsWith(QUOTE)) return Triple(Kind.QUOTE, false, QUOTE.length)
        calloutRx.find(line)?.let { m -> return Triple(Kind.CALLOUT, false, m.value.length) }
        checkRx.find(line)?.let { m -> return Triple(Kind.CHECK, m.groupValues[1].equals("x", ignoreCase = true), m.value.length) }
        bulletRx.find(line)?.let { m -> return Triple(Kind.BULLET, false, m.value.length) }
        numberRx.find(line)?.let { m -> return Triple(Kind.NUMBER, false, m.value.length) }
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
            val raw = text.substring(start, end)
            val (kind, done, prefix) = kindOf(raw)
            val level = if (kind == Kind.BULLET || kind == Kind.CHECK || kind == Kind.NUMBER) levelOf(raw) else 0
            val number = if (kind == Kind.NUMBER) numberRx.find(raw)?.groupValues?.get(1)?.toIntOrNull() ?: 1 else 0
            out += Line(index, start, end, kind, done, prefix, level, number)
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
        Kind.NUMBER -> "1. "
        Kind.QUOTE -> QUOTE
        Kind.CALLOUT -> CALLOUT
        Kind.DIVIDER -> ""
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
            // Il rientro resta se la riga resta in una lista.
            val indent = if (l.isList && (target == Kind.BULLET || target == Kind.CHECK || target == Kind.NUMBER)) INDENT.repeat(l.level) else ""
            val keep = indent + if (target == Kind.CHECK && l.kind == Kind.CHECK) prefixFor(Kind.CHECK, l.done) else prefixFor(target)
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
            // Le voci e la citazione (1.586) continuano nella riga nuova; Invio su una vuota le chiude.
            if (!(line.isList || line.kind == Kind.QUOTE) || p < line.contentStart) return null
            if (content(old, line).isBlank()) {
                // Invio su una voce vuota rientrata: torna su di un livello; non rientrata: la lista finisce.
                if (line.level > 0) {
                    val e = indent(old, line.contentStart, line.contentStart, -1)
                    return Edit(e.text, e.start)
                }
                return Edit(old.removeRange(line.start, line.end), line.start)
            }
            val indent = old.substring(line.start, line.contentStart).takeWhile { it == ' ' || it == '\t' }
            val pre = indent + when (line.kind) {
                Kind.CHECK -> CHECK
                Kind.NUMBER -> "${line.number + 1}. "
                else -> old.substring(line.start + indent.length, line.contentStart)
            }
            return Edit(new.substring(0, cursor) + pre + new.substring(cursor), cursor + pre.length)
        }
        // Scorciatoia (1.579): "# " a inizio riga diventa un titolo.
        if (new.length == old.length + 1 && cursor >= 2 && cursor <= new.length && new[cursor - 1] == ' ') {
            val line = lineAt(new, cursor)
            if (new.substring(line.start, cursor) == "# " && line.kind == Kind.TEXT) {
                return Edit(new.substring(0, line.start) + HEADING + new.substring(cursor), line.start + HEADING.length)
            }
        }
        // Un carattere cancellato dentro il segno (indietro a inizio voce): la voce torna testo.
        if (new.length == old.length - 1 && cursor in 0..new.length &&
            new.regionMatches(0, old, 0, cursor) && new.regionMatches(cursor, old, cursor + 1, old.length - cursor - 1)
        ) {
            val line = lineAt(old, cursor)
            if (line.hasMark && line.kind != Kind.DIVIDER && cursor >= line.start && cursor < line.contentStart) {
                return Edit(old.removeRange(line.start, line.contentStart), line.start)
            }
            // Indietro all'inizio della riga sotto una linea: se ne va la linea, non il testo.
            if (cursor >= 1 && old[cursor] == '\n') {
                val above = lineAt(old, cursor)
                if (above.kind == Kind.DIVIDER && above.end == cursor) return Edit(old.removeRange(above.start, above.end + 1), above.start)
            }
        }
        return null
    }

    /**
     * I tasti del rientro (1.579): le voci toccate dalla selezione vanno a destra ([by] = +1, diventano sotto-voci) o
     * a sinistra (-1). Le righe che non sono voci non si muovono. La selezione segue il testo.
     */
    fun indent(text: String, selStart: Int, selEnd: Int, by: Int): Edit {
        val s = minOf(selStart, selEnd).coerceIn(0, text.length)
        val e = maxOf(selStart, selEnd).coerceIn(0, text.length)
        val touched = parse(text).filter { it.end >= s && it.start <= e && it.isList }
        if (touched.isEmpty()) return Edit(text, selStart, selEnd)
        val sb = StringBuilder()
        val shifts = mutableListOf<Pair<Int, Int>>() // dove, quanto cambia
        var at = 0
        touched.forEach { l ->
            sb.append(text, at, l.start)
            val ws = text.substring(l.start, l.contentStart).takeWhile { it == ' ' || it == '\t' }
            val level = (l.level + by).coerceIn(0, MAX_LEVEL)
            val now = INDENT.repeat(level)
            sb.append(now)
            shifts += l.start to (now.length - ws.length)
            at = l.start + ws.length
        }
        sb.append(text, at, text.length)
        // Un punto a inizio riga resta a inizio riga; gli altri si spostano col rientro di tutte le righe prima.
        fun map(o: Int): Int = o + shifts.filter { it.first < o }.sumOf { it.second }
        return renumber(Edit(sb.toString(), map(s), map(e)))
    }

    /** Può andare a destra / a sinistra la riga del cursore? Per accendere o spegnere i tasti. */
    fun canIndent(text: String, offset: Int, by: Int): Boolean {
        val l = lineAt(text, offset.coerceIn(0, text.length))
        return l.isList && (if (by > 0) l.level < MAX_LEVEL else l.level > 0)
    }

    /**
     * I numeri delle liste numerate sempre 1, 2, 3 (1.579): contano le voci numerate di fila allo stesso livello; una
     * riga di testo ricomincia da 1, una voce più in fuori ricomincia le sotto-liste. Il cursore resta dov'era.
     */
    fun renumber(edit: Edit): Edit {
        val text = edit.text
        val lines = parse(text)
        if (lines.none { it.kind == Kind.NUMBER }) return edit
        val counters = IntArray(MAX_LEVEL + 1)
        val changes = mutableListOf<Triple<Int, Int, String>>() // inizio cifre, fine cifre, cifre nuove
        lines.forEach { l ->
            if (!l.isList) { counters.fill(0); return@forEach }
            for (d in l.level + 1..MAX_LEVEL) counters[d] = 0
            if (l.kind != Kind.NUMBER) { counters[l.level] = 0; return@forEach }
            counters[l.level]++
            val want = counters[l.level]
            if (want != l.number) {
                val raw = text.substring(l.start, l.contentStart)
                val m = numberRx.find(raw) ?: return@forEach
                val ds = l.start + m.groups[1]!!.range.first
                changes += Triple(ds, ds + m.groups[1]!!.value.length, want.toString())
            }
        }
        if (changes.isEmpty()) return edit
        val sb = StringBuilder()
        var at = 0
        changes.forEach { (a, b, d) -> sb.append(text, at, a).append(d); at = b }
        sb.append(text, at, text.length)
        fun map(o: Int): Int = o + changes.filter { it.second <= o }.sumOf { it.third.length - (it.second - it.first) }
        return Edit(sb.toString(), map(edit.start), map(edit.end))
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

    /** La nota ha almeno un elenco (punti, numeri o spunte): solo allora "Ordina la lista". */
    fun hasList(text: String): Boolean = parse(text).any { it.isList }

    /**
     * "Dalla A alla Z" (1.586): in ogni elenco le voci si mettono in ordine alfabetico, ognuna con le sue sotto-voci;
     * le spuntate restano in fondo. I numeri si rifanno.
     */
    fun sortAlpha(text: String): String {
        val all = text.split('\n')
        val out = mutableListOf<String>()
        var i = 0
        while (i < all.size) {
            if (!kindOf(all[i]).first.isListKind()) { out += all[i]; i++; continue }
            // Un elenco: righe di voci una dopo l'altra. Le voci del livello più basso portano con sé quelle sotto.
            var j = i
            while (j < all.size && kindOf(all[j]).first.isListKind()) j++
            val block = all.subList(i, j)
            val top = block.minOf { levelOf(it) }
            val groups = mutableListOf<MutableList<String>>()
            block.forEach { line -> if (levelOf(line) == top || groups.isEmpty()) groups += mutableListOf(line) else groups.last() += line }
            out += groups.sortedWith(compareBy<List<String>>({ kindOf(it.first()).second }, { plainLine(it.first()).lowercase() })).flatten()
            i = j
        }
        return renumber(Edit(out.joinToString("\n"), 0)).text
    }

    private fun Kind.isListKind() = this == Kind.BULLET || this == Kind.CHECK || this == Kind.NUMBER

    /**
     * Tocco sotto l'ultima riga (1.586): il cursore va in fondo. Se l'ultima riga è una voce scritta, se ne apre una nuova
     * uguale (stesso rientro, spunta vuota, numero dopo).
     */
    fun tapBelow(text: String): Edit {
        val last = parse(text).lastOrNull() ?: return Edit(text, text.length)
        if (!last.isList || content(text, last).isBlank()) return Edit(text, text.length)
        val indent = INDENT.repeat(last.level)
        val pre = indent + when (last.kind) {
            Kind.CHECK -> CHECK
            Kind.NUMBER -> "${last.number + 1}. "
            else -> BULLET
        }
        val t = text + "\n" + pre
        return Edit(t, t.length)
    }

    /** I titoli della nota (1.586), per l'indice in cima: dove comincia la riga e il testo. */
    fun headings(text: String): List<Pair<Int, String>> =
        parse(text).filter { it.kind == Kind.HEADING }.map { it.start to plainLine(text.substring(it.start, it.end)) }.filter { it.second.isNotEmpty() }

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

    /**
     * Al posto del segno di una voce: uno spazio sottile (allargato dall'editor, sopra ci va il cerchio o il pallino) e
     * uno spazio di larghezza zero. Così il posto non è mai l'ultimo carattere della riga e su una voce vuota il cursore
     * sta dopo il cerchio, non in mezzo (1.562).
     */
    const val PLACE = "\u2009\u200B"

    /**
     * Il testo come lo disegna l'editor: i segni dei formati [hidden] (da [NoteFormat.hidden]) spariscono, il segno di
     * ogni voce diventa [PLACE]. Il cursore non può finire dentro un segno: da lì va sempre dopo.
     */
    class Shown(val text: String, hidden: List<IntRange> = emptyList()) {
        data class Cut(val start: Int, val end: Int, val with: String)

        val lines: List<Line> = parse(text)
        val cuts: List<Cut> = (lines.filter { it.hasMark && it.prefix > 0 }.map { Cut(it.start, it.contentStart, PLACE) } +
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

    /** Una linea sotto la riga del cursore (1.584); il cursore va nella riga dopo la linea. */
    fun insertDivider(text: String, offset: Int): Edit {
        val l = lineAt(text, offset.coerceIn(0, text.length))
        val blank = text.substring(l.contentStart, l.end).isBlank() && l.kind == Kind.TEXT
        if (blank) {
            // Su una riga vuota la linea va lì.
            val t = text.substring(0, l.start) + DIVIDER + "\n" + text.substring(l.end)
            return Edit(t, l.start + DIVIDER.length + 1)
        }
        // Sotto la riga: se dopo c'è già una riga il cursore va lì, se no se ne apre una nuova.
        val rest = text.substring(l.end)
        val t = text.substring(0, l.end) + "\n" + DIVIDER + (if (rest.isEmpty()) "\n" else "") + rest
        return Edit(t, (l.end + DIVIDER.length + 2).coerceAtMost(t.length))
    }

    /** "Cerca nella nota" (1.579): dove compare [query], senza badare a maiuscole. Vuota = niente. */
    fun matches(text: String, query: String): List<IntRange> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        val out = mutableListOf<IntRange>()
        var at = text.indexOf(q, 0, ignoreCase = true)
        while (at >= 0) {
            out += at until at + q.length
            at = text.indexOf(q, at + q.length, ignoreCase = true)
        }
        return out
    }

    /** Un cursore finito dentro il segno di una voce va subito dopo il segno. */
    fun outOfPrefix(text: String, offset: Int): Int {
        val line = lineAt(text, offset)
        return if (line.hasMark && offset >= line.start && offset < line.contentStart) line.contentStart else offset
    }
}
