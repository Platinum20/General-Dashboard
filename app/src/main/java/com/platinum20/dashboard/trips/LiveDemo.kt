package com.platinum20.dashboard.trips

import com.platinum20.dashboard.calendar.CalendarEvent
import com.platinum20.dashboard.calendar.Trip

/** Un fotogramma della prova: l'ora finta da mostrare e cosa sapremmo del treno a quell'ora. */
data class DemoFrame(val now: Long, val status: TrainStatus?)

/**
 * La prova della notifica dal vivo (Impostazioni › Notifiche, Antonio 2026-11-20): un treno finto che in poco più
 * di un minuto passa per l'attesa, il ritardo, il viaggio senza rete e l'arrivo in ritardo, con le stesse
 * [LiveTrip.card] e notifica vere. Pura: l'orologio finto parte da [base].
 */
object LiveDemo {
    /** Id finto, fuori dagli eventi veri (i turni sono negativi ma piccoli). */
    const val EVENT_ID = -9_424_242L
    /** Ogni fotogramma resta [FRAME_MS] sullo schermo e fa avanzare l'orologio finto di [STEP_MS]. */
    const val FRAME_MS = 3_000L
    const val STEP_MS = 5 * 60_000L
    private const val MIN = 60_000L

    fun event(base: Long): CalendarEvent {
        val begin = start(base)
        return CalendarEvent(
            EVENT_ID, "Treno per Bologna Centrale", begin, begin + 62 * MIN, allDay = false, location = "", color = 0,
            trip = Trip("Milano Centrale", "Bologna Centrale", "Frecciarossa 9541", coach = "8", seat = "11D"),
        )
    }

    /** La partenza finta: 50 minuti dopo l'inizio della prova, al minuto tondo. */
    private fun start(base: Long) = base - base % MIN + 50 * MIN

    /**
     * La storia: in orario e binario 14 fino a mezz'ora prima; poi 8 minuti di ritardo; a bordo 12; da metà
     * viaggio la rete cade e resta l'ultima lettura ("letto N min fa"); si chiude 5 minuti dopo l'arrivo vero.
     */
    fun frames(base: Long): List<DemoFrame> {
        val e = event(base)
        val stops = listOf(
            TrainStop("MILANO CENTRALE", e.begin), TrainStop("REGGIO EMILIA AV", e.begin + 38 * MIN),
            TrainStop("BOLOGNA CENTRALE", e.end),
        )
        val offline = e.begin + 30 * MIN
        val out = mutableListOf<DemoFrame>()
        var now = base - base % MIN
        while (true) {
            val delay = when { now < e.begin - 30 * MIN -> 0; now < e.begin -> 8; else -> 12 }
            val status = TrainStatus(delay, platform = "14", readAt = minOf(now, offline), stops = stops, until = e.end + delay * MIN)
            if (LiveTrip.phase(e, now, status) == null && out.isNotEmpty()) break
            out += DemoFrame(now, status)
            now += STEP_MS
        }
        return out
    }

    /** Il titolo della prova: "Prova · Treno per Bologna Centrale", così non si confonde con un treno vero. */
    fun title(title: String): String = "Prova · $title"
}
