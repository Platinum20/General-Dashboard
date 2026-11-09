package com.platinum20.dashboard.shifts

import org.json.JSONArray
import org.json.JSONObject
import java.text.Normalizer
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

/** Un turno di lavoro di Antonio su Creators Pro: un evento con inizio e fine. */
data class Shift(val id: String, val start: Long, val end: Long, val title: String = "Turno", val scope: String = "") {
    /** Id negativo: nel calendario non si confonde con gli eventi del telefono (positivi, -1 = nessuno). */
    val eventId: Long get() = -((id.hashCode().toLong() and 0xFFFFFFFFL) + 10)
}

/**
 * Quello che dice una risposta della timeline: i turni di Antonio, il periodo che la risposta copre
 * (per togliere quelli spariti) e gli id con cui Creators Pro lo indica.
 */
data class ShiftRead(val mine: List<Shift>, val from: Long, val to: Long, val myIds: Set<String>, val seen: Int = mine.size, val scope: String = "") {
    /** Solo aggiunte, niente tolto: un messaggio in tempo reale o un turno solo (di un altro) non dicono tutto il periodo. */
    fun additive() = copy(from = 0, to = 0)
}

/**
 * Lettura dei turni da qualunque risposta JSON di Creators Pro. Il formato non è pubblico: un turno
 * è un oggetto con un inizio e una fine (ISO, epoch o data + "07:00"), di Antonio se porta il suo nome
 * (anche in un oggetto persona dentro o sopra) o un id che altrove è legato al suo nome.
 */
object ShiftParse {
    /**
     * Le creator del turno, dal titolo "Turno · Prima, Seconda": sulla linea del tempo vanno sotto "Turno",
     * tutte e intere (Antonio). Vuoto se il titolo non è quello di un turno.
     */
    fun creators(title: String): List<String> =
        title.removePrefix("Turno · ").takeIf { title.startsWith("Turno · ") }
            ?.split(", ")?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()

    const val ME = "Antonio Nesca"
    private const val MAX_MS = 24 * 3600_000L
    private val START = Regex("^(shift|work)?(start|begin|from)(s)?(at|on|date|time|datetime|ts|timestamp)?$")
    private val END = Regex("^(shift|work)?(end|finish|to|until|stop)(s)?(at|on|date|time|datetime|ts|timestamp)?$")
    private val START2 = Regex("^(date|time|datetime)(start|begin|from)$")
    private val END2 = Regex("^(date|time|datetime)(end|finish|to|until)$")
    private val DAY = setOf("date", "day", "shiftdate", "workdate", "dateday")
    private val HHMM = Regex("""^(\d{1,2})[:.](\d{2})(?::\d{2})?$""")
    private val PERSON = Regex("^(user|employee|member|assignee|assignedto|chatter|staff|person|worker|operator|owner|profile|resource|agent|teammember)s?$")
    private val TITLE = listOf("title", "label", "role", "position", "shiftname", "shifttype", "type", "name")

    private fun norm(key: String) = key.lowercase().replace("_", "").replace("-", "")

    private fun plain(s: String) = Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{M}"), "").lowercase()

    /** "Antonio Nesca", "NESCA Antonio", "antonio.nesca": basta che ci siano nome e cognome. */
    fun isMe(text: String, me: String = ME): Boolean {
        val t = plain(text)
        return plain(me).split(' ').filter { it.isNotBlank() }.all { t.contains(it) }
    }

    private val SHIFTY = Regex("(?i)shift|turn|timeline|schedul|planning|roster|calendar")
    private val NAME_KEY = Regex("^(full|display|user)?(name|title|label)$")

    /**
     * Il nome nell'oggetto stesso: un campo testo, o nome e cognome in due campi. Con [strict] solo un
     * campo che è proprio il nome (name, fullName, title…): così "utente collegato: Antonio" in cima a
     * una risposta non fa diventare suoi i turni di tutti.
     */
    private fun namesMe(o: JSONObject, me: String, strict: Boolean = false): Boolean {
        var first = ""; var last = ""
        for (k in o.keys()) {
            val v = o.opt(k) as? String ?: continue
            if (v.length <= 120 && isMe(v, me) && (!strict || NAME_KEY.matches(norm(k)))) return true
            val n = norm(k)
            if (n.contains("first") || n == "nome" || n == "givenname") first = v
            if (n.contains("last") || n.contains("surname") || n == "cognome" || n == "familyname") last = v
        }
        return first.isNotEmpty() && last.isNotEmpty() && isMe("$first $last", me)
    }

