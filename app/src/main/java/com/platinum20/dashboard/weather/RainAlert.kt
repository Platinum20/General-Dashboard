package com.platinum20.dashboard.weather

import java.time.Duration
import java.time.LocalDateTime

/** Un avviso "sta per piovere" pronto da mostrare. */
data class RainNotice(
    /** Minuti da adesso all'inizio della pioggia. */
    val inMinutes: Int,
    /** Quanto dura, in minuti, finché il nowcast la vede. */
    val durationMinutes: Int,
    val intensity: String,
    /** True se la pioggia continua oltre l'orizzonte del nowcast. */
    val longer: Boolean,
) {
    fun text(place: String): String {
        val start = if (inMinutes <= 5) "Pioggia tra pochi minuti" else "Pioggia tra $inMinutes minuti"
        val length = when {
            longer && durationMinutes >= 60 -> "per più di un'ora"
            longer -> "per almeno $durationMinutes minuti"
            durationMinutes >= 60 -> "per circa " + hours(durationMinutes)
            else -> "per circa $durationMinutes minuti"
        }
        return "$start a $place, $intensity $length"
    }

    private fun hours(minutes: Int): String {
        val h = minutes / 60
        val m = minutes % 60
        val base = if (h == 1) "un'ora" else "$h ore"
        return if (m >= 30) (if (h == 1) "un'ora e mezza" else "$h ore e mezza") else base
    }
}

/**
 * Decide se avvisare che sta per piovere, dal nowcast a 15 minuti di Open-Meteo.
 * Funzione pura: il worker le passa i dati e l'ora dell'ultimo avviso.
 */
object RainAlert {
    /** Millimetri in 15 minuti da cui si parla di pioggia vera (sotto è qualche goccia). */
    const val THRESHOLD = 0.3
    /** Fin dove guardare avanti. */
    const val HORIZON_MINUTES = 45L
    /** Al massimo un avviso ogni tanto. */
    const val QUIET_HOURS = 3L
    const val FIRST_HOUR = 7
    const val LAST_HOUR = 22

    fun decide(
        nowcast: List<Pair<LocalDateTime, Double>>,
        now: LocalDateTime,
        lastAlert: LocalDateTime?,
    ): RainNotice? {
        if (now.hour !in FIRST_HOUR..LAST_HOUR) return null
        if (lastAlert != null && Duration.between(lastAlert, now).toMinutes() < QUIET_HOURS * 60) return null
        val slots = nowcast.sortedBy { it.first }
        // Il quarto d'ora in corso: se piove già non c'è niente da annunciare.
        val current = slots.lastOrNull { !it.first.isAfter(now) }
            ?.takeIf { Duration.between(it.first, now).toMinutes() < 15 }
        if (current != null && current.second >= 0.1) return null
        val ahead = slots.filter { it.first.isAfter(now) }
        val startIndex = ahead.indexOfFirst {
            it.second >= THRESHOLD && Duration.between(now, it.first).toMinutes() <= HORIZON_MINUTES
        }
        if (startIndex < 0) return null
        val start = ahead[startIndex]
        val rainy = ahead.drop(startIndex).takeWhile { it.second >= 0.1 }
        val longer = startIndex + rainy.size == ahead.size
        val peak = rainy.maxOf { it.second } * 4 // mm/h
        val intensity = when {
            peak < 2.5 -> "debole"
            peak < 7.6 -> "moderata"
            else -> "forte"
        }
        return RainNotice(
            inMinutes = Duration.between(now, start.first).toMinutes().toInt(),
            durationMinutes = rainy.size * 15,
            intensity = intensity,
            longer = longer,
        )
    }
}
