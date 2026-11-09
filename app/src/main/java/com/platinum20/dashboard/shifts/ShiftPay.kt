package com.platinum20.dashboard.shifts

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import kotlin.math.abs

/**
 * Com'è andato un turno, dalla busta paga di Creators Pro: venduto, guadagnato, a che percentuale.
 * [current] = il turno in corso: i numeri crescono ancora.
 */
data class ShiftEarning(
    val id: String,
    val start: Long,
    val end: Long,
    val sold: Double,
    val earned: Double,
    /** La soglia raggiunta (0.12 = 12%). */
    val percent: Double,
    val hours: Double,
    val current: Boolean = false,
)

/** La busta paga di un mese: il totale e il minimo garantito. */
data class PayMonth(
    val id: String,
    val start: Long,
    val end: Long,
    val total: Double,
    val sold: Double,
    val shifts: Int,
    val hours: Double,
    val guaranteed: Double,
    val minShifts: Int,
    val status: String,
)

/**
 * Lettura della busta paga (1.385, formato vero dal file di Antonio):
 * `GET api.creatorspro.com/v1/sellers-wage/wages?startedAt&endedAt&memberId` = i mesi,
 * `GET …/sellers-wage/wages/<id>` = i turni del mese con venduto e guadagno. Le vendite una per una
 * (nomi dei fan) non si tengono.
 */
object ShiftPay {
    private val DETAIL = Regex("/v1/sellers-wage/wages/([0-9a-fA-F-]{8,})(?:[?#]|$)")
    private val LIST = Regex("/v1/sellers-wage/wages(?:[?#]|$)")

    fun isPay(url: String) = DETAIL.containsMatchIn(url) || LIST.containsMatchIn(url)

    /** I mesi dalla lista delle buste paga; null se la risposta non è quella. */
    fun months(body: String, url: String): List<PayMonth>? {
        if (!LIST.containsMatchIn(url)) return null
        val data = runCatching { JSONObject(body).optJSONArray("data") }.getOrNull() ?: return null
        return (0 until data.length()).mapNotNull { i ->
            val w = data.optJSONObject(i) ?: return@mapNotNull null
            val info = w.optJSONObject("info") ?: return@mapNotNull null
            val settings = w.optJSONObject("settings")
            PayMonth(
                id = info.optString("id").ifBlank { return@mapNotNull null },
                start = millis(info.optString("startedAt")) ?: return@mapNotNull null,
                end = millis(info.optString("endedAt")) ?: return@mapNotNull null,
                total = info.optDouble("totalWage").orZero(),
                sold = info.optDouble("totalAttributedFromTakes").orZero(),
                shifts = info.optInt("totalWorkedShifts"),
                hours = info.optDouble("totalWorkedHours").orZero(),
                guaranteed = settings?.optDouble("guaranteed").orZero(),
                minShifts = settings?.optInt("guaranteedShiftThreshold") ?: 0,
                status = w.optString("status").ifBlank { info.optString("status") },
            )
        }
    }

    /** I turni di un mese dal dettaglio della busta paga; null se la risposta non è quella. */
    fun shifts(body: String, url: String): List<ShiftEarning>? {
        if (!DETAIL.containsMatchIn(url)) return null
        val list = runCatching { JSONObject(body).optJSONObject("data")?.optJSONArray("shifts") }.getOrNull() ?: return null
        return (0 until list.length()).mapNotNull { i ->
            val s = list.optJSONObject(i) ?: return@mapNotNull null
            val sold = s.optDouble("totalAttributed").orZero()
            val earned = s.optDouble("totalEarnings").orZero()
            val active = s.optJSONArray("thresholds")?.let { t ->
                (0 until t.length()).mapNotNull { t.optJSONObject(it) }.firstOrNull { it.optBoolean("isActive") }
            }?.optDouble("percentage")?.takeIf { !it.isNaN() }
            ShiftEarning(
                id = s.optString("id").ifBlank { return@mapNotNull null },
                start = millis(s.optString("startedAt")) ?: return@mapNotNull null,
                end = millis(s.optString("endedAt")) ?: return@mapNotNull null,
                sold = sold,
                earned = earned,
                percent = active ?: if (sold > 0) earned / sold else 0.0,
                hours = s.optDouble("workedHours").orZero(),
                current = s.optBoolean("isCurrent"),
            )
        }
    }

    /**
     * La busta paga di un turno del calendario: stesso id, altrimenti l'inizio entro un'ora e mezza
     * (la timeline e la busta paga potrebbero chiamarlo in modo diverso).
     */
    fun of(shift: Shift, pay: List<ShiftEarning>): ShiftEarning? =
        pay.firstOrNull { it.id == shift.id } ?: pay.filter { abs(it.start - shift.start) <= 90 * 60_000L }.minByOrNull { abs(it.start - shift.start) }

    /** Il mese di busta paga che contiene quel momento. */
    fun monthOf(at: Long, months: List<PayMonth>): PayMonth? = months.firstOrNull { at in it.start..it.end }

