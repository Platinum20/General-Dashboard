package com.platinum20.dashboard.weather

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/** Da quanto è vecchio il meteo mostrato: al posto della rotella quando l'aggiornamento tarda. */
object Freshness {
    /** Oltre questo tempo la rotella dell'aggiornamento lascia il posto a "5 min fa". */
    const val SPINNER_MS = 10_000L

    /** "5 min fa", "alle 9:40", "ieri 18:20", "22/10"; null se è di meno di 2 minuti fa (non serve dirlo). */
    fun label(fetchedAt: Long, now: LocalDateTime, zone: ZoneId = ZoneId.systemDefault()): String? {
        if (fetchedAt <= 0) return null
        val at = Instant.ofEpochMilli(fetchedAt).atZone(zone).toLocalDateTime()
        val minutes = java.time.Duration.between(at, now).toMinutes()
        val time = "%d:%02d".format(at.hour, at.minute)
        return when {
            minutes < 2 -> null
            minutes < 60 -> "$minutes min fa"
            at.toLocalDate() == now.toLocalDate() -> "alle $time"
            at.toLocalDate() == now.toLocalDate().minusDays(1) -> "ieri $time"
            else -> "%d/%d".format(at.dayOfMonth, at.monthValue)
        }
    }
}
