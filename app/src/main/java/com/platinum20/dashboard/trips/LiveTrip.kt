package com.platinum20.dashboard.trips

import com.platinum20.dashboard.calendar.CalendarEvent
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Prima di partire (o di iniziare il turno) e durante il viaggio. */
enum class LivePhase { SOON, ON_BOARD }

/** Come segnare un pezzo di testo: arancio (in ritardo) o barrato (l'orario del biglietto superato dal ritardo). */
enum class MarkKind { LATE, STRUCK }

/** Un pezzo di testo da segnare nella riga [line] di [LiveCard.lines] (-1 = nel [LiveCard.text]). */
data class LiveMark(val line: Int, val text: String, val kind: MarkKind)

/**
 * La linea della notifica (2026-11-20, proposta A): dal [start] alla [end], con un punto per ogni fermata
 * ([points], orari già spostati dal ritardo). [steps] la traduce in minuti per la ProgressStyle di Android 16.
 */
data class LiveTrack(val start: Long, val end: Long, val points: List<Long> = emptyList()) {
    fun steps(now: Long): TrackSteps {
        val max = ((end - start) / 60_000L).toInt().coerceAtLeast(1)
        val progress = ((now - start) / 60_000L).toInt().coerceIn(0, max)
        val marks = points.map { ((it - start) / 60_000L).toInt() }.filter { it in 1 until max }.distinct().sorted()
        // Android ne disegna al massimo una quindicina: oltre, una sì e una no finché ci stanno.
        var shown = marks
        while (shown.size > MAX_POINTS) shown = shown.filterIndexed { i, _ -> i % 2 == 0 }
        val edges = listOf(0) + shown + max
        return TrackSteps(max, progress, shown, edges.zipWithNext { a, b -> b - a }.filter { it > 0 })
    }

    companion object {
        const val MAX_POINTS = 14
    }
}

/** La linea in minuti: lunghezza, dove sei, i punti delle fermate e i tratti fra un punto e l'altro. */
data class TrackSteps(val max: Int, val progress: Int, val points: List<Int>, val segments: List<Int>)

/**
 * Cosa dice la notifica viva in questo momento: titolo, testo su una riga, la scritta corta del chip in alto
 * ([chip], l'orario che conta), [header] = "arriva tra 3 h 54 min" nell'intestazione (al posto del cronometro
 * incollato al titolo), l'avanzamento 0-100 e la [track] con le fermate. [lines] = la notifica aperta, una cosa
 * per riga, con [marks] arancio o barrati; [late] = in ritardo o soppresso: prende l'arancio dell'attenzione.
 */
