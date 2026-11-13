package com.platinum20.dashboard.sim

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.floor

/** Una lettura dei giga rimasti: se ne tiene una al giorno (l'ultima). */
data class SimReading(val at: Long, val left: Double)

/** La risposta a "ti bastano i giga fino al rinnovo?". */
sealed interface GigaVerdict {
    /** Ancora poche letture: lo si dice invece di tirare a indovinare. */
    data object Learning : GigaVerdict
    /** Bastano: [spare] GB avanzano al rinnovo. */
    data class Enough(val perDay: Double, val spare: Double) : GigaVerdict
    /** Non bastano: al ritmo di [perDay] finiscono il giorno [runsOut]. */
    data class NotEnough(val perDay: Double, val runsOut: LocalDate) : GigaVerdict
}

/**
 * Il consumo medio dei giga dalle letture di WindTre. Canvas legge la SIM ogni ora, ma per la media
 * basta una lettura al giorno per qualche settimana: tenerle tutte sarebbe solo peso.
 */
object SimUsage {
    /** Cinque settimane: più di un mese di rinnovo, così la media copre un periodo intero. */
    const val KEEP_DAYS = 35
    /** Sotto i due giorni di letture la media è un'ora di uso moltiplicata: non si dice niente. */
    const val MIN_DAYS = 2.0
    private const val DAY_MS = 24 * 60 * 60 * 1000.0

    /** Aggiunge [r] tenendo una sola lettura per giorno (l'ultima) e solo le ultime [KEEP_DAYS]. */
    fun record(history: List<SimReading>, r: SimReading, zone: ZoneId = ZoneId.systemDefault()): List<SimReading> {
        fun day(at: Long) = Instant.ofEpochMilli(at).atZone(zone).toLocalDate()
        val today = day(r.at)
        val from = today.minusDays(KEEP_DAYS.toLong())
        return (history.filter { day(it.at) != today && day(it.at).isAfter(from) && it.at < r.at } + r).sortedBy { it.at }
    }

    /**
     * GB al giorno. Conta solo i tratti in cui i giga scendono: quando risalgono è un rinnovo o una
     * ricarica, e quel tratto non dice niente del consumo. Null se le letture coprono meno di [MIN_DAYS].
     */
    fun perDay(history: List<SimReading>): Double? {
        var used = 0.0
        var days = 0.0
        history.sortedBy { it.at }.zipWithNext { a, b ->
            if (b.left <= a.left) {
                used += a.left - b.left
                days += (b.at - a.at) / DAY_MS
            }
        }
        return if (days < MIN_DAYS) null else used / days
    }

    /** Null quando la domanda non ha senso: giga illimitati, rinnovo sconosciuto o già oggi. */
    fun verdict(status: SimStatus, history: List<SimReading>, today: LocalDate): GigaVerdict? {
        val data = status.data?.takeIf { !it.unlimited } ?: return null
        val renewal = status.renewal ?: return null
        val daysLeft = ChronoUnit.DAYS.between(today, renewal)
        if (daysLeft <= 0) return null
        val rate = perDay(history) ?: return GigaVerdict.Learning
        val need = rate * daysLeft
        if (need <= data.left) return GigaVerdict.Enough(rate, data.left - need)
        return GigaVerdict.NotEnough(rate, today.plusDays(floor(data.left / rate).toLong()))
    }

    fun toJson(history: List<SimReading>): String =
        JSONArray(history.map { JSONObject().put("at", it.at).put("left", it.left) }).toString()

    fun fromJson(text: String?): List<SimReading> = runCatching {
        val arr = JSONArray(text ?: return emptyList())
        (0 until arr.length()).map { arr.getJSONObject(it).let { o -> SimReading(o.getLong("at"), o.getDouble("left")) } }
    }.getOrDefault(emptyList())

    /**
     * Quanto hai usato ogni giorno (GB), dalla differenza tra due letture vicine: il grafico della pagina SIM
     * (1.528). Un giorno in cui i giga salgono è un rinnovo: conta zero, non un consumo negativo.
     */
    fun dailyUse(history: List<SimReading>): List<Double> =
        history.sortedBy { it.at }.zipWithNext { a, b -> (a.left - b.left).coerceAtLeast(0.0) }
}
