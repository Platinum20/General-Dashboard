package com.platinum20.dashboard.trips

import com.platinum20.dashboard.calendar.CalendarEvent
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Prima di partire (o di iniziare il turno) e durante il viaggio. */
enum class LivePhase { SOON, ON_BOARD }

/**
 * Cosa dice la notifica viva in questo momento: titolo, testo, la scritta corta del chip in alto
 * ([chip], l'orario che conta), il conto alla rovescia verso [countdownTo], l'avanzamento 0-100 in viaggio.
 */
data class LiveCard(
    val phase: LivePhase,
    val title: String,
    val text: String,
    val chip: String,
    val countdownTo: Long,
    val progress: Int? = null,
)

/**
 * Il viaggio vivo (1.467): un'ora prima del treno nasce una notifica sola che conta i minuti alla partenza,
 * poi in viaggio avanza fino all'arrivo. Per i turni (se acceso) solo il conto alla rovescia all'inizio.
 * Logica pura: orari, ritardo e testi, per i test.
 */
object LiveTrip {
    /** Da quanto prima della partenza compare. */
    const val LEAD_MS = 60 * 60_000L
    /** Per i turni basta meno: il tempo di prepararsi e uscire. */
    const val SHIFT_LEAD_MS = 45 * 60_000L
    /** Quanto resta dopo l'arrivo, per vedere che sei arrivato. */
    const val AFTER_MS = 5 * 60_000L

    private val hour = DateTimeFormatter.ofPattern("H:mm")

    /** Una fase per [event] adesso, o null se è troppo presto o è già finito. Il ritardo sposta partenza e arrivo. */
    fun phase(event: CalendarEvent, now: Long, status: TrainStatus? = null): LivePhase? {
        if (event.allDay || event.end <= event.begin) return null
        val shift = event.trip == null
        val delay = if (shift) 0L else (status?.delay ?: 0).coerceAtLeast(0) * 60_000L
        val start = event.begin + delay
        val lead = if (shift) SHIFT_LEAD_MS else LEAD_MS
        return when {
            now < start - lead -> null
            now < start -> LivePhase.SOON
            // Il turno non ha notifica mentre lavori: sarebbe solo rumore.
            shift -> null
            status?.cancelled == true -> null
            now < event.end + delay + AFTER_MS -> LivePhase.ON_BOARD
            else -> null
        }
    }

    /** La notifica per [event] adesso; null se non serve. [checked] = abbiamo provato a leggere il ritardo. */
    fun card(event: CalendarEvent, now: Long, status: TrainStatus?, checked: Boolean, zone: ZoneId = ZoneId.systemDefault()): LiveCard? {
        val trip = event.trip
        if (trip == null) {
            if (phase(event, now) != LivePhase.SOON) return null
            val company = event.mates.takeIf { it.isNotEmpty() }
                ?.let { com.platinum20.dashboard.shifts.ShiftParse.company(it.map(com.platinum20.dashboard.shifts.ShiftParse::firstName)) }
            return LiveCard(
                LivePhase.SOON,
                title = com.platinum20.dashboard.ui.screens.RowText.event(event),
                text = listOfNotNull("Inizia alle ${at(event.begin, zone)}", company).joinToString(" · "),
                chip = at(event.begin, zone),
                countdownTo = event.begin,
            )
        }
        if (status?.cancelled == true && now < event.begin) {
            return LiveCard(LivePhase.SOON, com.platinum20.dashboard.ui.screens.RowText.event(event), listOfNotNull(trip.train, "Soppresso").joinToString(" · "), "Soppresso", event.begin)
        }
        val phase = phase(event, now, status) ?: return null
        val delay = (status?.delay ?: 0).coerceAtLeast(0) * 60_000L
        val live = when {
            status != null -> TrainLive.delayText(status)
            checked -> "ritardo non disponibile"
            else -> null
        }
        return when (phase) {
            LivePhase.SOON -> {
                val leave = event.begin + delay
                LiveCard(
                    phase,
                    title = com.platinum20.dashboard.ui.screens.RowText.event(event),
                    text = listOfNotNull(
                        "Parte alle ${at(leave, zone)}",
                        live,
                        status?.platform?.let { "binario $it" },
                        SeatText.short(trip),
                    ).joinToString(" · "),
                    chip = at(leave, zone),
                    countdownTo = leave,
                )
            }
            LivePhase.ON_BOARD -> {
                val begin = event.begin + delay
                val end = event.end + delay
                val progress = (((now - begin).toDouble() / (end - begin)) * 100).toInt().coerceIn(0, 100)
                LiveCard(
                    phase,
                    title = com.platinum20.dashboard.ui.screens.RowText.event(event),
                    text = listOfNotNull(
                        if (now >= end) "Arrivato a ${trip.to}" else "Arrivo alle ${at(end, zone)}",
                        live,
                        SeatText.short(trip),
                    ).joinToString(" · "),
                    chip = at(end, zone),
                    countdownTo = end,
                    progress = progress,
                )
            }
        }
    }

    private fun at(ms: Long, zone: ZoneId) = Instant.ofEpochMilli(ms).atZone(zone).format(hour)
}

/** Carrozza e posto in breve, per la notifica: "carr. 8, posto 11D". */
private object SeatText {
    fun short(trip: com.platinum20.dashboard.calendar.Trip): String? = listOfNotNull(
        trip.coach?.let { "carr. $it" },
        trip.seat?.let { "posto $it" },
    ).joinToString(", ").takeIf { it.isNotEmpty() }
}
