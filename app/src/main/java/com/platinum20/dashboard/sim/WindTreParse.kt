package com.platinum20.dashboard.sim

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Quanto resta di una soglia dell'offerta: giga in GB, minuti, SMS. */
data class Allowance(val left: Double, val total: Double? = null, val unlimited: Boolean = false) {
    /** Da 0 a 1, quanto resta rispetto al totale; null se non si sa. */
    val fraction: Float? get() = total?.takeIf { it > 0 && !unlimited }?.let { (left / it).coerceIn(0.0, 1.0).toFloat() }
}

/** Lo stato della SIM letto dall'area clienti WindTre. */
data class SimStatus(
    val data: Allowance? = null,
    val minutes: Allowance? = null,
    val sms: Allowance? = null,
    /** I giga in roaming UE, a parte da quelli nazionali. */
    val roaming: Allowance? = null,
    val credit: Double? = null,
    val renewal: LocalDate? = null,
    val offer: String? = null,
    val at: Long = 0,
) {
    val hasAny: Boolean get() = data != null || minutes != null || sms != null || credit != null || renewal != null

    /** I campi che mancano qui si prendono da [other]. */
    fun orElse(other: SimStatus?): SimStatus = if (other == null) this else copy(
        data = data ?: other.data,
        minutes = minutes ?: other.minutes,
        sms = sms ?: other.sms,
        roaming = roaming ?: other.roaming,
        credit = credit ?: other.credit,
        renewal = renewal ?: other.renewal,
        offer = offer ?: other.offer,
    )

    fun toJson(): JSONObject = JSONObject().apply {
        fun Allowance.json() = JSONObject().put("left", left).put("total", total ?: JSONObject.NULL).put("unlimited", unlimited)
        data?.let { put("data", it.json()) }
        minutes?.let { put("minutes", it.json()) }
        sms?.let { put("sms", it.json()) }
        roaming?.let { put("roaming", it.json()) }
        credit?.let { put("credit", it) }
        renewal?.let { put("renewal", it.toString()) }
        offer?.let { put("offer", it) }
        put("at", at)
    }

    companion object {
        fun fromJson(o: JSONObject): SimStatus {
            fun allowance(key: String) = o.optJSONObject(key)?.let {
                Allowance(it.optDouble("left"), if (it.isNull("total")) null else it.optDouble("total"), it.optBoolean("unlimited"))
            }
            return SimStatus(
                data = allowance("data"),
                minutes = allowance("minutes"),
                sms = allowance("sms"),
                roaming = allowance("roaming"),
                credit = if (o.has("credit")) o.optDouble("credit") else null,
                renewal = o.optString("renewal").takeIf { it.isNotBlank() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
                offer = o.optString("offer").takeIf { it.isNotBlank() },
                at = o.optLong("at"),
            )
        }
    }
}

/**
 * Legge giga, minuti, SMS, credito e rinnovo da quello che riceve l'area clienti WindTre.
 * Il formato delle sue risposte non è pubblico: si cercano i nomi tipici (residuo, totale, credito,
 * rinnovo) in qualsiasi punto del JSON, e in mancanza si legge il testo della pagina. Funzioni pure.
 */
object WindTreParse {

    private enum class Kind { DATA, MINUTES, SMS, ROAMING }

    private val leftKeys = Regex("residu|remain|availab|rimanent|left|balance", RegexOption.IGNORE_CASE)
    private val totalKeys = Regex("total|initial|totale|max|quota|threshold|soglia|amount|bundle", RegexOption.IGNORE_CASE)
    private val usedKeys = Regex("used|consum|utilizz", RegexOption.IGNORE_CASE)
    private val creditKeys = Regex("credit|credito|saldo", RegexOption.IGNORE_CASE)
    private val renewalKeys = Regex("renew|rinnov|expir|scadenz|validto|validuntil|enddate|nextcharge|nextbilling|deactivation", RegexOption.IGNORE_CASE)
    private val unlimitedWords = Regex("illimitat|unlimited|unlim", RegexOption.IGNORE_CASE)

    /** Tutto quello che si capisce da una risposta JSON; null se non c'è niente di utile. */
    fun fromJson(text: String, today: LocalDate = LocalDate.now()): SimStatus? {
        val root: Any = runCatching {
            val t = text.trim()
            if (t.startsWith("[")) JSONArray(t) else JSONObject(t)
        }.getOrNull() ?: return null
        val counters = mutableListOf<Pair<Kind, Allowance>>()
        val credits = mutableListOf<Double>()
        val dates = mutableListOf<LocalDate>()
        var offer: String? = null
        var masterOffer: String? = null
        fun walk(node: Any?, path: String) {
            when (node) {
                is JSONObject -> {
                    // Un dizionario di messaggi (centinaia di chiavi, quasi tutte testo) non contiene contatori.
                    if (node.length() > 80 && node.keys().asSequence().count { node.opt(it) is String } > node.length() * 0.9) return
                    counter(node, path)?.let { counters += it }
                    // L'area clienti vera (lineunfolded) segna l'offerta coi contatori come classification MASTER:
                    // il suo nome ("GO 200 XXS") vale più di quello del piano base ("New Basic").
                    if (node.optString("classification").equals("MASTER", ignoreCase = true)) {
                        node.optString("name").takeIf { it.isNotBlank() }?.let { masterOffer = it }
                    }
                    for (key in node.keys()) {
                        val value = node.opt(key)
                        val k = key.lowercase()
                        // "balance" da solo è anche il residuo di un contatore: vale come credito solo sotto "credit" o come "mainbalance".
                        val isCredit = creditKeys.containsMatchIn(k) || k == "mainbalance" ||
                            (k.contains("balance") && creditKeys.containsMatchIn(path))
                        if (isCredit) number(value)?.let { v -> credit(k, v)?.let { credits += it } }
                        if (renewalKeys.containsMatchIn(k)) date(value)?.let { if (!it.isBefore(today)) dates += it }
                        if (offer == null && (k == "offername" || k == "nomeofferta" || k == "tariffname" || k == "planname")) {
                            (value as? String)?.takeIf { it.isNotBlank() }?.let { offer = it }
                        }
                        if (value is JSONObject || value is JSONArray) walk(value, "$path/$k")
                    }
                }
                is JSONArray -> for (i in 0 until node.length()) walk(node.opt(i), path)
            }
        }
        walk(root, "")
        val status = SimStatus(
            data = best(counters, Kind.DATA),
            minutes = best(counters, Kind.MINUTES),
            sms = best(counters, Kind.SMS),
            roaming = best(counters, Kind.ROAMING),
            credit = credits.firstOrNull(),
            renewal = dates.minOrNull(),
            offer = masterOffer ?: offer,
        )
        return status.takeIf { it.hasAny }
    }

    /** Il contatore principale di un tipo: quello col totale più grande (le opzioni piccole restano fuori). */
    private fun best(list: List<Pair<Kind, Allowance>>, kind: Kind): Allowance? =
        list.filter { it.first == kind }.map { it.second }
            .maxWithOrNull(compareBy<Allowance> { it.unlimited }.thenBy { it.total ?: it.left })

    private fun counter(o: JSONObject, path: String): Pair<Kind, Allowance>? {
        var left: Double? = null
        var total: Double? = null
        var used: Double? = null
        var unit = ""
        var unlimited = false
        val words = StringBuilder()
        for (key in o.keys()) {
            val k = key.lowercase()
            val value = o.opt(key)
            words.append(' ').append(k)
            if (value is String) {
                words.append(' ').append(value.lowercase())
                if (k.contains("unit") || k.contains("measure") || k == "um") unit = value.lowercase()
                if (unlimitedWords.containsMatchIn(value)) unlimited = true
            }
            if (value is Boolean && value && unlimitedWords.containsMatchIn(k)) unlimited = true
            if (creditKeys.containsMatchIn(k) || k == "mainbalance") continue
            val n = number(value) ?: continue
            when {
                leftKeys.containsMatchIn(k) -> if (left == null) left = n
                usedKeys.containsMatchIn(k) -> if (used == null) used = n
                totalKeys.containsMatchIn(k) -> if (total == null) total = n
            }
        }
        if (left == null && total != null && used != null) left = total - used
        if (left == null && !unlimited) return null
        val text = words.toString()
        // Prima le parole dell'oggetto stesso, poi il percorso (senza i soliti involucri "data", "result").
        val outer = path.split('/').filter { it !in setOf("", "data", "result", "results", "body", "payload", "response") }.joinToString(" ")
        val kind = kindOf(text) ?: kindOf(outer) ?: return null
        if (unit.isEmpty()) unit = Regex("""\b(gb|mb|kb|byte|sec|min)\b""").find(text)?.value.orEmpty()
        val scale = when (kind) {
            Kind.DATA, Kind.ROAMING -> dataScale(unit, maxOf(left ?: 0.0, total ?: 0.0))
            Kind.MINUTES -> if (unit.startsWith("sec")) 1 / 60.0 else 1.0
            Kind.SMS -> 1.0
        }
        // I giga in roaming UE sono un contatore a parte: non devono prendere il posto di quelli nazionali.
        val roaming = kind == Kind.DATA && Regex("roaming|\\beu\\b|\\bue\\b", RegexOption.IGNORE_CASE).containsMatchIn("$text $outer")
        return (if (roaming) Kind.ROAMING else kind) to Allowance((left ?: 0.0) * scale, total?.let { it * scale }, unlimited)
    }

    private fun kindOf(text: String): Kind? = when {
        Regex("sms|messag", RegexOption.IGNORE_CASE).containsMatchIn(text) -> Kind.SMS
        Regex("voice|voce|minut|call|chiamat|fonia", RegexOption.IGNORE_CASE).containsMatchIn(text) -> Kind.MINUTES
        Regex("data|dati|giga|internet|traffic|\\bgb\\b|\\bmb\\b|byte", RegexOption.IGNORE_CASE).containsMatchIn(text) -> Kind.DATA
        else -> null
    }

    /** Fattore per arrivare ai GB: con l'unità scritta è sicuro, senza si deduce dalla grandezza. */
    private fun dataScale(unit: String, biggest: Double): Double = when {
        unit.startsWith("gb") || unit.startsWith("giga") -> 1.0
        unit.startsWith("mb") -> 1 / 1024.0
        unit.startsWith("kb") -> 1 / (1024.0 * 1024)
        unit.startsWith("b") -> 1 / (1024.0 * 1024 * 1024)
        biggest > 50_000_000 -> 1 / (1024.0 * 1024 * 1024)
        biggest > 3_000 -> 1 / 1024.0
        else -> 1.0
    }

    /** Il credito in euro; i valori in centesimi (chiave "cent" o numeri interi enormi) si dividono. */
    private fun credit(key: String, v: Double): Double? {
        val euros = if (key.contains("cent")) v / 100 else v
        return euros.takeIf { it in 0.0..2_000.0 }
    }

    private fun number(value: Any?): Double? = when (value) {
        is Number -> value.toDouble()
        is String -> value.trim().replace("€", "").trim()
            .let { Regex("""^(-?\d+(?:[.,]\d+)?)\s*[a-zA-Z]{0,6}$""").find(it)?.groupValues?.get(1) }
            ?.replace(',', '.')?.toDoubleOrNull()
        else -> null
    }

    private fun date(value: Any?): LocalDate? = when (value) {
        is Number -> value.toLong().takeIf { it > 1_000_000_000_000L }?.let {
            Instant.ofEpochMilli(it).atZone(ZoneId.of("Europe/Rome")).toLocalDate()
        }
        is String -> textDate(value)
        else -> null
    }

    private fun textDate(s: String): LocalDate? {
        Regex("""(\d{4})-(\d{2})-(\d{2})""").find(s)?.destructured?.let { (y, m, d) ->
            return runCatching { LocalDate.of(y.toInt(), m.toInt(), d.toInt()) }.getOrNull()
        }
        Regex("""(\d{1,2})[/.](\d{1,2})[/.](\d{2,4})""").find(s)?.destructured?.let { (d, m, y) ->
            val year = y.toInt().let { if (it < 100) 2000 + it else it }
            return runCatching { LocalDate.of(year, m.toInt(), d.toInt()) }.getOrNull()
        }
        return null
    }

    private val months = listOf("gennaio", "febbraio", "marzo", "aprile", "maggio", "giugno", "luglio", "agosto", "settembre", "ottobre", "novembre", "dicembre")

    /** Il testo visibile della pagina, quando il JSON non basta. */
    fun fromText(text: String, today: LocalDate = LocalDate.now()): SimStatus? {
        val t = text.replace(' ', ' ')
        fun num(s: String) = s.replace(".", "").replace(',', '.').toDoubleOrNull()
            ?: s.replace(',', '.').toDoubleOrNull()
        fun gb(v: Double, unit: String) = if (unit.equals("MB", true)) v / 1024 else v
        // Come la pagina dell'offerta: "141,4 / 200 Giga" (unità solo sul totale) o "3 GB su 10 GB".
        val dataRe = Regex("""(\d+(?:[.,]\d+)?)\s*(GB|MB|Giga)?\s*(?:su|di|/)\s*(\d+(?:[.,]\d+)?)\s*(GB|MB|Giga)""", RegexOption.IGNORE_CASE)
        fun dataIn(part: String) = dataRe.find(part)?.destructured?.let { (l, lu, tot, tu) ->
            Allowance(gb(num(l) ?: 0.0, lu.ifEmpty { tu }), gb(num(tot) ?: 0.0, tu))
        }
        // Il roaming UE viene dopo i nazionali: si legge a parte, e i nazionali solo prima.
        val roamingAt = Regex("""roaming""", RegexOption.IGNORE_CASE).find(t)?.range?.first
        val national = roamingAt?.let { t.substring(0, it) } ?: t
        val roaming = roamingAt?.let { dataIn(t.substring(it).take(200)) }
        val data = dataIn(national)
            ?: Regex("""giga\s+illimitat""", RegexOption.IGNORE_CASE).find(national)?.let { Allowance(0.0, null, true) }
        val minutes = Regex("""(\d+)\s*(?:min|minuti)\s*(?:su|di|/)\s*(\d+)""", RegexOption.IGNORE_CASE)
            .find(t)?.destructured?.let { (l, tot) -> Allowance(l.toDouble(), tot.toDouble()) }
            ?: Regex("""minuti\s+illimitat""", RegexOption.IGNORE_CASE).find(t)?.let { Allowance(0.0, null, true) }
        val sms = Regex("""(\d+)\s*SMS\s*(?:su|di|/)\s*(\d+)""", RegexOption.IGNORE_CASE)
            .find(t)?.destructured?.let { (l, tot) -> Allowance(l.toDouble(), tot.toDouble()) }
            ?: Regex("""(\d+)\s*(?:su|di|/)\s*(\d+)\s*SMS""", RegexOption.IGNORE_CASE)
                .find(t)?.destructured?.let { (l, tot) -> Allowance(l.toDouble(), tot.toDouble()) }
            ?: Regex("""SMS\s+illimitat""", RegexOption.IGNORE_CASE).find(t)?.let { Allowance(0.0, null, true) }
        val credit = Regex("""credito(?:\s+residuo)?\s*:?\s*€?\s*(\d+[.,]\d{2})""", RegexOption.IGNORE_CASE)
            .find(t)?.groupValues?.get(1)?.let { num(it) }
        val renewalText = Regex("""(?:rinnov\w*|si rinnova|prossimo addebito)[^\n]{0,40}(?:\n[^\n]{0,20})?""", RegexOption.IGNORE_CASE).find(t)?.value
        val renewal = renewalText?.let { r ->
            textDate(r) ?: Regex("""(\d{1,2})[/.](\d{1,2})\b""").find(r)?.destructured?.let { (d, m) ->
                runCatching { LocalDate.of(today.year, m.toInt(), d.toInt()) }.getOrNull()?.let { if (it.isBefore(today)) it.plusYears(1) else it }
            } ?: Regex("""(\d{1,2})\s+(${months.joinToString("|")})""", RegexOption.IGNORE_CASE).find(r)?.destructured?.let { (d, m) ->
                runCatching { LocalDate.of(today.year, months.indexOf(m.lowercase()) + 1, d.toInt()) }.getOrNull()?.let { if (it.isBefore(today)) it.plusYears(1) else it }
            }
        }
        val offer = Regex("""\b(GO\s+\d+[\w ]{0,12}?)\s*(?:\n|nazionale|$)""", RegexOption.IGNORE_CASE).find(t)?.groupValues?.get(1)?.trim()
        return SimStatus(data, minutes, sms, roaming, credit, renewal, offer).takeIf { it.hasAny }
    }

    /** Testi e impostazioni dell'app WindTre (migliaia di messaggi): niente contatori, solo falsi allarmi. */
    fun isConfiguration(url: String): Boolean = Regex("/configuration/|internationalization|business-message", RegexOption.IGNORE_CASE).containsMatchIn(url)

    /** Le parole che non si salvano mai: risposte di accesso, token, codici. */
    fun isPrivate(url: String): Boolean = Regex("login|token|auth|otp|password|session|oauth", RegexOption.IGNORE_CASE).containsMatchIn(url)

    /** "15/11" per la card. */
    val shortDate: DateTimeFormatter = DateTimeFormatter.ofPattern("d/M")
}