    /** "$60,56": come li scrive Creators Pro, in dollari, con la virgola. */
    fun money(v: Double): String = "$" + String.format(Locale.ITALY, "%.2f", v)

    fun percent(p: Double): String {
        val v = p * 100
        return if (abs(v - Math.round(v)) < 0.05) "${Math.round(v)}%" else String.format(Locale.ITALY, "%.1f%%", v)
    }

    /** Sulla linea del tempo, accanto alla durata: "$60,56 · 12%" (il turno in corso dice "finora"). */
    fun line(e: ShiftEarning): String =
        money(e.earned) + " · " + percent(e.percent) + if (e.current) " finora" else ""

    /** Le righe della scheda del turno: com'è andato e il mese. */
    fun detail(e: ShiftEarning?, month: PayMonth?, zone: ZoneId = ZoneId.systemDefault()): String = buildList {
        if (e != null) {
            add("Guadagnato: ${money(e.earned)} al ${percent(e.percent)}" + if (e.current) " (finora)" else "")
            add("Venduto: ${money(e.sold)}")
            if (e.hours > 0) add("Ore lavorate: " + String.format(Locale.ITALY, "%.1f", e.hours))
        }
        if (month != null) {
            val name = Instant.ofEpochMilli(month.start).atZone(zone).month
                .getDisplayName(java.time.format.TextStyle.FULL_STANDALONE, Locale.ITALIAN).replaceFirstChar { it.uppercase() }
            add("$name: ${money(month.total)} in ${month.shifts} turni")
            if (month.guaranteed > 0) add("Minimo garantito ${money(month.guaranteed)} con ${month.minShifts} turni")
        }
    }.joinToString("\n")

    /** Il mese per Impostazioni: "Novembre: $747,04 in 7 turni". */
    fun monthLine(month: PayMonth, zone: ZoneId = ZoneId.systemDefault()): String {
        val name = Instant.ofEpochMilli(month.start).atZone(zone).month
            .getDisplayName(java.time.format.TextStyle.FULL_STANDALONE, Locale.ITALIAN).replaceFirstChar { it.uppercase() }
        return "$name: ${money(month.total)} in ${month.shifts} turni"
    }

    /** Aggiorna: i turni di un mese letto sostituiscono quelli dello stesso periodo. */
    fun mergeShifts(old: List<ShiftEarning>, read: List<ShiftEarning>): List<ShiftEarning> {
        if (read.isEmpty()) return old
        val from = read.minOf { it.start } - 1
        val to = read.maxOf { it.start } + 1
        val ids = read.map { it.id }.toSet()
        return (read + old.filter { it.id !in ids && it.start !in from..to }).sortedBy { it.start }.takeLast(400)
    }

    fun mergeMonths(old: List<PayMonth>, read: List<PayMonth>): List<PayMonth> =
        (read + old.filter { o -> read.none { it.id == o.id } }).sortedBy { it.start }.takeLast(24)

    fun toJson(list: List<ShiftEarning>): String = JSONArray().also { a ->
        list.forEach {
            a.put(JSONObject().put("id", it.id).put("start", it.start).put("end", it.end).put("sold", it.sold)
                .put("earned", it.earned).put("percent", it.percent).put("hours", it.hours).put("current", it.current))
        }
    }.toString()

    fun fromJson(s: String?): List<ShiftEarning> = runCatching {
        val a = JSONArray(s ?: return emptyList())
        (0 until a.length()).map { a.getJSONObject(it) }.map {
            ShiftEarning(it.getString("id"), it.getLong("start"), it.getLong("end"), it.optDouble("sold").orZero(),
                it.optDouble("earned").orZero(), it.optDouble("percent").orZero(), it.optDouble("hours").orZero(), it.optBoolean("current"))
        }
    }.getOrDefault(emptyList())

    fun monthsToJson(list: List<PayMonth>): String = JSONArray().also { a ->
        list.forEach {
            a.put(JSONObject().put("id", it.id).put("start", it.start).put("end", it.end).put("total", it.total).put("sold", it.sold)
                .put("shifts", it.shifts).put("hours", it.hours).put("guaranteed", it.guaranteed).put("minShifts", it.minShifts).put("status", it.status))
        }
    }.toString()

    fun monthsFromJson(s: String?): List<PayMonth> = runCatching {
        val a = JSONArray(s ?: return emptyList())
        (0 until a.length()).map { a.getJSONObject(it) }.map {
            PayMonth(it.getString("id"), it.getLong("start"), it.getLong("end"), it.optDouble("total").orZero(), it.optDouble("sold").orZero(),
                it.optInt("shifts"), it.optDouble("hours").orZero(), it.optDouble("guaranteed").orZero(), it.optInt("minShifts"), it.optString("status"))
        }
    }.getOrDefault(emptyList())

    private fun millis(s: String): Long? = runCatching { Instant.parse(s).toEpochMilli() }.getOrNull()
    private fun Double?.orZero(): Double = if (this == null || isNaN()) 0.0 else this
}
