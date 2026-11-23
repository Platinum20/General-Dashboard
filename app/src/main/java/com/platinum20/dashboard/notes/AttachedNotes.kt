package com.platinum20.dashboard.notes

import java.time.Instant
import java.time.ZoneId

/**
 * Gli appunti attaccati a una cosa (idee 1, 17, 21, 24, 28 di note-integrate, Antonio 2026-11-23): un evento, un turno,
 * un treno o un'abitudine hanno una loro nota ([Note.about] = la chiave). La nota vive lì, non in Appunti; si cerca
 * come le altre. Qui solo funzioni pure: chiavi e righe ancora da fare.
 */
object AttachedNotes {
    private const val EVENT = "ev:"
    private const val HABIT = "habit:"

    /**
     * La chiave di un evento (turni e treni compresi: hanno il loro id). Un evento che si ripete ha appunti suoi per
     * ogni volta, quindi anche il giorno; uno solo no, così spostarlo non perde gli appunti.
     */
    fun event(id: Long, begin: Long = 0L, recurring: Boolean = false, zone: ZoneId = ZoneId.systemDefault()): String =
        if (recurring && begin > 0) "$EVENT$id@${Instant.ofEpochMilli(begin).atZone(zone).toLocalDate().toEpochDay()}" else "$EVENT$id"

    fun habit(id: Long): String = "$HABIT$id"

    /** La nota di un evento: prima quella di questa volta, poi quella dell'evento intero. */
    fun forEvent(notes: List<Note>, id: Long, begin: Long, zone: ZoneId = ZoneId.systemDefault()): Note? {
        val day = event(id, begin, recurring = true, zone = zone)
        val whole = event(id)
        return notes.firstOrNull { it.about == day } ?: notes.firstOrNull { it.about == whole }
    }

    /** L'id dell'evento di una chiave ("ev:12@20050" → 12); null se non è di un evento. */
    fun eventId(key: String?): Long? = key?.takeIf { it.startsWith(EVENT) }?.removePrefix(EVENT)?.substringBefore('@')?.toLongOrNull()

    /** Una riga ancora da fare: [index] = la riga nel testo, [text] = senza segni. */
    data class Open(val index: Int, val text: String)

    /**
     * Le cose ancora da fare: le voci da spuntare aperte; se la nota non ha voci, ogni riga scritta (sono appunti
     * semplici, "Portare badge" senza quadratino, e vanno ricordati lo stesso). I titoletti non contano.
     */
    fun open(body: String): List<Open> {
        val lines = NoteLines.parse(body)
        val checks = lines.filter { it.kind == NoteLines.Kind.CHECK }
        val wanted = if (checks.isNotEmpty()) checks.filter { !it.done }
        else lines.filter { it.kind == NoteLines.Kind.TEXT || it.kind == NoteLines.Kind.BULLET || it.kind == NoteLines.Kind.NUMBER }
        return wanted.map { Open(it.index, NoteLines.plainLine(body.substring(it.start, it.end))) }.filter { it.text.isNotEmpty() }
    }

    /** Solo le voci da spuntare aperte: le "cose rimaste" dopo un evento (idea 28). */
    fun openChecks(body: String): List<Open> {
        val lines = NoteLines.parse(body)
        return lines.filter { it.kind == NoteLines.Kind.CHECK && !it.done }
            .map { Open(it.index, NoteLines.plainLine(body.substring(it.start, it.end))) }.filter { it.text.isNotEmpty() }
    }

    /** "Fatto" dalla notifica: una voce si spunta, una riga semplice diventa una voce spuntata. */
    fun tick(body: String, index: Int): String {
        val line = NoteLines.parse(body).getOrNull(index) ?: return body
        if (line.kind == NoteLines.Kind.CHECK) return if (line.done) body else NoteLines.toggle(body, index)
        val content = NoteLines.plainLine(body.substring(line.start, line.end))
        if (content.isEmpty()) return body
        return body.substring(0, line.start) + NoteLines.DONE + body.substring(line.contentStart)
    }

    /** "Fatte" / "Fatto tutto": tutto quello ancora da fare spuntato ([open]); le righe non cambiano di numero. */
    fun tickAll(body: String): String {
        var t = body
        open(body).forEach { t = tick(t, it.index) }
        return t
    }

    /** "Sposta in Da fare": le voci aperte escono dalla nota (vanno in Da fare), le fatte restano. */
    fun withoutOpenChecks(body: String): String {
        val drop = openChecks(body).map { it.index }.toSet()
        if (drop.isEmpty()) return body
        return body.split('\n').filterIndexed { i, _ -> i !in drop }.joinToString("\n")
    }

    /** La riga della notifica dal vivo e del calendario: "📝 Portare badge e caricatore". */
    fun line(body: String): String? = open(body).firstOrNull()?.let { "📝 ${it.text}" }

    /** "2 cose aperte" / "1 cosa aperta". */
    fun leftCount(n: Int): String = if (n == 1) "1 cosa aperta" else "$n cose aperte"

    /**
     * La riga della notifica del mattino (idea 21): le prime cose da fare degli appunti di oggi, "📝 Oggi: badge,
     * cambio con Marco". Massimo [max] pezzi, poi "…".
     */
    fun morning(bodies: List<String>, max: Int = 3): String? {
        val all = bodies.flatMap { b -> open(b).map { it.text } }.distinct()
        if (all.isEmpty()) return null
        val shown = all.take(max).joinToString(", ")
        return "📝 Oggi: $shown" + if (all.size > max) ", …" else ""
    }

    /** Il testo della nota attaccata: il titolo della cosa sulla prima riga (si trova cercando), poi gli appunti. */
    fun text(title: String, body: String): String = NoteText.join(title.replace('\n', ' ').trim().ifEmpty { "Appunti" }, body.trimEnd())

    /** Gli appunti senza la prima riga (il titolo della cosa). */
    fun body(note: Note?): String = note?.text?.let { NoteText.split(it).second }.orEmpty()
}
