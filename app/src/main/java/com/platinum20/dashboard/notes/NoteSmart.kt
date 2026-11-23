package com.platinum20.dashboard.notes

import java.time.LocalDateTime

/**
 * Le parti "intelligenti" del testo di una nota (2026-11-23, Antonio: le note legate al resto dell'app):
 * #etichette, date scritte a parole, codici e password, codici di spedizione. Funzioni pure: l'editor
 * le colora e ci mette sopra un tocco con il fumetto delle scelte.
 */
object NoteSmart {
    enum class Kind { TAG, DATE, CODE, PARCEL }

    /** [range] = i caratteri nel testo grezzo della nota; [value] = l'etichetta, il codice o la data scritta. */
    data class Smart(val range: IntRange, val kind: Kind, val value: String)

    private const val B0 = "(?<![\\p{L}\\d])"
    private const val B1 = "(?![\\p{L}\\d])"

    /** #casa, #lavoro_2: dopo uno spazio o a inizio riga, almeno due caratteri, non solo numeri. */
    private val tagRx = Regex("(?<![^\\s(])#([\\p{L}\\d_]{2,40})(?![\\p{L}\\d_])")

    private const val WEEKDAY = "luned[iì]|marted[iì]|mercoled[iì]|gioved[iì]|venerd[iì]|sabato|domenica"
    private const val MONTH = "gennaio|febbraio|marzo|aprile|maggio|giugno|luglio|agosto|settembre|ottobre|novembre|dicembre"
    private const val TIME = "(?: (?:alle|all'|ore|verso le) ?\\d{1,2}(?:[:.]\\d{2})?| \\d{1,2}[:.]\\d{2})?"
    private val dateRx = Regex(
        "$B0(?:(?:entro |il |per )?(?:(?:oggi|domani|dopodomani)(?: (?:mattina|pomeriggio|sera))?|stasera|stamattina|domattina|" +
            "(?:(?:il |questo |questa |prossimo |prossima )?(?:$WEEKDAY))(?: prossim[oa])?(?: \\d{1,2}(?: (?:$MONTH)))?|" +
            "\\d{1,2} (?:$MONTH)(?: \\d{4})?|\\d{1,2}/\\d{1,2}(?:/\\d{2,4})?)$TIME|entro il \\d{1,2}$TIME)$B1",
        RegexOption.IGNORE_CASE,
    )

    /** "password: x", "wifi: x", "pin 1234": il valore dopo la parola. */
    private val codeRx = Regex(
        "$B0(?:password|passwd|pass|pw|pin|puk|wi-?fi|codice|code|cancello|citofono|otp|iban)(?: wifi)?\\s*[:=]?\\s*([^\\s,;]{3,64})",
        RegexOption.IGNORE_CASE,
    )

    /** Tutte le parti intelligenti del testo, in ordine e senza sovrapposizioni; mai dentro un link. */
    fun find(text: String, links: List<IntRange> = emptyList()): List<Smart> {
        val out = mutableListOf<Smart>()
        fun free(r: IntRange) = links.none { it.first <= r.last && r.first <= it.last } && out.none { it.range.first <= r.last && r.first <= it.range.last }
        Carrier.findCode(text)?.let { code ->
            val at = text.uppercase().indexOf(code)
            if (at >= 0) (at until at + code.length).takeIf(::free)?.let { out += Smart(it, Kind.PARCEL, code) }
        }
        codeRx.findAll(text).forEach { m ->
            val g = m.groups[1] ?: return@forEach
            if (g.value.startsWith("#") || g.value.startsWith("http")) return@forEach
            // "codice fiscale", "pass del treno": senza ":" il valore deve avere almeno un numero.
            if (!m.value.contains(':') && !m.value.contains('=') && g.value.none { it.isDigit() }) return@forEach
            if (free(g.range)) out += Smart(g.range, Kind.CODE, g.value)
        }
        tagRx.findAll(text).forEach { m ->
            val name = m.groupValues[1]
            if (name.all { it.isDigit() }) return@forEach
            if (free(m.range)) out += Smart(m.range, Kind.TAG, name.lowercase())
        }
        dateRx.findAll(text).forEach { m -> if (free(m.range)) out += Smart(m.range, Kind.DATE, m.value) }
        return out.sortedBy { it.range.first }
    }