data class LiveCard(
    val phase: LivePhase,
    val title: String,
    val text: String,
    val chip: String,
    val countdownTo: Long,
    val progress: Int? = null,
    val lines: List<String> = listOf(text),
    val late: Boolean = false,
    val header: String? = null,
    val marks: List<LiveMark> = emptyList(),
    val track: LiveTrack? = null,
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
        val delay = if (shift) 0L else TrainLive.shiftMs(status)
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

    /** "tra 3 h 54 min", "tra 12 min", "tra meno di un minuto": l'intestazione, aggiornata a ogni giro da un minuto. */
    fun inText(ms: Long): String {
        val minutes = (ms + 59_999L) / 60_000L
        return when {
            minutes <= 1 -> "tra meno di un minuto"
            minutes < 60 -> "tra $minutes min"
            minutes % 60 == 0L -> "tra ${minutes / 60} h"
            else -> "tra ${minutes / 60} h ${minutes % 60} min"
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
                header = "inizia ${inText(event.begin - now)}",
            )
        }
        val title = com.platinum20.dashboard.ui.screens.RowText.event(event)
        val seat = SeatText.line(trip)
        if (status?.cancelled == true && now < event.begin) {
            val text = listOfNotNull(trip.train, "Soppresso").joinToString(" · ")
            return LiveCard(
                LivePhase.SOON, title, text, "Soppresso", event.begin,
                lines = listOfNotNull("Soppresso · doveva partire alle ${at(event.begin, zone)}", read(status, zone), seat),
                late = true,
                marks = listOf(LiveMark(-1, text, MarkKind.LATE), LiveMark(0, "Soppresso", MarkKind.LATE)),
            )
        }
        val phase = phase(event, now, status) ?: return null
        val delay = TrainLive.shiftMs(status)
        val late = delay > 0
        val live = when {
            status != null -> TrainLive.delayText(status)
            checked -> "ritardo non disponibile"
            else -> null
        }
        val leave = event.begin + delay
        val end = event.end + delay
        val marks = mutableListOf<LiveMark>()
        // "In ritardo di 92 min · rilevato alle 16:02": il ritardo in arancio, poi da quando vale (regola 4).
        val delayWords = if (late) "In ritardo di ${TrainLive.shiftMinutes(status)} min" else live
        val liveLine = listOfNotNull(delayWords, status?.let { "rilevato alle ${at(it.readAt, zone)}" }).joinToString(" · ").ifEmpty { null }
        // Con il ritardo l'orario del biglietto resta, barrato, davanti a quello vero: "18:27 19:59".
        fun time(planned: Long, real: Long, line: Int): String {
            if (!late) return at(real, zone)
            marks += LiveMark(line, at(planned, zone), MarkKind.STRUCK)
            marks += LiveMark(line, at(real, zone), MarkKind.LATE)
            return "${at(planned, zone)} ${at(real, zone)}"
        }
        val stops = TrainLive.between(status?.stops.orEmpty(), trip.from, trip.to)
        val points = stops.map { it.at + delay }.filter { it in (leave + 1) until end }
        return when (phase) {
            LivePhase.SOON -> {
                val platform = status?.platform?.let { "binario $it" }
                // La linea di progresso nell'ultima ora: si riempie man mano che la partenza si avvicina.
                val waited = (((now - (leave - LEAD_MS)).toDouble() / LEAD_MS) * 100).toInt().coerceIn(0, 100)
                val lines = mutableListOf<String>()
                lines += listOfNotNull("Parte alle ${time(event.begin, leave, 0)}", platform).joinToString(" · ")
                lines += "Arrivo a ${trip.to} alle ${time(event.end, end, 1)}"
                liveLine?.let { lines += it; if (late) marks += LiveMark(lines.lastIndex, delayWords!!, MarkKind.LATE) }
                seat?.let { lines += it }
                val text = listOfNotNull("Parte alle ${at(leave, zone)}", live, platform).joinToString(" · ")
                if (late) marks += LiveMark(-1, text, MarkKind.LATE)
                LiveCard(
                    phase, title = title, text = text,
                    chip = at(leave, zone),
                    countdownTo = leave,
                    progress = waited,
                    lines = lines,
                    late = late,
                    header = "parte ${inText(leave - now)}",
                    marks = marks,
                    track = LiveTrack(leave - LEAD_MS, leave),
                )
            }
            LivePhase.ON_BOARD -> {
                val progress = (((now - leave).toDouble() / (end - leave)) * 100).toInt().coerceIn(0, 100)
                val arrived = now >= end
                val arrival = if (arrived) "Arrivato a ${trip.to}" else "Arrivo alle ${at(end, zone)}"
                val lines = mutableListOf<String>()
                // Partenza a sinistra, arrivo a destra: la stessa linea scritta in parole.
                lines += if (arrived) arrival else
                    listOfNotNull(at(leave, zone), short(trip.from)).joinToString(" ") + " → " + "${time(event.end, end, 0)} ${short(trip.to) ?: trip.to}"
                liveLine?.let { lines += it; if (late) marks += LiveMark(lines.lastIndex, delayWords!!, MarkKind.LATE) }
                if (!arrived) stops.firstOrNull { it.at + delay > now }?.let { next ->
                    lines += "Prossima: ${TrainLive.pretty(next.name)} alle ${at(next.at + delay, zone)}"
                }
                seat?.let { lines += it }
                val text = listOfNotNull(arrival, live).joinToString(" · ")
                if (late) marks += LiveMark(-1, text, MarkKind.LATE)
                LiveCard(
                    phase, title = title, text = text,
                    chip = at(end, zone),
                    countdownTo = end,
                    progress = progress,
                    lines = lines,
                    late = late,
                    header = if (arrived) null else "arriva ${inText(end - now)}",
                    marks = marks,
                    track = LiveTrack(leave, end, points),
                )
            }
        }
    }

    /** "Pescara Centrale" → "Pescara": nella riga partenza → arrivo ogni parola in più la manda a capo. */
    fun short(station: String?): String? = station?.trim()
        ?.replace(Regex("""\s+(Centrale|C\.le|Centro)$""", RegexOption.IGNORE_CASE), "")
        ?.takeIf { it.isNotEmpty() }

    /** "rilevato alle 14:44", per dire da quando vale quello che leggi. */
    private fun read(status: TrainStatus?, zone: ZoneId): String? = status?.takeIf { it.readAt > 0 }?.let { "rilevato alle ${at(it.readAt, zone)}" }

    private fun at(ms: Long, zone: ZoneId) = Instant.ofEpochMilli(ms).atZone(zone).format(hour)
}

/** Treno, carrozza e posto in breve, per la notifica: "Intercity 612 · carr. 8, posto 11D". */
private object SeatText {
    fun line(trip: com.platinum20.dashboard.calendar.Trip): String? =
        listOfNotNull(trip.train, short(trip)).joinToString(" · ").ifEmpty { null }

    fun short(trip: com.platinum20.dashboard.calendar.Trip): String? = listOfNotNull(
        trip.coach?.let { "carr. $it" },
        trip.seat?.let { "posto $it" },
    ).joinToString(", ").takeIf { it.isNotEmpty() }
}