    /** Un oggetto persona appeso (user, employee, chatter…) col nome di Antonio. */
    private fun personMe(o: JSONObject, me: String): Boolean = o.keys().asSequence().any { k ->
        val v = o.opt(k)
        PERSON.matches(norm(k)) && v is JSONObject && namesMe(v, me)
    }

    private fun idOf(o: JSONObject): String? = listOf("id", "_id", "uuid", "uid").firstNotNullOfOrNull { k ->
        o.opt(k)?.takeIf { it is String || it is Number }?.toString()?.takeIf { it.isNotBlank() }
    }

    /** Gli id di persona che il turno cita (userId, employee_ids, resourceId…). */
    private fun refIds(o: JSONObject): Set<String> {
        val out = HashSet<String>()
        for (k in o.keys()) {
            val n = norm(k)
            if (n == "id" || !(n.endsWith("id") || n.endsWith("ids"))) continue
            when (val v = o.opt(k)) {
                is String, is Number -> out += v.toString()
                is JSONArray -> for (i in 0 until v.length()) v.opt(i)?.takeIf { it is String || it is Number }?.let { out += it.toString() }
            }
        }
        for (k in o.keys()) {
            val v = o.opt(k)
            if (PERSON.matches(norm(k)) && v is JSONObject) idOf(v)?.let { out += it }
        }
        return out
    }

    /** Un istante da un valore: epoch (secondi o millisecondi), ISO con o senza fuso, {dateTime: …}. */
    fun instant(v: Any?, zone: ZoneId): Long? {
        val ms = when (v) {
            is Number -> v.toDouble().let { if (it < 1e11) (it * 1000).toLong() else it.toLong() }
            is String -> {
                val s = v.trim()
                if (s.length < 16 || !s[4].equals('-')) null
                else runCatching { OffsetDateTime.parse(s).toInstant().toEpochMilli() }.getOrNull()
                    ?: runCatching { ZonedDateTime.parse(s).toInstant().toEpochMilli() }.getOrNull()
                    ?: runCatching { Instant.parse(s).toEpochMilli() }.getOrNull()
                    ?: runCatching { LocalDateTime.parse(s.replace(' ', 'T')).atZone(zone).toInstant().toEpochMilli() }.getOrNull()
            }
            is JSONObject -> instant(v.opt("dateTime") ?: v.opt("datetime") ?: v.opt("date_time"), zone)
            else -> null
        } ?: return null
        return ms.takeIf { it in 946_684_800_000L..4_102_444_800_000L }
    }

    private fun clock(v: Any?): LocalTime? {
        val m = HHMM.matchEntire((v as? String)?.trim() ?: return null) ?: return null
        val h = m.groupValues[1].toInt(); val min = m.groupValues[2].toInt()
        return if (h in 0..24 && min in 0..59) if (h == 24) LocalTime.MIDNIGHT else LocalTime.of(h, min) else null
    }

    /** Inizio e fine del turno, se l'oggetto ne ha (al massimo 24 ore, la fine dopo l'inizio). */
    fun times(o: JSONObject, zone: ZoneId): Pair<Long, Long>? {
        var s: Any? = null; var e: Any? = null; var day: String? = null
        for (k in o.keys()) {
            val n = norm(k)
            when {
                START.matches(n) || START2.matches(n) -> if (s == null) s = o.opt(k)
                END.matches(n) || END2.matches(n) -> if (e == null) e = o.opt(k)
                n in DAY -> day = o.optString(k).take(10)
            }
        }
        if (s == null || e == null) return null
        var a = instant(s, zone); var b = instant(e, zone)
        // Data a parte e ore "07:00"–"12:00": il turno di notte finisce il giorno dopo.
        if (a == null || b == null) {
            val d = day?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return null
            val ts = clock(s) ?: return null
            val te = clock(e) ?: return null
            a = d.atTime(ts).atZone(zone).toInstant().toEpochMilli()
            b = (if (te <= ts) d.plusDays(1) else d).atTime(te).atZone(zone).toInstant().toEpochMilli()
        }
        return if (b > a && b - a <= MAX_MS) a to b else null
    }

    private fun titleOf(o: JSONObject, me: String): String {
        val extra = TITLE.firstNotNullOfOrNull { want ->
            o.keys().asSequence().firstOrNull { norm(it) == want }?.let { o.opt(it) as? String }
                ?.trim()?.takeIf { it.isNotEmpty() && it.length <= 40 && !isMe(it, me) && !it.first().isDigit() }
        }
        return if (extra == null || extra.equals("turno", ignoreCase = true) || extra.equals("shift", ignoreCase = true)) "Turno" else "Turno · $extra"
    }