    /** Quando cade una data scritta ("giovedì alle 15"); null se non si capisce. */
    fun dateOf(written: String, now: LocalDateTime = LocalDateTime.now()): LocalDateTime? =
        QuickParse.parse(written.replace(Regex("^(?i)entro "), ""), now).due

    /** Il nome della cosa da mettere in calendario: la riga della data, senza la data e senza segni. */
    fun titleAround(text: String, smart: Smart): String {
        val start = text.lastIndexOf('\n', smart.range.first - 1) + 1
        val end = text.indexOf('\n', smart.range.last).let { if (it < 0) text.length else it }
        val line = text.substring(start, smart.range.first) + text.substring(smart.range.last + 1, end)
        return NoteLines.plainLine(line).replace(Regex("\\s+"), " ").trim(' ', ',', '.', ':', '-').ifEmpty { "Dalla nota" }
    }

    /** Le etichette di un testo, senza doppioni. */
    fun tags(text: String): Set<String> = find(text).filter { it.kind == Kind.TAG }.map { it.value }.toSet()

    /** Quante volte compare ogni etichetta in tutte le note e le cose da fare, le più usate prima. */
    fun allTags(data: NotesData): List<String> =
        (data.notes.map { it.text } + data.tasks.map { it.text + "\n" + it.notes })
            .flatMap { tags(it) }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.map { it.key }

    /** Le note e le cose da fare con l'etichetta [tag]. */
    fun tagged(data: NotesData, tag: String): Pair<List<Note>, List<TaskItem>> {
        val t = tag.lowercase().removePrefix("#")
        return data.notes.filter { it.archivedAt == null && t in tags(it.text) }.sortedByDescending { it.updatedAt } to
            data.tasks.filter { t in tags(it.text + "\n" + it.notes) }.sortedBy { it.dueAt ?: Long.MAX_VALUE }
    }

    /**
     * "Citato in": le note che contengono un link @ verso una di queste [urls] (indirizzi `app:` o `nota:`).
     * Un evento si riconosce dall'id anche se il link ha orari diversi (le ripetizioni).
     */
    fun citing(data: NotesData, urls: List<String>, selfId: Long? = null): List<Note> {
        if (urls.isEmpty()) return emptyList()
        val keys = urls.map(::linkKey)
        return data.notes.filter { n ->
            n.id != selfId && NoteFormat.mentionUrls(n.text).any { u -> linkKey(u) in keys }
        }.sortedByDescending { it.updatedAt }
    }

    /** La parte di un indirizzo che dice "quale cosa": per gli eventi solo l'id. */
    internal fun linkKey(url: String): String {
        val u = url.removePrefix(NoteFormat.APP_LINK)
        Regex("""^home/calendar/event\?id=(-?\d+)""").find(u)?.let { return "event:" + it.groupValues[1] }
        return u.substringBefore('?')
    }

    /** La riga della nota dove compare il link, per il sottotitolo di "Citato in". */
    fun citeLine(note: Note, urls: List<String>): String {
        val keys = urls.map(::linkKey)
        val line = note.text.lines().drop(1).firstOrNull { l -> NoteFormat.mentionUrls(l).any { linkKey(it) in keys } }
            ?: note.text.lines().firstOrNull { l -> NoteFormat.mentionUrls(l).any { linkKey(it) in keys } } ?: ""
        return NoteFormat.plain(line).replace(Regex("\\s+"), " ").trim()
    }

    /** Le persone dei turni (chi era in doppio), per "@" e per la pagina della persona; le più frequenti prima. */
    fun people(mates: List<List<String>>): List<String> =
        mates.flatten().map { it.trim() }.filter { it.isNotEmpty() }.groupingBy { it }.eachCount()
            .entries.sortedByDescending { it.value }.map { it.key }

    /** Il nome nella forma dei turni: solo il primo nome ("Marco Rossi" → "Marco"). */
    fun firstName(name: String): String = name.trim().substringBefore(' ')
}
