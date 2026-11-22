package com.platinum20.dashboard.weather

import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Le frasi del meteo (revisione 2026-11-22, Antonio: card C + pagina A): ogni numero con una parola che dice
 * se è tanto o poco, massima e minima scritte per esteso. Logica pura, così i test leggono le stesse frasi.
 */
object WeatherText {

    /** "max 19° · min 10°": "19° / 10°" non diceva quale fosse la massima. */
    fun range(max: Double, min: Double): String = "max ${max.asTemp()} · min ${min.asTemp()}"

    /** La minima da adesso alle 7 di domattina: la sera conta quella, non la minima di oggi già passata. */
    fun tonightMin(hours: List<HourlyPoint>, now: LocalDateTime): Double? {
        val until = now.toLocalDate().plusDays(1).atTime(7, 0)
        return hours.filter { !it.time.isBefore(now.withMinute(0)) && it.time.isBefore(until) && !it.temperature.isNaN() }
            .minOfOrNull { it.temperature }
    }

    /** La riga della sera in Adesso: "Domani sereno · max 19° · min 9°", la pioggia solo da 40% in su. */
    fun tomorrowLine(d: DailyPoint): String {
        val sky = WeatherCodes.description(d.code).lowercase()
        val wet = wet(d.code)
        val rain = when {
            d.precipitationProbability < 40 -> ""
            wet -> " ${d.precipitationProbability}%"
            else -> " · pioggia ${d.precipitationProbability}%"
        }
        return "Domani $sky$rain · ${range(d.max, d.min)}"
    }

    private fun wet(code: Int): Boolean =
        WeatherCodes.kind(code) in setOf(WeatherCodes.Kind.DRIZZLE, WeatherCodes.Kind.RAIN, WeatherCodes.Kind.STORM)

    fun humidity(percent: Int): String = when {
        percent < 35 -> "Aria secca"
        percent <= 65 -> "Nella norma"
        percent <= 85 -> "Aria umida"
        else -> "Molto umida"
    }

    /** Da dove arriva il vento, per esteso: i gradi di Open-Meteo sono la direzione di provenienza. */
    fun windFrom(degrees: Int): String {
        val names = listOf("nord", "nord-est", "est", "sud-est", "sud", "sud-ovest", "ovest", "nord-ovest")
        return names[((degrees % 360 + 360) % 360 + 22) / 45 % 8]
    }

    /** "Brezza da nord-ovest": la forza in una parola (scala Beaufort semplificata), poi la provenienza. */
    fun wind(kmh: Double, degrees: Int): String {
        val force = when {
            kmh.isNaN() -> return "–"
            kmh < 2 -> return "Calma"
            kmh < 12 -> "Brezza leggera"
            kmh < 29 -> "Brezza"
            kmh < 50 -> "Vento forte"
            else -> "Burrasca"
        }
        return "$force da ${windFrom(degrees)}"
    }

    fun uv(index: Double): String = when {
        index < 3 -> "Basso"
        index < 6 -> "Moderato"
        index < 8 -> "Alto: meglio la crema"
        index < 11 -> "Molto alto: crema e ombra"
        else -> "Estremo: evita il sole"
    }

    /** "9 ore e 44 minuti di luce" tra alba e tramonto. */
    fun daylight(sunrise: LocalDateTime?, sunset: LocalDateTime?): String? {
        if (sunrise == null || sunset == null || !sunset.isAfter(sunrise)) return null
        val minutes = Duration.between(sunrise, sunset).toMinutes()
        val h = minutes / 60
        val m = minutes % 60
        val hours = if (h == 1L) "1 ora" else "$h ore"
        return when (m) {
            0L -> "$hours di luce"
            1L -> "$hours e 1 minuto di luce"
            else -> "$hours e $m minuti di luce"
        }
    }

    /**
     * La riga del dato vecchio in cima alla pagina, da [Freshness.label]: "Dati di 5 min fa", "Dati delle 9:05",
     * "Dati di ieri 18:20", "Dati del 20/10".
     */
    fun staleLine(age: String): String = when {
        age.startsWith("alle ") -> "Dati delle " + age.removePrefix("alle ")
        age.first().isDigit() && age.contains('/') -> "Dati del $age"
        else -> "Dati di $age"
    }

    /** Oltre un'ora il dato è vecchio e lo si dice in cima alla pagina; anche subito se l'ultimo giro è fallito. */
    const val STALE_MS = 60 * 60_000L

    fun isStale(fetchedAt: Long, now: Long, failed: Boolean): Boolean = failed || now - fetchedAt > STALE_MS

    /** Le ore mostrate quando si apre un giorno: dalle 6 alle 21, una ogni tre. */
    val DAY_HOURS = listOf(6, 9, 12, 15, 18, 21)

    fun dayHours(all: List<HourlyPoint>, date: LocalDate): List<HourlyPoint> =
        DAY_HOURS.mapNotNull { h -> all.firstOrNull { it.time.toLocalDate() == date && it.time.hour == h } }

    /** La frase sotto le ore di un giorno aperto: il cielo, la pioggia se ce n'è, il vento se si sente. */
    fun dayPhrase(d: DailyPoint): String = listOfNotNull(
        WeatherCodes.description(d.code),
        d.precipitationSum.takeIf { !it.isNaN() && it >= 0.5 }?.let {
            // Se il cielo dice già "Pioggia", solo i millimetri.
            if (wet(d.code)) "${Math.round(it)} mm" else "pioggia ${Math.round(it)} mm"
        },
        d.windMax.takeIf { !it.isNaN() && it >= 12 }?.let { "vento fino a ${Math.round(it)} km/h" },
    ).joinToString(" · ")
}