    /** Primo giro: gli id legati al nome di Antonio (persone, righe della timeline), non i turni. */
    private fun learn(node: Any?, me: String, zone: ZoneId, out: MutableSet<String>) {
        when (node) {
            is JSONObject -> {
                if (times(node, zone) == null && namesMe(node, me)) idOf(node)?.let { out += it }
                for (k in node.keys()) learn(node.opt(k), me, zone, out)
            }
            is JSONArray -> for (i in 0 until node.length()) learn(node.opt(i), me, zone, out)
        }
    }

    private class Walk(val me: String, val zone: ZoneId, val ids: Set<String>) {
        val mine = ArrayList<Shift>()
        var from = Long.MAX_VALUE
        var to = Long.MIN_VALUE
        var seen = 0
        val any get() = seen > 0

        fun visit(node: Any?, owned: Boolean) {
            when (node) {
                is JSONObject -> {
                    val t = times(node, zone)
                    if (t != null) {
                        seen++
                        from = minOf(from, t.first); to = maxOf(to, t.second)
                        val isMine = owned || namesMe(node, me) || personMe(node, me) || refIds(node).any { it in ids }
                        if (isMine) mine += Shift(idOf(node) ?: "${t.first}-${t.second}", t.first, t.second, titleOf(node, me))
                        return
                    }
                    // Un gruppo con la persona sopra (es. {user: {name}, shifts: […]}): tutto il gruppo è suo.
                    val here = owned || namesMe(node, me, strict = true) || personMe(node, me)
                    for (k in node.keys()) visit(node.opt(k), here)
                }
                is JSONArray -> for (i in 0 until node.length()) visit(node.opt(i), owned)
            }
        }
    }

    /**
     * Il periodo chiesto nell'indirizzo (?start=2026-11-09&end=2026-11-16): conta anche quando la
     * risposta è vuota, così l'ultimo turno tolto della settimana sparisce lo stesso.
     */
    fun urlWindow(url: String, zone: ZoneId = ZoneId.systemDefault()): Pair<Long, Long>? {
        val query = url.substringAfter('?', "").takeIf { it.isNotEmpty() } ?: return null
        var a: Long? = null; var b: Long? = null
        for (part in query.split('&')) {
            val k = norm(part.substringBefore('='))
            val raw = java.net.URLDecoder.decode(part.substringAfter('=', ""), "UTF-8")
            val v = instant(raw, zone) ?: instant(raw.toLongOrNull(), zone)
                ?: runCatching { LocalDate.parse(raw.take(10)).atStartOfDay(zone).toInstant().toEpochMilli() }.getOrNull()
            when {
                START.matches(k) || START2.matches(k) -> a = a ?: v
                END.matches(k) || END2.matches(k) -> b = b ?: v
            }
        }
        val from = a ?: return null
        val to = b ?: return null
        return if (to > from) from to to else null
    }

    private fun param(url: String, name: String): String? = url.substringAfter('?', "").split('&')
        .firstOrNull { it.substringBefore('=').equals(name, ignoreCase = true) }
        ?.substringAfter('=')?.let { java.net.URLDecoder.decode(it, "UTF-8") }

