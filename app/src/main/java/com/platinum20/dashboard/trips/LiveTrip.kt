package com.platinum20.dashboard.trips

import com.platinum20.dashboard.calendar.CalendarEvent
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Prima di partire (o di iniziare il turno) e durante il viaggio. */
enum class LivePhase { SOON, ON_BOARD }

/** Come segnare un pezzo di testo: arancio (in ritardo) o barrato (l'orario del biglietto superato dal ritardo). */
/** LATE = arancio, STRUCK = barrato, SOFT = grigio (le righe di contorno, come nella proposta A). */
enum class MarkKind { LATE, STRUCK, SOFT }

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
    /** Sotto la linea a sinistra: "16:01 Pescara". Segni con riga [DEPART]. */
    val depart: String? = null,
    /** Sotto la linea a destra: "18:27 19:59 Taranto" (il primo barrato). Segni con riga [ARRIVE]. */
    val arrive: String? = null,
    /** Le fermate fra partenza e arrivo, per il tasto "Fermate". */
    val stops: List<LiveStop> = emptyList(),
    /** Sotto il titolo, prima della linea (versione compatta 1, Antonio 2026-11-21): "Bin. 14 · FR 9541 · carr. 8 · posto 11D". */
    val sub: String? = null,
    /** Fra titolo e [sub]: "Prossima: Reggio Emilia Av 23:56". */
    val next: String? = null,
) {
    companion object {
        const val TEXT = -1
        const val ARRIVE = -2
        const val DEPART = -3
        /** L'intestazione ([header]): il "+12 min" in arancio. */
        const val HEADER = -4
    }
}

/**
 * Una fermata nell'elenco "Fermate": orario (col ritardo), nome, già passata; [planned] = l'orario del biglietto,
 * solo se in ritardo, barrato davanti come per partenza e arrivo (2026-11-20).
 */
data class LiveStop(val time: String, val name: String, val passed: Boolean, val planned: String? = null)

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
        val seat = SeatText.line(trip, status?.platform)
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
        val leave = event.begin + delay
        val end = event.end + delay
        val marks = mutableListOf<LiveMark>()
        // Una lettura vecchia resta valida (gli orari restano spostati); "in orario" solo se fresca.
        val old = status != null && !TrainLive.isFresh(status, now)
        val plus = if (late) "+${TrainLive.shiftMinutes(status)} min" else null
        // Antonio, 2026-11-21 (provata sul telefono): il ritardo torna nella prima riga accanto a "parte tra",
        // senza l'ora della lettura; nessuna riga in fondo.
        fun head(rest: String): String {
            if (plus != null) marks += LiveMark(LiveCard.HEADER, plus, MarkKind.LATE)
            return listOfNotNull(plus, rest).joinToString(" · ")
        }
        // La notifica chiusa (proposta A, Antonio 2026-11-20): la stessa riga dell'intestazione più binario o prossima
        // fermata; arancio solo il ritardo.
        fun closed(rest: String, extra: String?): String {
            if (plus != null) marks += LiveMark(LiveCard.TEXT, plus, MarkKind.LATE)
            val missing = if (status == null && checked) "ritardo non disponibile" else null
            val onTime = if (status != null && !late && !old) "in orario" else null
            return listOfNotNull(plus, onTime, rest, extra, missing).joinToString(" · ").replaceFirstChar { it.uppercase() }
        }
        // Con il ritardo l'orario del biglietto resta, barrato, davanti a quello vero: "18:27 19:59".
        fun time(planned: Long, real: Long, line: Int): String {
            if (!late) return at(real, zone)
            marks += LiveMark(line, at(planned, zone), MarkKind.STRUCK)
            marks += LiveMark(line, at(real, zone), MarkKind.LATE)
            return "${at(planned, zone)} ${at(real, zone)}"
        }
        val depart = "${time(event.begin, leave, LiveCard.DEPART)} ${short(trip.from) ?: trip.from.orEmpty()}".trim()
        // La stazione d'arrivo prima dell'orario (Antonio, 2026-11-20): gli orari stanno ai due bordi, sotto i pallini.
        val arrive = "${short(trip.to) ?: trip.to} ${time(event.end, end, LiveCard.ARRIVE)}"
        val stops = TrainLive.between(status?.stops.orEmpty(), trip.from, trip.to)
        val points = stops.map { it.at + delay }.filter { it in (leave + 1) until end }
        val stopList = stops.map { LiveStop(at(it.at + delay, zone), TrainLive.pretty(it.name), it.at + delay <= now, at(it.at, zone).takeIf { late }) }
        val lines = emptyList<String>()
        val track = LiveTrack(leave, end, points)
        return when (phase) {
            LivePhase.SOON -> {
                val platform = status?.platform?.let { "binario $it" }
                val waited = (((now - (leave - LEAD_MS)).toDouble() / LEAD_MS) * 100).toInt().coerceIn(0, 100)
                val text = closed("parte ${inText(leave - now)}", platform)
                LiveCard(
                    phase, title = title, text = text,
                    chip = at(leave, zone),
                    countdownTo = leave,
                    progress = waited,
                    lines = lines,
                    late = late,
                    header = head("parte ${inText(leave - now)}"),
                    marks = marks,
                    track = track,
                    depart = depart,
                    arrive = arrive,
                    stops = stopList,
                    sub = seat,
                )
            }
            LivePhase.ON_BOARD -> {
                val progress = (((now - leave).toDouble() / (end - leave)) * 100).toInt().coerceIn(0, 100)
                val arrived = now >= end
                val text = closed(if (arrived) "arrivato a ${short(trip.to) ?: trip.to}" else "arriva ${inText(end - now)}", null)
                LiveCard(
                    phase, title = title, text = text,
                    chip = at(end, zone),
                    countdownTo = end,
                    progress = progress,
                    lines = lines,
                    late = late,
                    header = head(if (arrived) "arrivato" else "arriva ${inText(end - now)}"),
                    marks = marks,
                    track = track,
                    depart = depart,
                    arrive = arrive,
                    stops = stopList,
                    sub = seat,
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

/**
 * Binario, treno, carrozza e posto in una riga sola, accorciati perché ci stiano (versione compatta 1, Antonio
 * 2026-11-21): "Bin. 14 · FR 9541 · carr. 8 · posto 11D".
 */
object SeatText {
    fun line(trip: com.platinum20.dashboard.calendar.Trip, platform: String? = null): String? = listOfNotNull(
        platform?.let { "Bin. $it" },
        trip.train?.let(::train),
        trip.coach?.let { "carr. $it" },
        trip.seat?.let { "posto $it" },
    ).joinToString(" · ").ifEmpty { null }

    private val kinds = listOf(
        "Frecciarossa" to "FR", "Frecciargento" to "FA", "Frecciabianca" to "FB",
        "Intercity Notte" to "ICN", "Intercity" to "IC", "Regionale Veloce" to "RV", "Regionale" to "R",
    )

    /** "Frecciarossa 9541" → "FR 9541"; i nomi che non conosce (Italo) restano come sono. */
    fun train(name: String): String {
        val n = name.trim()
        val hit = kinds.firstOrNull { n.startsWith(it.first, ignoreCase = true) } ?: return n
        return hit.second + n.substring(hit.first.length)
    }
}