    /**
     * La timeline vera di Creators Pro (/v1/timeline/events, una creator per volta): ogni slot ha solo
     * l'inizio, la fascia ("Morning" 7–12…) ha un modello con inizio e fine, e il turno dice chi c'è
     * (shift.member). L'inizio salvato è sfasato di un'ora d'inverno (il sito lo scrive con l'ora del
     * modello, estiva): l'ora giusta è quella del modello nel suo fuso, come la mostra la pagina.
     */
    fun creatorsEvents(root: Any, url: String, ids: Set<String>, me: String = ME, zone: ZoneId = ZoneId.systemDefault()): ShiftRead? {
        val data = (root as? JSONObject)?.optJSONArray("data") ?: return null
        val mine = ArrayList<Shift>()
        var any = false
        for (i in 0 until data.length()) {
            val ev = data.optJSONObject(i) ?: continue
            val begin = instant(ev.opt("startedAt"), zone) ?: continue
            val interval = ev.optJSONObject("interval") ?: continue
            any = true
            val shift = ev.optJSONObject("shift") ?: continue
            val member = shift.optJSONObject("member") ?: continue
            if (!namesMe(member, me) && idOf(member) !in ids) continue
            val model = interval.optJSONArray("creators")?.optJSONObject(0)
            val tz = runCatching { ZoneId.of(model?.optString("timeZone")?.takeIf { it.isNotBlank() } ?: "Europe/Rome") }.getOrDefault(zone)
            val ms = model?.let { instant(it.opt("startedAt"), zone) }
            val me2 = model?.let { instant(it.opt("endedAt"), zone) }
            val length = if (ms != null && me2 != null && me2 > ms && me2 - ms <= MAX_MS) me2 - ms else 5 * 3600_000L
            // Data e ora come sulla pagina: l'istante salvato letto con lo scarto del modello.
            val offset = tz.rules.getOffset(Instant.ofEpochMilli(ms ?: begin))
            val local = LocalDateTime.ofEpochSecond(begin / 1000, 0, offset)
            val start = local.atZone(tz).toInstant().toEpochMilli()
            val aliases = ev.optJSONArray("creators")?.let { a ->
                (0 until a.length()).mapNotNull { a.optJSONObject(it)?.optJSONObject("creator")?.optString("alias")?.takeIf { s -> s.isNotBlank() } }
            }.orEmpty()
            val title = if (aliases.isEmpty()) "Turno" else "Turno · " + aliases.joinToString(", ")
            val scope = "cp:" + (param(url, "creatorId") ?: "")
            mine += Shift(shift.optString("id").ifBlank { ev.optString("id") }, start, start + length, title, scope)
        }
        val from = instant(param(url, "startedAt"), zone)
        val to = instant(param(url, "endedAt"), zone)
        if (!any && (from == null || to == null)) return null
        return ShiftRead(
            mine.distinctBy { it.id }.sortedBy { it.start },
            from ?: mine.minOfOrNull { it.start } ?: 0, to ?: mine.maxOfOrNull { it.end } ?: 0,
            emptySet(), data.length(), "cp:" + (param(url, "creatorId") ?: ""),
        )
    }

    /** null se la risposta non è JSON o non contiene turni di nessuno (e l'indirizzo non dice il periodo). */
    fun read(body: String, known: Set<String> = emptySet(), me: String = ME, zone: ZoneId = ZoneId.systemDefault(), url: String = ""): ShiftRead? {
        val root: Any = runCatching {
            val t = body.trimStart()
            if (t.startsWith("[")) JSONArray(t) else JSONObject(t)
        }.getOrNull() ?: return null
        val ids = HashSet(known).also { learn(root, me, zone, it) }
        if (url.contains("/timeline/events")) creatorsEvents(root, url, ids, me, zone)?.let { return it.copy(myIds = ids - known) }
        val walk = Walk(me, zone, ids).also { it.visit(root, false) }
        // Il periodo dell'indirizzo vale solo per chiamate che parlano di turni: altrove svuoterebbe a torto.
        val asked = urlWindow(url, zone)?.takeIf { SHIFTY.containsMatchIn(url.substringBefore('?')) }
        if (!walk.any && asked == null) return null
        val from = minOf(walk.from, asked?.first ?: Long.MAX_VALUE)
        val to = maxOf(walk.to, asked?.second ?: Long.MIN_VALUE)
        return ShiftRead(walk.mine.distinctBy { it.id }.sortedBy { it.start }, from, to, ids - known, walk.seen)
    }

    /**
     * I turni dopo una lettura: quelli già noti nel periodo letto lasciano il posto ai nuovi
     * (così un turno tolto sparisce), fuori dal periodo restano com'erano.
     */
    fun merge(old: List<Shift>, read: ShiftRead): List<Shift> {
        // Solo la stessa fonte (es. la stessa creator): la timeline di una creator non dice niente delle altre.
        val kept = old.filter { it.scope != read.scope || it.end <= read.from || it.start >= read.to }
        return (read.mine + kept).distinctBy { it.id }.sortedBy { it.start }
    }

    fun toJson(list: List<Shift>): String = JSONArray().also { a ->
        list.forEach { a.put(JSONObject().put("id", it.id).put("start", it.start).put("end", it.end).put("title", it.title).put("scope", it.scope)) }
    }.toString()

    fun fromJson(text: String?): List<Shift> = runCatching {
        val a = JSONArray(text ?: return emptyList())
        (0 until a.length()).map { i ->
            val o = a.getJSONObject(i)
            Shift(o.getString("id"), o.getLong("start"), o.getLong("end"), o.optString("title", "Turno"), o.optString("scope"))
        }
    }.getOrDefault(emptyList())
}
